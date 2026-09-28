package io.dataroots.savingstreak.whatifididthisinstead;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SimulationView;
import io.dataroots.savingstreak.support.SimulationView.AThingThatHappensView;
import io.dataroots.savingstreak.support.SimulationView.HowAScenarioTurnsOutView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.aScenarioCalled;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.aStopFrom;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.anotherEachWeekOf;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.andAtMostAYearsInterestOn;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What stopping for a couple of months would cost, driven over HTTP the way the frontend drives it:
 * a first day, a last day, and a branch in which nothing is paid into savings between them.
 *
 * <p><strong>The interesting part is not the balance that fails to grow.</strong> Two months of a
 * fifty-a-week rule is four hundred euros that are not there, and a customer can do that half in
 * their head. What they cannot do in their head is the run: the first Sunday inside the stop takes
 * in nothing, so the run ends rather than shortening, and every euro paid in afterwards is back at
 * the ordinary rate until the ladder has been climbed a second time, a week at a time. That is why
 * the months <em>after</em> the stop are asserted here as hard as the months inside it, and it is
 * the argument a customer weighing a pause is actually weighing.
 *
 * <p><strong>A pause of the saving rather than a pause of the life.</strong> The salary goes on
 * landing, the bills go on being taken and the points a customer has already earned go on reaching
 * their twelve months, because somebody who stops saving has not stopped being paid or stopped
 * owing rent. Both halves are asserted, because a branch that froze everything would be answering a
 * question about a coma and would read as more expensive than it is.
 *
 * <p>Everything that can only be reached by naming a day — which Sunday a run ends on, what the
 * first Monday after the stop earns to the point — is asserted as plain arithmetic in {@code
 * TheNightIsReplayedOneDayAtATimeForTwelveMonthsTest}, because over HTTP the only day there is is
 * whatever the clock the whole run shares has been wound to. What is asserted here is what the seam
 * owes a client: a stop asked for in a body, answered beside the year already under way, composing
 * with another change in the same scenario, and refused in words when it cannot be asked.
 *
 * <p>Its own customer every time and every date counted off the application's clock rather than the
 * machine's, for the reasons {@link AnAccountWithAFutureToAskAbout} gives. Every span of days named
 * here is a whole number of weeks, so that it holds exactly as many Fridays — and exactly as many
 * of the customer's own weekly mornings — whatever day of the week the clock happens to read.
 */
class WhatStoppingForTwoMonthsWouldCostApiTest extends ApiIntegrationTest {

    /** Eight weeks, which is the "couple of months" the ticket is named for, counted in whole weeks. */
    private static final int DAYS_IN_THE_STOP = 56;

    @Test
    void a_standing_rule_moves_nothing_on_the_mornings_the_branch_has_stopped_saving_on() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a rule gone quiet");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAnIncome("1", "5000.00");
        account.leavesARuleStanding(Map.of(
                "name", "Every Friday", "fromCurrentAccountId", account.currentAccountId(),
                "trigger", "WEEKLY", "dayOfWeek", DayOfWeek.FRIDAY.name(),
                "howMuchMoves", "A_FIXED_AMOUNT", "amount", "25.00"));
        // Far enough in that a payday has certainly landed before it begins, so that every Friday
        // inside it is one the year already under way could afford and did.
        LocalDate stopBegins = today.plusDays(40);
        LocalDate stopEnds = stopBegins.plusDays(DAYS_IN_THE_STOP - 1);

        SimulationView answered = account.asking(
                aScenarioCalled("Two months off", aStopFrom(stopBegins, stopEnds)));
        HowAScenarioTurnsOutView carryingOn = answered.theYearAlreadyUnderWay();
        HowAScenarioTurnsOutView branch = answered.scenarios().get(1);

        assertThat(branch.called())
                .as("the branch comes back under the words the customer typed, beside the year "
                        + "they are already in, because a pause with nothing next to it is a "
                        + "number rather than an argument")
                .isEqualTo("Two months off");
        assertThat(carryingOn.months().get(11).balance()
                .subtract(branch.months().get(11).balance()))
                .as("eight weeks is eight Fridays whatever day the clock reads, and every one of "
                        + "them is a morning the rule did not fire: twenty-five apiece, and not a "
                        + "cent of it made up on the day the stop lifts, because a pause has never "
                        + "been made up in this application. A little more than that, because the "
                        + "euros that never went in earned nothing either")
                .isBetween(new BigDecimal("25.00").multiply(BigDecimal.valueOf(8)),
                        andAtMostAYearsInterestOn(
                                new BigDecimal("25.00").multiply(BigDecimal.valueOf(8))));
    }

    @Test
    void the_first_sunday_inside_the_stop_ends_the_run_and_the_ladder_is_climbed_again_after_it() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a run that ends");
        LocalDate today = account.theDateTheClockReads();
        LocalDate stopBegins = today.plusDays(1);
        LocalDate stopEnds = today.plusDays(DAYS_IN_THE_STOP);
        Map<String, Object> fiftyAWeek = anotherEachWeekOf("50.00", today);

        HowAScenarioTurnsOutView savingOn = account
                .asking(aScenarioCalled("Fifty a week", fiftyAWeek))
                .scenarios().get(1);
        HowAScenarioTurnsOutView stopping = account
                .asking(aScenarioCalled("Fifty a week, with two months off",
                        List.of(fiftyAWeek, aStopFrom(stopBegins, stopEnds))))
                .scenarios().get(1);

        assertThat(savingOn.thingsOfKind("A_WEEK_IS_LOST"))
                .as("fifty a week is exactly what a week has to take in, so the customer who keeps "
                        + "it up loses no week at all in a year")
                .isEmpty();
        assertThat(stopping.thingsOfKind("A_WEEK_IS_LOST"))
                .as("and the one who stops loses the run once, on the first Sunday inside the "
                        + "stop: a run ends rather than shortening, so the seven silent Sundays "
                        + "after it take nothing further — there is nothing left to take")
                .singleElement()
                .extracting(AThingThatHappensView::on)
                .isEqualTo(SavingsWeek.containing(today).endsOn().plusWeeks(1));
        assertThat(stopping.months().get(2).pointsEarned())
                .as("the row that closes after the stop lifts has the same weekly fifties in it as "
                        + "the branch that never stopped, and earns less on every one of them: "
                        + "fifty at the ordinary rate, then fifty-five, then sixty, because the "
                        + "ladder is climbed a step a week and does not remember where it was")
                .isLessThan(savingOn.months().get(2).pointsEarned());
        assertThat(stopping.months().get(11).securedWeeks())
                .as("by the end of the year the run is long past the six the ladder stops climbing "
                        + "at, which is the branch having climbed it a second time")
                .isGreaterThanOrEqualTo(6);
        assertThat(stopping.months().get(11).pointsEarned())
                .as("and the last month of the two branches earns the same, on the same mornings: "
                        + "the cost of stopping is the months it took to get back here, not a rate "
                        + "the customer never recovers")
                .isEqualTo(savingOn.months().get(11).pointsEarned());
    }

    @Test
    void another_amount_each_week_does_not_land_on_the_mornings_the_same_scenario_stopped_on() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a stop and an extra together");
        LocalDate today = account.theDateTheClockReads();
        Map<String, Object> fiftyAWeek = anotherEachWeekOf("50.00", today);

        HowAScenarioTurnsOutView savingOn = account
                .asking(aScenarioCalled("Fifty a week", fiftyAWeek))
                .scenarios().get(1);
        HowAScenarioTurnsOutView stopping = account
                .asking(aScenarioCalled("Fifty a week, with two months off",
                        List.of(fiftyAWeek, aStopFrom(today.plusDays(1),
                                today.plusDays(DAYS_IN_THE_STOP)))))
                .scenarios().get(1);

        assertThat(savingOn.months().get(11).balance()
                .subtract(stopping.months().get(11).balance()))
                .as("eight of the customer's own weekly mornings fall inside the stop and not one "
                        + "of them lands: they asked about both changes in one scenario and meant "
                        + "both, and an extra that went in through a pause would answer with a "
                        + "pause that cost them nothing. A little more than that, because the euros "
                        + "that never went in earned nothing either")
                .isBetween(new BigDecimal("50.00").multiply(BigDecimal.valueOf(8)),
                        andAtMostAYearsInterestOn(
                                new BigDecimal("50.00").multiply(BigDecimal.valueOf(8))));
    }

    @Test
    void the_salary_goes_on_landing_and_the_bills_go_on_being_taken_inside_the_stop() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a life that carries on");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAnIncome("5", "2000.00");
        account.declaresABill("Rent", "10", "500.00");
        // A sweep rather than a fixed amount, so that what it moves is a reading of what the
        // everyday account was left holding on the mornings the branch was not saving.
        account.leavesARuleStanding(Map.of(
                "name", "Everything above nothing", "fromCurrentAccountId", account.currentAccountId(),
                "trigger", "MONTHLY", "dayOfMonth", "20",
                "howMuchMoves", "EVERYTHING_ABOVE", "floor", "0.00"));

        SimulationView answered = account.asking(aScenarioCalled("Ten weeks off",
                aStopFrom(today.plusDays(1), today.plusDays(70))));
        HowAScenarioTurnsOutView carryingOn = answered.theYearAlreadyUnderWay();
        HowAScenarioTurnsOutView branch = answered.scenarios().get(1);

        assertThat(branch.months().get(1).balance())
                .as("two months in, the branch is behind: the sweeps that fall inside the stop do "
                        + "not fire, so the surplus is sitting in the everyday account instead of "
                        + "in savings")
                .isLessThan(carryingOn.months().get(1).balance());
        assertThat(carryingOn.months().get(11).balance()
                .subtract(branch.months().get(11).balance()))
                .as("and by the end of the year the two columns are level again but for small "
                        + "change, which is the whole of this criterion. The salary went on landing "
                        + "and the rent went on being taken all through the stop, so the first "
                        + "sweep after it found every euro of the surplus waiting — had the salary "
                        + "stopped there would be months of it missing, and had the rent stopped "
                        + "there would be months of it over. What the branch is still short of is "
                        + "only the interest those euros did not earn during the ten weeks they sat "
                        + "in the everyday account, which could never come to a month's rent")
                .isBetween(BigDecimal.ZERO, new BigDecimal("500.00"));
    }

    @Test
    void points_go_on_the_day_they_were_always_going_to_go_although_the_branch_has_stopped() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "points that still go");
        account.depositsByHand("100.00");
        SimulationView standing = account.simulation();
        // The last day of the window is the day a batch earned this morning reaches its twelve
        // months, so a stop that runs up to it is a stop the expiry falls inside.
        LocalDate theWindowCloses = standing.until();

        HowAScenarioTurnsOutView branch = account
                .asking(aScenarioCalled("Stopped at the end of the year",
                        aStopFrom(theWindowCloses.minusDays(41), theWindowCloses)))
                .scenarios().get(1);

        assertThat(branch.thingsOfKind("POINTS_EXPIRE"))
                .as("a hundred euros put away this morning earns a hundred points whose twelve "
                        + "months are up on the very last day of the window, and they go on it "
                        + "although the branch stopped saving six weeks earlier: stopping is a "
                        + "pause on the saving and not a stopped clock on what was already earned, "
                        + "and a branch that quietly kept them would be a lie in the customer's "
                        + "favour — it answered " + branch.thingsThatHappen())
                .singleElement()
                .extracting(AThingThatHappensView::on)
                .isEqualTo(theWindowCloses);
    }

    @Test
    void a_stop_that_ends_before_it_begins_is_refused_in_words_naming_both_days() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "the wrong way round");
        LocalDate today = account.theDateTheClockReads();
        LocalDate firstDay = today.plusDays(30);
        LocalDate lastDay = today.plusDays(10);

        ResponseEntity<JsonNode> refused = account.askingFor(
                aScenarioCalled("Backwards", aStopFrom(firstDay, lastDay)));

        assertThat(refused.getStatusCode())
                .as("nothing is in an unexpected state and nothing is missing: what the customer "
                        + "does next is retype one of the two dates, which is what a 400 asks for")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("and both days are named back, because somebody who has typed two dates into "
                        + "two boxes and been told they are the wrong way round has otherwise to "
                        + "guess which box the application read as which — it answered "
                        + refused.getBody())
                .isEqualTo("A stop has to end on or after the day it begins, and " + lastDay
                        + " is before " + firstDay + ".");
    }

    @Test
    void a_stop_that_begins_outside_the_window_is_refused_in_words_naming_the_window() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "beginning too late");
        SimulationView answered = account.simulation();
        LocalDate theDayAfterTheWindowCloses = answered.until().plusDays(1);

        ResponseEntity<JsonNode> refused = account.askingFor(aScenarioCalled("Stopping next year",
                aStopFrom(theDayAfterTheWindowCloses, theDayAfterTheWindowCloses.plusDays(30))));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("the refusal names both ends of the year the branches are drawn over, in the "
                        + "words every other change's day is refused in, because a customer told "
                        + "their day will not do has otherwise to guess which days would — it "
                        + "answered " + refused.getBody())
                .isEqualTo("The day a stop begins has to be inside the year this simulation is "
                        + "drawn over, which runs from " + answered.from() + " to "
                        + answered.until() + ", and " + theDayAfterTheWindowCloses + " is not.");
    }

    @Test
    void a_stop_that_ends_outside_the_window_is_refused_in_words_naming_the_window() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "ending too late");
        SimulationView answered = account.simulation();
        LocalDate theDayAfterTheWindowCloses = answered.until().plusDays(1);

        ResponseEntity<JsonNode> refused = account.askingFor(aScenarioCalled("Stopping for good",
                aStopFrom(answered.from(), theDayAfterTheWindowCloses)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("the second day is refused in the same sentence as the first and named as the "
                        + "day it is, so that a customer whose stop runs off the end of the year "
                        + "is not left wondering which of their two dates the application means — "
                        + "it answered " + refused.getBody())
                .isEqualTo("The day a stop ends has to be inside the year this simulation is "
                        + "drawn over, which runs from " + answered.from() + " to "
                        + answered.until() + ", and " + theDayAfterTheWindowCloses + " is not.");
    }

    @Test
    void asking_what_stopping_for_two_months_would_cost_still_writes_nothing() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "stopping is still free");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAnIncome("1", "5000.00");
        account.leavesARuleStanding(Map.of(
                "name", "Every Friday", "fromCurrentAccountId", account.currentAccountId(),
                "trigger", "WEEKLY", "dayOfWeek", DayOfWeek.FRIDAY.name(),
                "howMuchMoves", "A_FIXED_AMOUNT", "amount", "25.00"));
        var rulesBefore = account.rules();
        var balancesBefore = account.balances();

        account.asking(aScenarioCalled("Two months off",
                aStopFrom(today.plusDays(1), today.plusDays(DAYS_IN_THE_STOP))));

        assertThat(account.rules())
                .as("a branch that silences a rule for eight weeks silenced it inside the fold and "
                        + "nowhere else: the rule is still standing, still live and still owed "
                        + "every Friday, or exploring would cost a customer their automation")
                .isEqualTo(rulesBefore);
        assertThat(account.balances())
                .as("and not a euro of the account moved, which is the promise asking makes")
                .isEqualTo(balancesBefore);
    }
}
