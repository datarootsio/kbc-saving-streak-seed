package io.dataroots.savingstreak.whatifididthisinstead;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.anotherEachWeekOf;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.andAtMostAYearsInterestOn;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first branch anybody can ask for, driven over HTTP the way the frontend drives it: another
 * amount put away every week, from a day, beside the year the customer is already in.
 *
 * <p><strong>The question is "is the sacrifice worth anything", and the answer is a date.</strong>
 * More money in the bank is the obvious half and it is the half a customer can do in their head.
 * What they cannot do in their head is the rest of it: weeks that were not going to be secured are,
 * the ladder climbs, every euro afterwards is paid at a better rate — and the boat arrives in
 * October rather than in December. So what is asserted here is the day each goal is reached rather
 * than any row, which is what the ticket asks for and what the screen is actually for.
 *
 * <p>Everything about the arithmetic of the extra — which mornings it lands on, what it earns on the
 * morning the window opens, the week it secures — is asserted as plain arithmetic in {@code
 * TheNightIsReplayedOneDayAtATimeForTwelveMonthsTest}, because over HTTP the only way to reach a
 * named day is to wind a clock the whole run shares. What is asserted here is what the seam owes a
 * client: a branch asked for in a body, answered beside the do-nothing one, and refused in words
 * when it cannot be asked.
 *
 * <p>Its own customer every time and every date counted off the application's clock rather than the
 * machine's, for the reasons {@link AnAccountWithAFutureToAskAbout} gives.
 */
class WhatAnotherTwentyFiveAWeekWouldDoApiTest extends ApiIntegrationTest {

    @Test
    void a_branch_comes_back_beside_the_year_already_under_way_under_the_name_the_customer_gave_it() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a branch of its own");
        LocalDate today = account.theDateTheClockReads();

        SimulationView answered = account.asking(aScenarioCalled("Another twenty-five a week",
                anotherEachWeekOf("25.00", today)));

        assertThat(answered.scenarios())
                .as("two columns: the year the customer is already in, and the one they asked "
                        + "about — a projection with nothing beside it answers no question")
                .hasSize(2);
        assertThat(answered.theYearAlreadyUnderWay().called())
                .as("the do-nothing branch is first and is still named in the customer's own words "
                        + "for it, whatever else was asked")
                .isEqualTo("If I carry on as I am");
        assertThat(answered.scenarios().get(1).called())
                .as("and the branch they typed comes back under the words they typed, because four "
                        + "columns on a screen are four arguments")
                .isEqualTo("Another twenty-five a week");
        assertThat(answered.scenarios().get(1).months())
                .as("twelve rows, drawn over the same window as every other column")
                .hasSize(12);
        assertThat(answered.scenarios().get(1).until()).isEqualTo(answered.until());
    }

    @Test
    void the_extra_is_added_to_what_the_account_is_already_doing_rather_than_put_in_its_place() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "more rather than instead");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAnIncome("25", "2400.00");
        account.leavesARuleStanding(Map.of(
                "name", "Every Friday", "fromCurrentAccountId", account.currentAccountId(),
                "trigger", "WEEKLY", "dayOfWeek", DayOfWeek.FRIDAY.name(),
                "howMuchMoves", "A_FIXED_AMOUNT", "amount", "60.00"));

        SimulationView answered = account.asking(aScenarioCalled("Another twenty-five a week",
                anotherEachWeekOf("25.00", today)));
        HowAScenarioTurnsOutView carryingOn = answered.theYearAlreadyUnderWay();
        HowAScenarioTurnsOutView branch = answered.scenarios().get(1);

        long weeksOfIt = ChronoUnit.DAYS.between(answered.from(), answered.until()) / 7 + 1;
        assertThat(branch.months().get(11).balance()
                .subtract(carryingOn.months().get(11).balance()))
                .as("the difference between the two columns after a year is the extra, and the "
                        + "interest the extra itself earns: every Friday the rule already moves "
                        + "still moves, and twenty-five lands beside it on the day the customer "
                        + "named and every seventh day after — " + weeksOfIt + " of them. More "
                        + "means more, and a customer who wanted to change the rule's own figure "
                        + "would be editing the rule")
                .isBetween(new BigDecimal("25.00").multiply(BigDecimal.valueOf(weeksOfIt)),
                        andAtMostAYearsInterestOn(new BigDecimal("25.00")
                                .multiply(BigDecimal.valueOf(weeksOfIt))));
    }

    @Test
    void an_extra_that_secures_weeks_the_account_was_not_securing_climbs_the_ladder() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "climbing the ladder");
        LocalDate today = account.theDateTheClockReads();

        SimulationView answered = account.asking(aScenarioCalled("Fifty a week",
                anotherEachWeekOf("50.00", today)));
        HowAScenarioTurnsOutView carryingOn = answered.theYearAlreadyUnderWay();
        HowAScenarioTurnsOutView branch = answered.scenarios().get(1);

        assertThat(carryingOn.months().get(11).securedWeeks())
                .as("an account with no rule and no salary behind it secures nothing at all, which "
                        + "is the year this customer is heading for")
                .isZero();
        assertThat(carryingOn.months().get(11).pointsEarned())
                .as("and earns nothing in it either")
                .isZero();
        assertThat(branch.months().get(11).securedWeeks())
                .as("fifty a week is exactly what a week has to take in, so every week of the "
                        + "branch is secured and the run is well past the six the ladder stops "
                        + "climbing at")
                .isGreaterThanOrEqualTo(6);
        assertThat(branch.months().get(11).pointsEarned())
                .as("and by the last month every euro of it is paid at the top of the ladder — the "
                        + "half of the answer a balance cannot show, and the reason the run is one "
                        + "of the seven figures on a row")
                .isGreaterThan(carryingOn.months().get(11).pointsEarned());
    }

    @Test
    void a_goal_is_reached_earlier_in_the_branch_than_in_the_year_already_under_way() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a goal that arrives earlier");
        LocalDate today = account.theDateTheClockReads();
        LocalDate thisWeekBeganOn = SavingsWeek.containing(today).startsOn();
        account.declaresAWeeklyCapacity("50.00");
        account.opensAGoal("A boat", "400.00", today.plusMonths(11).toString());

        SimulationView answered = account.asking(aScenarioCalled("Another fifty a week",
                anotherEachWeekOf("50.00", today)));

        assertThat(theDaysGoalsAreReachedIn(answered.theYearAlreadyUnderWay()))
                .as("four hundred euros at the fifty a week this customer has declared is eight "
                        + "weeks of Mondays, which is the day the goals screen itself names — the "
                        + "do-nothing column has to be the customer's actual present or the "
                        + "comparison means nothing")
                .containsExactly(thisWeekBeganOn.plusWeeks(8));
        assertThat(theDaysGoalsAreReachedIn(answered.scenarios().get(1)))
                .as("and at a hundred a week it is four: the extra is given to the goals in the "
                        + "order the plan already spends weekly money in, so the boat arrives a "
                        + "month earlier. That day is the answer the customer came for, and it is "
                        + "the reason the extra has to reach the plan and not only the balance")
                .containsExactly(thisWeekBeganOn.plusWeeks(4));
    }

    @Test
    void an_amount_that_is_not_an_amount_of_money_is_refused_in_that_rules_own_words() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "not an amount");

        ResponseEntity<JsonNode> refused = account.askingFor(aScenarioCalled("Nothing a week",
                anotherEachWeekOf("0.00", account.theDateTheClockReads())));

        assertThat(refused.getStatusCode())
                .as("nothing is in an unexpected state and nothing is missing: what the customer "
                        + "does next is type a different figure, which is what a 400 asks for")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("and the sentence is the one a deposit of nothing has always been refused in, "
                        + "word for word, because a customer who has met both objections has met "
                        + "one objection — it answered " + refused.getBody())
                .isEqualTo("A deposit has to be an amount of more than zero, and 0.00 is not.");
    }

    @Test
    void a_day_the_simulation_is_not_drawn_over_is_refused_in_words_naming_the_window() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a day outside the window");
        SimulationView answered = account.simulation();
        LocalDate theDayAfterTheWindowCloses = answered.until().plusDays(1);

        ResponseEntity<JsonNode> refused = account.askingFor(aScenarioCalled("Starting too late",
                anotherEachWeekOf("25.00", theDayAfterTheWindowCloses)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("and the refusal names both ends of the year the branches are drawn over, "
                        + "because a customer told their day will not do has otherwise to guess "
                        + "which days would — it answered " + refused.getBody())
                .isEqualTo("The day another amount each week starts has to be inside the year this "
                        + "simulation is drawn over, which runs from " + answered.from() + " to "
                        + answered.until() + ", and " + theDayAfterTheWindowCloses + " is not.");
    }

    @Test
    void asking_what_another_twenty_five_a_week_would_do_still_writes_nothing() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "asking is still free");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAWeeklyCapacity("50.00");
        account.opensAGoal("A boat", "400.00", today.plusMonths(11).toString());
        account.depositsByHand("100.00");
        var before = account.balances();
        var goalsBefore = account.goals();
        var capacityBefore = account.weeklyCapacity();

        account.asking(aScenarioCalled("Another fifty a week", anotherEachWeekOf("50.00", today)));

        assertThat(account.balances())
                .as("a branch that saves fifty a week for a year makes fifty-two deposits inside "
                        + "itself and not one euro of them reached the account, or exploring would "
                        + "cost a customer money")
                .isEqualTo(before);
        assertThat(account.goals())
                .as("and the goal is exactly where it was, funded by nothing that was imagined")
                .isEqualTo(goalsBefore);
        assertThat(account.weeklyCapacity())
                .as("and the weekly figure is still the one the customer actually declared: a "
                        + "branch raises it inside the fold and adopting is a different press")
                .isEqualTo(capacityBefore);
    }

    /** The days the goals arrive on in a column, in the order the answer put them in. */
    private static List<LocalDate> theDaysGoalsAreReachedIn(HowAScenarioTurnsOutView branch) {
        return branch.thingsOfKind("A_GOAL_IS_REACHED").stream()
                .map(AThingThatHappensView::on)
                .toList();
    }
}
