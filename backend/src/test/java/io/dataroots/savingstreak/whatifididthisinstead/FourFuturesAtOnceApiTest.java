package io.dataroots.savingstreak.whatifididthisinstead;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SimulationView;
import io.dataroots.savingstreak.support.SimulationView.AMonthOfTheFutureView;
import io.dataroots.savingstreak.support.SimulationView.HowAScenarioTurnsOutView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.aScenarioCalled;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.aStopFrom;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.anotherEachWeekOf;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.movingTheDeadlineOf;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.takingOutOn;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.andAtMostAYearsInterestOn;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Four futures asked about in one request, answered side by side, and the limits on how much one
 * asking may carry.
 *
 * <p><strong>Comparing is the point of the whole feature.</strong> A customer holding one branch in
 * their head and another on the screen is remembering rather than choosing, and a customer who has
 * to ask four times gets four answers folded from four different presents. So the request carries
 * the branches together, one snapshot is taken, every branch is folded from it, and they come back
 * in the order they were typed with the year already under way first.
 *
 * <p><strong>The assertion this class exists for is the one about leaking.</strong> Four columns
 * folded from one present are only four answers if none of them can reach another, and the way that
 * could go wrong is quiet: a withdrawal that drew down the snapshot's own list of deposits would
 * reprice the anniversaries in the column beside it, and every figure on the screen would still look
 * like an answer. It is asserted inside one request rather than by asking twice — the same change
 * asked for twice with a withdrawal between the two has to come back twice with the same figures,
 * and no second snapshot is involved to explain a difference away.
 *
 * <p><strong>Why the caps land here.</strong> Four scenarios and ten changes only start to mean
 * anything once a request can carry more than one of either, and the same is true of a scenario
 * nobody named: one column needs no heading to be compared against, and four do. The wordings are
 * asserted whole, because a refusal that names no limit leaves somebody finding the number by
 * trying.
 *
 * <p>Its own customer every time and every date counted off the application's clock rather than the
 * machine's, for the reasons {@link AnAccountWithAFutureToAskAbout} gives.
 */
class FourFuturesAtOnceApiTest extends ApiIntegrationTest {

    @Test
    void four_futures_come_back_in_the_order_they_were_asked_for_each_beside_the_year_already_under_way() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "four at once");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAWeeklyCapacity("50.00");
        GoalView car = account.opensAGoal("A car", "1000.00", today.plusMonths(4).toString());
        account.depositsByHand("800.00");

        SimulationView answered = account.asking(List.of(
                aScenarioCalled("Another twenty-five a week", anotherEachWeekOf("25.00", today)),
                aScenarioCalled("Two months off", aStopFrom(today, today.plusMonths(2))),
                aScenarioCalled("Five hundred out", takingOutOn("500.00", today)),
                aScenarioCalled("Two more months on the car",
                        movingTheDeadlineOf(car.id(), today.plusMonths(6)))));

        assertThat(answered.scenarios())
                .extracting(HowAScenarioTurnsOutView::called)
                .as("the year already under way first and always, then the four branches in the "
                        + "order the customer typed them — a page draws its columns left to right "
                        + "and a customer reading them is reading their own four questions back. "
                        + "Four asked is five columns, because the branch nobody has to ask for is "
                        + "a fold that ran and a column somebody reads")
                .containsExactly("If I carry on as I am", "Another twenty-five a week",
                        "Two months off", "Five hundred out", "Two more months on the car");
        assertThat(answered.scenarios())
                .as("and every one of them is drawn over the same year and comes back as twelve "
                        + "rows, because a page reading across four columns cannot draw one of them "
                        + "with eleven bars or over a different twelvemonth")
                .allSatisfy(branch -> {
                    assertThat(branch.from()).isEqualTo(answered.from());
                    assertThat(branch.until()).isEqualTo(answered.until());
                    assertThat(branch.months()).hasSize(12);
                });
        assertThat(answered.theYearAlreadyUnderWay().months().get(11).balance())
                .as("nothing else is standing on this account, so the year already under way ends "
                        + "on the eight hundred it began with and the interest the bank adds to a "
                        + "balance nobody touches — which is what the other four columns are better "
                        + "or worse than")
                .isBetween(new BigDecimal("800.00"), andAtMostAYearsInterestOn("800.00"));
    }

    @Test
    void a_withdrawal_in_one_future_leaves_the_others_holding_every_cent() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "no leaking");
        LocalDate today = account.theDateTheClockReads();
        account.depositsByHand("800.00");
        Map<String, Object> twentyFiveAWeek = anotherEachWeekOf("25.00", today);

        // The same change asked for twice with a withdrawal between the two, in one request and
        // therefore off one snapshot: if the middle branch could reach what it was folded from, the
        // third column would answer a different question from the first and nothing would say so.
        SimulationView answered = account.asking(List.of(
                aScenarioCalled("Twenty-five a week", twentyFiveAWeek),
                aScenarioCalled("Five hundred out", takingOutOn("500.00", today)),
                aScenarioCalled("Twenty-five a week, asked again", twentyFiveAWeek)));
        HowAScenarioTurnsOutView first = answered.scenarios().get(1);
        HowAScenarioTurnsOutView takingItOut = answered.scenarios().get(2);
        HowAScenarioTurnsOutView askedAgain = answered.scenarios().get(3);

        assertThat(takingItOut.months().get(11).balance())
                .as("the branch in the middle really did take five hundred out, so this is not "
                        + "three columns agreeing about nothing")
                .isBetween(new BigDecimal("300.00"), andAtMostAYearsInterestOn("300.00"));
        assertThat(askedAgain.months())
                .as("and the same question asked on the far side of it comes back with every one "
                        + "of its eighty-four figures unchanged. TheStartingPoint copies every list "
                        + "it is handed for exactly this — the deposits a withdrawal draws down are "
                        + "that branch's own running copy, so the anniversaries the other columns "
                        + "are priced on are the ones the customer actually has")
                .isEqualTo(first.months());
        assertThat(askedAgain.thingsThatHappen())
                .as("the dated events with them, which is where a bonus repriced by somebody else's "
                        + "withdrawal would show up first")
                .isEqualTo(first.thingsThatHappen());
        assertThat(answered.theYearAlreadyUnderWay().months())
                .extracting(AMonthOfTheFutureView::balance)
                .as("and the column nobody asked for is untouched by all three of them: eight "
                        + "hundred euros throughout, give or take what the bank itself adds")
                .allMatch(balance -> balance.compareTo(new BigDecimal("800.00")) >= 0
                        && balance.compareTo(andAtMostAYearsInterestOn("800.00")) <= 0);
    }

    @Test
    void two_withdrawals_on_two_days_in_one_scenario_are_two_withdrawals() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "two withdrawals");
        LocalDate today = account.theDateTheClockReads();
        account.depositsByHand("800.00");

        SimulationView answered = account.asking(aScenarioCalled("Three hundred now, two hundred later",
                List.of(takingOutOn("300.00", today),
                        takingOutOn("200.00", today.plusMonths(3)))));

        assertThat(answered.scenarios().get(1).months().get(11).balance())
                .as("five hundred out altogether, because every change on a branch is asked of "
                        + "every morning and the later one does not stand in for the earlier. A "
                        + "branch in which it did would close on six hundred and quietly hand back "
                        + "two hundred euros the customer had said they were taking out")
                .isBetween(new BigDecimal("300.00"), andAtMostAYearsInterestOn("300.00"));
        assertThat(answered.theYearAlreadyUnderWay().months().get(11).balance())
                .as("beside the year they are already in, which took nothing out at all")
                .isBetween(new BigDecimal("800.00"), andAtMostAYearsInterestOn("800.00"));
    }

    @Test
    void a_fifth_future_in_one_asking_is_refused_in_words_naming_the_limit() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a fifth column");
        LocalDate today = account.theDateTheClockReads();
        List<Map<String, Object>> fiveOfThem = new ArrayList<>();
        for (int column = 1; column <= 5; column++) {
            fiveOfThem.add(aScenarioCalled("Branch " + column,
                    anotherEachWeekOf("10.00", today)));
        }

        ResponseEntity<JsonNode> refused = account.askingFor(fiveOfThem);

        assertThat(refused.getStatusCode())
                .as("nothing is in an unexpected state and nothing is missing: what the customer "
                        + "does next is ask about fewer futures, which is what a 400 asks for")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("and the sentence names the limit rather than merely refusing, because a "
                        + "customer told they have asked for too many has otherwise to find the "
                        + "number by trying — it answered " + refused.getBody())
                .isEqualTo("This question asks about 5 futures at once, and at most 4 can be "
                        + "compared in one asking. Ask about fewer of them, or ask twice.");
        assertThat(refused.getBody().has("scenario"))
                .as("and it points at no column, because the objection is to how many there are "
                        + "rather than to any one of them")
                .isFalse();
    }

    @Test
    void an_eleventh_change_in_one_future_is_refused_in_words_naming_the_limit() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "an eleventh change");
        LocalDate today = account.theDateTheClockReads();
        List<Map<String, Object>> tenChanges = new ArrayList<>();
        for (int change = 1; change <= 10; change++) {
            tenChanges.add(anotherEachWeekOf("1.00", today.plusDays(change)));
        }
        List<Map<String, Object>> elevenChanges = new ArrayList<>(tenChanges);
        elevenChanges.add(anotherEachWeekOf("1.00", today.plusDays(11)));

        SimulationView tenAreAnswered =
                account.asking(aScenarioCalled("Ten is fine", tenChanges));
        ResponseEntity<JsonNode> refused = account.askingFor(
                List.of(aScenarioCalled("Everything at once", elevenChanges)));

        assertThat(tenAreAnswered.scenarios()).hasSize(2);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("the eleventh is refused in a sentence naming both the count and the limit, and "
                        + "saying the one thing a customer can actually do about it — it answered "
                        + refused.getBody())
                .isEqualTo("The scenario called \"Everything at once\" is made of 11 changes, and "
                        + "one scenario can be made of at most 10. Ask about fewer changes, or "
                        + "split it into two scenarios.");
        assertThat(refused.getBody().get("scenario").asText())
                .as("and the column it is about travels beside the sentence, so a page comparing "
                        + "four of them can point at the right heading")
                .isEqualTo("Everything at once");
    }

    @Test
    void a_bad_change_in_the_last_future_is_refused_naming_which_scenario_and_which_change() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "which column");
        LocalDate today = account.theDateTheClockReads();
        SimulationView answered = account.simulation();
        LocalDate theDayAfterTheWindowCloses = answered.until().plusDays(1);

        ResponseEntity<JsonNode> refused = account.askingFor(List.of(
                aScenarioCalled("Twenty-five a week", anotherEachWeekOf("25.00", today)),
                aScenarioCalled("Fifty a week", anotherEachWeekOf("50.00", today)),
                aScenarioCalled("Five hundred out", takingOutOn("500.00", today)),
                aScenarioCalled("Both, but badly", List.of(
                        anotherEachWeekOf("25.00", today),
                        anotherEachWeekOf("25.00", theDayAfterTheWindowCloses)))));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("the reason is the kind's own sentence, word for word the one a customer would "
                        + "have read had they asked about this one change on its own: an objection "
                        + "that reworded itself according to how many columns were on the screen "
                        + "would be two refusals for one mistake — it answered " + refused.getBody())
                .isEqualTo("The day another amount each week starts has to be inside the year this "
                        + "simulation is drawn over, which runs from " + answered.from() + " to "
                        + answered.until() + ", and " + theDayAfterTheWindowCloses + " is not.");
        assertThat(refused.getBody().get("scenario").asText())
                .as("and which of the four columns it is about travels beside it, because a true "
                        + "sentence about a day leaves somebody staring at forty boxes otherwise")
                .isEqualTo("Both, but badly");
        assertThat(refused.getBody().get("changeNumber").asInt())
                .as("with which chip under that heading, counting from one as a customer counts "
                        + "them — the position and not only the words, because two changes of one "
                        + "kind in one scenario compose rather than replacing each other and can "
                        + "read identically")
                .isEqualTo(2);
        assertThat(refused.getBody().get("change").asText())
                .as("and the change as it was asked, in the phrase the log line names it by, so "
                        + "that a reviewer reading the warning and a customer reading the screen "
                        + "are looking at the same thing")
                .isEqualTo("SAVE_MORE_EACH_WEEK amount=25.00 from=" + theDayAfterTheWindowCloses);
    }

    @Test
    void a_future_nobody_named_is_refused_in_words_naming_where_it_was_typed() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "no heading");
        LocalDate today = account.theDateTheClockReads();

        ResponseEntity<JsonNode> refused = account.askingFor(List.of(
                aScenarioCalled("Twenty-five a week", anotherEachWeekOf("25.00", today)),
                Map.<String, Object>of("adjustments",
                        List.of(anotherEachWeekOf("50.00", today)))));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("a column with no heading cannot be compared against the one beside it, which "
                        + "is the whole point of asking about several at once — and the refusal "
                        + "names the position rather than the name, because the position is the "
                        + "only thing there is to call it — it answered " + refused.getBody())
                .isEqualTo("The scenario at position 2 in this question has no name, and a column "
                        + "with no heading cannot be compared against the one beside it. Give it "
                        + "the words you would use to choose between them.");
    }

    @Test
    void a_future_named_with_nothing_but_spaces_is_refused_in_the_same_words() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a heading of spaces");

        ResponseEntity<JsonNode> refused = account.askingFor(List.of(
                Map.<String, Object>of("called", "   ", "adjustments", List.of())));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("a heading a page would draw as an empty box is no heading, and it is refused "
                        + "in the same sentence rather than in one of its own: a customer who has "
                        + "typed a space and a customer who has typed nothing have made the same "
                        + "mistake — it answered " + refused.getBody())
                .isEqualTo("The scenario at position 1 in this question has no name, and a column "
                        + "with no heading cannot be compared against the one beside it. Give it "
                        + "the words you would use to choose between them.");
    }

    @Test
    void asking_about_four_futures_at_once_still_writes_nothing() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "four is still free");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAWeeklyCapacity("50.00");
        GoalView car = account.opensAGoal("A car", "1000.00", today.plusMonths(4).toString());
        account.depositsByHand("500.00");
        var savingsBefore = account.balances();
        var goalsBefore = account.goals();
        var capacityBefore = account.weeklyCapacity();
        var everydayBefore = account.currentAccount();

        account.asking(List.of(
                aScenarioCalled("Another twenty-five a week", anotherEachWeekOf("25.00", today)),
                aScenarioCalled("Two months off", aStopFrom(today, today.plusMonths(2))),
                aScenarioCalled("Everything out", takingOutOn("500.00", today)),
                aScenarioCalled("Two more months on the car",
                        movingTheDeadlineOf(car.id(), today.plusMonths(6)))));

        assertThat(account.balances())
                .as("four branches at once, one of which empties the account inside itself, and "
                        + "the real one holds every cent with the same points and the same "
                        + "high-water mark behind them")
                .isEqualTo(savingsBefore);
        assertThat(account.goals())
                .as("the deadline moved in the fourth column moved nowhere on the goal")
                .isEqualTo(goalsBefore);
        assertThat(account.weeklyCapacity())
                .as("and the extra twenty-five in the first is not something this customer has "
                        + "declared — asking four questions is exactly as free as asking none")
                .isEqualTo(capacityBefore);
        assertThat(account.currentAccount())
                .as("and the money the third column took out did not arrive anywhere either")
                .isEqualTo(everydayBefore);
    }
}
