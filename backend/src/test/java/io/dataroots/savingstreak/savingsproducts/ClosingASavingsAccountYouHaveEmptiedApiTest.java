package io.dataroots.savingstreak.savingsproducts;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingsAccountOnTheOverviewView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsproducts.ASaverChoosingAProduct.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A product somebody no longer uses stops cluttering their overview: an account they have emptied
 * can be closed, one that still holds money cannot, and closing one throws nothing away.
 *
 * <p><strong>Closed means ended, not deleted, and this file is where that is nailed down.</strong>
 * Nothing in this application is ever deleted — an offer is withdrawn, a goal is abandoned, a bill
 * is ended — and an account is no exception. A closed account keeps its number, its agreement, its
 * deposits, its withdrawals, its goals and its saving rules, and every one of those goes on reading.
 * What changes is one date, and everything in this test that is not about that date is about what
 * did <em>not</em> change.
 *
 * <p><strong>And it behaves as an account with nothing in it, because that is what it is.</strong>
 * Only an emptied account may be closed, so a goal, an allocation or a saving rule pointing at one
 * is pointing at an account holding nought — which is a state every one of those modules already
 * handles, has always handled, and handles here without having been told that closing exists.
 * There is no orphan to leave behind and nothing to crash, and the assertions below are what say so
 * rather than a note claiming it.
 *
 * <p><strong>A customer of this test's own.</strong> Closing an account changes how many a customer
 * holds, and the seeded pair are counted by a hundred other classes.
 */
class ClosingASavingsAccountYouHaveEmptiedApiTest extends ApiIntegrationTest {

    /**
     * The whole of the criterion: an emptied account is closed, and the agreement comes back saying
     * which day it ended on.
     *
     * <p>Everything else on the agreement is asserted to be unchanged in the same breath, because
     * that is the half of "closed" that is easy to get wrong: an account whose product or version
     * moved when it closed would make every deposit in its history unanswerable about what it was
     * paid under.
     */
    @Test
    void an_emptied_savings_account_can_be_closed() {
        ASaverChoosingAProduct saver = aSaver("somebody closing an account they emptied");
        long savingsAccountId = saver.open("NOTICE32").id();
        AnAgreementView before = saver.agreementOn(savingsAccountId);

        AnAgreementView closed = saver.close(savingsAccountId);

        assertThat(closed.closedOn()).isEqualTo(saver.theDayTheApplicationIsStandingOn());
        assertThat(closed.productCode()).isEqualTo(before.productCode());
        assertThat(closed.version()).isEqualTo(before.version());
        assertThat(closed.openedOn()).isEqualTo(before.openedOn());
        assertThat(saver.agreementOn(savingsAccountId)).isEqualTo(closed);
    }

    /**
     * An account with money still in it is refused, and the sentence says how much — because the
     * thing to do next is take that much out.
     *
     * <p>Refused rather than emptied on the customer's behalf: where those euros land decides a
     * week, a streak and a loyalty clock, and an application that chose for them would be taking
     * three decisions to save somebody one press.
     */
    @Test
    void an_account_still_holding_money_is_refused_in_a_sentence() {
        ASaverChoosingAProduct saver = aSaver("somebody closing an account with money in it");
        long savingsAccountId = saver.open("INSTANT").id();
        saver.payIn(savingsAccountId, "40.00");

        ResponseEntity<JsonNode> refused = saver.tryToClose(savingsAccountId);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .isEqualTo("Savings account " + savingsAccountId + " still holds EUR 40.00. Take "
                        + "the money out or move it somewhere else, and then the account can be "
                        + "closed.");
        assertThat(saver.agreementOn(savingsAccountId).closedOn())
                .as("a refused closing leaves the account open").isNull();
    }

    /** And once it has been emptied, the same press goes through. */
    @Test
    void the_same_account_closes_once_the_money_has_been_taken_out() {
        ASaverChoosingAProduct saver = aSaver("somebody emptying before closing");
        long savingsAccountId = saver.open("INSTANT").id();
        saver.payIn(savingsAccountId, "25.00");
        assertThat(saver.tryToClose(savingsAccountId).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        saver.takeOut(savingsAccountId, "25.00");

        assertThat(saver.close(savingsAccountId).closedOn()).isNotNull();
    }

    /**
     * Closing is one-way, so a second press is refused and says when it happened.
     *
     * <p>Unlike closing a product to new accounts, which is a flag with a way back and is not
     * refused a second time. There is no reopening an account, so a second press is somebody acting
     * on a screen that is out of date about something they cannot undo — and the honest answer names
     * the day.
     */
    @Test
    void closing_an_account_that_is_already_closed_is_refused_and_says_when() {
        ASaverChoosingAProduct saver = aSaver("somebody pressing close twice");
        long savingsAccountId = saver.open("CORE").id();
        AnAgreementView closed = saver.close(savingsAccountId);

        ResponseEntity<JsonNode> again = saver.tryToClose(savingsAccountId);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(again)).isEqualTo(
                "Savings account " + savingsAccountId + " was closed on " + closed.closedOn() + ".");
    }

    /**
     * A closed account keeps its deposits, its withdrawals and its timeline, all still readable.
     *
     * <p>This is the criterion that says "closed" is not "deleted". The money moved, the record of
     * it moving is what the customer reads back over, and a closing that took it away would make a
     * balance somebody remembers unexplainable for ever.
     */
    @Test
    void a_closed_account_keeps_its_deposits_withdrawals_and_history() {
        ASaverChoosingAProduct saver = aSaver("somebody reading a closed account back");
        long savingsAccountId = saver.open("INSTANT").id();
        saver.payIn(savingsAccountId, "30.00");
        saver.takeOut(savingsAccountId, "30.00");

        saver.close(savingsAccountId);

        assertThat(saver.read("/api/savings-accounts/{id}/deposits", savingsAccountId))
                .satisfies(deposits -> {
                    assertThat(deposits.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(deposits.getBody()).isNotEmpty();
                });
        assertThat(saver.read("/api/savings-accounts/{id}/withdrawals", savingsAccountId))
                .satisfies(withdrawals -> {
                    assertThat(withdrawals.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(withdrawals.getBody()).isNotEmpty();
                });
        assertThat(saver.read("/api/savings-accounts/{id}/timeline", savingsAccountId)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saver.account(savingsAccountId).moneyBalance()).isEqualByComparingTo("0.00");
    }

    /**
     * The goals and the allocations on a closed account read exactly as they read for an account
     * with nothing in it — which, since only an emptied account may be closed, is what it is.
     *
     * <p>No orphan and no crash, and nothing in Goals had to learn that closing exists: the goal is
     * still there with the name and the target somebody chose, and the account it points at holds
     * nought, which Goals has always known how to answer about.
     */
    @Test
    void goals_and_allocations_on_a_closed_account_read_as_they_do_for_an_empty_one() {
        ASaverChoosingAProduct saver = aSaver("somebody closing an account with a goal on it");
        long savingsAccountId = saver.open("CORE").id();
        ResponseEntity<JsonNode> goal = http.postForEntity("/api/savings-accounts/{id}/goals",
                Map.of("name", "A rainy day", "target", "500.00"), JsonNode.class, savingsAccountId);
        assertThat(goal.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        saver.close(savingsAccountId);

        ResponseEntity<JsonNode> goalsAfter =
                saver.read("/api/savings-accounts/{id}/goals", savingsAccountId);
        assertThat(goalsAfter.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(goalsAfter.getBody()).hasSize(1);
        assertThat(goalsAfter.getBody().get(0).path("name").asText()).isEqualTo("A rainy day");
        assertThat(goalsAfter.getBody().get(0).path("allocated").decimalValue())
                .isEqualByComparingTo("0.00");
        assertThat(saver.read("/api/savings-accounts/{id}/goals/allocations", savingsAccountId)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * A saving rule pointing at a closed account still reads, and so does the preview of what it
     * would move.
     *
     * <p>The same claim as the goals above, for the module that is a standing instruction rather
     * than a record. Nothing in Automation knows that an account can be closed; the rule goes on
     * naming the account it was left on, and reading it is the assertion that there is no orphan
     * here.
     */
    @Test
    void a_saving_rule_pointing_at_a_closed_account_still_reads() {
        ASaverChoosingAProduct saver = aSaver("somebody closing an account a rule points at");
        long savingsAccountId = saver.open("INSTANT").id();
        ResponseEntity<JsonNode> rule = http.postForEntity(
                "/api/savings-accounts/{id}/saving-rules",
                Map.of("name", "Every Monday", "fromCurrentAccountId", saver.currentAccountId(),
                        "trigger", "WEEKLY", "dayOfWeek", "MONDAY",
                        "howMuchMoves", "A_FIXED_AMOUNT", "amount", "10.00"),
                JsonNode.class, savingsAccountId);
        assertThat(rule.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        saver.close(savingsAccountId);

        ResponseEntity<JsonNode> rulesAfter =
                saver.read("/api/savings-accounts/{id}/saving-rules", savingsAccountId);
        assertThat(rulesAfter.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rulesAfter.getBody()).hasSize(1);
        assertThat(saver.read("/api/savings-accounts/{id}/saving-rules/preview", savingsAccountId)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * A closed account is still on the overview, marked with the day it closed.
     *
     * <p>Still there, because the money history hanging off the same screen reads every euro that
     * moved through every account this customer holds — an account that vanished from the list
     * would leave those euros attributed to nothing. Marked, because the list is how somebody tells
     * their accounts apart, and an account that cannot be saved into has to look different from one
     * that can. Where to draw it is the page's decision; what the API owes it is the date.
     */
    @Test
    void a_closed_account_is_still_on_the_overview_and_says_the_day_it_closed() {
        ASaverChoosingAProduct saver = aSaver("somebody looking at a closed account");
        long savingsAccountId = saver.open("NOTICE32").id();

        AnAgreementView closed = saver.close(savingsAccountId);

        assertThat(saver.savingsAccounts())
                .filteredOn(account -> account.id().equals(savingsAccountId))
                .singleElement()
                .satisfies(account -> {
                    assertThat(account.closedOn()).isEqualTo(closed.closedOn());
                    assertThat(account.productCode()).isEqualTo("NOTICE32");
                    assertThat(account.moneyBalance()).isEqualByComparingTo("0.00");
                });
        assertThat(saver.savingsAccounts())
                .filteredOn(account -> account.id().equals(saver.theAccountTheyWereOpenedWith()))
                .singleElement()
                .satisfies(account -> assertThat(account.closedOn())
                        .as("closing one account closes no other").isNull());
    }

    /**
     * An account nobody has heard of cannot be closed, in the words Accounts owns for an absent
     * account — settled before the catalogue is asked anything at all.
     */
    @Test
    void an_account_nobody_has_heard_of_cannot_be_closed() {
        ASaverChoosingAProduct saver = aSaver("somebody closing a number");
        long noSuchAccount = saver.savingsAccounts().stream()
                .mapToLong(SavingsAccountOnTheOverviewView::id).max().orElse(0L) + 1_000_000L;

        ResponseEntity<JsonNode> refused = saver.tryToClose(noSuchAccount);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(refused))
                .isEqualTo("There is no savings account " + noSuchAccount + ".");
    }

    private ASaverChoosingAProduct aSaver(String whoTheyAre) {
        return new ASaverChoosingAProduct(http, whoTheyAre);
    }
}
