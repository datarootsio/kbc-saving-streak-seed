package io.dataroots.savingstreak.savingsgoals;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsgoals.AnAccountWithGoals.idsOf;
import static io.dataroots.savingstreak.savingsgoals.AnAccountWithGoals.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The order of importance is set whole, in one call, or not at all.
 *
 * <p>A strict total order has no valid intermediate state, which is the argument for the shape of
 * this endpoint and the thing these tests exist to hold it to. A request that names a subset would
 * leave a goal with no place and no way to say where it went; a request that named one goal twice
 * would leave two goals wanting one place. Both are refused, and — the half that matters — both
 * leave the order exactly where it was, because an order half applied is worse than an order not
 * applied: every figure a later slice derives would be read off it.
 *
 * <p>Every refusal is asserted three ways: the status, a problem document carrying words a person
 * can read, and the order itself unchanged.
 */
class TheOrderOfImportanceIsSetInOneCallApiTest extends ApiIntegrationTest {

    private AnAccountWithGoals account;
    private GoalView emergencyFund;
    private GoalView holiday;
    private GoalView bike;

    @BeforeEach
    void threeGoalsInTheOrderTheyWereOpenedIn() {
        account = new AnAccountWithGoals(http, "reordered");
        emergencyFund = account.add("Emergency fund", "3000.00", null);
        holiday = account.add("Holiday", "1200.00", null);
        bike = account.add("New bike", "800.00", null);
        assertThat(idsOf(account.goals()))
                .containsExactly(emergencyFund.id(), holiday.id(), bike.id());
    }

    /**
     * The whole point of the feature: saying that the house matters more than the holiday now, and
     * having the application read it that way afterwards. The answer and the list are both asserted,
     * because the answer is what the page redraws from and the list is what everything else reads.
     */
    @Test
    void a_complete_permutation_applies_the_whole_new_order() {
        List<GoalView> reordered = account.reorder(List.of(bike.id(), emergencyFund.id(), holiday.id()));

        assertThat(idsOf(reordered)).containsExactly(bike.id(), emergencyFund.id(), holiday.id());
        assertThat(reordered.stream().map(GoalView::rank))
                .describedAs("the places still run 1..n with no tie and no gap")
                .containsExactly(1, 2, 3);
        assertThat(idsOf(account.goals()))
                .containsExactly(bike.id(), emergencyFund.id(), holiday.id());
    }

    /**
     * A list that leaves one out. There is no answer to "where does the one you did not mention go",
     * so there is no order to apply, and the goal that was left out is named in the reason so the
     * page can say which box it forgot.
     */
    @Test
    void an_order_that_omits_a_live_goal_is_refused_and_nothing_moves() {
        ResponseEntity<JsonNode> response = account.tryToReorder(List.of(bike.id(), holiday.id()));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains(String.valueOf(emergencyFund.id()));
        assertNothingMoved();
    }

    /** A list that wants to put one goal in two places at once. */
    @Test
    void an_order_that_names_a_goal_twice_is_refused_and_nothing_moves() {
        ResponseEntity<JsonNode> response =
                account.tryToReorder(List.of(bike.id(), bike.id(), holiday.id()));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("more than once");
        assertNothingMoved();
    }

    /**
     * A goal on somebody else's account, which is the refusal with something to protect: accepted, it
     * would drag another customer's goal into this account's order, and the rank it took there would
     * be the rank it no longer holds where it belongs.
     */
    @Test
    void an_order_naming_a_goal_on_another_account_is_refused_and_nothing_moves() {
        AnAccountWithGoals somebodyElse = new AnAccountWithGoals(http, "reordered-elsewhere");
        GoalView theirs = somebodyElse.add("Their kitchen", "4000.00", null);

        ResponseEntity<JsonNode> response = account.tryToReorder(
                List.of(bike.id(), emergencyFund.id(), holiday.id(), theirs.id()));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains(String.valueOf(theirs.id()));
        assertNothingMoved();
        assertThat(somebodyElse.goal(theirs.id()).rank())
                .describedAs("and the goal that was dragged into it is where it was, on its own account")
                .isEqualTo(1);
    }

    /** An empty list is the same mistake read from the far end: it omits all three. */
    @Test
    void an_empty_order_is_refused_and_nothing_moves() {
        ResponseEntity<JsonNode> response = account.tryToReorder(List.of());

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingMoved();
    }

    private void assertRefused(ResponseEntity<JsonNode> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(reasonGivenBy(response))
                .describedAs("a refusal is only useful if the person who caused it can read why")
                .isNotBlank();
    }

    private void assertNothingMoved() {
        List<GoalView> unchanged = account.goals();
        assertThat(idsOf(unchanged))
                .describedAs("an order half applied is worse than an order not applied")
                .containsExactly(emergencyFund.id(), holiday.id(), bike.id());
        assertThat(unchanged.stream().map(GoalView::rank)).containsExactly(1, 2, 3);
    }
}
