package io.dataroots.savingstreak.points;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The twelve-month rule itself, at the rule rather than over HTTP.
 *
 * <p>The one test in this repo that is not an API test, and it says why out loud. Every other test
 * drives the whole application over HTTP because that keeps them free of the storage decisions later
 * slices need to change. This rule cannot be reached that way: the only way to move time in this
 * application is {@code POST /api/dev/clock/advance}, which takes whole days and refuses to go
 * backwards, so no request a test can make lands on the far side of an anniversary by an hour, or on
 * 29 February, or in the hour the clocks go forward. The expiry API tests wind the clock a
 * comfortable 379 days precisely so that they are about the rule working rather than about which day
 * of which month the run happens on — which is exactly what makes them blind to the boundary.
 *
 * <p>So the boundary is asserted here, on the two functions that decide it, and nothing else in this
 * class knows anything about the ledger. The prose in {@link PointsExpiry} argues for two decisions —
 * calendar months rather than a count of days, and slack in the cut-off rather than exactness — and
 * both of those arguments are only worth having if something fails when they are undone. Reversing
 * either one fails a test below.
 */
class PointsExpiryTest {

    private static final ZoneId BRUSSELS = SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN;

    /**
     * The spec's own worked example: twelve months lands on the same day of the month it started on.
     *
     * <p>A count of days does not. 15 January 2024 plus 365 days is the 14th, because 2024 is a leap
     * year — and a customer who was promised "twelve months" and read "14 January" on the screen has
     * caught the application out.
     */
    @Test
    void twelve_months_lands_on_the_same_day_of_the_month_a_year_later() {
        assertThat(dayItExpires(brussels(2026, 1, 15, 9, 30)))
                .isEqualTo(LocalDate.of(2027, 1, 15));
        // Across a leap day, which is where a count of 365 days and twelve calendar months part.
        assertThat(dayItExpires(brussels(2024, 1, 15, 9, 30)))
                .isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(dayItExpires(brussels(2023, 3, 1, 9, 30)))
                .isEqualTo(LocalDate.of(2024, 3, 1));
        // And the ordinary case, so a rule that only worked around February would be caught.
        assertThat(dayItExpires(brussels(2026, 7, 4, 23, 59)))
                .isEqualTo(LocalDate.of(2027, 7, 4));
    }

    /**
     * A batch earned on 29 February expires on 28 February, which is the answer somebody looking at a
     * calendar would give — there is no 29th to expire on.
     */
    @Test
    void a_batch_earned_on_a_leap_day_expires_on_the_twenty_eighth() {
        assertThat(dayItExpires(brussels(2024, 2, 29, 12, 0)))
                .isEqualTo(LocalDate.of(2025, 2, 28));
    }

    /**
     * The day is read in the zone this application counts calendars in, not in UTC.
     *
     * <p>A batch earned twenty minutes past midnight in Brussels was earned the previous evening in
     * UTC, and the two readings name different days. Which one the customer is told is the whole of
     * why the day is decided here rather than by whatever machine is drawing the screen.
     */
    @Test
    void the_day_is_read_in_the_zone_the_application_counts_calendars_in() {
        Instant justAfterMidnightInBrussels = brussels(2026, 1, 15, 0, 20);

        assertThat(dayItExpires(justAfterMidnightInBrussels))
                .isEqualTo(LocalDate.of(2027, 1, 15));
        // The same moment, read in UTC, is the 14th. This is the day a page formatting the raw
        // moment in the browser's own zone would have shown a customer outside Brussels.
        assertThat(PointsExpiry.anniversaryOf(justAfterMidnightInBrussels).atZone(ZoneId.of("UTC"))
                .toLocalDate())
                .isEqualTo(LocalDate.of(2027, 1, 14));
    }

    /**
     * The cut-off the sweep queries with is late enough to catch every batch whose anniversary has
     * arrived — including the leap-day batch, which is the case the slack exists for.
     *
     * <p>Counting twelve months forward and twelve months back are not exact inverses. Twelve months
     * back from 28 February 2025 is 28 February 2024, and the batch that expires that day was earned
     * on the 29th — later than the cut-off, and so missed by a query that had no slack. A day of
     * slack would do and there are two; slack in the other direction misses ordinary batches by two
     * days.
     */
    @Test
    void the_cut_off_catches_the_leap_day_batch_on_its_anniversary() {
        Instant earnedOnTheLeapDay = brussels(2024, 2, 29, 12, 0);
        Instant itsAnniversary = PointsExpiry.anniversaryOf(earnedOnTheLeapDay);

        assertThat(earnedOnTheLeapDay)
                .as("the sweep asks for batches earned before the cut-off, so a batch at its own "
                        + "anniversary has to fall on the early side of it")
                .isBefore(PointsExpiry.nothingEarnedAfterThisCanHaveExpiredBy(itsAnniversary));
    }

    /**
     * And it is late enough for every batch, on every day of six years, not only for the ones this
     * test could think of.
     *
     * <p>The invariant the sweep leans on: if a batch's anniversary has arrived by the moment the
     * sweep is running at, the cut-off that sweep queries with is later than the moment the batch was
     * earned. Break that and the batch is never read, and points that should have gone stay
     * spendable — silently, because nothing anywhere would report a row it never saw.
     *
     * <p>Walked hour by hour rather than day by day, because the cases that go wrong are the ones
     * near a boundary: midnight, the leap day, and the two nights a year the clocks move.
     */
    @Test
    void no_batch_whose_anniversary_has_arrived_is_ever_missed_by_the_cut_off() {
        ZonedDateTime earned = brussels(2024, 1, 1, 0, 0).atZone(BRUSSELS);
        ZonedDateTime lastOne = brussels(2028, 1, 1, 0, 0).atZone(BRUSSELS);
        int checked = 0;
        while (earned.isBefore(lastOne)) {
            Instant earnedAt = earned.toInstant();
            Instant anniversary = PointsExpiry.anniversaryOf(earnedAt);
            // The first moment the batch has expired, and the last moment it has not: the cut-off
            // has to catch it at the first and is under no obligation at the second.
            assertThat(earnedAt)
                    .as("a batch earned at " + earnedAt + " has its anniversary at " + anniversary
                            + ", and a sweep run then must be able to see it")
                    .isBefore(PointsExpiry.nothingEarnedAfterThisCanHaveExpiredBy(anniversary));
            assertThat(earnedAt)
                    .as("and a sweep run a year later must still see it")
                    .isBefore(PointsExpiry.nothingEarnedAfterThisCanHaveExpiredBy(
                            anniversary.plus(Duration.ofDays(365))));
            earned = earned.plusHours(1);
            checked++;
        }
        assertThat(checked)
                .as("four years of hours, so the leap day and both clock changes are in there")
                .isGreaterThan(35_000);
    }

    /**
     * The other side of the slack: it is slack and not a second rule. A batch two days short of its
     * anniversary is read by the query and then declined, and the figure that declines it is the
     * anniversary — so the sweep's own decision, not the cut-off, is what twelve months means.
     */
    @Test
    void a_batch_read_by_the_cut_off_but_short_of_its_anniversary_has_not_expired() {
        Instant earnedAt = brussels(2026, 3, 15, 12, 0);
        // A sweep run one day before this batch's anniversary. The cut-off's two days of slack reach
        // past it, so the query hands the batch over.
        Instant aDayEarly = PointsExpiry.anniversaryOf(earnedAt).minus(Duration.ofDays(1));

        assertThat(earnedAt)
                .as("the slack means the query reads it")
                .isBefore(PointsExpiry.nothingEarnedAfterThisCanHaveExpiredBy(aDayEarly));
        assertThat(PointsExpiry.anniversaryOf(earnedAt))
                .as("and the anniversary is what says it stays")
                .isAfter(aDayEarly);
    }

    private static LocalDate dayItExpires(Instant earnedAt) {
        return PointsExpiry.dayOf(PointsExpiry.anniversaryOf(earnedAt));
    }

    private static Instant brussels(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(BRUSSELS).toInstant();
    }
}
