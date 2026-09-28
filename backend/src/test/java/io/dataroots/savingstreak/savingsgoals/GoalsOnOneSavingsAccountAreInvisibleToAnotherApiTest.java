package io.dataroots.savingstreak.savingsgoals;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * One savings account's goals are its own, including against another account held by the same
 * customer.
 *
 * <p>Anke holds two savings accounts, and that is why she holds two: somebody saving towards two
 * different things is the only way to ask whether the two stay apart. The same-customer case is the
 * one worth testing rather than the two-customer one, because it is the one a scoping mistake
 * survives — a read scoped by customer, or by nothing at all, would pass every test about two
 * strangers and fail here.
 *
 * <p>Asserted by membership rather than by counting. The run shares one database and nothing clears
 * goals, so a count is the one thing about either list that cannot be true for two tests at once;
 * what is being asserted is that this goal is in that list and not in this one.
 */
class GoalsOnOneSavingsAccountAreInvisibleToAnotherApiTest extends ApiIntegrationTest {

    private long oneOfHers;
    private long theOtherOfHers;

    @BeforeEach
    void theTwoSavingsAccountsOneCustomerHolds() {
        SeededAccounts seeded = new SeededAccounts(http);
        oneOfHers = seeded.savingsAccountOf(ANKE);
        theOtherOfHers = seeded.otherSavingsAccountOf(ANKE);
        assertThat(oneOfHers).isNotEqualTo(theOtherOfHers);
    }

    @Test
    void a_goal_on_one_account_is_not_in_the_other_accounts_list() {
        GoalView here = addTo(oneOfHers, "Kitchen, on the first account");
        GoalView there = addTo(theOtherOfHers, "Kitchen, on the second account");

        assertThat(idsIn(oneOfHers)).contains(here.id()).doesNotContain(there.id());
        assertThat(idsIn(theOtherOfHers)).contains(there.id()).doesNotContain(here.id());
    }

    /**
     * And it cannot be reached by naming it under the other account. Every read in the module is
     * scoped by both halves of the key for exactly this: a goal named by a customer looking at a
     * different account is a goal that is not there as far as that request is concerned, and the
     * answer is the same one a goal that never existed gets.
     */
    @Test
    void a_goal_cannot_be_read_changed_or_given_up_on_through_the_other_account() {
        GoalView here = addTo(oneOfHers, "Not yours to touch");

        ResponseEntity<JsonNode> read = http.getForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}", JsonNode.class, theOtherOfHers, here.id());
        ResponseEntity<JsonNode> changed = http.exchange(
                "/api/savings-accounts/{id}/goals/{goalId}", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", "Renamed from elsewhere")), JsonNode.class,
                theOtherOfHers, here.id());
        ResponseEntity<JsonNode> abandoned = http.postForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}/abandon", null, JsonNode.class,
                theOtherOfHers, here.id());

        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(abandoned.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        GoalView stillThere = http.getForObject("/api/savings-accounts/{id}/goals/{goalId}",
                GoalView.class, oneOfHers, here.id());
        assertThat(stillThere.name())
                .describedAs("and nothing the other account asked for touched it")
                .isEqualTo("Not yours to touch");
        assertThat(stillThere.state()).isEqualTo("LIVE");
    }

    /**
     * Each account counts its own order from 1. Ranks are per account, so the first goal on the
     * second account is the most important thing that account is saving towards — not the fourth
     * thing its holder is.
     */
    @Test
    void each_account_holds_its_own_order_starting_at_one() {
        long fresh = new AnAccountWithGoals(http, "invisible").id();
        addTo(oneOfHers, "Something on the busy account");

        GoalView firstOnTheFreshOne = addTo(fresh, "The only thing here");

        assertThat(firstOnTheFreshOne.rank()).isEqualTo(1);
        assertThat(goalsOn(fresh)).singleElement()
                .satisfies(goal -> assertThat(goal.id()).isEqualTo(firstOnTheFreshOne.id()));
    }

    private GoalView addTo(long savingsAccountId, String name) {
        ResponseEntity<GoalView> opened = http.postForEntity("/api/savings-accounts/{id}/goals",
                Map.of("name", name, "target", "4000.00"), GoalView.class, savingsAccountId);
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return opened.getBody();
    }

    private List<Long> idsIn(long savingsAccountId) {
        return goalsOn(savingsAccountId).stream().map(GoalView::id).toList();
    }

    private List<GoalView> goalsOn(long savingsAccountId) {
        return List.of(http.getForObject("/api/savings-accounts/{id}/goals", GoalView[].class,
                savingsAccountId));
    }
}
