package io.dataroots.savingstreak.accounts;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window the month-ahead card is counted over: how far it reaches from every day the clock can
 * read, and in particular from the days a shorter month follows.
 *
 * <p>A direct unit test rather than an API one, for the reason {@link WhenABillIsDueTest} gives
 * about its own calendar: this is a function of one argument with no database, no clock and no HTTP
 * in it, and its combinatorics — the 29th, 30th and 31st of every month, February, a leap February —
 * are a day of wound clocks and round-trips otherwise. What the figures counted over the window then
 * come to is asserted over HTTP, in
 * {@link TheMonthAheadWithAShortMonthAheadOfItStillHoldsTheRentAndTheSalaryApiTest} and
 * {@link TheMonthAheadSaysWhatThisMonthHasToCoverApiTest}, where every other claim in this feature
 * is.
 *
 * <p><strong>This is the test the previous attempt at this ticket did not have.</strong>
 * {@code opensOn.plus(Period.ofMonths(1)).minusDays(1)} clamps to the shorter month's last day
 * <em>before</em> the day comes off, so the window lost a day for every day the next month was
 * short, and a bill or a salary falling on the days that dropped off contributed nothing at all to
 * what the month had to cover. The claims below are the promise that arithmetic was supposed to
 * keep, made day by day rather than by reading the expression.
 */
class TheMonthAheadWindowHoldsAWholeMonthEvenWhenTheNextOneIsShorterTest {

    @Test
    void ordinarily_it_closes_the_day_before_this_day_next_month() {
        assertThat(TheMonthAhead.closesOn(LocalDate.of(2026, 1, 15)))
                .as("a window inclusive of both ends that ran to the same date next month would "
                        + "count a bill on today's day of the month twice")
                .isEqualTo(LocalDate.of(2026, 2, 14));
    }

    @Test
    void and_when_next_month_has_no_such_day_it_closes_on_its_last_one() {
        assertThat(TheMonthAhead.closesOn(LocalDate.of(2028, 3, 31)))
                .as("the day before the 31st of April is the 30th of April, because there is no "
                        + "31st of April — taking a day off the clamped date loses one instead")
                .isEqualTo(LocalDate.of(2028, 4, 30));
        assertThat(TheMonthAhead.closesOn(LocalDate.of(2026, 1, 31)))
                .as("and February keeps its last day rather than stopping on the 27th")
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(TheMonthAhead.closesOn(LocalDate.of(2026, 1, 30)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(TheMonthAhead.closesOn(LocalDate.of(2028, 1, 31)))
                .as("a leap February holds the 29th, and the window reaches it")
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void a_month_followed_by_one_at_least_as_long_is_untouched_by_any_of_that() {
        assertThat(TheMonthAhead.closesOn(LocalDate.of(2026, 12, 31)))
                .as("January has a 31st, so this day next month exists and the day comes off it")
                .isEqualTo(LocalDate.of(2027, 1, 30));
        assertThat(TheMonthAhead.closesOn(LocalDate.of(2026, 2, 28)))
                .isEqualTo(LocalDate.of(2026, 3, 27));
    }

    @Test
    void no_day_of_the_month_ever_falls_outside_the_window_from_any_day_of_any_month() {
        // Four years of opening days, which is every arrangement of month lengths there is,
        // including a leap year and the century's own oddities. For each of them, every day a
        // customer may have named for a bill or a payday — the 1st to the 31st, clamped the way the
        // nightly run clamps it — has to fall inside the window at least once. A day of the month
        // that falls in it nowhere is a rent or a salary that contributes nothing to what the month
        // has to cover, which is exactly the defect this ticket was sent back for.
        for (LocalDate opensOn = LocalDate.of(2026, 1, 1);
             opensOn.isBefore(LocalDate.of(2030, 1, 1));
             opensOn = opensOn.plusDays(1)) {
            LocalDate closesOn = TheMonthAhead.closesOn(opensOn);
            assertThat(closesOn)
                    .as("a window that closed before it opened would be a month with nothing in it "
                            + "at all: " + opensOn)
                    .isAfterOrEqualTo(opensOn);
            for (int dayOfMonth = WhenABillIsDue.EARLIEST_DAY_OF_THE_MONTH;
                 dayOfMonth <= WhenABillIsDue.LATEST_DAY_OF_THE_MONTH;
                 dayOfMonth++) {
                assertThat(theDatesThatDayFallsOnIn(opensOn, closesOn, dayOfMonth))
                        .as("the " + dayOfMonth + " of the month, in the window opening on "
                                + opensOn + " and closing on " + closesOn)
                        .isNotEmpty();
            }
        }
    }

    @Test
    void and_falls_in_it_twice_only_where_a_shorter_month_really_does_take_it_twice() {
        // The other half of the promise, and the reason the day comes off at all: a claim counted
        // twice is a month quoted as costing two rents. It can happen only where the next month is
        // too short to hold the day the window opened on, and there it is the truth — standing on
        // the 29th of January, a rent on the 31st leaves on the 31st of January and again on the
        // 28th of February, and both of those are inside the month this window covers.
        for (LocalDate opensOn = LocalDate.of(2026, 1, 1);
             opensOn.isBefore(LocalDate.of(2030, 1, 1));
             opensOn = opensOn.plusDays(1)) {
            LocalDate closesOn = TheMonthAhead.closesOn(opensOn);
            boolean nextMonthIsTooShortToHoldToday = YearMonth.from(opensOn).plusMonths(1)
                    .lengthOfMonth() < opensOn.getDayOfMonth();
            for (int dayOfMonth = WhenABillIsDue.EARLIEST_DAY_OF_THE_MONTH;
                 dayOfMonth <= WhenABillIsDue.LATEST_DAY_OF_THE_MONTH;
                 dayOfMonth++) {
                List<LocalDate> falls = theDatesThatDayFallsOnIn(opensOn, closesOn, dayOfMonth);
                assertThat(falls)
                        .as("the " + dayOfMonth + " of the month, in the window opening on "
                                + opensOn + " and closing on " + closesOn)
                        .hasSizeLessThanOrEqualTo(2);
                if (falls.size() == 2) {
                    assertThat(nextMonthIsTooShortToHoldToday)
                            .as("the " + dayOfMonth + " of the month falls twice in the window "
                                    + opensOn + ".." + closesOn + ", on " + falls + ", and that is "
                                    + "only ever right where the next month has no such day as the "
                                    + "one the window opened on")
                            .isTrue();
                }
            }
        }
    }

    /**
     * The dates a day of the month actually falls on inside the window, read through the very clamp
     * the nightly run reads: the 31st is the 28th in February, and a window is judged on the dates
     * money moves on rather than on the numbers a customer typed.
     */
    private static List<LocalDate> theDatesThatDayFallsOnIn(LocalDate opensOn, LocalDate closesOn,
                                                            int dayOfMonth) {
        List<LocalDate> falls = new ArrayList<>();
        for (YearMonth month = YearMonth.from(opensOn);
             !month.isAfter(YearMonth.from(closesOn));
             month = month.plusMonths(1)) {
            LocalDate fallsOn = WhenABillIsDue.theDayItFallsOnIn(month, dayOfMonth);
            if (!fallsOn.isBefore(opensOn) && !fallsOn.isAfter(closesOn)) {
                falls.add(fallsOn);
            }
        }
        return falls;
    }
}
