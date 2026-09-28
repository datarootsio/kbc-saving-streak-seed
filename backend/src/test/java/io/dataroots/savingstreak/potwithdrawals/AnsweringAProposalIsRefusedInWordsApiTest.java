package io.dataroots.savingstreak.potwithdrawals;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ProposalAnswerView;
import io.dataroots.savingstreak.support.SharedPotView;
import io.dataroots.savingstreak.support.WithdrawalProposalView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every way of answering a withdrawal proposal that this application will not take, and the sentence
 * it says so in.
 *
 * <p>Stories 54, 55, 27 and 69. An answer moves money, so the ways of getting one wrong are the ways
 * somebody's euros could be spent by the wrong person or counted twice: proposing is not a way of
 * voting for yourself, a member answers once, and somebody with nothing at stake has nothing to say
 * about it. Each refusal is a sentence a person could read out loud and a status that says what kind
 * of mistake it was — 403 when the request is understood and the caller is simply not allowed, 409
 * when what they asked is perfectly good and the state of the proposal will not have it, 404 when
 * the thing they named is not there.
 *
 * <p>Every method asserts the state of the proposal afterwards as well as the sentence, because a
 * refusal that let the money move anyway would satisfy a test that only read the answer.
 *
 * <p>Its own application, for the reason the other shared-pot tests give. A pot of its own per test,
 * so that what one of them refused cannot be what another one is asking about.
 */
class AnsweringAProposalIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-answering-a-proposal-is-refused-in-words"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    // ------------------------------------------------- proposing is not a way of voting for yourself

    /** Story 55. */
    @Test
    void the_member_who_proposed_it_may_not_approve_it() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "30.00");

        ResponseEntity<JsonNode> refused =
                app.tryToApprove(pot.id(), proposed.id(), app.customerIdOf(ANKE));

        assertThat(refused.getStatusCode())
                .as("a request this application understood, from a member it knows, that they are "
                        + "not allowed to make: " + refused.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detailOf(refused))
                .isEqualTo("A member does not answer their own proposal, and " + ANKE + " is who "
                        + "proposed this one. Take it back instead if you have thought better of it.");
        assertThat(app.withdrawalProposalsOf(pot.id()))
                .as("and it is still waiting for the member whose money is actually at stake")
                .extracting(WithdrawalProposalView::state)
                .containsExactly("PROPOSED");
    }

    /** And not reject it either: a change of mind is taking it back, which is a different record. */
    @Test
    void the_member_who_proposed_it_may_not_reject_it_either() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        app.deposit(pot.savingsAccountId(), BRAM, "20.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "10.00");

        ResponseEntity<JsonNode> refused =
                app.tryToReject(pot.id(), proposed.id(), app.customerIdOf(ANKE));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detailOf(refused))
                .as("and the sentence says what she should do instead, which is the thing that is "
                        + "hers to do")
                .contains("Take it back instead");
        assertThat(app.withdrawalProposalsOf(pot.id()))
                .extracting(WithdrawalProposalView::state)
                .containsExactly("PROPOSED");
    }

    // ---------------------------------------------------------------- a proposal is answered once

    /** Story 54, with the proposal still open, so that a count of approvals cannot be inflated. */
    @Test
    void a_member_answers_a_proposal_once_even_while_it_is_still_waiting() {
        SharedPotView pot = app.openAPot(ANKE, "The extension");
        String carla = app.aCustomerOfItsOwn("still to answer");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, carla, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        app.deposit(pot.savingsAccountId(), carla, "30.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "60.00");
        app.approve(pot.id(), proposed.id(), BRAM);

        ResponseEntity<JsonNode> refused =
                app.tryToApprove(pot.id(), proposed.id(), app.customerIdOf(BRAM));

        assertThat(refused.getStatusCode())
                .as("what he asked for is perfectly good and it is the answer he has already given "
                        + "that will not allow it, which is a conflict and not a form to fix: "
                        + refused.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused))
                .isEqualTo(BRAM + " has already approved that proposal, and a member answers a "
                        + "proposal once.");
        WithdrawalProposalView afterwards = app.withdrawalProposalsOf(pot.id()).get(0);
        assertThat(afterwards.answeredBy())
                .as("and he is on the record once, which is what stops two clicks looking like two "
                        + "people")
                .extracting(ProposalAnswerView::name)
                .containsExactly(BRAM);
        assertThat(afterwards.state())
                .as("and the proposal is still waiting for the member who really has not answered")
                .isEqualTo("PROPOSED");
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("120.00");
    }

    /** And having said no, a member may not then say yes. */
    @Test
    void a_member_who_rejected_it_may_not_approve_it_afterwards() {
        SharedPotView pot = app.openAPot(ANKE, "A boat");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "40.00");
        app.deposit(pot.savingsAccountId(), BRAM, "20.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "10.00");
        app.reject(pot.id(), proposed.id(), BRAM);

        ResponseEntity<JsonNode> refused =
                app.tryToApprove(pot.id(), proposed.id(), app.customerIdOf(BRAM));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused))
                .as("the proposal itself is closed, which is the more basic news of the two and is "
                        + "the sentence a proposal already has")
                .isEqualTo("That proposal is REJECTED, and a proposal is answered once.");
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("60.00");
    }

    /** A proposal that has already paid out cannot be approved a second time into paying twice. */
    @Test
    void a_proposal_that_has_already_gone_through_is_answered_once() {
        SharedPotView pot = app.openAPot(ANKE, "The bathroom");
        String carla = app.aCustomerOfItsOwn("approving after the fact");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, carla, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        app.deposit(pot.savingsAccountId(), carla, "30.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "20.00");
        app.approve(pot.id(), proposed.id(), BRAM);
        app.approve(pot.id(), proposed.id(), carla);

        ResponseEntity<JsonNode> refused =
                app.tryToReject(pot.id(), proposed.id(), app.customerIdOf(carla));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused))
                .isEqualTo("That proposal is APPROVED, and a proposal is answered once.");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and the money went once, which is the thing being protected here")
                .isEqualByComparingTo("100.00");
    }

    /** A proposal the proposer took back is not there to be answered either. */
    @Test
    void a_proposal_that_was_taken_back_is_not_answered_afterwards() {
        SharedPotView pot = app.openAPot(ANKE, "The garden");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "40.00");
        app.deposit(pot.savingsAccountId(), BRAM, "20.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "15.00");
        app.takeBackTheProposal(pot.id(), proposed.id(), ANKE);

        ResponseEntity<JsonNode> refused =
                app.tryToApprove(pot.id(), proposed.id(), app.customerIdOf(BRAM));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused))
                .isEqualTo("That proposal is WITHDRAWN, and a proposal is answered once.");
    }

    // ------------------------------------------- only the members whose money is at stake answer

    /** Story 27: a viewer has nothing in the pot by construction, so there is nothing to veto. */
    @Test
    void a_viewer_may_not_answer_it() {
        SharedPotView pot = app.openAPot(ANKE, "The attic");
        String watching = app.aCustomerOfItsOwn("only watching the pot");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, watching, "VIEWER");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        app.deposit(pot.savingsAccountId(), BRAM, "30.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "20.00");

        ResponseEntity<JsonNode> refused =
                app.tryToApprove(pot.id(), proposed.id(), app.customerIdOf(watching));

        assertThat(refused.getStatusCode())
                .as("understood, from somebody the pot knows, who is not allowed: "
                        + refused.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detailOf(refused))
                .isEqualTo("Only the members whose money is still in this pot answer a withdrawal "
                        + "from it, and none of " + watching + "'s is.");
        assertThat(app.withdrawalProposalsOf(pot.id()))
                .as("and it still waits for the member it was always waiting for")
                .extracting(WithdrawalProposalView::state)
                .containsExactly("PROPOSED");
    }

    /** Story 49 from the refusing side: nothing at stake, nothing to say. */
    @Test
    void a_member_who_has_never_paid_in_may_not_answer_it() {
        SharedPotView pot = app.openAPot(ANKE, "The loft");
        String emptyHanded = app.aCustomerOfItsOwn("nothing in the pot");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, emptyHanded, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        app.deposit(pot.savingsAccountId(), BRAM, "30.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "20.00");

        ResponseEntity<JsonNode> refused =
                app.tryToApprove(pot.id(), proposed.id(), app.customerIdOf(emptyHanded));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detailOf(refused))
                .as("a member in every other way, and a withdrawal held up by somebody with no "
                        + "stake in it is a gate standing in an empty field")
                .contains("Only the members whose money is still in this pot");
    }

    /** And a stranger, in the same words, because telling them otherwise tells them about the pot. */
    @Test
    void somebody_who_is_not_in_the_pot_at_all_may_not_answer_it() {
        SharedPotView pot = app.openAPot(ANKE, "The cellar");
        String stranger = app.aCustomerOfItsOwn("a stranger answering");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        app.deposit(pot.savingsAccountId(), BRAM, "30.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "20.00");

        ResponseEntity<JsonNode> refused =
                app.tryToApprove(pot.id(), proposed.id(), app.customerIdOf(stranger));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detailOf(refused)).contains("Only the members whose money is still in this pot");
    }

    // ------------------------------------------------------ things that are not there at all

    @Test
    void a_customer_nobody_has_heard_of_is_told_so_in_the_words_this_application_uses() {
        SharedPotView pot = app.openAPot(ANKE, "The shed");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "40.00");
        app.deposit(pot.savingsAccountId(), BRAM, "20.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "10.00");
        long nobody = app.anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = app.tryToApprove(pot.id(), proposed.id(), nobody);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused)).isEqualTo("There is no customer " + nobody + ".");
    }

    @Test
    void a_pot_nobody_has_heard_of_is_told_so_before_anything_else() {
        long noSuchPot = app.anIdNoCustomerHas() + 10_000;

        ResponseEntity<JsonNode> refused =
                app.tryToApprove(noSuchPot, 1L, app.customerIdOf(ANKE));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused)).isEqualTo("There is no shared pot " + noSuchPot + ".");
    }

    @Test
    void a_proposal_the_pot_never_had_is_not_there_to_answer() {
        SharedPotView pot = app.openAPot(ANKE, "The porch");
        SharedPotView somewhereElse = app.openAPot(ANKE, "The drive");
        AnotherMemberOfThePot.joins(app, somewhereElse.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(somewhereElse.savingsAccountId(), ANKE, "30.00");
        app.deposit(somewhereElse.savingsAccountId(), BRAM, "20.00");
        WithdrawalProposalView elsewhere =
                app.proposeAWithdrawal(somewhereElse.id(), ANKE, "10.00");

        ResponseEntity<JsonNode> refused =
                app.tryToApprove(pot.id(), elsewhere.id(), app.customerIdOf(BRAM));

        assertThat(refused.getStatusCode())
                .as("the pot is part of what identifies a proposal, so this one is not there: "
                        + refused.getBody())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused))
                .isEqualTo("There is no withdrawal proposal " + elsewhere.id() + " to shared pot "
                        + pot.id() + ".");
        assertThat(app.withdrawalProposalsOf(somewhereElse.id()))
                .as("and the proposal that really exists was not touched by the mistake")
                .extracting(WithdrawalProposalView::state)
                .containsExactly("PROPOSED");
    }

    @Test
    void answering_needs_somebody_to_be_doing_it() {
        SharedPotView pot = app.openAPot(ANKE, "The hallway");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "30.00");
        app.deposit(pot.savingsAccountId(), BRAM, "20.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "10.00");

        ResponseEntity<JsonNode> refused = app.tryToApproveAsNobody(pot.id(), proposed.id());

        assertThat(refused.getStatusCode())
                .as("a form that was never filled in, which is a malformed request rather than a "
                        + "rule refusing anybody")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .isEqualTo("Approving a withdrawal proposal needs the customer doing it.");
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
