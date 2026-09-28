package io.dataroots.savingstreak.potwithdrawals;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * A change of mind needs nobody else's involvement: the member who proposed a withdrawal takes it
 * back, and nobody else can.
 *
 * <p>Story 53. Taking a proposal back is deliberately not a rejection — a rejection is somebody
 * protecting their own euros, and the pot's record has to be able to tell "I thought better of it"
 * from "you may not have my money". So the proposal stays in the record as {@code WITHDRAWN} rather
 * than disappearing, and it is nobody else's to withdraw: a member who could cancel somebody else's
 * proposal would be rejecting it without the rejection ever appearing anywhere.
 *
 * <p>Every pot here has a second member with money in it, and that is load-bearing rather than
 * scene-setting: a proposal nobody has to approve goes through the moment it is made, and there is
 * nothing left to take back. Taking one back is a thing you do to a proposal that is waiting.
 *
 * <p>Its own application, for the reason the other shared-pot tests give.
 */
class AProposerTakesTheirOwnProposalBackApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-proposer-takes-it-back"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_member_who_proposed_it_takes_it_back_and_the_pot_keeps_the_record() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "30.00");
        BigDecimal inThePot = app.potWith(pot.id()).moneyBalance();

        WithdrawalProposalView takenBack = app.takeBackTheProposal(pot.id(), proposed.id(), ANKE);

        assertThat(takenBack.state())
                .as("withdrawn, and not gone: " + takenBack)
                .isEqualTo("WITHDRAWN");
        assertThat(takenBack.closedAt())
                .as("and it says when it stopped waiting, which is what the absence on a waiting "
                        + "one means")
                .isNotNull();
        assertThat(takenBack.whoseAssentItNeeds())
                .as("nobody is being asked anything any more, and the state beside it says why")
                .isEmpty();
        assertThat(app.withdrawalProposalsOf(pot.id()))
                .as("the pot's record still has it, because somebody asking and thinking better of "
                        + "it is a thing that happened")
                .extracting(WithdrawalProposalView::id, WithdrawalProposalView::state)
                .containsExactly(tuple(proposed.id(), "WITHDRAWN"));
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and the money is where it was throughout: nothing moved when it was proposed "
                        + "and nothing moved when it was taken back")
                .isEqualByComparingTo(inThePot);
    }

    @Test
    void nobody_else_may_take_it_back() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        app.deposit(pot.savingsAccountId(), BRAM, "20.00");
        WithdrawalProposalView hers = app.proposeAWithdrawal(pot.id(), ANKE, "10.00");

        ResponseEntity<JsonNode> refused =
                app.tryToTakeBackTheProposal(pot.id(), hers.id(), app.customerIdOf(BRAM));

        assertThat(refused.getStatusCode())
                .as("a request this application understood, from a member it knows, that they are "
                        + "not allowed to make: " + refused.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detailOf(refused))
                .isEqualTo("Only the member who proposed a withdrawal may take it back, and "
                        + BRAM + " is who you are signed in as.");
        assertThat(app.withdrawalProposalsOf(pot.id()))
                .as("and hers is still waiting, which is the whole point of refusing him")
                .extracting(WithdrawalProposalView::state)
                .containsExactly("PROPOSED");
    }

    @Test
    void a_proposal_is_taken_back_once() {
        SharedPotView pot = app.openAPot(ANKE, "A boat");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "40.00");
        app.deposit(pot.savingsAccountId(), BRAM, "20.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "15.00");
        app.takeBackTheProposal(pot.id(), proposed.id(), ANKE);

        ResponseEntity<JsonNode> refused =
                app.tryToTakeBackTheProposal(pot.id(), proposed.id(), app.customerIdOf(ANKE));

        assertThat(refused.getStatusCode())
                .as("what she asked for is perfectly good and it is the proposal's state that "
                        + "will not allow it, which is a conflict and not a form to fix")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused))
                .isEqualTo("That proposal is WITHDRAWN, and a proposal is answered once.");
    }

    @Test
    void a_proposal_the_pot_never_had_is_not_there_to_take_back() {
        SharedPotView pot = app.openAPot(ANKE, "The bathroom");
        SharedPotView somewhereElse = app.openAPot(ANKE, "The loft");
        AnotherMemberOfThePot.joins(app, somewhereElse.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(somewhereElse.savingsAccountId(), ANKE, "30.00");
        app.deposit(somewhereElse.savingsAccountId(), BRAM, "20.00");
        WithdrawalProposalView elsewhere = app.proposeAWithdrawal(somewhereElse.id(), ANKE, "10.00");

        ResponseEntity<JsonNode> refused =
                app.tryToTakeBackTheProposal(pot.id(), elsewhere.id(), app.customerIdOf(ANKE));

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
    void taking_one_back_needs_somebody_to_be_doing_it() {
        SharedPotView pot = app.openAPot(ANKE, "The garden");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "25.00");
        app.deposit(pot.savingsAccountId(), BRAM, "10.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "5.00");

        ResponseEntity<JsonNode> refused =
                app.tryToTakeBackTheProposalAsNobody(pot.id(), proposed.id());

        assertThat(refused.getStatusCode())
                .as("a form that was never filled in, which is a malformed request rather than a "
                        + "rule refusing anybody")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .isEqualTo("Taking back a withdrawal proposal needs the customer doing it.");
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
