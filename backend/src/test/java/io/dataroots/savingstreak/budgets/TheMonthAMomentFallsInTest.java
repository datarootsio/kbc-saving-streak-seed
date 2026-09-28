package io.dataroots.savingstreak.budgets;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calendar behind every figure this module derives: which month a moment falls in, and where the
 * edges of a month are.
 *
 * <p>A direct unit test rather than an API one, and the exception is the one
 * {@code WhenABillIsDueTest}, {@code WhenIncomeIsDueTest} and {@code HowAnAmountIsSplitTest} already
 * take: this is a function of its arguments with no database, no clock and no HTTP in it, and its
 * interesting cases — the two edges of a month, the hours where Brussels and UTC disagree about
 * which month it is, the two days a year the clocks move — are an hour of round-trips and a wound
 * clock otherwise. What the month read actually does with the answer is asserted over HTTP, where
 * every other claim in this feature is.
 *
 * <p><strong>The zone is the whole of this test.</strong> Every one of these assertions would still
 * pass against a function that read the server's own calendar, on a machine standing in Brussels, on
 * most days of most months. The cases that would not are the ones written out at length below, and
 * they are the reason this class exists rather than a comment saying the zone matters.
 *
 * <p>Covers user stories 7, 9 and 23: a month a customer decides about in advance has to be the
 * month they live in, a figure quoted for a month already gone has to stay quoted for that month,
 * and a category's arithmetic is only "done for me" if the months it is done over are the ones a
 * person would name.
 */
class TheMonthAMomentFallsInTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    /** A moment written the way somebody in Brussels would read it off a clock on the wall. */
    private static Instant inBrusselsAt(int year, int month, int day, int hour, int minute,
                                        int second) {
        return LocalDate.of(year, month, day)
                .atTime(hour, minute, second)
                .atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .toInstant();
    }

    /** The same moment written as the application's clock actually records it, in UTC. */
    private static Instant inUtcAt(String moment) {
        return Instant.parse(moment);
    }

    @Test
    void a_moment_in_the_middle_of_a_month_falls_in_that_month() {
        assertThat(TheMonthAMomentFallsIn.of(inBrusselsAt(2026, 3, 14, 12, 0, 0)))
                .as("the ordinary case, and the one every other assertion here is the edge of")
                .isEqualTo(MARCH);
    }

    @Test
    void a_spend_in_the_last_second_of_the_last_day_falls_in_that_month_and_not_the_next() {
        assertThat(TheMonthAMomentFallsIn.of(inBrusselsAt(2026, 3, 31, 23, 59, 59)))
                .as("somebody spending at a minute to midnight on the thirty-first spent it in "
                        + "March, and a figure that said April would be a month they had no chance "
                        + "to decide about")
                .isEqualTo(MARCH);
        assertThat(TheMonthAMomentFallsIn.of(inBrusselsAt(2026, 4, 1, 0, 0, 0)))
                .as("and the very next second is April, because a month is closed at the bottom and "
                        + "open at the top — no instant is in both and none is in neither")
                .isEqualTo(YearMonth.of(2026, 4));
    }

    @Test
    void a_moment_the_clock_records_as_next_month_is_still_this_month_in_brussels() {
        // 00:30 on 1 April in Brussels is 22:30 on 31 March in UTC. The clock this application
        // stamps a spend with reads the second of those, so a month counted off the reading rather
        // than off the calendar somebody lives in would file this in March.
        assertThat(TheMonthAMomentFallsIn.of(inUtcAt("2026-03-31T22:30:00Z")))
                .as("half past midnight on the first of April is April, wherever the clock the "
                        + "application happens to read thinks it is")
                .isEqualTo(YearMonth.of(2026, 4));
    }

    @Test
    void a_moment_the_clock_records_as_this_month_can_belong_to_the_one_before() {
        // 00:30 on 1 March in UTC is 01:30 on 1 March in Brussels, which is the same month — so
        // the interesting direction is the other one, an hour before midnight UTC on the last day.
        assertThat(TheMonthAMomentFallsIn.of(inUtcAt("2026-02-28T23:30:00Z")))
                .as("half past midnight on the first of March in Brussels, recorded as the "
                        + "twenty-eighth of February in UTC: the customer's month is March")
                .isEqualTo(MARCH);
        assertThat(TheMonthAMomentFallsIn.of(inUtcAt("2026-02-28T22:30:00Z")))
                .as("and an hour earlier is still the twenty-eighth of February in Brussels")
                .isEqualTo(YearMonth.of(2026, 2));
    }

    @Test
    void a_month_begins_at_the_first_moment_of_its_first_day_where_the_customer_stands() {
        assertThat(TheMonthAMomentFallsIn.theMomentItBegins(MARCH))
                .as("midnight in Brussels, which in March is an hour ahead of UTC")
                .isEqualTo(inUtcAt("2026-02-28T23:00:00Z"));
        assertThat(TheMonthAMomentFallsIn.theMomentTheNextOneBegins(MARCH))
                .as("and the top of the window is the moment April begins — after the clocks went "
                        + "forward, so it is two hours ahead of UTC and not one")
                .isEqualTo(inUtcAt("2026-03-31T22:00:00Z"));
    }

    @Test
    void the_window_is_closed_at_the_bottom_and_open_at_the_top() {
        Instant begins = TheMonthAMomentFallsIn.theMomentItBegins(MARCH);
        Instant theNextOneBegins = TheMonthAMomentFallsIn.theMomentTheNextOneBegins(MARCH);

        assertThat(TheMonthAMomentFallsIn.holds(MARCH, begins))
                .as("the moment a month begins is in it")
                .isTrue();
        assertThat(TheMonthAMomentFallsIn.holds(MARCH, begins.minusNanos(1)))
                .as("and the instant before it is not")
                .isFalse();
        assertThat(TheMonthAMomentFallsIn.holds(MARCH, theNextOneBegins.minusNanos(1)))
                .as("the last instant before April begins is March's")
                .isTrue();
        assertThat(TheMonthAMomentFallsIn.holds(MARCH, theNextOneBegins))
                .as("and the moment April begins is April's, so no instant is counted twice and "
                        + "none falls through the gap")
                .isFalse();
    }

    @Test
    void a_month_with_nothing_in_it_still_has_a_window_and_it_is_the_right_length() {
        assertThat(TheMonthAMomentFallsIn.theMomentTheNextOneBegins(YearMonth.of(2024, 2))
                .minusMillis(TheMonthAMomentFallsIn.theMomentItBegins(YearMonth.of(2024, 2))
                        .toEpochMilli()).toEpochMilli())
                .as("a leap February is twenty-nine days long, counted through the calendar rather "
                        + "than by adding a fixed span to a moment")
                .isEqualTo(29L * 24 * 60 * 60 * 1000);
    }

    @Test
    void the_two_days_a_year_the_clocks_move_do_not_move_a_month_boundary() {
        // The clocks go forward on the last Sunday in March and back on the last Sunday in October,
        // so March is an hour short of its days and October an hour long. A boundary worked out by
        // adding thirty-one days to a moment would be an hour out on both.
        assertThat(TheMonthAMomentFallsIn.of(inBrusselsAt(2026, 10, 31, 23, 59, 59)))
                .as("the last second of October, after the clocks went back")
                .isEqualTo(YearMonth.of(2026, 10));
        assertThat(TheMonthAMomentFallsIn.theMomentTheNextOneBegins(YearMonth.of(2026, 10)))
                .as("and November begins at midnight in Brussels, which is one hour ahead of UTC "
                        + "rather than the two October started on")
                .isEqualTo(inUtcAt("2026-10-31T23:00:00Z"));
    }

    @Test
    void a_day_is_read_without_a_zone_because_it_already_has_one() {
        assertThat(TheMonthAMomentFallsIn.of(LocalDate.of(2026, 3, 31)))
                .as("a bill due on the thirty-first is due in March, and there is no time of day "
                        + "here to convert")
                .isEqualTo(MARCH);
    }

    @Test
    void a_month_is_written_down_as_the_day_it_begins_and_read_back_from_it() {
        assertThat(TheMonthAMomentFallsIn.theDayItBegins(MARCH))
                .as("what a budget row carries, because a database has dates and no months")
                .isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(TheMonthAMomentFallsIn.of(TheMonthAMomentFallsIn.theDayItBegins(MARCH)))
                .as("and the two directions are each other's opposite, which is the whole of what "
                        + "makes the column safe to store")
                .isEqualTo(MARCH);
    }

    @Test
    void a_moment_nobody_recorded_is_in_no_month_rather_than_a_thrown_exception() {
        assertThat(TheMonthAMomentFallsIn.holds(MARCH, null))
                .as("a bill occurrence with no settlement moment cannot have been settled in March; "
                        + "the read that walks them filters rather than aborting, because one "
                        + "hand-edited row would otherwise take a page down")
                .isFalse();
    }
}
