package io.dataroots.savingstreak.savingsgoals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine, driven over HTTP: what each goal is given each week out of the one figure its holder
 * said they could put away.
 *
 * <p>This is where the goals compete, so almost every test here is about two or three goals and
 * which of them comes up short. A test with one goal could not tell a plan from a copy of the
 * capacity.
 *
 * <p>Deadlines are set relative to what the application's clock says today is, not to the machine's,
 * because other classes in this run wind that clock forward — and because how many whole weeks are
 * left before a deadline is the whole of what a dated goal's minimum is divided by.
 *
 * <p>Money is only paid in where a goal has to be <em>completed</em> to make a point. The weekly
 * amount is worked out from what a goal still needs, and a goal nobody has allocated to still needs
 * the whole of its target, so most of these tests never touch the balance.
 */
class ThePlanSaysWhatEachGoalGetsEachWeekApiTest extends ApiIntegrationTest {

    @Test
    void one_goal_and_a_capacity_gives_the_goal_the_whole_capacity() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "the whole capacity");
        GoalView holiday = account.add("Holiday", "1000.00", null);
        account.canSave("125.00");

        assertThat(account.goal(holiday.id()).weeklyAmount())
                .describedAs("the only goal there is, and every cent the customer can save")
                .isEqualByComparingTo("125.00");
    }

    @Test
    void two_dated_goals_each_take_their_minimum_and_the_surplus_goes_to_the_higher_ranked_one() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "both minimums and the surplus");
        LocalDate inFourWeeks = account.today().plusDays(28);
        GoalView kitchen = account.add("Kitchen", "100.00", inFourWeeks);
        GoalView bike = account.add("Bike", "200.00", inFourWeeks);
        account.canSave("200.00");

        List<GoalView> goals = account.goals();
        assertThat(weeklyAmountOf(goals, kitchen.id()))
                .describedAs("25.00 a week is its own minimum, and the 125.00 nobody else claimed "
                        + "goes to the goal that matters most")
                .isEqualByComparingTo("150.00");
        assertThat(weeklyAmountOf(goals, bike.id()))
                .describedAs("200.00 over four weeks, and not a cent of the surplus")
                .isEqualByComparingTo("50.00");
        assertThat(weeklyAmountOf(goals, kitchen.id()).add(weeklyAmountOf(goals, bike.id())))
                .isEqualByComparingTo("200.00");
    }

    @Test
    void when_the_minimums_do_not_both_fit_the_lower_ranked_goal_gets_only_what_is_left() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "minimums that do not both fit");
        LocalDate nextWeek = account.today().plusDays(7);
        GoalView rent = account.add("Rent", "100.00", nextWeek);
        GoalView tickets = account.add("Tickets", "100.00", nextWeek);
        account.canSave("150.00");

        List<GoalView> goals = account.goals();
        assertThat(weeklyAmountOf(goals, rent.id()))
                .describedAs("rank 1 takes the whole 100.00 it needs to arrive on time")
                .isEqualByComparingTo("100.00");
        assertThat(weeklyAmountOf(goals, tickets.id()))
                .describedAs("and rank 2 gets the 50.00 that was left, which is the competition "
                        + "doing its work rather than a fault")
                .isEqualByComparingTo("50.00");
    }

    @Test
    void a_goal_the_capacity_never_reaches_is_given_zero() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "nothing left by the time it asks");
        LocalDate nextWeek = account.today().plusDays(7);
        GoalView roof = account.add("Roof", "100.00", nextWeek);
        GoalView lastInLine = account.add("Camera", "50.00", nextWeek);
        account.canSave("100.00");

        List<GoalView> goals = account.goals();
        assertThat(weeklyAmountOf(goals, roof.id())).isEqualByComparingTo("100.00");
        assertThat(weeklyAmountOf(goals, lastInLine.id()))
                .describedAs("zero, and not absent: a capacity was declared and this goal was "
                        + "reached with nothing left to give it")
                .isEqualByComparingTo("0.00");
        assertThat(weeklyAmountOf(goals, lastInLine.id())).isNotNull();
    }

    @Test
    void a_goal_with_no_deadline_claims_nothing_in_the_second_pass_and_only_takes_the_surplus() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "no deadline claims nothing");
        GoalView emergencyFund = account.add("Emergency fund", "5000.00", null);
        GoalView course = account.add("Course", "100.00", account.today().plusDays(14));
        account.canSave("80.00");

        List<GoalView> goals = account.goals();
        assertThat(weeklyAmountOf(goals, course.id()))
                .describedAs("100.00 over two whole weeks, taken although the goal above it wants "
                        + "5000.00 — a waterfall would have starved it")
                .isEqualByComparingTo("50.00");
        assertThat(weeklyAmountOf(goals, emergencyFund.id()))
                .describedAs("and the fund gets the 30.00 left over, which is the only way a goal "
                        + "with no date is given anything at all")
                .isEqualByComparingTo("30.00");
    }

    @Test
    void with_two_undated_goals_the_higher_ranked_one_takes_the_whole_surplus() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "two goals with no dates");
        GoalView rainyDay = account.add("Rainy day", "3000.00", null);
        GoalView someday = account.add("Someday", "3000.00", null);
        account.canSave("60.00");

        List<GoalView> goals = account.goals();
        assertThat(weeklyAmountOf(goals, rainyDay.id())).isEqualByComparingTo("60.00");
        assertThat(weeklyAmountOf(goals, someday.id()))
                .describedAs("the surplus is not shared out: somebody has to be able to see who lost")
                .isEqualByComparingTo("0.00");
    }

    @Test
    void a_deadline_minimum_is_rounded_up_to_the_cent_and_never_down() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "rounded up to the cent");
        // The undated goal at rank 1 is there to take the surplus. Without it the third pass would
        // hand the whole capacity to the goal under test and the division would be invisible.
        account.add("Buffer", "5000.00", null);
        GoalView gift = account.add("Gift", "10.00", account.today().plusDays(21));
        account.canSave("50.00");

        assertThat(account.goal(gift.id()).weeklyAmount())
                .describedAs("10.00 over three weeks is 3.3333..., and rounded down to 3.33 the "
                        + "goal would arrive a cent short on the day it was due")
                .isEqualByComparingTo("3.34");
    }

    @Test
    void completed_and_abandoned_goals_are_given_nothing_and_consume_no_capacity() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "finished goals claim nothing");
        account.savesUp("300.00");
        LocalDate nextWeek = account.today().plusDays(7);
        GoalView arrived = account.add("Arrived", "100.00", nextWeek);
        GoalView stillGoing = account.add("Still going", "100.00", nextWeek);
        GoalView givenUpOn = account.add("Given up on", "100.00", null);
        account.putTowards(arrived.id(), "100.00");
        account.canSave("100.00");

        List<GoalView> goals = account.goals();
        assertThat(goals).extracting(GoalView::id).contains(arrived.id());
        assertThat(theGoal(goals, arrived.id()).status())
                .describedAs("the set-up: it has reached its target")
                .isEqualTo("COMPLETED");
        assertThat(weeklyAmountOf(goals, arrived.id()))
                .describedAs("a goal that has arrived needs nothing, so it claims nothing")
                .isEqualByComparingTo("0.00");
        assertThat(weeklyAmountOf(goals, stillGoing.id()))
                .describedAs("and the whole capacity is still there for the goal below it, which "
                        + "is what 'consumes no capacity' means")
                .isEqualByComparingTo("100.00");

        account.abandon(givenUpOn.id());
        assertThat(account.goal(givenUpOn.id()).weeklyAmount())
                .describedAs("a goal that has left the order is not competing for anything")
                .isEqualByComparingTo("0.00");
        assertThat(weeklyAmountOf(account.goals(), stillGoing.id()))
                .describedAs("and abandoning it changed nothing about what the live goals are given")
                .isEqualByComparingTo("100.00");
    }

    @Test
    void reordering_two_goals_swaps_which_of_them_is_short_without_any_money_moving() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "reordering makes the other short");
        account.savesUp("400.00");
        LocalDate nextWeek = account.today().plusDays(7);
        GoalView first = account.add("First", "100.00", nextWeek);
        GoalView second = account.add("Second", "100.00", nextWeek);
        account.putTowards(first.id(), "40.00");
        account.putTowards(second.id(), "10.00");
        account.canSave("110.00");

        AllocationsView before = account.allocations();
        assertThat(before.goal(first.id()).weeklyAmount())
                .describedAs("60.00 still needed, wanted in a week, and rank 1 takes it in full")
                .isEqualByComparingTo("60.00");
        assertThat(before.goal(second.id()).weeklyAmount())
                .describedAs("90.00 still needed, and only 50.00 of the capacity was left for it")
                .isEqualByComparingTo("50.00");

        account.reorder(List.of(second.id(), first.id()));

        AllocationsView after = account.allocations();
        assertThat(after.goal(second.id()).rank())
                .describedAs("the set-up: it is the most important goal now")
                .isEqualTo(1);
        assertThat(after.goal(second.id()).weeklyAmount())
                .describedAs("and at rank 1 it takes the whole 90.00 it needs")
                .isEqualByComparingTo("90.00");
        assertThat(after.goal(first.id()).weeklyAmount())
                .describedAs("and the goal that was comfortable is the one coming up short")
                .isEqualByComparingTo("20.00");

        assertThat(after.balance()).isEqualByComparingTo(before.balance());
        assertThat(after.allocated()).isEqualByComparingTo(before.allocated());
        assertThat(after.unallocated()).isEqualByComparingTo(before.unallocated());
        assertThat(after.goal(first.id()).allocation())
                .describedAs("not a cent moved: the order changed and nothing else did")
                .isEqualByComparingTo(before.goal(first.id()).allocation());
        assertThat(after.goal(second.id()).allocation())
                .isEqualByComparingTo(before.goal(second.id()).allocation());
        assertThat(account.savingsBalance()).isEqualByComparingTo("400.00");
    }

    @Test
    void with_no_capacity_declared_every_goal_reports_no_weekly_amount_rather_than_zero() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "nothing says how fast it fills");
        GoalView dated = account.add("Dated", "100.00", account.today().plusDays(7));
        GoalView undated = account.add("Undated", "100.00", null);

        assertThat(account.savingCapacity().declared())
                .describedAs("the set-up: nobody has said what they can save")
                .isFalse();
        assertThat(account.goals())
                .describedAs("absent, not 0.00 — nobody having said how fast this fills is a "
                        + "different sentence from a plan in which the goal gets nothing")
                .allSatisfy(goal -> assertThat(goal.weeklyAmount()).isNull());
        assertThat(account.goal(dated.id()).weeklyAmount()).isNull();
        assertThat(account.goal(undated.id()).weeklyAmount()).isNull();
        assertThat(account.allocations().goal(dated.id()).weeklyAmount()).isNull();

        account.canSave("70.00");
        assertThat(account.goal(dated.id()).weeklyAmount())
                .describedAs("and the moment a figure exists, every goal carries one")
                .isEqualByComparingTo("70.00");
    }

    @Test
    void the_weekly_amounts_never_add_up_to_more_than_the_capacity() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "never more than the capacity");
        LocalDate today = account.today();
        account.add("In a week", "100.00", today.plusDays(7));
        account.add("In a month", "400.00", today.plusDays(28));
        account.add("No date", "2500.00", null);
        account.add("Tomorrow", "75.50", today.plusDays(1));

        for (String capacity : List.of("10.00", "37.50", "123.45", "1000.00", "0.01")) {
            account.canSave(capacity);
            List<GoalView> goals = account.goals();
            BigDecimal givenOut = goals.stream()
                    .map(GoalView::weeklyAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(givenOut)
                    .describedAs("the plan spends a capacity of " + capacity + " and never more: "
                            + goals.stream().map(GoalView::weeklyAmount).toList())
                    .isLessThanOrEqualTo(new BigDecimal(capacity));
            assertThat(goals)
                    .describedAs("and no goal is ever given a negative figure")
                    .allSatisfy(goal -> assertThat(goal.weeklyAmount()).isNotNegative());
        }
    }

    private static BigDecimal weeklyAmountOf(List<GoalView> goals, long goalId) {
        return theGoal(goals, goalId).weeklyAmount();
    }

    private static GoalView theGoal(List<GoalView> goals, long goalId) {
        return goals.stream()
                .filter(goal -> goal.id() == goalId)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "goal " + goalId + " is not among the goals on this account"));
    }
}
