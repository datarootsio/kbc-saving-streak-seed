package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.goals.HowTheWeeklyMoneyIsSpent.AGoalCompetingForIt;
import io.dataroots.savingstreak.goals.HowTheWeeklyMoneyIsSpent.TheWeeklyMoneySpent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three passes asserted directly, for the cases the HTTP seam cannot reach.
 *
 * <p>The same bargain {@code TimelineHorizonTest} and {@code PointsExpiryTest} strike. Everything
 * about which goal comes up short is asserted over HTTP in
 * {@code ThePlanSaysWhatEachGoalGetsEachWeekApiTest}, where it belongs; what no request can set up
 * is a goal whose deadline has already gone by, because a deadline in the past is refused when a
 * goal is opened and the only way to move time is a development endpoint that winds whole days
 * forward for the entire application. A deadline that went by while the goal stayed open is an
 * ordinary state of affairs — a deadline is soft, and nothing in this module closes a goal because a
 * date passed — and dividing by the nought weeks left is the arithmetic that would fail at a demo
 * rather than here.
 *
 * <p>Which Monday the weeks are counted from is the other one, and it is the reason this class has
 * a fixed {@code TODAY} at all. Over HTTP it is a goal opened on an unknown weekday against a clock
 * other tests have wound, so a test naming a date either side of a week boundary would be asserting
 * about the day the run happened on. That the count starts on this week's Monday rather than on
 * today is what makes the plan and {@code WhenAGoalWillBeReached}'s projection agree; the agreement
 * itself is pinned end to end in {@code WhenAGoalWillBeReachedTest}.
 */
class HowTheWeeklyMoneyIsSpentTest {

    private static final long AN_ACCOUNT = 1;

    /** A Thursday, so that the Monday the weeks are counted from is visibly not the day asked about. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    /** The Monday that week began on, named once so every figure below can be checked by hand. */
    private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 9, 14);

    @Test
    void a_deadline_exactly_a_week_away_is_one_whole_week_to_save_in() {
        assertThat(whatOneDatedGoalGets("100.00", TODAY.plusDays(7), "500.00"))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void a_deadline_a_day_past_a_week_away_is_still_the_one_monday_that_beats_it() {
        // Restated when ticket 06 moved this count onto the Monday the projection is counted from.
        // It used to read eight days as two whole weeks and give the goal 50.00 — but the money
        // arrives on Mondays, and the only Monday on or before 2026-09-25 is 2026-09-21, one week
        // on. A goal given 50.00 would not have it all until 2026-09-28, three days late, and the
        // same read would have said so. One week, and the whole of what it needs.
        assertThat(whatOneDatedGoalGets("100.00", TODAY.plusDays(8), "500.00"))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void a_deadline_reaching_the_second_monday_is_two_whole_weeks_rather_than_one() {
        // The other side of that boundary, and what the old test was reaching for: a goal that
        // genuinely has two Mondays to arrive on is not asked for everything this week, because
        // asking would crowd out every goal under it for nothing.
        assertThat(whatOneDatedGoalGets("100.00", THIS_MONDAY.plusWeeks(2), "500.00"))
                .isEqualByComparingTo("50.00");
        assertThat(whatOneDatedGoalGets("100.00", THIS_MONDAY.plusWeeks(2).plusDays(6), "500.00"))
                .describedAs("and every day up to the Sunday before the third Monday is still two")
                .isEqualByComparingTo("50.00");
    }

    @Test
    void the_weeks_left_are_counted_from_this_monday_and_not_from_today() {
        // The bug ticket 06's review sent the work back for, pinned from this side. Counted from
        // today, 2026-10-02 is fifteen days off, three whole weeks and a minimum of 33.34 — and
        // 100.00 at 33.34 a week is three Mondays on, 2026-10-05, three days late. Counted from
        // 2026-09-14 it is two weeks and 50.00, and 50.00 has the money all there by 2026-09-28,
        // on or before the day it is wanted by.
        assertThat(THIS_MONDAY.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(whatOneDatedGoalGets("100.00", THIS_MONDAY.plusDays(18), "500.00"))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void a_deadline_today_leaves_one_week_rather_than_none() {
        assertThat(whatOneDatedGoalGets("30.00", TODAY, "500.00"))
                .describedAs("the whole of what is left is wanted now, and dividing by nought "
                        + "weeks is not an answer")
                .isEqualByComparingTo("30.00");
    }

    @Test
    void a_deadline_that_went_by_while_the_goal_stayed_open_still_asks_for_the_whole_remainder() {
        assertThat(whatOneDatedGoalGets("30.00", TODAY.minusDays(40), "500.00"))
                .describedAs("a deadline is soft: the goal is still being saved for, and what it "
                        + "needs it needs now")
                .isEqualByComparingTo("30.00");
    }

    @Test
    void a_dated_goal_takes_only_its_minimum_even_when_there_is_far_more_to_give() {
        // Which is the whole reason the second pass is not a waterfall: a goal that took everything
        // it needed would leave the goals under it nothing, and this one does not need everything
        // this week to arrive in four.
        assertThat(whatOneDatedGoalGets("100.00", TODAY.plusDays(28), "500.00"))
                .isEqualByComparingTo("25.00");
    }

    @Test
    void nothing_is_given_out_at_all_when_no_capacity_has_been_declared() {
        TheWeeklyMoneySpent spent = HowTheWeeklyMoneyIsSpent.asAt(AN_ACCOUNT, null,
                List.of(aGoal(7L, 1, "100.00", TODAY.plusDays(7))), TODAY);

        assertThat(spent.forGoal(7L))
                .describedAs("absent rather than zero: nobody has said how fast anything fills")
                .isNull();
        assertThat(spent.weeklyCapacity()).isNull();
    }

    @Test
    void a_goal_the_plan_never_saw_is_given_nothing_once_a_capacity_exists() {
        TheWeeklyMoneySpent spent = HowTheWeeklyMoneyIsSpent.asAt(AN_ACCOUNT, new BigDecimal("50.00"),
                List.of(aGoal(7L, 1, "100.00", null)), TODAY);

        assertThat(spent.forGoal(99L))
                .describedAs("an abandoned goal has left the order and never reaches the passes, "
                        + "and what it is given is nothing rather than nothing said")
                .isEqualByComparingTo("0.00");
    }

    @Test
    void a_finished_goal_claims_no_minimum_and_takes_no_surplus() {
        TheWeeklyMoneySpent spent = HowTheWeeklyMoneyIsSpent.asAt(AN_ACCOUNT, new BigDecimal("80.00"),
                List.of(aGoal(7L, 1, "0.00", TODAY.plusDays(7)), aGoal(8L, 2, "400.00", null)),
                TODAY);

        assertThat(spent.forGoal(7L)).isEqualByComparingTo("0.00");
        assertThat(spent.forGoal(8L))
                .describedAs("the whole capacity falls through to the goal below it")
                .isEqualByComparingTo("80.00");
    }

    /**
     * What the plan gives one dated goal, which is exactly the minimum its deadline asks for.
     *
     * <p>The goal under test is ranked second, under an undated goal that wants far more than the
     * capacity. That is what isolates the division: the third pass hands the surplus to the
     * highest-ranked unfinished goal, so a dated goal at rank 1 would be given its minimum and then
     * everything left over, and every figure below would read as the whole capacity.
     */
    private static BigDecimal whatOneDatedGoalGets(String stillNeeded, LocalDate deadline,
                                                   String weeklyCapacity) {
        return HowTheWeeklyMoneyIsSpent
                .asAt(AN_ACCOUNT, new BigDecimal(weeklyCapacity),
                        List.of(aGoal(9L, 1, "9000.00", null),
                                aGoal(7L, 2, stillNeeded, deadline)),
                        TODAY)
                .forGoal(7L);
    }

    private static AGoalCompetingForIt aGoal(Long goalId, int rank, String stillNeeded,
                                             LocalDate deadline) {
        return new AGoalCompetingForIt(goalId, "goal " + goalId, rank, new BigDecimal(stillNeeded),
                deadline, null);
    }
}
