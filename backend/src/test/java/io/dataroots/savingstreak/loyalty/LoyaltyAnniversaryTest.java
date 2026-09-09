package io.dataroots.savingstreak.loyalty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The recurring twelve-month clock itself, at the rule rather than over HTTP.
 *
 * <p>Beside {@code PointsExpiryTest}, and here for the same reason it is. Every other test in this
 * repo drives the whole application over HTTP, which keeps them free of the storage decisions later
 * slices need to change; this rule cannot be reached that way. The only way to move time in this
 * application is {@code POST /api/dev/clock/advance}, which takes whole days and refuses to go
 * backwards, so no request a test can make lands a deposit on 29 February and then walks it through
 * four Februaries. The loyalty API tests wind the clock a comfortable number of days past each
 * anniversary precisely so that they are about the rule working rather than about which day of which
 * month the run happens on — which is exactly what makes them blind to the calendar cases.
 *
 * <p>So the calendar is asserted here, on the two functions that decide it. The prose in
 * {@link LoyaltyAnniversary} argues for three decisions — calendar months rather than days, twelve
 * months multiplied out rather than added repeatedly, and slack in the cut-off rather than exactness
 * — and each argument is only worth having if something fails when it is undone. Reversing any one
 * of them fails a test below.
 */
class LoyaltyAnniversaryTest {

    private static final ZoneId BRUSSELS = SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN;

    /**
     * Twelve months multiplied out: every anniversary lands on the day of the month the money landed
     * on, and the third falls exactly two years after the first.
     *
     * <p>A count of days does not. 15 January 2024 plus 365 days is the 14th, because 2024 is a leap
     * year, and a customer promised "a year" who is paid on the 14th has caught the application out.
     */
    @Test
    void every_anniversary_lands_on_the_day_of_the_month_the_money_landed_on() {
        Instant landed = brussels(2026, 1, 15, 9, 30);

        assertThat(dayOfAnniversary(landed, 1)).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(dayOfAnniversary(landed, 2)).isEqualTo(LocalDate.of(2028, 1, 15));
        assertThat(dayOfAnniversary(landed, 3)).isEqualTo(LocalDate.of(2029, 1, 15));
        assertThat(dayOfAnniversary(landed, 10)).isEqualTo(LocalDate.of(2036, 1, 15));

        // The spec's own promise about the recurring clock, said as arithmetic: the tenth is exactly
        // nine years after the first, so nothing has drifted along the way.
        assertThat(dayOfAnniversary(landed, 3))
                .isEqualTo(dayOfAnniversary(landed, 1).plusYears(2));
        assertThat(dayOfAnniversary(landed, 10))
                .isEqualTo(dayOfAnniversary(landed, 1).plusYears(9));

        // Across a leap day, which is where a count of 365 days and twelve calendar months part.
        assertThat(dayOfAnniversary(brussels(2024, 1, 15, 9, 30), 1))
                .isEqualTo(LocalDate.of(2025, 1, 15));
    }

    /**
     * A deposit made on 29 February has its anniversaries clamped to the 28th in the years without a
     * 29th, and lands back on the 29th in the leap year — which is the answer somebody reading a
     * calendar would give.
     *
     * <p>And this is why the ordinal is multiplied into the span rather than added a year at a time.
     * Clamping is not associative: 29 February plus twelve months is the 28th, and the 28th plus
     * twelve months is the 28th for ever after. Adding repeatedly would put the fourth anniversary
     * of a leap-day deposit on 28 February 2024 and keep it a day early for the rest of the
     * deposit's life; counted from the day the money landed, the clamp applies to one addition and
     * undoes itself.
     */
    @Test
    void a_deposit_made_on_a_leap_day_is_clamped_to_the_twenty_eighth_and_comes_back() {
        Instant landedOnTheLeapDay = brussels(2020, 2, 29, 12, 0);

        assertThat(dayOfAnniversary(landedOnTheLeapDay, 1)).isEqualTo(LocalDate.of(2021, 2, 28));
        assertThat(dayOfAnniversary(landedOnTheLeapDay, 2)).isEqualTo(LocalDate.of(2022, 2, 28));
        assertThat(dayOfAnniversary(landedOnTheLeapDay, 3)).isEqualTo(LocalDate.of(2023, 2, 28));
        assertThat(dayOfAnniversary(landedOnTheLeapDay, 4))
                .as("the fourth is a 29 February again, which repeated addition could never reach")
                .isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(dayOfAnniversary(landedOnTheLeapDay, 8)).isEqualTo(LocalDate.of(2028, 2, 29));

        // The end of a shorter month, for a rule that only worked around February.
        assertThat(dayOfAnniversary(brussels(2026, 3, 31, 8, 0), 1))
                .isEqualTo(LocalDate.of(2027, 3, 31));
    }

    /** There is no anniversary before the first, and asking for one is a mistake in the caller. */
    @Test
    void there_is_no_anniversary_before_the_first() {
        assertThatThrownBy(() -> LoyaltyAnniversary.anniversaryOf(brussels(2026, 1, 15, 9, 30), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first");
    }

    /**
     * How many anniversaries a deposit has passed by a given moment — the figure that decides how
     * many bonuses a single sweep pays.
     *
     * <p>An anniversary falling exactly at the moment being judged has arrived, the same way the
     * points sweep treats a batch whose anniversary is exactly now as expired. A minute short of it
     * has not.
     */
    @Test
    void the_ordinal_of_a_given_date_is_how_many_anniversaries_have_arrived() {
        Instant landed = brussels(2026, 1, 15, 9, 30);

        assertThat(LoyaltyAnniversary.anniversariesPassedBy(landed, landed))
                .as("nothing is owed on the day the money lands")
                .isZero();
        assertThat(LoyaltyAnniversary.anniversariesPassedBy(landed, brussels(2027, 1, 15, 9, 29)))
                .as("a minute short of twelve months is short of twelve months")
                .isZero();
        assertThat(LoyaltyAnniversary.anniversariesPassedBy(landed, brussels(2027, 1, 15, 9, 30)))
                .as("and the anniversary itself has arrived")
                .isEqualTo(1);
        assertThat(LoyaltyAnniversary.anniversariesPassedBy(landed, brussels(2028, 1, 15, 9, 29)))
                .isEqualTo(1);
        assertThat(LoyaltyAnniversary.anniversariesPassedBy(landed, brussels(2029, 6, 1, 0, 0)))
                .as("three years on, three anniversaries are owed and one sweep pays them all")
                .isEqualTo(3);
        assertThat(LoyaltyAnniversary.anniversariesPassedBy(landed, brussels(2036, 1, 15, 12, 0)))
                .isEqualTo(10);

        // A clock a trainer wound forward, paid money in on, and wound back again leaves a deposit
        // dated in the future. It is owed nothing rather than a negative number of anniversaries.
        assertThat(LoyaltyAnniversary.anniversariesPassedBy(landed, brussels(2020, 1, 1, 0, 0)))
                .isZero();
    }

    /**
     * The leap-day case that month-counting arithmetic gets wrong, which is why the ordinal is
     * counted by walking the anniversaries rather than by dividing months.
     *
     * <p>29 February 2024 to 28 February 2025 is eleven months and thirty days to
     * {@code ChronoUnit.MONTHS}, so a count that divided months by twelve would say this deposit is
     * owed nothing — on the very day its first anniversary falls.
     */
    @Test
    void the_leap_day_deposit_is_owed_its_anniversary_on_the_day_it_falls() {
        Instant landedOnTheLeapDay = brussels(2024, 2, 29, 12, 0);

        assertThat(LoyaltyAnniversary.anniversariesPassedBy(
                landedOnTheLeapDay, brussels(2025, 2, 28, 12, 0)))
                .isEqualTo(1);
        assertThat(LoyaltyAnniversary.anniversariesPassedBy(
                landedOnTheLeapDay, brussels(2025, 2, 28, 11, 59)))
                .isZero();
    }

    /**
     * The cut-off the sweep queries with is early enough to catch every deposit with an anniversary
     * to pay — including the leap-day deposit, which is the case the slack exists for.
     *
     * <p>Counting twelve months forward and twelve months back are not exact inverses. Twelve months
     * back from 28 February 2025 is 28 February 2024, and the deposit that pays that day landed on
     * the 29th — later than the cut-off, and so missed by a query with no slack. A day of slack
     * would do and there are two.
     */
    @Test
    void the_cut_off_catches_the_leap_day_deposit_on_its_anniversary() {
        Instant landedOnTheLeapDay = brussels(2024, 2, 29, 12, 0);
        Instant itsFirstAnniversary = LoyaltyAnniversary.anniversaryOf(landedOnTheLeapDay, 1);

        assertThat(landedOnTheLeapDay)
                .as("the sweep asks for deposits that landed before the cut-off, so a deposit at "
                        + "its own anniversary has to fall on the early side of it")
                .isBefore(LoyaltyAnniversary.nothingLandedAfterThisCanHaveAnAnniversaryBy(
                        itsFirstAnniversary));
    }

    /**
     * And it is early enough for every deposit, on every hour of four years, not only for the ones
     * this test could think of.
     *
     * <p>The invariant the sweep leans on: if a deposit's first anniversary has arrived by the moment
     * the sweep is running at, the cut-off that sweep queries with is later than the moment the
     * money landed. Break it and the deposit is never read, and a bonus that was owed is never paid
     * — silently, because nothing anywhere would report a row it never saw. Only the first
     * anniversary needs checking: a deposit whose second or tenth has arrived is older still, and so
     * further inside the same window.
     *
     * <p>Walked hour by hour rather than day by day, because the cases that go wrong are the ones
     * near a boundary: midnight, the leap day, and the two nights a year the clocks move.
     */
    @Test
    void no_deposit_whose_anniversary_has_arrived_is_ever_missed_by_the_cut_off() {
        ZonedDateTime landed = brussels(2024, 1, 1, 0, 0).atZone(BRUSSELS);
        ZonedDateTime lastOne = brussels(2028, 1, 1, 0, 0).atZone(BRUSSELS);
        int checked = 0;
        while (landed.isBefore(lastOne)) {
            Instant landedAt = landed.toInstant();
            Instant anniversary = LoyaltyAnniversary.anniversaryOf(landedAt, 1);
            assertThat(landedAt)
                    .as("a deposit made at " + landedAt + " has its first anniversary at "
                            + anniversary + ", and a sweep run then must be able to see it")
                    .isBefore(LoyaltyAnniversary.nothingLandedAfterThisCanHaveAnAnniversaryBy(
                            anniversary));
            assertThat(LoyaltyAnniversary.anniversariesPassedBy(landedAt, anniversary))
                    .as("and must find exactly one anniversary owed on it")
                    .isEqualTo(1);
            landed = landed.plusHours(1);
            checked++;
        }
        assertThat(checked)
                .as("four years of hours, so the leap day and both clock changes are in there")
                .isGreaterThan(35_000);
    }

    /**
     * The other side of the slack: it is slack and not a second rule. A deposit two days short of
     * its anniversary is read by the query and then declined, and the figure that declines it is the
     * anniversary — so the sweep's own decision, not the cut-off, is what twelve months means.
     */
    @Test
    void a_deposit_read_by_the_cut_off_but_short_of_its_anniversary_is_owed_nothing() {
        Instant landedAt = brussels(2026, 3, 15, 12, 0);
        // A sweep run one day before this deposit's first anniversary. The cut-off's two days of
        // slack reach past it, so the query hands the deposit over.
        Instant aDayEarly = LoyaltyAnniversary.anniversaryOf(landedAt, 1).minusSeconds(86_400);

        assertThat(landedAt)
                .as("the slack means the query reads it")
                .isBefore(LoyaltyAnniversary.nothingLandedAfterThisCanHaveAnAnniversaryBy(aDayEarly));
        assertThat(LoyaltyAnniversary.anniversariesPassedBy(landedAt, aDayEarly))
                .as("and the anniversary is what says it pays nothing yet")
                .isZero();
    }

    /**
     * Which anniversary the calendar has coming for a deposit — the one after the last that has
     * arrived, counted off the same walk that says how many have arrived.
     *
     * <p>A calendar reading, and only that. It moves on the instant an anniversary falls, which is
     * hours before the overnight sweep pays it, so it is not on its own the anniversary that pays
     * next: crossing it with the record of what has been paid is
     * {@code LoyaltyService.whenTheDepositsInAnAccountNextPay}'s job, and
     * {@code TheHistorySaysWhatEachDepositHasBeenPaidAndWhenItNextPaysApiTest} is where that gap is
     * asserted. What is asserted here is that the two halves of the walk agree: the ordinal coming is
     * one more than the ordinal passed, at the minute either side of an anniversary.
     */
    @Test
    void the_anniversary_the_calendar_has_coming_is_the_one_after_the_last_that_arrived() {
        Instant landed = brussels(2026, 1, 15, 9, 30);

        assertThat(LoyaltyAnniversary.theAnniversaryAfterTheOnesThatHaveArrived(landed, landed))
                .as("a deposit made a moment ago is counting towards its first")
                .isEqualTo(1);
        assertThat(LoyaltyAnniversary.theAnniversaryAfterTheOnesThatHaveArrived(
                landed, brussels(2027, 1, 15, 9, 29)))
                .as("and still is, a minute short of it")
                .isEqualTo(1);
        assertThat(LoyaltyAnniversary.theAnniversaryAfterTheOnesThatHaveArrived(
                landed, brussels(2027, 1, 15, 9, 30)))
                .as("the first has arrived, so the calendar has moved on to the second — whether the "
                        + "first has been paid is a question this function does not ask")
                .isEqualTo(2);
        assertThat(LoyaltyAnniversary.theAnniversaryAfterTheOnesThatHaveArrived(
                landed, brussels(2036, 1, 15, 12, 0)))
                .as("ten years in, a deposit is not out of anniversaries: it is one year from its "
                        + "eleventh")
                .isEqualTo(11);

        // And the date that comes back is the one a calendar gives, which is the whole point of
        // asking this rather than adding a year to today.
        assertThat(dayOfAnniversary(landed, LoyaltyAnniversary.theAnniversaryAfterTheOnesThatHaveArrived(
                landed, brussels(2027, 6, 1, 0, 0))))
                .isEqualTo(LocalDate.of(2028, 1, 15));
    }

    /**
     * The day an anniversary falls on is read in the zone this application counts calendars in, not
     * in whatever zone the machine reporting it happens to be set to.
     *
     * <p>Money paid in just after midnight on a summer night in Brussels landed at half past ten the
     * evening before in UTC, and its anniversary is a Brussels date. A page or a service reading
     * that moment in UTC would tell the customer the day before the one they were promised.
     */
    @Test
    void the_day_an_anniversary_falls_on_is_read_in_the_zone_the_calendar_is_read_in() {
        Instant justAfterMidnightInSummer = brussels(2026, 7, 15, 0, 30);
        Instant itsFirstAnniversary = LoyaltyAnniversary.anniversaryOf(justAfterMidnightInSummer, 1);

        assertThat(LoyaltyAnniversary.dayOf(itsFirstAnniversary))
                .as("the day the customer would write on a calendar")
                .isEqualTo(LocalDate.of(2027, 7, 15));
        assertThat(itsFirstAnniversary.atZone(ZoneId.of("UTC")).toLocalDate())
                .as("and the day a reader in the wrong zone would have reported, which is not it")
                .isEqualTo(LocalDate.of(2027, 7, 14));
    }

    private static LocalDate dayOfAnniversary(Instant landedAt, int ordinal) {
        return LoyaltyAnniversary.anniversaryOf(landedAt, ordinal).atZone(BRUSSELS).toLocalDate();
    }

    private static Instant brussels(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(BRUSSELS).toInstant();
    }
}
