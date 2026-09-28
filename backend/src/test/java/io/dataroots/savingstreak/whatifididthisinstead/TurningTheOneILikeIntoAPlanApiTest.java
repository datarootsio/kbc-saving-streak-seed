package io.dataroots.savingstreak.whatifididthisinstead;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SavingRuleView;
import io.dataroots.savingstreak.support.SeededAccounts;
import io.dataroots.savingstreak.support.WhatAdoptingChangedView;
import io.dataroots.savingstreak.support.WhatAdoptingChangedView.AChangeThePlanNowCarriesView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.aScenarioCalled;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.aStopFrom;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.anotherEachWeekOf;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.movingTheDeadlineOf;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.takingOutOn;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one press that turns a branch a customer has read into the plan they have decided on, driven
 * over HTTP the way the frontend drives it.
 *
 * <p><strong>Every assertion here is made against the screens that own the figures</strong> — the
 * weekly plan's own resource, the goals screen's goals, the account's own balance, the rules screen's
 * rules — and never against the answer the press gave about itself. That is the whole of what these
 * tests are for: an adoption that reported a capacity of sixty a week and wrote nothing would satisfy
 * every assertion about its own answer ever written, and the only honest way to say a plan changed is
 * to go and read the plan.
 *
 * <p><strong>The all-or-nothing test is the one this ticket stands on.</strong> A customer left with
 * their capacity raised and their deadline unchanged has a plan they did not choose, which is worse
 * than a refusal, so the test that matters asserts the capacity as well as the refusal: the branch
 * carries a perfectly good weekly amount <em>first</em> and a deadline in the past second, so that a
 * press which applied as it went would leave a figure behind for the assertion to find.
 *
 * <p><strong>And two of the four kinds are asserted to have done nothing at all.</strong> A
 * withdrawal is a thing a customer does on the day and pausing is what a rule's own pause already
 * is; both come back named as theirs to carry out, and the balance and the rule are read back
 * afterwards, because "the answer said it did not move the money" is not the same claim as "the money
 * did not move".
 *
 * <p>Its own customer every time and every date counted off the application's clock rather than the
 * machine's, for the reasons {@link AnAccountWithAFutureToAskAbout} gives.
 */
class TurningTheOneILikeIntoAPlanApiTest extends ApiIntegrationTest {

    @Test
    void adopting_a_branch_that_saves_more_each_week_and_moves_a_deadline_changes_both_and_says_so() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "both halves of a plan");
        LocalDate today = account.theDateTheClockReads();
        LocalDate theCarWasWantedBy = today.plusWeeks(10);
        LocalDate theCarIsWantedByInstead = today.plusWeeks(30);
        account.declaresAWeeklyCapacity("40.00");
        GoalView car = account.opensAGoal("A car", "1000.00", theCarWasWantedBy.toString());

        WhatAdoptingChangedView adopted = account.adopting(aScenarioCalled(
                "Twenty-five more and two more months",
                List.of(anotherEachWeekOf("25.00", today.plusWeeks(1)),
                        movingTheDeadlineOf(car.id(), theCarIsWantedByInstead))));

        assertThat(adopted.called())
                .as("the branch comes back under the customer's own word for it, because four "
                        + "columns are four arguments and they chose between their own words")
                .isEqualTo("Twenty-five more and two more months");
        assertThat(adopted.changes()).hasSize(2);
        assertThat(adopted.changes().get(0).kind())
                .as("the lines are in the order the branch was built in")
                .isEqualTo("SAVE_MORE_EACH_WEEK");
        assertThat(adopted.changes().get(0).yoursToCarryOut())
                .as("and a weekly amount is one the application presses for the customer")
                .isFalse();
        assertThat(adopted.changes().get(0).what())
                .as("the sentence names the figure it raised to, the figure it raised by and the "
                        + "figure it raised from, so a second press is tellable from the first")
                .contains("65.00").contains("25.00").contains("40.00");
        assertThat(adopted.changes().get(1).kind()).isEqualTo("MOVE_A_DEADLINE");
        assertThat(adopted.changes().get(1).yoursToCarryOut()).isFalse();
        assertThat(adopted.changes().get(1).what())
                .as("and the deadline's line names the goal, the new day and the day it was")
                .contains("A car").contains(theCarIsWantedByInstead.toString())
                .contains(theCarWasWantedBy.toString());

        assertThat(account.weeklyCapacity().weeklyCapacity())
                .as("and the weekly plan's own screen reports the raised capacity, because the press "
                        + "went through the module that owns the figure rather than writing one here")
                .isEqualByComparingTo(new BigDecimal("65.00"));
        assertThat(theGoalCalled(account, "A car").deadline())
                .as("and the goals screen reports the moved deadline, for the same reason")
                .isEqualTo(theCarIsWantedByInstead);
    }

    @Test
    void a_deadline_in_the_past_is_refused_in_goals_own_words_and_leaves_the_capacity_unchanged() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "all of it or none of it");
        LocalDate today = account.theDateTheClockReads();
        LocalDate theCarIsWantedBy = today.plusWeeks(10);
        account.declaresAWeeklyCapacity("40.00");
        GoalView car = account.opensAGoal("A car", "1000.00", theCarIsWantedBy.toString());

        // The good half first and the bad half second, deliberately: a press that applied as it went
        // would have written the capacity before it ever reached the deadline, and the capacity is
        // what this test goes and reads afterwards.
        ResponseEntity<JsonNode> refused = account.adoptingFor(aScenarioCalled(
                "Twenty-five more, and the car by a day gone",
                List.of(anotherEachWeekOf("25.00", today.plusWeeks(1)),
                        movingTheDeadlineOf(car.id(), today.minusDays(1)))));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("the refusal is the sentence Goals already refuses a deadline in, word for word, "
                        + "because the press went to Goals rather than forming an opinion here")
                .isEqualTo("A savings goal is wanted by a day still to come, and " + today.minusDays(1)
                        + " has already passed. Today is " + today + ".");
        assertThat(refused.getBody().get("changeNumber").asInt())
                .as("and it names which of the branch's changes caused it, counting the chips the "
                        + "way the customer reads them")
                .isEqualTo(2);
        assertThat(refused.getBody().get("change").asText())
                .as("and how that change reads, so a branch carrying two of a kind is unambiguous")
                .contains("MOVE_A_DEADLINE");

        assertThat(account.weeklyCapacity().weeklyCapacity())
                .as("and nothing at all was applied: the weekly capacity is where it was, although "
                        + "the change that would have raised it was the first one in the branch and "
                        + "was perfectly good. A customer left with the capacity raised and the "
                        + "deadline unchanged has a plan they did not choose")
                .isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(theGoalCalled(account, "A car").deadline())
                .as("and the deadline is where it was too")
                .isEqualTo(theCarIsWantedBy);
    }

    @Test
    void adopting_a_branch_that_takes_money_out_reports_it_as_the_customers_to_carry_out() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a withdrawal is yours");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAnIncome("1", "2500.00");
        account.depositsByHand("800.00");
        BalancesView before = account.balances();

        WhatAdoptingChangedView adopted = account.adopting(aScenarioCalled("Five hundred out",
                takingOutOn("500.00", today.plusWeeks(3))));

        AChangeThePlanNowCarriesView line = onlyChangeOf(adopted, "TAKE_MONEY_OUT");
        assertThat(line.yoursToCarryOut())
                .as("a withdrawal is a thing a customer does on the day, and adopting says so "
                        + "rather than quietly moving the money")
                .isTrue();
        assertThat(line.what())
                .as("and the sentence names the figure, the day and the fact that nothing was moved "
                        + "and nothing was diarised, because this application has no future-dated "
                        + "withdrawal for it to have made")
                .contains("500.00").contains(today.plusWeeks(3).toString())
                .contains("yours to carry out").contains("No money has been moved");

        assertThat(account.balances().moneyBalance())
                .as("and no money moved: the pot holds exactly what it held before the press, which "
                        + "is the claim the answer's own words could never prove")
                .isEqualByComparingTo(before.moneyBalance());
        assertThat(account.balances().pointsBalance())
                .as("and no point was earned or lost by a press that moved nothing")
                .isEqualTo(before.pointsBalance());
    }

    @Test
    void adopting_a_branch_that_stops_for_a_while_stands_no_rule_down_and_says_so() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a pause is yours");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAnIncome("1", "2500.00");
        SavingRuleView standing = account.leavesARuleStanding(Map.of(
                "name", "Every Friday", "fromCurrentAccountId", account.currentAccountId(),
                "trigger", "WEEKLY", "dayOfWeek", DayOfWeek.FRIDAY.name(),
                "howMuchMoves", "A_FIXED_AMOUNT", "amount", "60.00"));

        WhatAdoptingChangedView adopted = account.adopting(aScenarioCalled("Two months off",
                aStopFrom(today.plusWeeks(1), today.plusWeeks(9))));

        AChangeThePlanNowCarriesView line = onlyChangeOf(adopted, "STOP_FOR_A_WHILE");
        assertThat(line.yoursToCarryOut())
                .as("pausing is what a rule's own pause already is, and a stop inside a branch "
                        + "silences every rule on the account — four decisions the branch never "
                        + "made, so the answer names it rather than making them")
                .isTrue();
        assertThat(line.what())
                .as("and names both days and the screen a pause is entered on, because the customer "
                        + "is about to type them")
                .contains(today.plusWeeks(1).toString()).contains(today.plusWeeks(9).toString())
                .contains("No rule has been stood down");

        SavingRuleView afterwards = account.rules().stream()
                .filter(rule -> rule.id().equals(standing.id())).findFirst().orElseThrow();
        assertThat(afterwards.state())
                .as("and the rules screen shows the rule exactly as it was left standing")
                .isEqualTo(standing.state());
        assertThat(afterwards.pausedAt())
                .as("with nothing stood down behind the customer's back")
                .isNull();
    }

    @Test
    void pressing_adopt_twice_raises_the_weekly_capacity_twice_and_says_what_each_press_changed() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a second press is a second decision");
        LocalDate today = account.theDateTheClockReads();
        Map<String, Object> branch = aScenarioCalled("Twenty-five more a week",
                anotherEachWeekOf("25.00", today.plusWeeks(1)));

        WhatAdoptingChangedView first = account.adopting(branch);
        WhatAdoptingChangedView again = account.adopting(branch);

        assertThat(onlyChangeOf(first, "SAVE_MORE_EACH_WEEK").what())
                .as("an account whose holder had declared nothing is raised from nothing rather "
                        + "than refused, and the sentence names the absence it was raised from")
                .contains("25.00").contains("nothing declared");
        assertThat(onlyChangeOf(again, "SAVE_MORE_EACH_WEEK").what())
                .as("and the second press says what it changed too, naming the figure the first "
                        + "press left behind, so the customer can see it happened twice")
                .contains("50.00").contains("25.00");
        assertThat(account.weeklyCapacity().weeklyCapacity())
                .as("pressing twice raises the capacity twice, because the second press is a second "
                        + "decision made by somebody reading a screen the first one had redrawn")
                .isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void a_branch_made_of_nothing_is_adopted_into_nothing_rather_than_refused() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a branch of no changes");
        account.declaresAWeeklyCapacity("40.00");

        WhatAdoptingChangedView adopted =
                account.adopting(Map.of("called", "Carry on as I am", "adjustments", List.of()));

        assertThat(adopted.changes())
                .as("deciding to carry on as you are is a decision this application has nothing to "
                        + "write down, and an empty list is a true and complete account of it")
                .isEmpty();
        assertThat(account.weeklyCapacity().weeklyCapacity())
                .as("and nothing was written down")
                .isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    void a_press_on_an_account_nobody_has_heard_of_is_refused_in_the_usual_words() {
        long noSuchAccount = new SeededAccounts(http).anIdNoSavingsAccountHas();

        ResponseEntity<JsonNode> refused = http.postForEntity(
                "/api/savings-accounts/{id}/simulations/adopt",
                aScenarioCalled("Twenty-five more a week", anotherEachWeekOf("25.00", "2030-01-01")),
                JsonNode.class, noSuchAccount);

        assertThat(refused.getStatusCode())
                .as("the account is vouched for before any module is asked to change anything on "
                        + "it, which matters more on a press than on the read beside it")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().get("detail").asText())
                .as("and the sentence is Accounts' own, as it is on every other read of one")
                .isEqualTo("There is no savings account " + noSuchAccount + ".");
    }

    /** The goal by the name this test opened it under, read off the goals screen. */
    private static GoalView theGoalCalled(AnAccountWithAFutureToAskAbout account, String name) {
        return account.goals().stream()
                .filter(goal -> name.equals(goal.name()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * The one line of that kind, insisted on as the only one. A branch of one change that came back
     * carrying two would otherwise pass every assertion written about the one this test looked at.
     */
    private static AChangeThePlanNowCarriesView onlyChangeOf(WhatAdoptingChangedView adopted,
                                                             String kind) {
        assertThat(adopted.changes())
                .describedAs("the lines of what adopting changed: " + adopted.changes())
                .hasSize(1);
        List<AChangeThePlanNowCarriesView> ofKind = adopted.changesOfKind(kind);
        assertThat(ofKind).describedAs("the line about " + kind).hasSize(1);
        return ofKind.get(0);
    }
}
