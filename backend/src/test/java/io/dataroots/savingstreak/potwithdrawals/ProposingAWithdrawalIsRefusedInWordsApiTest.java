package io.dataroots.savingstreak.potwithdrawals;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every way a withdrawal proposal can be turned down, answered as a sentence somebody could read out
 * loud — and each of them in its own words, because a page that said "that is not allowed" to six
 * different mistakes would send five people looking in the wrong place.
 *
 * <p>Story 69. The two about who is asking answer 403, which is the status roles on a pot already
 * use: the request is understood, the caller is known, and they are not allowed. The rest are 400s —
 * a figure to retype, an account to correct, an amount the pot does not hold — and the sentence says
 * which.
 *
 * <p>Nothing is left behind by any of them. A refused proposal is refused before a row is written,
 * so the pot's list is empty afterwards and its money is exactly where it was.
 *
 * <p>Its own application, for the reason the other shared-pot tests give.
 */
class ProposingAWithdrawalIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-proposing-a-withdrawal-is-refused-in-words"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    // ------------------------------------------------------------------ who is asking

    /** Story 32's sibling: a pot is not a public collection box, and it is not a public till either. */
    @Test
    void somebody_who_is_not_in_the_pot_is_refused() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");

        ResponseEntity<JsonNode> refused = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("10.00", app.currentAccountOf(BRAM), app.customerIdOf(BRAM)));

        assertThat(refused.getStatusCode())
                .as("understood, from somebody this application knows, and not allowed: "
                        + refused.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detailOf(refused))
                .isEqualTo("Only an owner or a contributor may propose taking money out of "
                        + "this pot.");
        assertThat(app.withdrawalProposalsOf(pot.id()))
                .as("and nothing was written down")
                .isEmpty();
    }

    /** Story 27: "viewer" means something, and this is one of the three things it means. */
    @Test
    void a_viewer_is_refused_in_the_same_words() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "VIEWER");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");

        ResponseEntity<JsonNode> refused = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("10.00", app.currentAccountOf(BRAM), app.customerIdOf(BRAM)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detailOf(refused))
                .as("the same sentence a stranger gets, on purpose: both are being told what "
                        + "would have been needed, and telling a stranger they are not a member "
                        + "would be telling them there is a pot here to be a member of")
                .isEqualTo("Only an owner or a contributor may propose taking money out of "
                        + "this pot.");
    }

    // --------------------------------------------------------------- where the money would go

    @Test
    void a_current_account_that_is_not_the_proposers_own_is_refused() {
        SharedPotView pot = app.openAPot(ANKE, "A boat");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");

        ResponseEntity<JsonNode> refused = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("10.00", app.currentAccountOf(BRAM), app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode())
                .as("real accounts and a request they cannot be asked to honour: "
                        + refused.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("whose account it is is never named, because that would be telling her "
                        + "something about a customer who is not her")
                .isEqualTo("A withdrawal from a shared pot comes back to one of the proposer's own "
                        + "current accounts, and current account " + app.currentAccountOf(BRAM)
                        + " is not one of " + ANKE + "'s.");
    }

    @Test
    void a_current_account_nobody_holds_is_refused_in_the_same_sentence() {
        SharedPotView pot = app.openAPot(ANKE, "The bathroom");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");
        long noSuchAccount = app.currentAccountOf(ANKE) + 10_000;

        ResponseEntity<JsonNode> refused = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("10.00", noSuchAccount, app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("an account nobody holds and an account somebody else holds are the same "
                        + "news to the proposer: name one of your own")
                .isEqualTo("A withdrawal from a shared pot comes back to one of the proposer's own "
                        + "current accounts, and current account " + noSuchAccount + " is not one "
                        + "of " + ANKE + "'s.");
    }

    // ------------------------------------------------------------------------ the figure

    /** The mistake a Belgian page makes most, answered with a sentence about the figure. */
    @Test
    void an_amount_that_cannot_be_read_as_money_is_refused_in_the_words_a_deposit_uses() {
        SharedPotView pot = app.openAPot(ANKE, "The loft");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");

        ResponseEntity<JsonNode> refused = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("2500,00", app.currentAccountOf(ANKE), app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("the comma is quoted back, because a person who typed one has to see it to "
                        + "see the mistake")
                .isEqualTo("\"2500,00\" is not an amount of money. Write it in digits with a full "
                        + "stop, like 25.00.");
    }

    @Test
    void an_amount_that_is_not_more_than_nothing_is_refused() {
        SharedPotView pot = app.openAPot(ANKE, "The garden");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");

        ResponseEntity<JsonNode> refused = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("0.00", app.currentAccountOf(ANKE), app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("the words a withdrawal from a personal savings account is refused in, "
                        + "because it is the same objection about the same kind of figure")
                .isEqualTo("A withdrawal has to be an amount of more than zero, and 0.00 is not.");
    }

    /** Story 56: nobody should spend days approving something impossible. */
    @Test
    void more_than_the_pot_holds_is_refused_when_it_is_proposed_and_says_what_it_holds() {
        SharedPotView pot = app.openAPot(ANKE, "The extension");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "30.00");
        app.deposit(pot.savingsAccountId(), BRAM, "20.00");

        ResponseEntity<JsonNode> refused = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("50.01", app.currentAccountOf(ANKE), app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode())
                .as("the pot is in no unexpected state, there is simply less in it than she "
                        + "asked for: " + refused.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("and the sentence says how much, so there is nothing left to work out")
                .isEqualTo("This pot holds EUR 50.00, and a withdrawal of EUR 50.01 is more "
                        + "than that.");
        assertThat(app.withdrawalProposalsOf(pot.id())).isEmpty();
    }

    @Test
    void the_whole_of_what_the_pot_holds_is_not_more_than_it_holds() {
        SharedPotView pot = app.openAPot(ANKE, "The whole lot");
        app.deposit(pot.savingsAccountId(), ANKE, "45.00");

        assertThat(app.proposeAWithdrawal(pot.id(), ANKE, "45.00").amount())
                .as("the gate is \"more than the pot holds\" and not \"nearly all of it\": "
                        + "emptying a pot is a thing members do")
                .isEqualByComparingTo("45.00");
    }

    // ------------------------------------------------------ things that are not there at all

    @Test
    void a_pot_nobody_has_heard_of_is_told_so_before_anything_else() {
        long noSuchPot = app.anIdNoCustomerHas() + 10_000;

        ResponseEntity<JsonNode> refused = app.tryToPropose(noSuchPot,
                app.aWithdrawalOf("10.00", app.currentAccountOf(ANKE), app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused)).isEqualTo("There is no shared pot " + noSuchPot + ".");
    }

    @Test
    void a_customer_nobody_has_heard_of_is_told_so_in_the_words_this_application_uses() {
        SharedPotView pot = app.openAPot(ANKE, "The attic");
        long nobody = app.anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("10.00", app.currentAccountOf(ANKE), nobody));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused))
                .as("who is asking is settled before what they asked for, because a page signed "
                        + "in as nobody will go on getting every other request wrong as well")
                .isEqualTo("There is no customer " + nobody + ".");
    }

    // --------------------------------------------------------- forms that were never filled in

    @Test
    void a_proposal_with_nobody_proposing_it_is_a_malformed_request() {
        SharedPotView pot = app.openAPot(ANKE, "The shed");

        ResponseEntity<JsonNode> refused = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("10.00", app.currentAccountOf(ANKE), null));

        assertThat(refused.getStatusCode())
                .as("a field that was never filled in, which is a malformed request rather than "
                        + "a rule refusing anybody")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .isEqualTo("A withdrawal proposal needs the customer making it.");
    }

    @Test
    void a_proposal_with_no_amount_or_no_account_is_a_malformed_request_too() {
        SharedPotView pot = app.openAPot(ANKE, "The driveway");

        ResponseEntity<JsonNode> noAmount = app.tryToPropose(pot.id(),
                app.aWithdrawalOf(null, app.currentAccountOf(ANKE), app.customerIdOf(ANKE)));
        ResponseEntity<JsonNode> noAccount = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("10.00", null, app.customerIdOf(ANKE)));

        assertThat(noAmount.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(noAmount))
                .as("there is no movement to be asking about without both of them")
                .isEqualTo("A withdrawal proposal needs an amount and the current account it "
                        + "would come back to.");
        assertThat(noAccount.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(noAccount))
                .isEqualTo("A withdrawal proposal needs an amount and the current account it "
                        + "would come back to.");
    }

    /** The sentence a refusal carries, which is the whole of what the person gets to act on. */
    private static String detailOf(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody()).isNotNull();
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered "
                        + refused.getBody())
                .isTrue();
        return refused.getBody().get("detail").asText();
    }
}
