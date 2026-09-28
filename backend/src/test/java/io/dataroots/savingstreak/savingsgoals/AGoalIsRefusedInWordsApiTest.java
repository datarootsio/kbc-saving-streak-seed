package io.dataroots.savingstreak.savingsgoals;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsgoals.AnAccountWithGoals.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A goal the application will not keep is refused with a reason somebody can act on, and nothing is
 * written down.
 *
 * <p>Every refusal is asserted twice over: the status, and words in {@code detail} naming what was
 * objected to. A refusal that came back as a bare status would leave a person guessing which of the
 * things they typed was wrong, and this application answers every error in one shape precisely so
 * that it never has to.
 *
 * <p>A target is an amount of money, so the two objections to one are the same two a deposit and a
 * withdrawal get, in the same words: {@code AmountOfMoney} owns the rule and the sentence, and a
 * customer who has met the objection once has met it everywhere. The decimal-places test below reads
 * that sentence rather than a paraphrase of it, which is what would fail if the wording were ever
 * forked into this module.
 */
class AGoalIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private AnAccountWithGoals account;

    @BeforeEach
    void anAccountToBeRefusedOn() {
        account = new AnAccountWithGoals(http, "refused");
    }

    @Test
    void a_target_of_zero_is_refused_and_nothing_is_written_down() {
        ResponseEntity<JsonNode> response = account.tryToAddWithTargetAsTyped("Nothing at all", "0.00");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("more than zero", "0.00");
        assertThat(account.goals()).isEmpty();
    }

    @Test
    void a_target_of_less_than_zero_is_refused() {
        ResponseEntity<JsonNode> response = account.tryToAddWithTargetAsTyped("Owing money", "-50.00");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("more than zero", "-50.00");
        assertThat(account.goals()).isEmpty();
    }

    /**
     * The sentence {@code AmountOfMoney} already gives, word for word. It is asserted in full rather
     * than by a keyword because the point of this criterion is that the wording is shared: a copy of
     * the rule written out in the goals module would pass a keyword check and would be exactly the
     * drift the shared class exists to prevent.
     */
    @Test
    void a_target_quoted_more_finely_than_money_is_refused_in_the_words_every_amount_gets() {
        ResponseEntity<JsonNode> response = account.tryToAddWithTargetAsTyped("Too precise", "10.005");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .isEqualTo("An amount of money has at most two decimal places, and 10.005 has 3.");
        assertThat(account.goals()).isEmpty();
    }

    /**
     * A day already gone. Refused at creation rather than accepted and reported late, because a
     * projection measured against it would call the goal late the moment it was opened — and the
     * customer has learned nothing they did not know when they typed it.
     */
    @Test
    void a_deadline_already_in_the_past_is_refused() {
        ResponseEntity<JsonNode> response =
                account.tryToAdd("Yesterday's goal", "500.00", account.today().minusDays(1));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("already passed");
        assertThat(account.goals()).isEmpty();
    }

    /**
     * Today is not in the past. The boundary is worth pinning down: a goal wanted by the end of today
     * is a goal somebody can still reach, and an off-by-one here would refuse the most urgent goal
     * anybody ever types.
     */
    @Test
    void a_deadline_of_today_is_accepted() {
        GoalView opened = account.add("Today's goal", "500.00", account.today());

        assertThat(opened.deadline()).isEqualTo(account.today());
    }

    /** A deadline that has gone by cannot be set on an existing goal either, for the same reason. */
    @Test
    void a_deadline_already_in_the_past_is_refused_on_a_goal_that_exists() {
        GoalView goal = account.add("Something real", "500.00", null);

        ResponseEntity<JsonNode> response = account.tryToChange(goal.id(),
                Map.of("deadline", account.today().minusMonths(1).toString()));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(account.goal(goal.id()).deadline()).isNull();
    }

    /** A goal with no name is one nobody could tell from the others they are saving for. */
    @Test
    void a_goal_with_no_name_is_refused() {
        ResponseEntity<JsonNode> response = account.tryToAdd("   ", "500.00", null);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("name");
        assertThat(account.goals()).isEmpty();
    }

    /**
     * A figure that is not a number at all, which is what a decimal comma arrives as. The page prints
     * amounts the Belgian way, so "2500,00" is the mistake somebody here is most likely to make, and
     * it has to come back as a refusal about the figure rather than as whatever the machinery says
     * when it cannot read a request.
     */
    @Test
    void a_target_that_is_not_a_number_is_refused_in_words_about_the_figure() {
        ResponseEntity<JsonNode> response = account.tryToAddWithTargetAsTyped("Comma", "2500,00");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("2500,00", "not an amount of money");
        assertThat(account.goals()).isEmpty();
    }

    /** And a deadline that is not a day, answered about the day rather than about the request. */
    @Test
    void a_deadline_that_is_not_a_day_is_refused_in_words_about_the_day() {
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/api/savings-accounts/{id}/goals",
                Map.of("name", "Someday", "target", "500.00", "deadline", "next Tuesday"),
                JsonNode.class, account.id());

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("next Tuesday", "not a day");
        assertThat(account.goals()).isEmpty();
    }

    /**
     * A savings account nobody has heard of, which this module cannot tell from a real one — it reads
     * no other module — so the endpoint vouches for it first and refuses in the words Accounts owns.
     * Not-found rather than the empty goal list of an account that simply has none.
     */
    @Test
    void goals_are_only_read_and_written_for_a_savings_account_that_exists() {
        long noSuchAccount = new SeededAccounts(http).anIdNoSavingsAccountHas();

        ResponseEntity<JsonNode> listed =
                http.getForEntity("/api/savings-accounts/{id}/goals", JsonNode.class, noSuchAccount);
        ResponseEntity<JsonNode> opened = http.postForEntity("/api/savings-accounts/{id}/goals",
                Map.of("name", "Nowhere", "target", "500.00"), JsonNode.class, noSuchAccount);

        assertRefused(listed, HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(listed)).contains(String.valueOf(noSuchAccount));
        assertRefused(opened, HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(opened)).contains(String.valueOf(noSuchAccount));
    }

    /** A goal identifier this account has never held. */
    @Test
    void a_goal_that_is_not_on_the_account_is_not_found() {
        GoalView real = account.add("Real", "500.00", null);

        ResponseEntity<JsonNode> response = account.tryToRead(real.id() + 100_000);

        assertRefused(response, HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(response)).contains(String.valueOf(account.id()));
    }

    /**
     * A goal that was given up on is a record of what somebody was saving for, and a record that can
     * be rewritten afterwards is not a record. A conflict rather than a bad request: what was typed
     * is fine, it is the state of the goal that will not allow it.
     */
    @Test
    void a_goal_that_was_given_up_on_cannot_be_changed_or_given_up_on_again() {
        GoalView goal = account.add("Given up", "500.00", null);
        account.abandon(goal.id());

        ResponseEntity<JsonNode> changed = account.tryToChange(goal.id(), Map.of("name", "Revived"));
        ResponseEntity<JsonNode> abandonedAgain = account.tryToAbandon(goal.id());

        assertRefused(changed, HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(changed)).contains("Given up");
        assertRefused(abandonedAgain, HttpStatus.CONFLICT);
        assertThat(account.goal(goal.id()).name())
                .describedAs("and what it was for is still readable, unchanged")
                .isEqualTo("Given up");
    }

    /** An abandoned goal holds no rank, so it cannot stand in an order either. */
    @Test
    void an_order_naming_a_goal_that_was_given_up_on_is_refused() {
        GoalView staying = account.add("Staying", "500.00", null);
        GoalView leaving = account.add("Leaving", "500.00", null);
        account.abandon(leaving.id());

        ResponseEntity<JsonNode> response =
                account.tryToReorder(List.of(leaving.id(), staying.id()));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(account.goal(staying.id()).rank()).isEqualTo(1);
    }

    private void assertRefused(ResponseEntity<JsonNode> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(reasonGivenBy(response))
                .describedAs("a refusal is only useful if the person who caused it can read why")
                .isNotBlank();
    }
}
