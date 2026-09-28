package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsproducts.ASaverChoosingAProduct.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A savings account whose agreement has ended takes no more money, and says so in a sentence naming
 * the day it ended.
 *
 * <p><strong>The door ticket 03 could not close.</strong> Closing an account was built while three
 * other tickets were editing {@code deposits/}, so an account could be closed and then paid into,
 * over HTTP and from its own screen. The rule belongs to the backend rather than to the page — a
 * page that hid the form while the application still accepted the request would be inventing a rule
 * nothing enforces — so the refusal is what is asserted here, and the page follows it.
 *
 * <p><strong>Money out is still allowed, and that is the second subject of this file.</strong> An
 * account can only be closed once it is empty, so nothing is trapped by closing one today; but a
 * later release that pays a cent into one — interest for a period that ran before it closed, a
 * shared pot settling, a term maturing — must not find the way out barred by a rule about the way
 * in. The assertion below is that a withdrawal from a closed account is refused for its balance and
 * for nothing else, which is the same refusal an open account with nothing in it gets.
 *
 * <p><strong>A customer of this test's own</strong>, because closing accounts changes how many
 * somebody holds and the seeded pair are counted by a hundred other classes.
 */
class ADepositIntoAClosedAccountIsRefusedInWordsApiTest extends ApiIntegrationTest {

    /** Enough to be refused for a reason that is plainly not the balance behind it. */
    private static final String A_DEPOSIT_THE_CURRENT_ACCOUNT_COULD_EASILY_COVER = "20.00";

    /**
     * The criterion itself: the deposit is refused, the status is a conflict, and the sentence names
     * the day the account closed.
     *
     * <p>The day is asserted against the one the closing itself answered with rather than against a
     * date this test writes down, so that the refusal and the agreement cannot drift apart and so
     * that the test says nothing about which day the suite is run on.
     */
    @Test
    void a_deposit_into_a_closed_savings_account_is_refused_in_a_sentence_naming_the_day() {
        ASaverChoosingAProduct saver = aSaver("somebody paying into an account they closed");
        long savingsAccountId = saver.open("INSTANT").id();
        AnAgreementView closed = saver.close(savingsAccountId);

        ResponseEntity<JsonNode> refused =
                saver.tryToPayIn(savingsAccountId, A_DEPOSIT_THE_CURRENT_ACCOUNT_COULD_EASILY_COVER);

        assertThat(refused.getStatusCode())
                .as("nothing was typed wrong: the account is real, the amount is an amount, and it "
                        + "is the state of the account that will not have the money")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).isEqualTo("Savings account " + savingsAccountId
                + " was closed on " + closed.closedOn() + ", so no more money can be paid into it. "
                + "Its history is still there to read.");
    }

    /**
     * And not a cent left the current account, which is the half of a refusal that is easy to get
     * wrong: a deposit that refused after taking the money would be the worst balance in this
     * application to be asked to explain.
     */
    @Test
    void a_refused_deposit_leaves_the_current_account_and_the_history_exactly_as_they_were() {
        ASaverChoosingAProduct saver = aSaver("somebody whose refused deposit changed nothing");
        long savingsAccountId = saver.open("CORE").id();
        saver.close(savingsAccountId);
        BigDecimal held = saver.whatTheirCurrentAccountHolds();

        saver.tryToPayIn(savingsAccountId, A_DEPOSIT_THE_CURRENT_ACCOUNT_COULD_EASILY_COVER);

        assertThat(saver.whatTheirCurrentAccountHolds())
                .as("the money is where it was, because the refusal arrived before it moved")
                .isEqualByComparingTo(held);
        assertThat(saver.account(savingsAccountId).moneyBalance())
                .as("and the closed account still holds nothing at all")
                .isEqualByComparingTo("0.00");
        assertThat(saver.read("/api/savings-accounts/{id}/deposits", savingsAccountId).getBody())
                .as("no deposit was written for a deposit that did not happen")
                .isEmpty();
    }

    /**
     * A withdrawal from a closed account is still allowed, and the proof is which refusal an empty
     * one gets: the balance, in the words any empty account is refused in, rather than anything
     * about the agreement having ended.
     *
     * <p>There is no money in a closed account to take out — being empty is what let it be closed —
     * so this is what "still allowed" can be asserted as today. It is worth asserting all the same:
     * the cheap way to close the deposit door is a rule about the account rather than about the
     * direction money is moving, and that rule would show up right here, as a conflict naming the
     * day, on a request that has to go on being about the balance.
     */
    @Test
    void a_withdrawal_from_a_closed_account_is_refused_for_its_balance_and_not_for_its_agreement() {
        ASaverChoosingAProduct saver = aSaver("somebody taking money out of a closed account");
        long savingsAccountId = saver.open("INSTANT").id();
        saver.close(savingsAccountId);

        ResponseEntity<JsonNode> refused = saver.tryToTakeOut(savingsAccountId, "10.00");

        assertThat(refused.getStatusCode())
                .as("a bad request about a figure, which is what every withdrawal from an account "
                        + "with nothing in it gets")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .as("about the money and never about the agreement")
                .doesNotContain("closed on")
                .contains("EUR 0.00");
    }

    /**
     * Closing one account closes the door on that one and on nothing else, including the account the
     * same customer was opened with.
     *
     * <p>The control this file needs: a refusal that was really about the customer, the product or
     * the state of the application rather than about this one account would pass every assertion
     * above and fail this one.
     */
    @Test
    void the_other_accounts_the_same_customer_holds_go_on_taking_money() {
        ASaverChoosingAProduct saver = aSaver("somebody with one account closed and one open");
        long closedOne = saver.open("INSTANT").id();
        saver.close(closedOne);

        saver.payIn(saver.theAccountTheyWereOpenedWith(),
                A_DEPOSIT_THE_CURRENT_ACCOUNT_COULD_EASILY_COVER);

        assertThat(saver.account(saver.theAccountTheyWereOpenedWith()).moneyBalance())
                .isEqualByComparingTo(A_DEPOSIT_THE_CURRENT_ACCOUNT_COULD_EASILY_COVER);
        assertThat(saver.tryToPayIn(closedOne, A_DEPOSIT_THE_CURRENT_ACCOUNT_COULD_EASILY_COVER)
                .getStatusCode())
                .as("and the closed one is still closed after the open one has taken money")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private ASaverChoosingAProduct aSaver(String whoTheyAre) {
        return new ASaverChoosingAProduct(http, whoTheyAre);
    }
}
