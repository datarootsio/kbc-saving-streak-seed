package io.dataroots.savingstreak.accounts;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calendar behind a recurring bill: which dates it falls due on between two moments, and what
 * "the 31st" means in a month that has no 31st.
 *
 * <p>A direct unit test rather than an API one, and the exception is the one
 * {@link WhenIncomeIsDueTest}, {@code LoyaltyAnniversaryTest} and
 * {@code HowTheWeeklyMoneyIsSpentTest} already take: this is a function of its arguments with no
 * database, no clock and no HTTP in it, and its combinatorics — month ends, leap years, the two
 * boundaries of a catch-up range — are an hour of round-trips and a wound clock otherwise. What the
 * nightly run actually does with the answer is asserted over HTTP, where every other claim in this
 * feature is.
 *
 * <p>It sits beside the income calendar's test deliberately. The two classes are separate because
 * the salary that lands and the rent that leaves are separate features that happen to count months
 * the same way, and a pair of tests making the same claims side by side is what keeps the two
 * honest: a clamp changed in one and not the other fails here rather than in February.
 */
class WhenABillIsDueTest {

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
    void a_month_whose_day_has_not_arrived_is_a_month_with_nothing_due() {
        assertThat(WhenABillIsDue.dueDatesBetween(25, middayOn(2025, 3, 1), middayOn(2025, 3, 20)))
                .as("the twenty-fifth has not arrived, and a rent taken early is a rent taken on a "
                        + "day nobody named")
                .isEmpty();
    }

    @Test
    void a_bill_is_due_from_the_moment_its_day_begins() {
        assertThat(WhenABillIsDue.dueDatesBetween(25, middayOn(2025, 3, 1), startOf(2025, 3, 25)))
                .as("a day that has begun is a day the money is owed on; the job runs at half past "
                        + "two in the morning, so anything later would never be taken on the day "
                        + "itself")
                .containsExactly(LocalDate.of(2025, 3, 25));
    }

    @Test
    void a_date_that_had_already_begun_when_the_bill_was_declared_is_behind_it() {
        assertThat(WhenABillIsDue.dueDatesBetween(25, middayOn(2025, 3, 25), middayOn(2025, 3, 26)))
                .as("a bill declared at lunchtime on the twenty-fifth is a promise about next "
                        + "month, not a debit for this morning")
                .isEmpty();
    }

    @Test
    void a_bill_declared_today_is_not_a_year_of_back_rent() {
        Instant declaredAt = middayOn(2025, 6, 10);

        assertThat(WhenABillIsDue.dueDatesBetween(1, declaredAt, middayOn(2025, 6, 30)))
                .as("the cursor starts at the declaration, so the six first-of-the-months already "
                        + "behind it are behind it for ever")
                .isEmpty();
        assertThat(WhenABillIsDue.dueDatesBetween(1, declaredAt, middayOn(2025, 7, 1)))
                .as("and the first one that falls after it is the one that is taken")
                .containsExactly(LocalDate.of(2025, 7, 1));
    }

    @Test
    void two_months_of_due_dates_come_back_oldest_first() {
        assertThat(WhenABillIsDue.dueDatesBetween(10, middayOn(2025, 1, 20), middayOn(2025, 3, 20)))
                .as("the order the dates actually fell, which is the only order arrears can be "
                        + "built in and the only one a bank statement can be reconciled against")
                .containsExactly(LocalDate.of(2025, 2, 10), LocalDate.of(2025, 3, 10));
    }

    @Test
    void the_thirty_first_falls_on_the_twenty_eighth_of_an_ordinary_february() {
        assertThat(WhenABillIsDue.dueDatesBetween(31, middayOn(2025, 2, 1), middayOn(2025, 2, 28)))
                .as("February has no 31st, and clamping is the only rule that does not skip it "
                        + "outright — a rent taken on the last day of the month is taken twelve "
                        + "times a year, not seven")
                .containsExactly(LocalDate.of(2025, 2, 28));
    }

    @Test
    void the_thirty_first_falls_on_the_twenty_ninth_of_a_leap_february() {
        assertThat(WhenABillIsDue.dueDatesBetween(31, middayOn(2024, 2, 1), middayOn(2024, 2, 29)))
                .as("2024 is a leap year, so the last day February has is the 29th")
                .containsExactly(LocalDate.of(2024, 2, 29));
    }

    @Test
    void the_thirty_first_is_taken_once_in_a_thirty_day_month_and_back_on_the_thirty_first_after() {
        assertThat(WhenABillIsDue.dueDatesBetween(31, middayOn(2025, 3, 31), middayOn(2025, 5, 31)))
                .as("April has thirty days, so the clamp names the 30th and names it once; the "
                        + "clamp is made month by month, so May is the 31st again")
                .containsExactly(LocalDate.of(2025, 4, 30), LocalDate.of(2025, 5, 31));
    }

    @Test
    void the_clamp_is_the_same_arithmetic_whoever_asks_for_it() {
        assertThat(WhenABillIsDue.theDayItFallsOnIn(YearMonth.of(2025, 2), 31))
                .as("one place decides what the 31st means in a short month, so that the nightly "
                        + "run and a forecast of the year ahead cannot answer differently")
                .isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(WhenABillIsDue.theDayItFallsOnIn(YearMonth.of(2025, 3), 31))
                .isEqualTo(LocalDate.of(2025, 3, 31));
    }

    @Test
    void a_stretch_that_has_not_moved_has_nothing_due_in_it() {
        Instant aMoment = middayOn(2025, 4, 10);

        assertThat(WhenABillIsDue.dueDatesBetween(10, aMoment, aMoment))
                .as("the ordinary answer for a second run of the job in one night: nothing has "
                        + "passed since the bill was last settled")
                .isEmpty();
    }

    @Test
    void a_bill_with_no_cursor_at_all_has_nothing_due_rather_than_a_century_of_back_rent() {
        assertThat(WhenABillIsDue.dueDatesBetween(1, null, middayOn(2025, 4, 10)))
                .as("unreachable in the application as it ships, and answered rather than thrown "
                        + "over: the nightly run is one transaction over every standing bill, so a "
                        + "row with no cursor would otherwise take no bill at all that night")
                .isEmpty();
    }

    @Test
    void the_first_and_the_thirty_first_are_days_a_bill_can_go_out_on_and_the_thirty_second_is_not() {
        assertThat(WhenABillIsDue.isADayOfTheMonth(1)).isTrue();
        assertThat(WhenABillIsDue.isADayOfTheMonth(31))
                .as("a rent taken on the last day of the month is declared as the 31st, and "
                        + "refusing it would make a real bill undeclarable")
                .isTrue();
        assertThat(WhenABillIsDue.isADayOfTheMonth(0)).isFalse();
        assertThat(WhenABillIsDue.isADayOfTheMonth(32)).isFalse();
    }
}
