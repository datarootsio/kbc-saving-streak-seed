package io.dataroots.savingstreak.savingsgoals;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsgoals.AnAccountWithGoals.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A move the application will not make is refused with the figure that decided it, and nothing is
 * recorded.
 *
 * <p>Every refusal is asserted three times over: the status, the words in {@code detail} naming the
 * figure the person needs in order to type something that would work, and that the account's money
 * is exactly where it was. The third is the important one — an invariant enforced in the answer and
 * not in the ledger is not enforced at all.
 *
 * <p>The two money refusals are apart on purpose and the tests keep them apart: "the account has not
 * got it" sends a customer to free money from another goal, and "this goal does not want it" sends
 * them to put the rest somewhere else. A single refusal for both would send half of them to the
 * wrong place.
 */
class AnAllocationIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private AnAccountWithGoals account;

    @BeforeEach
    void anAccountWithMoneyInIt() {
        account = new AnAccountWithGoals(http, "allocation refused");
        account.savesUp("500.00");
    }

    @Test
    void allocating_more_than_is_unallocated_is_refused_quoting_what_is_unallocated() {
        GoalView house = account.add("House", "20000.00", null);

        ResponseEntity<JsonNode> response = account.tryToPutTowards(house.id(), "700.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("500.00", "700.00", "Free money from another goal");
        assertNothingWasRecorded();
    }

    /** The same refusal when another goal is already holding most of the money. */
    @Test
    void allocating_more_than_the_other_goals_left_spare_is_refused_quoting_what_is_left() {
        GoalView holiday = account.add("Holiday", "600.00", null);
        GoalView house = account.add("House", "20000.00", null);
        account.putTowards(holiday.id(), "450.00");

        ResponseEntity<JsonNode> response = account.tryToPutTowards(house.id(), "100.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("50.00", "100.00");
        assertThat(account.allocations().allocated()).isEqualByComparingTo("450.00");
        assertThat(account.historyOf(house.id())).isEmpty();
    }

    @Test
    void allocating_more_than_the_goal_still_needs_is_refused_quoting_what_it_still_needs() {
        GoalView bike = account.add("Bike", "400.00", null);
        account.putTowards(bike.id(), "250.00");

        ResponseEntity<JsonNode> response = account.tryToPutTowards(bike.id(), "200.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("still needs", "150.00", "400.00", "200.00");
        assertThat(account.allocations().goal(bike.id()).allocation()).isEqualByComparingTo("250.00");
        assertThat(account.historyOf(bike.id())).hasSize(1);
    }

    /**
     * A conflict rather than a bad request, like an abandoned goal: what was typed is fine and it is
     * the state of the goal that will not have it.
     */
    @Test
    void a_goal_that_has_reached_its_target_refuses_further_money() {
        GoalView bike = account.add("Bike", "400.00", null);
        account.putTowards(bike.id(), "400.00");

        ResponseEntity<JsonNode> response = account.tryToPutTowards(bike.id(), "10.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(response)).contains("Bike", "reached its target", "400.00");
        assertThat(account.allocations().goal(bike.id()).allocation()).isEqualByComparingTo("400.00");
    }

    @Test
    void allocating_to_an_abandoned_goal_is_refused() {
        GoalView holiday = account.add("Holiday", "600.00", null);
        account.abandon(holiday.id());

        ResponseEntity<JsonNode> response = account.tryToPutTowards(holiday.id(), "10.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(response)).contains("Holiday", "given up on");
        assertNothingWasRecorded();
    }

    /** A goal cannot give back more than it is holding, and the sentence says how much that is. */
    @Test
    void freeing_more_than_the_goal_is_holding_is_refused() {
        GoalView holiday = account.add("Holiday", "600.00", null);
        account.putTowards(holiday.id(), "60.00");

        ResponseEntity<JsonNode> response = account.tryToFree(holiday.id(), "90.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("Holiday", "60.00", "90.00");
        assertThat(account.allocations().goal(holiday.id()).allocation()).isEqualByComparingTo("60.00");
    }

    @Test
    void an_amount_of_zero_is_refused() {
        GoalView holiday = account.add("Holiday", "600.00", null);

        ResponseEntity<JsonNode> response = account.tryToPutTowards(holiday.id(), "0.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("more than zero", "0.00");
        assertNothingWasRecorded();
    }

    @Test
    void a_negative_amount_is_refused() {
        GoalView holiday = account.add("Holiday", "600.00", null);

        ResponseEntity<JsonNode> response = account.tryToPutTowards(holiday.id(), "-25.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("more than zero", "-25.00");
        assertNothingWasRecorded();
    }

    /**
     * The sentence {@code AmountOfMoney} already gives, word for word — the one a deposit, a
     * withdrawal and a goal's target all get. Asserted in full rather than by a keyword because a
     * copy of the rule written out in this module would pass a keyword check and would be exactly the
     * drift the shared class exists to prevent.
     */
    @Test
    void an_amount_quoted_more_finely_than_money_is_refused_in_the_words_every_amount_gets() {
        GoalView holiday = account.add("Holiday", "600.00", null);

        ResponseEntity<JsonNode> response = account.tryToPutTowards(holiday.id(), "10.005");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .isEqualTo("An amount of money has at most two decimal places, and 10.005 has 3.");
        assertNothingWasRecorded();
    }

    /** A move with one goal at both ends moves nothing, and a row saying it happened would lie. */
    @Test
    void a_move_with_the_same_goal_at_both_ends_is_refused() {
        GoalView holiday = account.add("Holiday", "600.00", null);
        account.putTowards(holiday.id(), "100.00");

        ResponseEntity<JsonNode> response =
                account.tryToMoveBetween(holiday.id(), holiday.id(), "10.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("both ends");
        assertThat(account.historyOf(holiday.id())).hasSize(1);
    }

    /** A goal on nobody's account is the same answer as a goal on somebody else's: there is none. */
    @Test
    void moving_money_into_a_goal_that_is_not_on_the_account_is_refused() {
        GoalView holiday = account.add("Holiday", "600.00", null);

        ResponseEntity<JsonNode> response = account.tryToPutTowards(holiday.id() + 100_000, "10.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(response)).contains("no savings goal");
        assertNothingWasRecorded();
    }

    /** A direction is a word and not a sign, so a word that is not one of the two is a form to fix. */
    @Test
    void a_direction_that_is_not_one_of_the_two_is_refused() {
        GoalView holiday = account.add("Holiday", "600.00", null);

        ResponseEntity<JsonNode> response =
                account.tryToMove(holiday.id(), "10.00", "SIDEWAYS", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("INTO_THE_GOAL", "OUT_OF_THE_GOAL", "SIDEWAYS");
        assertNothingWasRecorded();
    }

    /** Nothing was written down: the whole balance is still spare, and no goal holds anything. */
    private void assertNothingWasRecorded() {
        AllocationsView allocations = account.allocations();
        assertThat(allocations.allocated()).isEqualByComparingTo("0.00");
        assertThat(allocations.unallocated()).isEqualByComparingTo(allocations.balance());
    }
}
