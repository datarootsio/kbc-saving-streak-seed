package io.dataroots.savingstreak.automation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingRuleView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rules are per savings account: leaving one standing on one account leaves another held by the same
 * customer untouched.
 *
 * <p>Two accounts held by <em>one</em> customer, deliberately. Two customers would prove almost
 * nothing — anything keyed by anything at all would pass — and the case that actually goes wrong is
 * the one where a read forgets to scope by the account because the customer is the same either way.
 * Anke holds two savings accounts for exactly this kind of question, and both ends of a rule have to
 * be hers, so both rules draw from her one current account.
 *
 * <p>Ending a rule is asserted across the two as well, because a delete scoped by rule identifier
 * alone would end somebody's rule on an account they did not name.
 */
class RulesOnOneSavingsAccountAreInvisibleToAnotherApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;
    private long oneAccount;
    private long theOtherAccount;
    private long theirCurrentAccount;

    @BeforeEach
    void twoAccountsHeldByOneCustomer() {
        seeded = new SeededAccounts(http);
        oneAccount = seeded.savingsAccountOf(SeededAccounts.ANKE);
        theOtherAccount = seeded.otherSavingsAccountOf(SeededAccounts.ANKE);
        theirCurrentAccount = seeded.currentAccountOf(SeededAccounts.ANKE);
    }

    @Test
    void a_rule_left_standing_on_one_account_is_not_on_the_other() {
        SavingRuleView here = leaveStandingOn(oneAccount, "Holiday on this account");

        assertThat(namesOf(rulesOn(oneAccount))).contains("Holiday on this account");
        assertThat(namesOf(rulesOn(theOtherAccount))).doesNotContain("Holiday on this account");
        assertThat(rulesOn(theOtherAccount))
                .extracting(SavingRuleView::id)
                .doesNotContain(here.id());
    }

    @Test
    void ending_a_rule_on_one_account_leaves_the_other_accounts_rule_standing() {
        SavingRuleView here = leaveStandingOn(oneAccount, "Ending this one");
        SavingRuleView there = leaveStandingOn(theOtherAccount, "Keeping this one");

        ResponseEntity<SavingRuleView> ended = http.exchange(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}", HttpMethod.DELETE, null,
                SavingRuleView.class, oneAccount, here.id());

        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(namesOf(rulesOn(theOtherAccount))).contains("Keeping this one");
        assertThat(rulesOn(theOtherAccount))
                .filteredOn(rule -> rule.id().equals(there.id()))
                .singleElement()
                .extracting(SavingRuleView::state).isEqualTo("LIVE");
    }

    /**
     * A rule named under the wrong account is not there, whoever holds either — which is the same
     * answer a goal gives, and for the same reason: saying "it exists but not here" would be saying
     * something about an account the request did not ask about.
     */
    @Test
    void a_rule_on_one_account_cannot_be_changed_through_the_other() {
        SavingRuleView here = leaveStandingOn(oneAccount, "Only on this account");

        ResponseEntity<String> refused = http.exchange(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", "Reached across")),
                String.class, theOtherAccount, here.id());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rulesOn(oneAccount))
                .filteredOn(rule -> rule.id().equals(here.id()))
                .singleElement()
                .extracting(SavingRuleView::name).isEqualTo("Only on this account");
    }

    private SavingRuleView leaveStandingOn(long savingsAccountId, String name) {
        ResponseEntity<SavingRuleView> left = http.postForEntity(
                "/api/savings-accounts/{id}/saving-rules",
                Map.of("name", name, "fromCurrentAccountId", theirCurrentAccount,
                        "trigger", "WEEKLY", "dayOfWeek", "MONDAY",
                        "howMuchMoves", "A_FIXED_AMOUNT", "amount", "10.00"),
                SavingRuleView.class, savingsAccountId);
        assertThat(left.getStatusCode())
                .describedAs("leaving \"" + name + "\" standing on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.CREATED);
        return left.getBody();
    }

    private List<SavingRuleView> rulesOn(long savingsAccountId) {
        ResponseEntity<SavingRuleView[]> listed = http.getForEntity(
                "/api/savings-accounts/{id}/saving-rules", SavingRuleView[].class, savingsAccountId);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        return Arrays.asList(listed.getBody());
    }

    private static List<String> namesOf(List<SavingRuleView> rules) {
        return rules.stream().map(SavingRuleView::name).toList();
    }
}
