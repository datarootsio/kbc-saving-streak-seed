package io.dataroots.savingstreak.automation;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The occurrence calendar behind a saving rule: which days a trigger falls on between two moments,
 * what "the 31st" means in a month that has no 31st, and where the two ends of a catch-up range are.
 *
 * <p>A direct unit test rather than an API one, and the exception is the one
 * {@code LoyaltyAnniversaryTest}, {@code HowTheWeeklyMoneyIsSpentTest} and
 * {@code WhenIncomeIsDueTest} already take: this is a function of its arguments with no database, no
 * clock and no HTTP in it, and its combinatorics — month ends, leap years, the two boundaries of a
 * range — are an hour of wound clocks and round-trips otherwise. What the nightly run does with the
 * answer is asserted over HTTP, where everything else in this feature is.
 *
 * <p><strong>Boundaries are asserted from both sides.</strong> A range open at the bottom and closed
 * at the top is two claims, and each of them passes on its own against a calendar that got the other
 * wrong — so the day before and the day itself are both here, for the cursor and for the moment
 * being run for.
 */
class WhichOccurrencesAreDueTest {

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

    private static List<LocalDate> monthlyOn(Integer dayOfMonth, Instant settledThrough,
                                            Instant now) {
        return WhichOccurrencesAreDue.daysDueBetween(
                RuleTrigger.MONTHLY, null, dayOfMonth, settledThrough, now);
    }

    private static List<LocalDate> weeklyOn(DayOfWeek dayOfWeek, Instant settledThrough, Instant now) {
        return WhichOccurrencesAreDue.daysDueBetween(
                RuleTrigger.WEEKLY, dayOfWeek, null, settledThrough, now);
    }

    // ---------------------------------------------------------------- the month-end clamp

    @Test
    void a_rule_set_for_the_thirty_first_falls_on_the_last_day_of_every_short_month() {
        assertThat(monthlyOn(31, middayOn(2027, 1, 1), middayOn(2027, 6, 30)))
                .as("the 31st is the day the customer said, and a month that has no 31st has its "
                        + "last day instead — which is the banking convention and the only rule "
                        + "that does not skip February outright")
                .containsExactly(
                        LocalDate.of(2027, 1, 31),
                        LocalDate.of(2027, 2, 28),
                        LocalDate.of(2027, 3, 31),
                        LocalDate.of(2027, 4, 30),
                        LocalDate.of(2027, 5, 31),
                        LocalDate.of(2027, 6, 30));
    }

    @Test
    void the_clamp_is_applied_month_by_month_and_never_carried_on_the_rule() {
        assertThat(monthlyOn(31, middayOn(2027, 2, 1), middayOn(2027, 3, 31)))
                .as("February takes the 28th and March goes back to the 31st: a rule clamped once "
                        + "and remembered would move on the 28th for ever after")
                .containsExactly(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31));
    }

    @Test
    void a_rule_set_for_the_thirtieth_is_clamped_in_february_alone() {
        assertThat(monthlyOn(30, middayOn(2027, 1, 1), middayOn(2027, 3, 31)))
                .as("thirty is a day every month but February has")
                .containsExactly(
                        LocalDate.of(2027, 1, 30),
                        LocalDate.of(2027, 2, 28),
                        LocalDate.of(2027, 3, 30));
    }

    @Test
    void a_day_every_month_has_is_never_clamped() {
        assertThat(monthlyOn(15, middayOn(2027, 1, 1), middayOn(2027, 4, 1)))
                .as("nothing is moved for a rule set for a day every month actually has")
                .containsExactly(
                        LocalDate.of(2027, 1, 15),
                        LocalDate.of(2027, 2, 15),
                        LocalDate.of(2027, 3, 15));
    }

    // ---------------------------------------------------------------- leap years

    @Test
    void february_in_a_leap_year_has_a_twenty_ninth_and_the_rule_falls_on_it() {
        assertThat(monthlyOn(31, middayOn(2028, 2, 1), middayOn(2028, 2, 29)))
                .as("2028 is divisible by four, so the last day of its February is the 29th")
                .containsExactly(LocalDate.of(2028, 2, 29));
    }

    @Test
    void a_rule_set_for_the_twenty_ninth_falls_on_the_twenty_eighth_of_a_common_february() {
        assertThat(monthlyOn(29, middayOn(2027, 2, 1), middayOn(2027, 2, 28)))
                .as("2027 is not a leap year, so the 29th its holder named is the 28th")
                .containsExactly(LocalDate.of(2027, 2, 28));
        assertThat(monthlyOn(29, middayOn(2028, 2, 1), middayOn(2028, 2, 28)))
                .as("and in a leap year the 28th is not yet the 29th, so nothing is due on it")
                .isEmpty();
    }

    @Test
    void a_century_year_not_divisible_by_four_hundred_is_not_a_leap_year() {
        assertThat(monthlyOn(31, middayOn(2100, 2, 1), middayOn(2100, 3, 1)))
                .as("2100 is divisible by four and by a hundred but not by four hundred, which is "
                        + "the rule the calendar keeps and this class does not restate")
                .containsExactly(LocalDate.of(2100, 2, 28));
        assertThat(monthlyOn(31, middayOn(2000, 2, 1), middayOn(2000, 3, 1)))
                .as("2000 is divisible by four hundred, so it does have a 29th of February")
                .containsExactly(LocalDate.of(2000, 2, 29));
    }

    @Test
    void the_days_a_leap_year_adds_do_not_move_a_weekly_rule_off_its_weekday() {
        assertThat(weeklyOn(DayOfWeek.TUESDAY, middayOn(2028, 2, 20), middayOn(2028, 3, 10)))
                .as("every day in the answer is the weekday the customer named, across the 29th "
                        + "of February and out the other side")
                .allSatisfy(day -> assertThat(day.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY))
                .containsExactly(
                        LocalDate.of(2028, 2, 22),
                        LocalDate.of(2028, 2, 29),
                        LocalDate.of(2028, 3, 7));
    }

    // ---------------------------------------------------------------- the ends of the range

    @Test
    void a_day_is_due_from_the_moment_it_begins_and_not_before() {
        assertThat(monthlyOn(15, middayOn(2027, 1, 1), startOf(2027, 1, 14).minusNanos(1)))
                .as("the 15th has not begun, so a rule set for it has nothing due")
                .isEmpty();
        assertThat(monthlyOn(15, middayOn(2027, 1, 1), startOf(2027, 1, 15)))
                .as("the moment the day begins it is due — the run happens at two in the morning, "
                        + "and a day due only from midday would never be caught on the day itself")
                .containsExactly(LocalDate.of(2027, 1, 15));
    }

    @Test
    void the_top_of_the_range_is_closed_and_the_day_after_it_is_not_due_yet() {
        assertThat(monthlyOn(15, middayOn(2027, 1, 1), middayOn(2027, 2, 15)))
                .as("the 15th of February has begun by midday on it, so it is in")
                .containsExactly(LocalDate.of(2027, 1, 15), LocalDate.of(2027, 2, 15));
        assertThat(monthlyOn(15, middayOn(2027, 1, 1), startOf(2027, 2, 15).minusNanos(1)))
                .as("a nanosecond before it begins it is not")
                .containsExactly(LocalDate.of(2027, 1, 15));
    }

    @Test
    void the_bottom_of_the_range_is_open_so_the_cursors_own_day_is_behind_it() {
        assertThat(monthlyOn(15, middayOn(2027, 1, 15), middayOn(2027, 1, 31)))
                .as("a rule written at lunchtime on the 15th is an instruction about the future, "
                        + "not a bill for the morning that had already gone")
                .isEmpty();
        assertThat(monthlyOn(15, startOf(2027, 1, 15).minusNanos(1), middayOn(2027, 1, 31)))
                .as("a rule written a nanosecond before that day began does own it")
                .containsExactly(LocalDate.of(2027, 1, 15));
    }

    @Test
    void one_runs_last_occurrence_is_not_the_next_runs_first() {
        Instant settledThrough = middayOn(2027, 3, 15);

        assertThat(monthlyOn(15, settledThrough, middayOn(2027, 4, 14)))
                .as("a run on the 15th settled that day and moved its cursor to the moment it ran")
                .isEmpty();
        assertThat(monthlyOn(15, settledThrough, middayOn(2027, 4, 15)))
                .as("and the next one picks up the next 15th and nothing before it")
                .containsExactly(LocalDate.of(2027, 4, 15));
    }

    @Test
    void a_range_that_has_not_moved_at_all_holds_nothing() {
        Instant theSameMoment = middayOn(2027, 3, 15);

        assertThat(monthlyOn(15, theSameMoment, theSameMoment))
                .as("the second run of a job in one night finds nothing, because no time has passed")
                .isEmpty();
        assertThat(monthlyOn(15, middayOn(2027, 3, 16), middayOn(2027, 3, 15)))
                .as("and a range that runs backwards is not a range at all")
                .isEmpty();
    }

    @Test
    void a_missing_cursor_is_answered_with_nothing_rather_than_thrown_over() {
        assertThat(monthlyOn(15, null, middayOn(2027, 3, 15)))
                .as("a run is one transaction over every standing rule, and one rule with no "
                        + "cursor must not abort the night for everybody")
                .isEmpty();
    }

    @Test
    void a_rule_missing_the_day_its_trigger_needs_falls_due_on_no_day() {
        assertThat(monthlyOn(null, middayOn(2027, 1, 1), middayOn(2027, 6, 1)))
                .as("a monthly rule with no day of the month is an instruction nobody finished "
                        + "writing, and no day is a better answer than every day")
                .isEmpty();
        assertThat(weeklyOn(null, middayOn(2027, 1, 1), middayOn(2027, 6, 1)))
                .as("and so is a weekly rule with no weekday")
                .isEmpty();
    }

    // ---------------------------------------------------------------- a long catch-up

    @Test
    void a_span_of_months_is_caught_up_whole_and_oldest_first() {
        List<LocalDate> due = monthlyOn(10, middayOn(2027, 1, 20), middayOn(2027, 7, 10));

        assertThat(due)
                .as("six months of downtime is six occurrences, not one — the cron never fired for "
                        + "the days it skipped, so the range is the only way any of them fire")
                .hasSize(6);
        assertThat(due)
                .as("oldest first, because that is the order the days actually fell and a history "
                        + "in any other order is one nobody can reconcile")
                .isSorted();
        assertThat(due.get(0)).isEqualTo(LocalDate.of(2027, 2, 10));
        assertThat(due.get(5)).isEqualTo(LocalDate.of(2027, 7, 10));
    }

    @Test
    void a_weekly_rule_caught_up_over_a_year_falls_on_its_weekday_every_week_and_no_other_day() {
        List<LocalDate> due = weeklyOn(DayOfWeek.MONDAY, middayOn(2027, 1, 1), middayOn(2028, 1, 1));

        assertThat(due)
                .as("a year holds fifty-two or fifty-three Mondays and nothing else")
                .hasSize(52)
                .isSorted()
                .allSatisfy(day -> assertThat(day.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY));
        assertThat(due.get(0))
                .as("the first Monday strictly after the cursor")
                .isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(due).doesNotHaveDuplicates();
    }

    // ---------------------------------------------------------------- a payday is not a calendar

    @Test
    void asking_this_calendar_when_a_payday_falls_is_refused_rather_than_answered() {
        assertThatThrownBy(() -> WhichOccurrencesAreDue.daysDueBetween(
                RuleTrigger.ON_PAYDAY, null, 25, middayOn(2027, 1, 1), middayOn(2027, 6, 1)))
                .as("the one mistake this feature has made three times is asking the calendar a "
                        + "question only the record can answer, and a plausible list of days money "
                        + "then moves on is worse than a stack trace")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("theDaysSalaryLandedOn");
    }

    @Test
    void the_days_a_salary_landed_on_are_the_credited_days_the_rule_was_already_standing_for() {
        List<LocalDate> credited = List.of(
                LocalDate.of(2027, 1, 25),
                LocalDate.of(2027, 2, 25),
                LocalDate.of(2027, 3, 25));

        assertThat(WhichOccurrencesAreDue.theDaysSalaryLandedOn(
                credited, middayOn(2027, 1, 25), middayOn(2027, 4, 1)))
                .as("a rule left standing at lunchtime on the 25th of January did not exist for "
                        + "that morning's salary, however late the money was actually credited")
                .containsExactly(LocalDate.of(2027, 2, 25), LocalDate.of(2027, 3, 25));
    }

    @Test
    void a_salary_credited_for_a_day_that_has_not_begun_yet_is_not_due() {
        assertThat(WhichOccurrencesAreDue.theDaysSalaryLandedOn(
                List.of(LocalDate.of(2027, 3, 25)),
                middayOn(2027, 1, 1), startOf(2027, 3, 25).minusNanos(1)))
                .as("closed at the top, the same way the calendar path is")
                .isEmpty();
        assertThat(WhichOccurrencesAreDue.theDaysSalaryLandedOn(
                List.of(LocalDate.of(2027, 3, 25)),
                middayOn(2027, 1, 1), startOf(2027, 3, 25)))
                .containsExactly(LocalDate.of(2027, 3, 25));
    }

    @Test
    void the_days_a_salary_landed_on_are_sorted_and_deduplicated_rather_than_trusted() {
        assertThat(WhichOccurrencesAreDue.theDaysSalaryLandedOn(
                List.of(LocalDate.of(2027, 3, 25), LocalDate.of(2027, 1, 25),
                        LocalDate.of(2027, 3, 25), LocalDate.of(2027, 2, 25)),
                middayOn(2027, 1, 1), middayOn(2027, 4, 1)))
                .as("a day due twice would be a day the unique index over rule and day due then "
                        + "refused mid-run, so it is made impossible rather than guarded against")
                .containsExactly(
                        LocalDate.of(2027, 1, 25),
                        LocalDate.of(2027, 2, 25),
                        LocalDate.of(2027, 3, 25));
    }

    @Test
    void a_payday_rule_that_was_credited_nothing_falls_due_on_nothing() {
        assertThat(WhichOccurrencesAreDue.theDaysSalaryLandedOn(
                List.of(), middayOn(2027, 1, 1), middayOn(2028, 1, 1)))
                .as("a year of no salaries is a year of no occurrences, which is an answer rather "
                        + "than an error: nobody was paid, so nothing was saved out of being paid")
                .isEmpty();
    }

    // ---------------------------------------------------------------- periods, and where a run stops

    @Test
    void the_period_a_rule_moves_in_once_is_its_week_or_its_month() {
        LocalDate aThursday = LocalDate.of(2027, 4, 15);

        assertThat(WhichOccurrencesAreDue.thePeriodItMovesInOnce(RuleTrigger.WEEKLY, aThursday))
                .as("a weekly rule's period is a savings week, so the week it counts as having "
                        + "fired in and the week its deposit counts toward are the same week")
                .isEqualTo(SavingsWeek.containing(aThursday).startsOn());
        assertThat(WhichOccurrencesAreDue.thePeriodItMovesInOnce(RuleTrigger.MONTHLY, aThursday))
                .isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(WhichOccurrencesAreDue.thePeriodItMovesInOnce(RuleTrigger.ON_PAYDAY, aThursday))
                .as("a payday rule moves once a calendar month, the same as a monthly one")
                .isEqualTo(LocalDate.of(2027, 4, 1));
    }

    @Test
    void a_period_ends_on_the_last_day_it_holds_including_a_short_february() {
        assertThat(WhichOccurrencesAreDue.theLastDayOfThePeriodHolding(
                RuleTrigger.MONTHLY, LocalDate.of(2027, 2, 1)))
                .isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(WhichOccurrencesAreDue.theLastDayOfThePeriodHolding(
                RuleTrigger.MONTHLY, LocalDate.of(2028, 2, 1)))
                .as("and a leap February holds one day more")
                .isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(WhichOccurrencesAreDue.theLastDayOfThePeriodHolding(
                RuleTrigger.WEEKLY, LocalDate.of(2027, 4, 15)))
                .isEqualTo(SavingsWeek.containing(LocalDate.of(2027, 4, 15)).endsOn());
    }

    @Test
    void a_run_stopped_at_a_day_settles_that_day_and_nothing_after_it() {
        Instant stoppedAt = WhichOccurrencesAreDue.theMomentThatDayBegins(LocalDate.of(2027, 4, 15));

        assertThat(monthlyOn(15, stoppedAt, middayOn(2027, 6, 30)))
                .as("a capped run leaves its cursor at the start of the last day it fired, and "
                        + "the next run takes every day after it and never that one again — which "
                        + "is the whole of 'the cap delays a catch-up rather than losing it'")
                .containsExactly(LocalDate.of(2027, 5, 15), LocalDate.of(2027, 6, 15));
    }

    @Test
    void a_day_that_a_month_does_not_have_is_clamped_to_the_one_it_ends_on() {
        assertThat(WhichOccurrencesAreDue.dayIn(YearMonth.of(2027, 2), 31))
                .as("YearMonth.atDay would throw, which is right for a date somebody typed and "
                        + "wrong for a recurring instruction")
                .isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(WhichOccurrencesAreDue.dayIn(YearMonth.of(2028, 2), 31))
                .isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(WhichOccurrencesAreDue.dayIn(YearMonth.of(2027, 4), 31))
                .isEqualTo(LocalDate.of(2027, 4, 30));
        assertThat(WhichOccurrencesAreDue.dayIn(YearMonth.of(2027, 5), 31))
                .isEqualTo(LocalDate.of(2027, 5, 31));
    }

    // ---------------------------------------------------------- the window a forecast asks over

    /**
     * The window a preview asks over is named by two days and includes both of them, where a
     * catch-up range is named by two moments and is open at the bottom. The difference is one
     * subtraction, and it is made here so that no caller can forget it: a preview missing the
     * transfer due this morning is the disagreement between the forecast and the firing that this
     * whole class exists to make impossible.
     */
    @Test
    void a_forecast_window_includes_both_the_day_it_opens_on_and_the_day_it_closes_on() {
        assertThat(WhichOccurrencesAreDue.daysDueInTheWindow(RuleTrigger.WEEKLY, DayOfWeek.THURSDAY,
                null, LocalDate.of(2027, 4, 15), LocalDate.of(2027, 4, 29), null))
                .as("the 15th of April 2027 is a Thursday and so is the 29th, and a window drawn "
                        + "between them holds both ends")
                .containsExactly(
                        LocalDate.of(2027, 4, 15),
                        LocalDate.of(2027, 4, 22),
                        LocalDate.of(2027, 4, 29));
    }

    /**
     * And the rule's own cursor still bounds it, so that a rule already settled through this morning
     * is not promised this morning a second time. That is the guarantee that makes a preview and a
     * run agree about the day a customer is looking at right now.
     */
    @Test
    void a_forecast_window_never_reaches_back_past_the_day_the_rule_is_already_settled_through() {
        assertThat(WhichOccurrencesAreDue.daysDueInTheWindow(RuleTrigger.WEEKLY, DayOfWeek.THURSDAY,
                null, LocalDate.of(2027, 4, 15), LocalDate.of(2027, 4, 29),
                middayOn(2027, 4, 15)))
                .as("the rule was settled at midday on the 15th, so the 15th is behind it and only "
                        + "the weeks in front of it are still to come")
                .containsExactly(LocalDate.of(2027, 4, 22), LocalDate.of(2027, 4, 29));
        assertThat(WhichOccurrencesAreDue.daysDueInTheWindow(RuleTrigger.WEEKLY, DayOfWeek.THURSDAY,
                null, LocalDate.of(2027, 4, 15), LocalDate.of(2027, 4, 29),
                startOf(2027, 4, 15)))
                .as("and a cursor sitting exactly where the 15th begins excludes the 15th, which "
                        + "is the same reading a catch-up gives it — the range is open at the "
                        + "bottom, and a run stopped at a day leaves its cursor there precisely so "
                        + "that the day it fired is not offered again")
                .containsExactly(LocalDate.of(2027, 4, 22), LocalDate.of(2027, 4, 29));
        assertThat(WhichOccurrencesAreDue.daysDueInTheWindow(RuleTrigger.WEEKLY, DayOfWeek.THURSDAY,
                null, LocalDate.of(2027, 4, 15), LocalDate.of(2027, 4, 29),
                startOf(2027, 4, 15).minusNanos(1)))
                .as("while a cursor a moment before that day begins keeps it, because it has not "
                        + "been settled for")
                .containsExactly(
                        LocalDate.of(2027, 4, 15),
                        LocalDate.of(2027, 4, 22),
                        LocalDate.of(2027, 4, 29));
    }

    /**
     * And in the other direction: a cursor <em>behind</em> the day the window opens reaches the
     * window back to it, so that the mornings the nightly run still owes are in the forecast.
     *
     * <p>This is the assertion the opposite of which was the defect. A window clamped to the day it
     * opens on drops every occurrence the next run is about to fire, and on a wound clock that is
     * every rule: the clock moves in whole days, the cron never fires for the days it skipped, and
     * between a wind and a run each rule is behind its own cursor. The customer was shown a year of
     * transfers starting next month and then watched the job move money on two mornings the forecast
     * had denied.
     */
    @Test
    void a_forecast_shows_the_mornings_the_run_still_owes_when_the_cursor_is_behind_the_window() {
        assertThat(WhichOccurrencesAreDue.daysDueInTheWindow(RuleTrigger.WEEKLY, DayOfWeek.THURSDAY,
                null, LocalDate.of(2027, 4, 15), LocalDate.of(2027, 4, 29),
                middayOn(2027, 3, 25)))
                .as("settled only through 25 March, so the 1st and the 8th of April are transfers "
                        + "the next run will make and the forecast says so — the run counts from "
                        + "the cursor, and a forecast that counted from today would be answering a "
                        + "different question from the one that moves the money")
                .containsExactly(
                        LocalDate.of(2027, 4, 1),
                        LocalDate.of(2027, 4, 8),
                        LocalDate.of(2027, 4, 15),
                        LocalDate.of(2027, 4, 22),
                        LocalDate.of(2027, 4, 29));
        assertThat(WhichOccurrencesAreDue.theDaysASalaryIsExpectedOn(10, LocalDate.of(2027, 4, 15),
                LocalDate.of(2027, 6, 30), middayOn(2027, 2, 1)))
                .as("and a payday rule's forecast reaches back the same way, or a rule whose holder "
                        + "declared an income two months ago would show none of the paydays it is "
                        + "waiting on")
                .containsExactly(
                        LocalDate.of(2027, 2, 10),
                        LocalDate.of(2027, 3, 10),
                        LocalDate.of(2027, 4, 10),
                        LocalDate.of(2027, 5, 10),
                        LocalDate.of(2027, 6, 10));
    }

    /** The clamp applies to a forecast exactly as it does to a catch-up, and for the same reason. */
    @Test
    void a_forecast_of_a_rule_set_for_the_thirty_first_clamps_it_month_by_month() {
        assertThat(WhichOccurrencesAreDue.daysDueInTheWindow(RuleTrigger.MONTHLY, null, 31,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 4, 30), null))
                .as("the 28th in February and back to the 31st in March, so that a customer "
                        + "looking ahead sees twelve transfers a year rather than seven")
                .containsExactly(
                        LocalDate.of(2027, 1, 31),
                        LocalDate.of(2027, 2, 28),
                        LocalDate.of(2027, 3, 31),
                        LocalDate.of(2027, 4, 30));
    }

    // ------------------------------------------------ what a payday rule is expected to fall on

    /**
     * A payday rule's <em>forecast</em> comes from the declaration, and nothing else can: the salary
     * has not landed and cannot have. {@link WhichOccurrencesAreDue#theDaysSalaryLandedOn} is the
     * other half and is asked in the opposite direction, out of the record, because a firing derived
     * from a day-number moves real money on a morning nobody was paid.
     */
    @Test
    void a_payday_rule_is_expected_on_the_day_its_holder_declared_with_the_same_clamp() {
        assertThat(WhichOccurrencesAreDue.theDaysASalaryIsExpectedOn(31, LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 4, 30), null))
                .containsExactly(
                        LocalDate.of(2027, 1, 31),
                        LocalDate.of(2027, 2, 28),
                        LocalDate.of(2027, 3, 31),
                        LocalDate.of(2027, 4, 30));
    }

    @Test
    void a_payday_rule_whose_holder_has_declared_no_income_is_expected_on_no_day_at_all() {
        assertThat(WhichOccurrencesAreDue.theDaysASalaryIsExpectedOn(null, LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 12, 31), null))
                .as("nothing yet says when that rule moves, which is an answer rather than an "
                        + "error — and is what a page draws \"nothing yet says when this moves\" from")
                .isEmpty();
    }

    @Test
    void the_day_a_moment_falls_on_is_read_in_the_calendar_the_rest_of_the_feature_counts_in() {
        assertThat(WhichOccurrencesAreDue.theDayItFallsOn(startOf(2027, 4, 15)))
                .as("the first moment of a day is that day, which is what makes a lateness of "
                        + "nought days mean settled on the day it was due")
                .isEqualTo(LocalDate.of(2027, 4, 15));
        assertThat(WhichOccurrencesAreDue.theDayItFallsOn(startOf(2027, 4, 16).minusNanos(1)))
                .as("and the last moment of it is still that day")
                .isEqualTo(LocalDate.of(2027, 4, 15));
    }
}
