package io.dataroots.savingstreak.savingsgoals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer fixing one figure of the plan themselves, driven over HTTP.
 *
 * <p>This is where the interesting problem in this feature lives. Everything else the plan says is
 * derived — the order decides who takes a deadline minimum first, and whatever is left falls to
 * whoever is highest-ranked — and if the engine owned every figure there would be nothing for a
 * customer to weigh. A pin is a commitment the engine has to work around, so almost every test here
 * is about what the pin costs the goals under it rather than about the pinned goal alone.
 *
 * <p><strong>Pins are taken in rank order and only as far as the capacity reaches.</strong> That is
 * the one thing this slice had to decide and the ticket asks for it from both ends: a pin over the
 * whole capacity is accepted rather than refused, and two pins that together exceed it leave the
 * lower-ranked one with only what is left. Accepting it and capping what is handed out are not in
 * tension — the customer is never told their figure was wrong, and the plan never hands out more in
 * a week than the same customer said they could put away.
 *
 * <p>Deadlines are set relative to what the application's clock says today is, not to the machine's,
 * because other classes in this run wind that clock forward; and every expected date is the Monday
 * this week began on plus whole weeks, which is how the projection counts them.
 */
class AWeeklyAmountCanBePinnedToAGoalApiTest extends ApiIntegrationTest {

    @Test
    void a_pinned_goal_is_given_the_pinned_figure_whatever_its_rank_and_whatever_its_deadline_implies() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "the pinned figure");
        LocalDate today = account.today();
        // Rank 1 wants the whole capacity and more to arrive on time, and rank 2's own deadline asks
        // for 50.00 a week. Neither figure is what the pinned goal gets.
        GoalView rent = account.add("Rent", "400.00", today.plusDays(7));
        GoalView course = account.add("Course", "100.00", today.plusDays(14));
        account.canSave("100.00");

        assertThat(account.goal(course.id()).weeklyAmount())
                .describedAs("the set-up: unpinned, rank 1 takes everything and this goal gets nothing")
                .isEqualByComparingTo("0.00");

        GoalView pinned = account.pin(course.id(), "20.00");

        assertThat(pinned.pinnedWeeklyAmount())
                .describedAs("what was asked for, reported back as its own figure")
                .isEqualByComparingTo("20.00");
        assertThat(pinned.weeklyAmount())
                .describedAs("20.00, although it is the second goal in the order and although its "
                        + "deadline would have wanted 50.00: the pin is taken before every minimum "
                        + "and nothing tops it up")
                .isEqualByComparingTo("20.00");
        assertThat(weeklyAmountOf(account.goals(), rent.id()))
                .describedAs("and the rest of the plan is worked out around what is left")
                .isEqualByComparingTo("80.00");
    }

    @Test
    void a_pin_below_what_the_deadline_needs_leaves_the_goal_off_track_rather_than_topping_it_up() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "pinned under the minimum");
        LocalDate wantedIn = account.today().plusDays(14);
        GoalView car = account.add("Car", "100.00", wantedIn);
        account.canSave("100.00");

        assertThat(account.goal(car.id()).status())
                .describedAs("the set-up: the whole capacity is there and it arrives in time")
                .isEqualTo("ON_TRACK");

        GoalView pinned = account.pin(car.id(), "10.00");

        assertThat(pinned.weeklyAmount())
                .describedAs("10.00 and not a cent more, although 90.00 of the capacity is going "
                        + "nowhere: the third pass passes a pinned goal over")
                .isEqualByComparingTo("10.00");
        assertThat(pinned.status())
                .describedAs("100.00 at 10.00 a week is ten weeks, and the goal was wanted in two")
                .isEqualTo("OFF_TRACK");
        assertThat(pinned.willBeReachedOn())
                .isEqualTo(thisMondayFor(account).plusWeeks(10));
        assertThat(pinned.willBeReachedOn())
                .describedAs("the account says so rather than quietly finding the money")
                .isAfter(wantedIn);
    }

    @Test
    void a_pin_above_what_the_deadline_needs_is_honoured_and_the_goal_arrives_early() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "pinned over the minimum");
        LocalDate wantedIn = account.today().plusDays(28);
        // The undated goal is opened first so that it ranks above the one under test: the third pass
        // then has somewhere to put the surplus, and the figure asserted below is the pin rather
        // than the pin plus everything nobody else claimed.
        GoalView buffer = account.add("Buffer", "5000.00", null);
        GoalView sofa = account.add("Sofa", "100.00", wantedIn);
        account.canSave("100.00");

        GoalView pinned = account.pin(sofa.id(), "50.00");

        assertThat(pinned.weeklyAmount())
                .describedAs("its deadline asked for 25.00 a week; the customer said 50.00 and the "
                        + "engine plans around that rather than over it")
                .isEqualByComparingTo("50.00");
        assertThat(pinned.willBeReachedOn())
                .describedAs("100.00 at 50.00 a week is two weeks, not the four it was given")
                .isEqualTo(thisMondayFor(account).plusWeeks(2));
        assertThat(pinned.willBeReachedOn())
                .describedAs("earlier than the day it was wanted by, which is what paying more does")
                .isBefore(wantedIn);
        assertThat(pinned.status()).isEqualTo("ON_TRACK");
        assertThat(weeklyAmountOf(account.goals(), buffer.id()))
                .describedAs("and the 50.00 the pin did not take is still spent on the goal above it")
                .isEqualByComparingTo("50.00");
    }

    @Test
    void pinning_one_goal_takes_an_on_track_goal_below_it_off_track() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "pinning makes another late");
        LocalDate wantedIn = account.today().plusDays(14);
        GoalView emergencyFund = account.add("Emergency fund", "1000.00", null);
        GoalView bike = account.add("Bike", "100.00", wantedIn);
        account.canSave("100.00");

        List<GoalView> before = account.goals();
        assertThat(weeklyAmountOf(before, bike.id()))
                .describedAs("the set-up: its own minimum, taken although the goal above it wants far more")
                .isEqualByComparingTo("50.00");
        assertThat(theGoal(before, bike.id()).status()).isEqualTo("ON_TRACK");
        assertThat(weeklyAmountOf(before, emergencyFund.id()))
                .describedAs("and the fund is living on the 50.00 left over")
                .isEqualByComparingTo("50.00");

        account.pin(emergencyFund.id(), "80.00");

        List<GoalView> after = account.goals();
        assertThat(weeklyAmountOf(after, emergencyFund.id())).isEqualByComparingTo("80.00");
        assertThat(weeklyAmountOf(after, bike.id()))
                .describedAs("20.00 is what the pin left, and the second pass hands out what exists")
                .isEqualByComparingTo("20.00");
        assertThat(theGoal(after, bike.id()).status())
                .describedAs("100.00 at 20.00 a week is five weeks against a deadline two weeks out: "
                        + "the cost of the pin, shown rather than hidden")
                .isEqualTo("OFF_TRACK");
    }

    @Test
    void a_pin_over_the_whole_capacity_is_accepted_and_leaves_every_other_goal_unreachable() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "pinned over the capacity");
        LocalDate today = account.today();
        GoalView wedding = account.add("Wedding", "5000.00", null);
        GoalView books = account.add("Books", "100.00", today.plusDays(14));
        GoalView rainyDay = account.add("Rainy day", "1000.00", null);
        account.canSave("50.00");

        GoalView pinned = account.pin(wedding.id(), "500.00");

        assertThat(pinned.pinnedWeeklyAmount())
                .describedAs("accepted, not refused: it is a legal thing to say about your own money")
                .isEqualByComparingTo("500.00");
        assertThat(pinned.weeklyAmount())
                .describedAs("and what the plan can actually give it is the whole capacity — the "
                        + "application does not plan to save more in a week than the customer said "
                        + "they could")
                .isEqualByComparingTo("50.00");

        List<GoalView> goals = account.goals();
        assertThat(weeklyAmountOf(goals, books.id()))
                .describedAs("every goal below it is given nothing, which is the consequence the "
                        + "customer asked for by pinning")
                .isEqualByComparingTo("0.00");
        assertThat(weeklyAmountOf(goals, rainyDay.id())).isEqualByComparingTo("0.00");
        assertThat(theGoal(goals, books.id()).status()).isEqualTo("UNREACHABLE");
        assertThat(theGoal(goals, rainyDay.id()).status()).isEqualTo("UNREACHABLE");
        assertThat(theGoal(goals, books.id()).willBeReachedOn())
                .describedAs("at this rate it never arrives, so there is no day to name")
                .isNull();
    }

    @Test
    void two_pins_are_taken_in_rank_order_and_the_lower_ranked_one_gets_only_what_is_left() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "two pins in rank order");
        GoalView first = account.add("First", "1000.00", null);
        GoalView second = account.add("Second", "1000.00", null);
        account.canSave("100.00");
        account.pin(first.id(), "70.00");
        account.pin(second.id(), "50.00");

        List<GoalView> goals = account.goals();
        assertThat(weeklyAmountOf(goals, first.id()))
                .describedAs("rank 1 commits first and its commitment is met in full")
                .isEqualByComparingTo("70.00");
        assertThat(weeklyAmountOf(goals, second.id()))
                .describedAs("and the 120.00 the two of them asked for does not exist, so the goal "
                        + "that matters less gets the 30.00 that was left")
                .isEqualByComparingTo("30.00");
        assertThat(theGoal(goals, second.id()).pinnedWeeklyAmount())
                .describedAs("what was asked for is still reported, so the customer can see the "
                        + "difference between what they committed and what the plan could give")
                .isEqualByComparingTo("50.00");

        account.reorder(List.of(second.id(), first.id()));

        List<GoalView> reordered = account.goals();
        assertThat(weeklyAmountOf(reordered, second.id()))
                .describedAs("promoted, its own 50.00 is met in full")
                .isEqualByComparingTo("50.00");
        assertThat(weeklyAmountOf(reordered, first.id()))
                .describedAs("and the one that now matters less is the one coming up short")
                .isEqualByComparingTo("50.00");
    }

    @Test
    void unpinning_returns_the_goal_to_the_ordinary_passes_and_the_plan_goes_back() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "unpinning puts it back");
        GoalView emergencyFund = account.add("Emergency fund", "1000.00", null);
        GoalView bike = account.add("Bike", "100.00", account.today().plusDays(14));
        account.canSave("100.00");

        List<GoalView> before = account.goals();
        account.pin(emergencyFund.id(), "80.00");
        assertThat(weeklyAmountOf(account.goals(), bike.id()))
                .describedAs("the set-up: the pin is in force and the goal below it is short")
                .isEqualByComparingTo("20.00");

        GoalView unpinned = account.unpin(emergencyFund.id());

        assertThat(unpinned.pinnedWeeklyAmount())
                .describedAs("the commitment is gone, not set to zero")
                .isNull();
        List<GoalView> after = account.goals();
        assertThat(weeklyAmountOf(after, bike.id()))
                .describedAs("the second pass sees the dated goal again")
                .isEqualByComparingTo(weeklyAmountOf(before, bike.id()));
        assertThat(weeklyAmountOf(after, emergencyFund.id()))
                .describedAs("and the fund is back to living on the surplus")
                .isEqualByComparingTo(weeklyAmountOf(before, emergencyFund.id()));
        assertThat(theGoal(after, bike.id()).status())
                .isEqualTo(theGoal(before, bike.id()).status());
    }

    @Test
    void unpinning_a_goal_nobody_pinned_changes_nothing_and_is_not_refused() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "unpinning nothing");
        GoalView holiday = account.add("Holiday", "1000.00", null);
        account.canSave("60.00");

        GoalView unpinned = account.unpin(holiday.id());

        assertThat(unpinned.pinnedWeeklyAmount())
                .describedAs("there is at most one pin, and somebody asking for it to be gone when "
                        + "it already is has got what they asked for")
                .isNull();
        assertThat(unpinned.weeklyAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    void a_pinned_goal_that_reaches_its_target_is_completed_and_claims_nothing() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "a pinned goal that arrives");
        account.savesUp("300.00");
        GoalView arrived = account.add("Arrived", "100.00", account.today().plusDays(14));
        GoalView stillGoing = account.add("Still going", "500.00", null);
        account.canSave("100.00");
        account.pin(arrived.id(), "40.00");
        assertThat(weeklyAmountOf(account.goals(), stillGoing.id()))
                .describedAs("the set-up: the pin is taking 40.00 of the capacity")
                .isEqualByComparingTo("60.00");

        account.putTowards(arrived.id(), "100.00");

        List<GoalView> goals = account.goals();
        assertThat(theGoal(goals, arrived.id()).status()).isEqualTo("COMPLETED");
        assertThat(theGoal(goals, arrived.id()).pinnedWeeklyAmount())
                .describedAs("the commitment is still on the goal: it was not quietly withdrawn")
                .isEqualByComparingTo("40.00");
        assertThat(weeklyAmountOf(goals, arrived.id()))
                .describedAs("a goal that has arrived needs nothing, so its pin claims nothing")
                .isEqualByComparingTo("0.00");
        assertThat(weeklyAmountOf(goals, stillGoing.id()))
                .describedAs("and the whole capacity is there for the goal below it again")
                .isEqualByComparingTo("100.00");
    }

    @Test
    void a_pin_of_zero_or_less_or_quoted_too_finely_is_refused() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "a pin that is not money");
        GoalView holiday = account.add("Holiday", "1000.00", null);
        account.canSave("100.00");

        ResponseEntity<JsonNode> nothing = account.tryToPin(holiday.id(), "0.00");
        assertThat(nothing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(AnAccountWithGoals.reasonGivenBy(nothing))
                .describedAs("the same sentence a target, a move and a capacity get, because it is "
                        + "the same objection")
                .isEqualTo("A weekly amount pinned to a savings goal has to be an amount of more "
                        + "than zero, and 0.00 is not.");

        ResponseEntity<JsonNode> negative = account.tryToPin(holiday.id(), "-5.00");
        assertThat(negative.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(AnAccountWithGoals.reasonGivenBy(negative)).contains("-5.00 is not");

        ResponseEntity<JsonNode> tooFine = account.tryToPin(holiday.id(), "10.005");
        assertThat(tooFine.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(AnAccountWithGoals.reasonGivenBy(tooFine))
                .isEqualTo("An amount of money has at most two decimal places, and 10.005 has 3.");

        ResponseEntity<JsonNode> notANumber = account.tryToPin(holiday.id(), "50,00");
        assertThat(notANumber.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(AnAccountWithGoals.reasonGivenBy(notANumber))
                .describedAs("named back with the comma still in it, so the person can see it")
                .contains("\"50,00\" is not an amount of money");

        assertThat(account.goal(holiday.id()).pinnedWeeklyAmount())
                .describedAs("and after four refusals nothing is pinned to the goal")
                .isNull();
    }

    @Test
    void a_goal_that_was_given_up_on_cannot_be_pinned() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "pinning a goal given up on");
        GoalView givenUpOn = account.add("Given up on", "1000.00", null);
        account.canSave("100.00");
        account.abandon(givenUpOn.id());

        ResponseEntity<JsonNode> refused = account.tryToPin(givenUpOn.id(), "20.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(AnAccountWithGoals.reasonGivenBy(refused))
                .isEqualTo("\"Given up on\" was given up on and cannot be pinned.");
    }

    @Test
    void a_pin_on_a_goal_that_is_not_on_this_account_is_refused() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "pinning a goal that is not here");
        long noSuchGoal = 9_999_999L;

        ResponseEntity<JsonNode> refused = account.tryToPin(noSuchGoal, "20.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(AnAccountWithGoals.reasonGivenBy(refused)).contains(String.valueOf(noSuchGoal));
        assertThat(account.tryToUnpin(noSuchGoal).getStatusCode())
                .describedAs("and taking a pin off a goal that is not here is the same answer")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void a_pin_on_an_account_with_no_capacity_is_kept_and_takes_effect_when_one_is_declared() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "a pin with nothing to spend");
        GoalView dated = account.add("Dated", "100.00", account.today().plusDays(14));
        GoalView pinnedGoal = account.add("Pinned", "1000.00", null);

        GoalView pinned = account.pin(pinnedGoal.id(), "30.00");

        assertThat(pinned.pinnedWeeklyAmount())
                .describedAs("the commitment is kept whether or not anything says how fast the "
                        + "account fills")
                .isEqualByComparingTo("30.00");
        assertThat(pinned.weeklyAmount())
                .describedAs("absent, not 30.00: a pin is a claim on a capacity, and nobody has "
                        + "declared one to claim against")
                .isNull();

        account.canSave("100.00");

        List<GoalView> goals = account.goals();
        assertThat(weeklyAmountOf(goals, pinnedGoal.id()))
                .describedAs("and the moment there is a figure, the commitment is spent first")
                .isEqualByComparingTo("30.00");
        assertThat(weeklyAmountOf(goals, dated.id()))
                .describedAs("its own 50.00 minimum out of the 70.00 the pin left, and then the "
                        + "20.00 nobody else claimed, because it is the highest-ranked unpinned goal")
                .isEqualByComparingTo("70.00");
    }

    @Test
    void the_weekly_amounts_never_add_up_to_more_than_the_capacity_when_goals_are_pinned() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "pins never overspend");
        LocalDate today = account.today();
        GoalView first = account.add("In a week", "100.00", today.plusDays(7));
        account.add("No date", "2500.00", null);
        GoalView third = account.add("In a month", "400.00", today.plusDays(28));
        account.add("Tomorrow", "75.50", today.plusDays(1));
        account.pin(first.id(), "60.00");
        account.pin(third.id(), "45.50");

        for (String capacity : List.of("10.00", "37.50", "105.50", "1000.00", "0.01")) {
            account.canSave(capacity);
            List<GoalView> goals = account.goals();
            BigDecimal givenOut = goals.stream()
                    .map(GoalView::weeklyAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(givenOut)
                    .describedAs("two pins asking for 105.50 between them, against a capacity of "
                            + capacity + ": " + goals.stream().map(GoalView::weeklyAmount).toList())
                    .isLessThanOrEqualTo(new BigDecimal(capacity));
            assertThat(goals)
                    .describedAs("and no goal is ever given a negative figure")
                    .allSatisfy(goal -> assertThat(goal.weeklyAmount()).isNotNegative());
        }
    }

    /** The Monday the application's own week began on, which is where every projection counts from. */
    private static LocalDate thisMondayFor(AnAccountWithGoals account) {
        return SavingsWeek.containing(account.today()).startsOn();
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
