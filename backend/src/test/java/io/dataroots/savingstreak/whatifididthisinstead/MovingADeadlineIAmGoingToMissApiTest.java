package io.dataroots.savingstreak.whatifididthisinstead;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SimulationView;
import io.dataroots.savingstreak.support.SimulationView.AThingThatHappensView;
import io.dataroots.savingstreak.support.SimulationView.HowAScenarioTurnsOutView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.aScenarioCalled;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.movingTheDeadlineOf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The branch that moves no money, driven over HTTP the way the frontend drives it: a goal on this
 * account, and the day it is wanted by instead.
 *
 * <p><strong>The question is "if I give myself two more months on the car, what does that do to the
 * holiday", and no other branch can answer it.</strong> Saving more, stopping and taking money out
 * all change what a year has in it; this changes only which goal the weekly plan funds first,
 * because {@code HowTheWeeklyMoneyIsSpent} gives every dated goal what it needs to arrive on time
 * before anything is left over for the goals behind it. Push one deadline out and that goal's weekly
 * minimum falls, and the money it stops taking is what the next goal down was waiting for. So what
 * is asserted here is the days the goals are reached on, which is the whole of what this change
 * moves.
 *
 * <p><strong>And the real goal is asked about afterwards, every time it matters.</strong> A branch
 * that quietly moved a deadline would be the one thing this feature promises never to do, and the
 * promise is worth nothing unless the goals screen is read back after the question was asked.
 *
 * <p>Its own customer every time and every date counted off the application's clock rather than the
 * machine's, for the reasons {@link AnAccountWithAFutureToAskAbout} gives. The days are counted from
 * the Monday this week began on rather than from the clock's own day, because that is the Monday
 * both halves of a goal's arithmetic are anchored on — the weeks left before a deadline and the
 * weeks of saving until it arrives.
 */
class MovingADeadlineIAmGoingToMissApiTest extends ApiIntegrationTest {

    @Test
    void moving_one_goals_deadline_moves_the_day_another_goal_is_reached() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "one goal moving another");
        LocalDate thisWeekBeganOn = thisWeekBeganOnFor(account);
        LocalDate theCarIsWantedBy = thisWeekBeganOn.plusWeeks(10);
        LocalDate theHolidayIsWantedBy = thisWeekBeganOn.plusWeeks(8);
        account.declaresAWeeklyCapacity("100.00");
        // A thousand euros wanted in ten weeks costs the whole hundred a week, so the holiday
        // behind it in the order is given nothing at all and never arrives.
        GoalView car = account.opensAGoal("A car", "1000.00", theCarIsWantedBy.toString());
        account.opensAGoal("A holiday", "400.00", theHolidayIsWantedBy.toString());

        SimulationView answered = account.asking(aScenarioCalled("Two more months on the car",
                movingTheDeadlineOf(car.id(), thisWeekBeganOn.plusWeeks(20))));
        HowAScenarioTurnsOutView carryingOn = answered.theYearAlreadyUnderWay();
        HowAScenarioTurnsOutView branch = answered.scenarios().get(1);

        assertThat(theDaysGoalsAreReachedIn(carryingOn))
                .as("in the year the customer is already in, the car takes the whole plan to arrive "
                        + "on the day it is wanted by, and the holiday has no day at all")
                .containsExactly(theCarIsWantedBy);
        assertThat(theDaysDeadlinesAreMissedOnIn(carryingOn))
                .as("and the holiday's own deadline goes by with the money never having reached it")
                .containsExactly(theHolidayIsWantedBy);
        assertThat(theDaysGoalsAreReachedIn(branch))
                .as("two more months on the car halves what its deadline asks for every week, and "
                        + "the fifty a week that frees is exactly what the holiday needed — so a "
                        + "change that moves not one euro moves the day a second goal arrives, "
                        + "which is the question this branch exists to answer and no other can")
                .containsExactly(theHolidayIsWantedBy, thisWeekBeganOn.plusWeeks(20));
        assertThat(theDaysDeadlinesAreMissedOnIn(branch))
                .as("and nothing in the branch is late: the car arrives on the day it is now wanted "
                        + "by and the holiday on the day it always was")
                .isEmpty();
    }

    @Test
    void a_goal_that_was_off_track_arrives_in_time_once_the_deadline_it_is_judged_against_moves() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "off track and then on it");
        LocalDate thisWeekBeganOn = thisWeekBeganOnFor(account);
        LocalDate wantedBy = thisWeekBeganOn.plusWeeks(10);
        // Fifty a week against a thousand is twenty weeks of saving, and the goal is wanted in ten:
        // the plan cannot find the hundred a week that arriving on time would cost.
        account.declaresAWeeklyCapacity("50.00");
        GoalView car = account.opensAGoal("A car", "1000.00", wantedBy.toString());

        SimulationView answered = account.asking(aScenarioCalled("Ten more weeks on the car",
                movingTheDeadlineOf(car.id(), thisWeekBeganOn.plusWeeks(30))));
        HowAScenarioTurnsOutView carryingOn = answered.theYearAlreadyUnderWay();
        HowAScenarioTurnsOutView branch = answered.scenarios().get(1);

        assertThat(account.goals().get(0).status())
                .as("the goals screen calls it OFF_TRACK today, which is the projection landing ten "
                        + "weeks past the day it is wanted by")
                .isEqualTo("OFF_TRACK");
        assertThat(theDaysDeadlinesAreMissedOnIn(carryingOn))
                .as("and the do-nothing column says the same thing in the shape a branch says it "
                        + "in: a marker on the day the deadline goes by")
                .containsExactly(wantedBy);
        assertThat(theDaysDeadlinesAreMissedOnIn(branch))
                .as("the same projection judged against a day thirty weeks out is ON_TRACK, and the "
                        + "marker is raised on exactly the comparison that makes a goal OFF_TRACK — "
                        + "so a branch without one is a branch in which the goal arrives in time")
                .isEmpty();
        assertThat(theDaysGoalsAreReachedIn(branch))
                .as("on the very Monday it was always going to arrive on: nothing about the money "
                        + "moved, only the day the customer would be content to have it by")
                .containsExactly(thisWeekBeganOn.plusWeeks(20));
        assertThat(theDaysGoalsAreReachedIn(carryingOn))
                .as("which is the same Monday the year already under way names")
                .isEqualTo(theDaysGoalsAreReachedIn(branch));
    }

    @Test
    void asking_what_moving_a_deadline_would_do_changes_nothing_about_the_real_goal() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "the real goal is untouched");
        LocalDate thisWeekBeganOn = thisWeekBeganOnFor(account);
        account.declaresAWeeklyCapacity("50.00");
        GoalView car = account.opensAGoal("A car", "1000.00",
                thisWeekBeganOn.plusWeeks(10).toString());
        account.depositsByHand("100.00");
        var goalsBefore = account.goals();
        var balancesBefore = account.balances();

        account.asking(aScenarioCalled("Ten more weeks on the car",
                movingTheDeadlineOf(car.id(), thisWeekBeganOn.plusWeeks(30))));

        assertThat(account.goals())
                .as("the deadline the customer gave the goal is the deadline the goal has, and so "
                        + "is the status derived from it: a branch is a question, and a question "
                        + "that moved a date would be the one thing this feature promises never to "
                        + "do. Adopting is a different press, and it is Goals that writes it")
                .isEqualTo(goalsBefore);
        assertThat(account.balances())
                .as("and no euro moved either, which is true of this kind of change twice over — "
                        + "inside the branch as well as outside it")
                .isEqualTo(balancesBefore);
    }

    @Test
    void a_goal_that_is_not_on_this_account_is_refused_in_words() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a goal nobody has heard of");
        LocalDate today = account.theDateTheClockReads();

        ResponseEntity<JsonNode> refused = account.askingFor(aScenarioCalled("A goal I made up",
                movingTheDeadlineOf(9_999_999L, today.plusWeeks(4))));

        assertThat(refused.getStatusCode())
                .as("nothing is in an unexpected state: what the customer does next is name one of "
                        + "the goals the screen listed, which is what a 400 asks for")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("and the sentence names the goal and the account, and says what became of a "
                        + "goal that was given up on, because those are the two ways to be missing "
                        + "from a plan — it answered " + refused.getBody())
                .isEqualTo("There is no savings goal 9999999 still being saved towards on savings "
                        + "account " + account.id() + ", so there is no deadline on it to move. A "
                        + "goal that was given up on has left the order and has no day to be wanted "
                        + "by.");
    }

    @Test
    void a_goal_that_was_given_up_on_is_refused_in_words_too() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a goal given up on");
        LocalDate today = account.theDateTheClockReads();
        GoalView car = account.opensAGoal("A car", "1000.00", today.plusWeeks(10).toString());
        account.givesUpOn(car.id());

        ResponseEntity<JsonNode> refused = account.askingFor(aScenarioCalled("The car after all",
                movingTheDeadlineOf(car.id(), today.plusWeeks(30))));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("a goal that was given up on has left the order the plan competes in, holds no "
                        + "money and has no day to be late for, so there is nothing about it a "
                        + "branch could project — and the customer hunting for it on this screen is "
                        + "told so rather than left wondering — it answered " + refused.getBody())
                .isEqualTo("There is no savings goal " + car.id() + " still being saved towards on "
                        + "savings account " + account.id() + ", so there is no deadline on it to "
                        + "move. A goal that was given up on has left the order and has no day to "
                        + "be wanted by.");
    }

    @Test
    void a_day_the_simulation_is_not_drawn_over_is_refused_in_words_naming_the_window() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "wanted by a day outside the window");
        SimulationView answered = account.simulation();
        LocalDate theDayAfterTheWindowCloses = answered.until().plusDays(1);
        GoalView car = account.opensAGoal("A car", "1000.00",
                answered.from().plusWeeks(10).toString());

        ResponseEntity<JsonNode> refused = account.askingFor(aScenarioCalled("Far too late",
                movingTheDeadlineOf(car.id(), theDayAfterTheWindowCloses)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("the refusal names both ends of the year the branches are drawn over, in the "
                        + "one wording every kind of change reuses for the one objection — a "
                        + "customer who has met it twice has met it once — it answered "
                        + refused.getBody())
                .isEqualTo("The day a goal is wanted by instead has to be inside the year this "
                        + "simulation is drawn over, which runs from " + answered.from() + " to "
                        + answered.until() + ", and " + theDayAfterTheWindowCloses + " is not.");
    }

    /**
     * The Monday this week began on, in the zone the weeks are counted in, read off the
     * application's clock rather than the machine's.
     *
     * <p>Every day in this class is counted from here rather than from the clock's own day, because
     * that Monday is the anchor both halves of a goal's arithmetic use: how many whole weeks are
     * left before a deadline, and how many weeks of saving it takes to arrive. Counting from today
     * would make the same assertion true on a Monday and false on a Thursday.
     */
    private static LocalDate thisWeekBeganOnFor(AnAccountWithAFutureToAskAbout account) {
        return SavingsWeek.containing(account.theDateTheClockReads()).startsOn();
    }

    /** The days the goals arrive on in a column, in the order the answer put them in. */
    private static List<LocalDate> theDaysGoalsAreReachedIn(HowAScenarioTurnsOutView branch) {
        return theDaysOf(branch, "A_GOAL_IS_REACHED");
    }

    /** The days a deadline goes by without the goal having arrived, in that same order. */
    private static List<LocalDate> theDaysDeadlinesAreMissedOnIn(HowAScenarioTurnsOutView branch) {
        return theDaysOf(branch, "A_DEADLINE_IS_MISSED");
    }

    private static List<LocalDate> theDaysOf(HowAScenarioTurnsOutView branch, String kind) {
        return branch.thingsOfKind(kind).stream().map(AThingThatHappensView::on).toList();
    }
}
