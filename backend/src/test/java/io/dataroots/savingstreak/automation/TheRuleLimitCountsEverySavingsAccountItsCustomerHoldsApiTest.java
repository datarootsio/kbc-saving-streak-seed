package io.dataroots.savingstreak.automation;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.automation.AnAccountWithRules.reasonGivenBy;
import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The limit on how many rules may stand at once is the customer's, not the account's.
 *
 * <p>{@link AnEleventhRuleForOneCustomerIsRefusedApiTest} fills one savings account, which is the
 * ordinary way a customer meets the limit — and a count that had quietly become per-account would
 * satisfy every assertion in it. This one spreads the ten rules across both savings accounts the
 * same customer holds, so that neither account is near the limit on its own and only the count over
 * the customer can refuse the eleventh.
 *
 * <p>It is asserted from both ends: the eleventh is refused on the account that holds five, and the
 * refusal quotes the limit. An implementation counting per account would have five slots free there
 * and would answer 201.
 *
 * <p>Its own application, rather than the shared one, because it needs a customer who holds
 * <em>two</em> savings accounts: only the seeded Anke does, and ten rules left standing on her
 * account in the shared application would refuse the eleventh rule of whichever class ran after
 * this one. A fresh database gives her back with no rules at all.
 */
class TheRuleLimitCountsEverySavingsAccountItsCustomerHoldsApiTest extends ApiIntegrationTest {

    /** The most this application keeps for one customer, restated here so the test says what it tests. */
    private static final int THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING = 10;

    private static final int HALF_OF_THEM = THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING / 2;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseCustomerHoldsTwoSavingsAccounts() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-rule-limit-is-per-customer"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void five_rules_on_each_of_two_savings_accounts_refuse_the_eleventh_on_either() {
        long oneAccount = app.savingsAccountOf(ANKE);
        long theOther = app.otherSavingsAccountOf(ANKE);
        long currentAccount = app.currentAccountOf(ANKE);
        for (int rule = 1; rule <= HALF_OF_THEM; rule++) {
            app.leaveARuleStanding(oneAccount, RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                    currentAccount, "On the first account " + rule, "MONDAY", "5.00"));
            app.leaveARuleStanding(theOther, RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                    currentAccount, "On the second account " + rule, "MONDAY", "5.00"));
        }

        ResponseEntity<JsonNode> refused = app.tryToLeaveARuleStanding(oneAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                        currentAccount, "One too many", "MONDAY", "5.00"));

        assertThat(refused.getStatusCode())
                .as("five rules stand on this account and five on the other one the same customer "
                        + "holds; a limit counted per account would have had five slots free here")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains(String.valueOf(THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING));
        assertThat(app.rulesOn(oneAccount))
                .as("and nothing was written: this account still holds the five it held")
                .hasSize(HALF_OF_THEM);
        assertThat(app.rulesOn(theOther)).hasSize(HALF_OF_THEM);
    }
}
