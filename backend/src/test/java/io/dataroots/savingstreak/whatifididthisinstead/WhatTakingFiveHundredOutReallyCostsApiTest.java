package io.dataroots.savingstreak.whatifididthisinstead;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.anotherEachWeekOf;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.takingOutOn;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.andAtMostAYearsInterestOn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * What taking five hundred euros out really costs, asked over HTTP the way the frontend asks it.
 *
 * <p><strong>The branch the whole feature is most worth having for.</strong> A customer about to
 * take money out of savings can predict exactly one of the five things that then happen: the balance
 * falls. The other four are the ones this asserts — the anniversaries ahead of the withdrawal pay a
 * smaller tenth because the deposits they are counted on hold less, the week's net saving falls and
 * takes a run with it, the money paid back in afterwards earns nothing at all, and a goal's claim
 * stops a withdrawal in a branch exactly as it stops one in the application. Every one of them is a
 * rule already written down in this codebase, and until this branch existed the only way to find out
 * was to press the button.
 *
 * <p><strong>Why the anniversary is asserted as the figure a bonus pays rather than as a row.</strong>
 * A month row would move for half a dozen reasons at once and a column of balances cannot tell a
 * repriced anniversary from a withdrawal that was merely subtracted — which is the one thing this
 * branch has to prove. The dated event carries the points the bonus actually paid, so thirty in the
 * year already under way and ten in the branch is the whole argument in two numbers.
 *
 * <p>The arithmetic of a withdrawal that lands on a Sunday, of the deposits it draws down in order
 * and of the week it costs is asserted as plain arithmetic in {@code
 * TheNightIsReplayedOneDayAtATimeForTwelveMonthsTest}, for the reason that class gives: over HTTP
 * the only way to reach a named day is to wind a clock the whole run shares. What is asserted here
 * is what the seam owes a client — a withdrawal asked for in a body, answered beside the do-nothing
 * branch, refused in words when it cannot be asked at all, and writing nothing whatsoever.
 *
 * <p>Its own customer every time and every date counted off the application's clock rather than the
 * machine's, for the reasons {@link AnAccountWithAFutureToAskAbout} gives.
 */
class WhatTakingFiveHundredOutReallyCostsApiTest extends ApiIntegrationTest {

    @Test
    void a_branch_that_takes_money_out_comes_back_beside_the_year_already_under_way_with_less_in_it() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "five hundred out");
        LocalDate today = account.theDateTheClockReads();
        account.depositsByHand("800.00");

        SimulationView answered = account.asking(aScenarioCalled("Taking five hundred out",
                takingOutOn("500.00", today)));
        HowAScenarioTurnsOutView carryingOn = answered.theYearAlreadyUnderWay();
        HowAScenarioTurnsOutView branch = answered.scenarios().get(1);

        assertThat(branch.called())
                .as("the branch comes back under the words the customer typed, beside the year they "
                        + "are already in — a figure with nothing to be worse than is not an answer")
                .isEqualTo("Taking five hundred out");
        assertThat(carryingOn.months().get(11).balance())
                .as("nothing else is happening on this account, so the year already under way ends "
                        + "where it began, plus the interest the bank adds to money left alone")
                .isBetween(new BigDecimal("800.00"), andAtMostAYearsInterestOn("800.00"));
        assertThat(branch.months().get(11).balance())
                .as("and the branch ends five hundred lower, which is the one consequence of a "
                        + "withdrawal a customer can work out for themselves — and a little lower "
                        + "still, because the five hundred that left in March earned nothing for "
                        + "the rest of the year")
                .isBetween(new BigDecimal("300.00"), andAtMostAYearsInterestOn("300.00"));
    }

    @Test
    void a_later_anniversary_pays_less_in_the_branch_because_the_deposit_it_is_paid_on_holds_less() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a repriced anniversary");
        LocalDate today = account.theDateTheClockReads();
        // Paid in today, so it turns a year old on the very last day the branches are drawn over,
        // which is the only anniversary a test that may not wind the clock can reach.
        account.depositsByHand("300.00");

        SimulationView answered = account.asking(aScenarioCalled("Taking two hundred out",
                takingOutOn("200.00", today)));

        assertThat(theBonusesPaidIn(answered.theYearAlreadyUnderWay()))
                .as("three hundred euros left where they are pay a tenth of their whole euros on "
                        + "the day they turn a year old, which is the day this window closes")
                .containsExactly(new AThingThatHappensView(answered.until(), "A_BONUS_IS_PAID",
                        new BigDecimal("30")));
        assertThat(theBonusesPaidIn(answered.scenarios().get(1)))
                .as("and two hundred of them taken out leaves a hundred to be paid on: the "
                        + "anniversary is repriced rather than cancelled. This is the assertion that "
                        + "proves the withdrawal is costed rather than merely subtracted — a branch "
                        + "that lowered a balance and left the deposits alone would still pay thirty "
                        + "here, and would be promising the customer points the application is never "
                        + "going to credit them")
                .containsExactly(new AThingThatHappensView(answered.until(), "A_BONUS_IS_PAID",
                        new BigDecimal("10")));
    }

    @Test
    void a_withdrawal_draws_the_oldest_deposit_down_first_which_is_what_decides_the_bonuses_ahead() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "the oldest deposit first");
        LocalDate today = account.theDateTheClockReads();
        // Two deposits, in this order, both turning a year old on the day the window closes: what
        // each of them pays on that day is the only evidence there is of which one the withdrawal
        // actually came out of, and it is exactly the evidence the customer will get.
        account.depositsByHand("100.00");
        account.depositsByHand("300.00");

        SimulationView answered = account.asking(aScenarioCalled("Taking a hundred and fifty out",
                takingOutOn("150.00", today)));

        assertThat(theBonusesPaidIn(answered.theYearAlreadyUnderWay()))
                .as("left alone, each deposit pays a tenth of its own whole euros")
                .containsExactly(
                        new AThingThatHappensView(answered.until(), "A_BONUS_IS_PAID",
                                new BigDecimal("10")),
                        new AThingThatHappensView(answered.until(), "A_BONUS_IS_PAID",
                                new BigDecimal("30")));
        assertThat(theBonusesPaidIn(answered.scenarios().get(1)))
                .as("a hundred and fifty out empties the deposit that has been there longest and "
                        + "takes the other fifty off the newer one, exactly as a withdrawal at the "
                        + "cashpoint draws: the older pays nothing, because a tenth of nothing is "
                        + "not a bonus and an anniversary worth nothing is not an event, and the "
                        + "newer pays on the two hundred and fifty it has left. Drawn the other way "
                        + "round this column would read 10 and 15, which is a different year")
                .containsExactly(new AThingThatHappensView(answered.until(), "A_BONUS_IS_PAID",
                        new BigDecimal("25")));
    }

    @Test
    void a_withdrawal_that_takes_the_week_under_the_minimum_loses_that_week_and_ends_the_run() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a run that ends");
        LocalDate today = account.theDateTheClockReads();
        LocalDate theDayItComesOut = today.plusMonths(6);
        Map<String, Object> fiftyAWeek = anotherEachWeekOf("50.00", today);

        HowAScenarioTurnsOutView savingOn = account.asking(
                aScenarioCalled("Fifty a week", List.of(fiftyAWeek))).scenarios().get(1);
        HowAScenarioTurnsOutView takingSomeBack = account.asking(
                aScenarioCalled("Fifty a week, and five hundred out in six months",
                        List.of(fiftyAWeek, takingOutOn("500.00", theDayItComesOut))))
                .scenarios().get(1);

        assertThat(savingOn.thingsOfKind("A_WEEK_IS_LOST"))
                .as("fifty a week is exactly what a week asks for, so a branch that only saves "
                        + "never loses one")
                .isEmpty();
        assertThat(takingSomeBack.thingsOfKind("A_WEEK_IS_LOST"))
                .extracting(AThingThatHappensView::on)
                .as("but five hundred out of the same week leaves it four hundred and fifty short, "
                        + "because a week counts what came out as well as what went in — so the "
                        + "week is lost on its own Sunday and the whole run behind it goes with it. "
                        + "A run does not shorten by one: it ends, and the ladder starts again from "
                        + "the bottom")
                .containsExactly(SavingsWeek.containing(theDayItComesOut).endsOn());
        assertThat(takingSomeBack.months().get(11).pointsStanding())
                .as("which is why the branch is short of points a year later by far more than the "
                        + "five hundred euros it took out — every euro paid in for the six weeks "
                        + "afterwards is climbing the ladder again. That is the cost of a "
                        + "withdrawal a balance on its own can never show")
                .isLessThan(savingOn.months().get(11).pointsStanding());
    }

    @Test
    void money_paid_back_in_after_a_withdrawal_earns_nothing_on_the_euros_that_climb_back_to_the_mark() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "paying it back in");
        LocalDate today = account.theDateTheClockReads();
        account.depositsByHand("300.00");

        SimulationView answered = account.asking(
                aScenarioCalled("Take two hundred out and save it back", List.of(
                        takingOutOn("200.00", today),
                        anotherEachWeekOf("50.00", today.plusDays(1)))));

        assertThat(theMoneyThatEarnedNothingIn(answered.theYearAlreadyUnderWay()))
                .as("a customer standing at their own high-water mark earns on every euro they put "
                        + "away, so the year already under way has nothing of this kind in it")
                .isEmpty();
        assertThat(theMoneyThatEarnedNothingIn(answered.scenarios().get(1)))
                .as("but two hundred taken out leaves them two hundred below a mark that does not "
                        + "come down with it, so the first four fifties merely fill the gap back up "
                        + "and earn not one point between them. Four, and not every week of the "
                        + "year: the fifth is above the mark again and earns in full. A customer who "
                        + "believes a withdrawal is a loan against their own points is wrong, and "
                        + "this is the application saying so before it costs them rather than after")
                .extracting(AThingThatHappensView::on, AThingThatHappensView::figure)
                .containsExactly(
                        tuple(today.plusDays(1), new BigDecimal("50.00")),
                        tuple(today.plusDays(8), new BigDecimal("50.00")),
                        tuple(today.plusDays(15), new BigDecimal("50.00")),
                        tuple(today.plusDays(22), new BigDecimal("50.00")));
    }

    @Test
    void a_withdrawal_larger_than_what_the_goals_have_left_free_falls_short_at_that_figure() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "money the goals have claimed");
        LocalDate today = account.theDateTheClockReads();
        account.depositsByHand("500.00");
        GoalView boat = account.opensAGoal("A boat", "600.00", today.plusMonths(6).toString());
        movesIntoTheGoal(account, boat.id(), "400.00");

        SimulationView answered = account.asking(aScenarioCalled("Taking three hundred out",
                takingOutOn("300.00", today)));
        HowAScenarioTurnsOutView branch = answered.scenarios().get(1);

        assertThat(answered.whereThisAccountStands().unallocated())
                .as("five hundred in the account with four hundred of it spoken for by the boat")
                .isEqualByComparingTo("100.00");
        assertThat(branch.thingsOfKind("A_WITHDRAWAL_FALLS_SHORT"))
                .as("so a request for three hundred takes the hundred that is free and says so, "
                        + "carrying what it managed. Not a refusal: the fold takes what there is and "
                        + "carries on, because refusing would make the most interesting question in "
                        + "the set — what happens if I take out more than I have — unaskable")
                .containsExactly(new AThingThatHappensView(today, "A_WITHDRAWAL_FALLS_SHORT",
                        new BigDecimal("100.00")));
        assertThat(branch.months().get(11).balance())
                .as("and the branch carries on a hundred lighter rather than five hundred lighter, "
                        + "because what is free is the unallocated money and not the balance — a "
                        + "goal's claim stops a withdrawal in a branch exactly as it stops one at "
                        + "the cashpoint")
                .isBetween(new BigDecimal("400.00"), andAtMostAYearsInterestOn("400.00"));
    }

    @Test
    void an_amount_that_is_not_an_amount_of_money_is_refused_in_that_rules_own_words_about_a_withdrawal() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "not an amount to take out");

        ResponseEntity<JsonNode> refused = account.askingFor(aScenarioCalled("Taking nothing out",
                takingOutOn("0.00", account.theDateTheClockReads())));

        assertThat(refused.getStatusCode())
                .as("nothing is missing and nothing is in an unexpected state: what the customer "
                        + "does next is type a different figure, which is what a 400 asks for")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("and the sentence names the movement they were making, which is the whole of "
                        + "what this kind has to say about it — a customer who typed a bad figure "
                        + "into a withdrawal is answered about a withdrawal. It answered "
                        + refused.getBody())
                .isEqualTo("A withdrawal has to be an amount of more than zero, and 0.00 is not.");
    }

    @Test
    void a_day_the_simulation_is_not_drawn_over_is_refused_in_words_naming_the_window() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "taking money out too late");
        SimulationView answered = account.simulation();
        LocalDate theDayAfterTheWindowCloses = answered.until().plusDays(1);

        ResponseEntity<JsonNode> refused = account.askingFor(aScenarioCalled("Taking it out too late",
                takingOutOn("500.00", theDayAfterTheWindowCloses)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("the same form of words every other change's day is refused in, naming both "
                        + "ends of the year the branches are drawn over — a customer who has met two "
                        + "refusals should have met one objection twice. It answered "
                        + refused.getBody())
                .isEqualTo("The day the money comes out has to be inside the year this simulation "
                        + "is drawn over, which runs from " + answered.from() + " to "
                        + answered.until() + ", and " + theDayAfterTheWindowCloses + " is not.");
    }

    @Test
    void asking_what_taking_five_hundred_out_would_do_takes_nothing_out() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "taking it out costs nothing");
        LocalDate today = account.theDateTheClockReads();
        account.depositsByHand("500.00");
        var savingsBefore = account.balances();
        var everydayBefore = account.currentAccount();

        account.asking(aScenarioCalled("Take it all out and put it back", List.of(
                takingOutOn("500.00", today),
                anotherEachWeekOf("50.00", today.plusDays(1)))));

        assertThat(account.balances())
                .as("a branch that empties the account inside itself leaves the real one holding "
                        + "every cent, with the same points and the same high-water mark behind "
                        + "them — the promise the whole feature rests on is that asking is free")
                .isEqualTo(savingsBefore);
        assertThat(account.currentAccount())
                .as("and the money the branch took out did not arrive anywhere either, because no "
                        + "money moved: a withdrawal in a branch is arithmetic over a snapshot "
                        + "nobody can write to")
                .isEqualTo(everydayBefore);
    }

    /** Only the bonuses a branch's anniversaries paid, which is what a withdrawal reprices. */
    private static List<AThingThatHappensView> theBonusesPaidIn(HowAScenarioTurnsOutView branch) {
        return branch.thingsOfKind("A_BONUS_IS_PAID");
    }

    /** Only the days money arrived in a branch and earned nothing at all. */
    private static List<AThingThatHappensView> theMoneyThatEarnedNothingIn(
            HowAScenarioTurnsOutView branch) {
        return branch.thingsOfKind("MONEY_ARRIVES_AND_EARNS_NOTHING");
    }

    /**
     * Money moved into a goal out of what no goal has claimed, insisted on — which is the only way
     * to make a branch's withdrawal fall short at a figure the goals decided.
     *
     * <p>Through the goals screen's own resource rather than through anything this feature owns,
     * because the claim a withdrawal is stopped by has to be the claim the application itself would
     * stop one at.
     */
    private void movesIntoTheGoal(AnAccountWithAFutureToAskAbout account, long goalId,
                                  String amount) {
        Map<String, Object> move = new HashMap<>();
        move.put("amount", amount);
        move.put("direction", "INTO_THE_GOAL");
        ResponseEntity<JsonNode> moved = http.postForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}/allocations", move, JsonNode.class,
                account.id(), goalId);
        assertThat(moved.getStatusCode())
                .describedAs("moving " + amount + " into goal " + goalId + ": " + moved.getBody())
                .isEqualTo(HttpStatus.OK);
    }
}
