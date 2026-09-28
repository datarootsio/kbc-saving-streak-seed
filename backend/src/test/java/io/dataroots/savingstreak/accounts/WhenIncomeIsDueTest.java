package io.dataroots.savingstreak.accounts;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calendar behind a monthly income: which days it falls due on between two moments, and what
 * "the 31st" means in a month that has no 31st.
 *
 * <p>A direct unit test rather than an API one, and the exception is the one
 * {@code LoyaltyAnniversaryTest}, {@code HowTheWeeklyMoneyIsSpentTest} and
 * {@code AReallocationWorthSuggestingTest} already take: this is a function of its arguments with no
 * database, no clock and no HTTP in it, and its combinatorics — month ends, leap years, the two
 * boundaries of a catch-up range — are an hour of round-trips and a wound clock otherwise. What the
 * job actually does with the answer is asserted over HTTP, where everything else in this feature is.
 */
class WhenIncomeIsDueTest {

    /** Midday, so that a moment inside a day is plainly inside it however the zone is read. */
    private static Instant middayOn(int year, int month, int day) {
        return LocalDate.of(year, month, day)
                .atTime(12, 0)
                .atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .toInstant();
    }

    private static Instant startOf(int year, int month, int day) {
        return LocalDate.of(year, month, day)
                .atStartOfDay(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .toInstant();
    }

    @Test
    void a_month_with_no_paydays_in_it_is_a_month_with_nothing_due() {
        assertThat(WhenIncomeIsDue.paydaysBetween(25, middayOn(2025, 3, 1), middayOn(2025, 3, 20)))
                .as("the twenty-fifth has not arrived yet")
                .isEmpty();
    }

    @Test
    void a_payday_is_due_from_the_moment_its_day_begins() {
        assertThat(WhenIncomeIsDue.paydaysBetween(25, middayOn(2025, 3, 1), startOf(2025, 3, 25)))
                .as("a day that has begun is a day the money is owed on; the job runs at one in "
                        + "the morning, so anything later would never be credited on the day itself")
                .containsExactly(LocalDate.of(2025, 3, 25));
    }

    @Test
    void a_payday_whose_day_had_already_begun_when_the_cursor_was_set_is_behind_it() {
        assertThat(WhenIncomeIsDue.paydaysBetween(25, middayOn(2025, 3, 25), middayOn(2025, 3, 26)))
                .as("an income declared at lunchtime on the twenty-fifth is a promise about next "
                        + "month, not a payment for this morning")
                .isEmpty();
    }

    @Test
    void an_income_declared_today_is_not_a_year_of_back_pay() {
        Instant declaredAt = middayOn(2025, 6, 10);

        assertThat(WhenIncomeIsDue.paydaysBetween(1, declaredAt, middayOn(2025, 6, 30)))
                .as("the cursor starts at the declaration, so the six first-of-the-months already "
                        + "behind it are behind it for ever")
                .isEmpty();
        assertThat(WhenIncomeIsDue.paydaysBetween(1, declaredAt, middayOn(2025, 7, 1)))
                .as("and the first one that falls after it is the one that is paid")
                .containsExactly(LocalDate.of(2025, 7, 1));
    }

    @Test
    void three_months_of_paydays_come_back_oldest_first() {
        assertThat(WhenIncomeIsDue.paydaysBetween(15, middayOn(2025, 1, 20), middayOn(2025, 4, 20)))
                .as("the order the days actually fell, which is the only order a history can be "
                        + "reconciled in")
                .containsExactly(
                        LocalDate.of(2025, 2, 15),
                        LocalDate.of(2025, 3, 15),
                        LocalDate.of(2025, 4, 15));
    }

    @Test
    void an_income_due_on_the_thirty_first_is_paid_on_the_last_day_of_a_short_month() {
        assertThat(WhenIncomeIsDue.paydaysBetween(31, middayOn(2025, 1, 1), middayOn(2025, 4, 1)))
                .as("clamping is the banking convention, and the only rule that does not skip "
                        + "February outright")
                .containsExactly(
                        LocalDate.of(2025, 1, 31),
                        LocalDate.of(2025, 2, 28),
                        LocalDate.of(2025, 3, 31));
    }

    @Test
    void the_clamp_finds_the_twenty_ninth_in_a_leap_year() {
        assertThat(WhenIncomeIsDue.paydaysBetween(31, middayOn(2024, 2, 1), middayOn(2024, 3, 1)))
                .as("2024 is a leap year, so the last day of February is the twenty-ninth")
                .containsExactly(LocalDate.of(2024, 2, 29));
    }

    @Test
    void the_clamp_does_not_stick_to_the_day_it_clamped_to() {
        assertThat(WhenIncomeIsDue.paydaysBetween(30, middayOn(2025, 2, 1), middayOn(2025, 4, 1)))
                .as("the day the customer said is kept, so a February clamped to the twenty-eighth "
                        + "is back on the thirtieth in March")
                .containsExactly(LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 30));
    }

    @Test
    void a_year_wound_in_one_jump_produces_a_year_of_paydays() {
        assertThat(WhenIncomeIsDue.paydaysBetween(5, middayOn(2025, 1, 10), middayOn(2026, 1, 10)))
                .as("a trainer who winds a year forward and runs the job once is paid twelve times, "
                        + "because the cron never fired for the days the clock skipped")
                .hasSize(12)
                .startsWith(LocalDate.of(2025, 2, 5))
                .endsWith(LocalDate.of(2026, 1, 5));
    }

    @Test
    void nothing_is_due_when_no_time_has_passed_since_the_account_was_settled() {
        Instant sameMoment = middayOn(2025, 5, 5);

        assertThat(WhenIncomeIsDue.paydaysBetween(5, sameMoment, sameMoment))
                .as("a second run of the job in one night has nothing left to do")
                .isEmpty();
    }

    @Test
    void an_account_with_no_cursor_at_all_has_nothing_due() {
        assertThat(WhenIncomeIsDue.paydaysBetween(5, null, middayOn(2025, 5, 5)))
                .as("a cursor that is not there cannot happen in the application as it ships, and "
                        + "the nightly run is one transaction over every declaration — so a single "
                        + "row without one has to credit nothing rather than throw and cost every "
                        + "other account its night. MonthlyIncome.settledThrough defends against "
                        + "the same null, and the two agree")
                .isEmpty();
    }

    @Test
    void the_next_payday_is_this_months_until_its_day_has_begun() {
        assertThat(WhenIncomeIsDue.theNextPaydayAfter(25, middayOn(2025, 3, 10)))
                .isEqualTo(LocalDate.of(2025, 3, 25));
        assertThat(WhenIncomeIsDue.theNextPaydayAfter(25, middayOn(2025, 3, 25)))
                .as("the day has begun, so what is coming is next month's")
                .isEqualTo(LocalDate.of(2025, 4, 25));
    }

    @Test
    void the_next_payday_after_the_thirty_first_of_january_is_the_end_of_february() {
        assertThat(WhenIncomeIsDue.theNextPaydayAfter(31, middayOn(2025, 2, 1)))
                .as("what a page tells a customer to expect has to be the day the job will credit")
                .isEqualTo(LocalDate.of(2025, 2, 28));
    }

    @Test
    void the_days_a_month_actually_has_are_the_days_an_income_can_be_declared_for() {
        assertThat(WhenIncomeIsDue.isADayOfTheMonth(1)).isTrue();
        assertThat(WhenIncomeIsDue.isADayOfTheMonth(31)).isTrue();
        assertThat(WhenIncomeIsDue.isADayOfTheMonth(0)).isFalse();
        assertThat(WhenIncomeIsDue.isADayOfTheMonth(32)).isFalse();
        assertThat(WhenIncomeIsDue.isADayOfTheMonth(-1)).isFalse();
    }

    @Test
    void every_payday_in_a_range_falls_inside_it() {
        List<LocalDate> due = WhenIncomeIsDue.paydaysBetween(
                28, middayOn(2025, 1, 28), middayOn(2025, 6, 28));

        assertThat(due)
                .as("open at the bottom and closed at the top, so one run's last payday is never "
                        + "the next run's first")
                .containsExactly(
                        LocalDate.of(2025, 2, 28),
                        LocalDate.of(2025, 3, 28),
                        LocalDate.of(2025, 4, 28),
                        LocalDate.of(2025, 5, 28),
                        LocalDate.of(2025, 6, 28));
    }
}
