package io.dataroots.savingstreak.potclosing;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PotInvitationView;
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
 * Story 67: every action on a closed pot is refused with a sentence saying the pot is closed.
 *
 * <p><strong>Every one of them, because "properly closed" is not a claim that can be made by
 * example.</strong> Paying in, inviting, answering an invitation, changing a role, proposing,
 * approving, opening a goal, leaving and closing it again are each asked of a pot that is over, and
 * each is turned down in words a person could read aloud. One of those left open would be the one
 * somebody found — a euro paid into a pot whose money has already gone back to everybody, a member
 * added to an arrangement that ended, a proposal to take out what is no longer there.
 *
 * <p><strong>The sentence says it is closed rather than saying they may not.</strong> Nothing about
 * who is asking comes into it: the owner who closed the pot is refused in the same words a stranger
 * is, because what is wrong is the pot and not the person. A refusal that named a role would send
 * somebody off to be promoted into doing something that cannot be done.
 *
 * <p><strong>And the status is a conflict rather than a 403 or a 404.</strong> The request was
 * understood and perfectly well formed; it is the state of the pot that will not allow it — the same
 * reading an abandoned goal and a closed proposal already get. A 404 would be a lie the person could
 * disprove by reading the pot, which still answers, which is what
 * {@code AClosedPotStaysAReadableRecordApiTest} is about.
 *
 * <p>Paying in is the one refusal here that does not come from the Shared Pots module at all: a
 * deposit into a pot goes through the deposit endpoint that already existed, is judged by the
 * pairing Accounts works out, and is refused in the Deposits module's own words. That it says the
 * same thing in its own sentence is the point of asserting it here.
 *
 * <p>Its own application and a pot of its own per test, for the reason every other shared-pot test
 * gives. The clock never moves here, so the methods may run in any order.
 */
class EveryActionOnAClosedPotIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-every-action-on-a-closed-pot"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** Paying into a pot that is over, refused by the deposit endpoint in its own words. */
    @Test
    void paying_into_a_closed_pot_is_refused() {
        SharedPotView pot = aPotThatHasBeenClosed("Paid for and finished");

        ResponseEntity<JsonNode> payingIn = app.tryToDeposit(pot.savingsAccountId(), ANKE, "10.00");

        assertThat(payingIn.getStatusCode())
                .as("the request is understood and it is the pot that will not have it")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(payingIn))
                .as("and she owns it, so nothing about her role could have made it go through")
                .contains("shared pot that has been closed")
                .contains("cannot be paid into");
    }

    /** Inviting somebody into a pot that is over. */
    @Test
    void inviting_somebody_into_a_closed_pot_is_refused() {
        SharedPotView pot = aPotThatHasBeenClosed("Nobody else is joining");
        String latecomer = app.aCustomerOfItsOwn("invited to a closed pot");

        ResponseEntity<JsonNode> inviting = app.tryToInvite(pot.id(),
                Map.of("contactDetails", app.contactDetailsOf(latecomer),
                        "role", "CONTRIBUTOR",
                        "customerId", app.customerIdOf(ANKE)));

        assertThat(inviting.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(inviting)).contains("was closed on").contains("invite somebody into it");
    }

    /**
     * Accepting an invitation that was still waiting when the pot closed, which is the only way
     * anybody could try to become a member of one.
     */
    @Test
    void accepting_an_invitation_to_a_closed_pot_is_refused() {
        SharedPotView pot = app.openAPot(ANKE, "Asked before it ended");
        String latecomer = app.aCustomerOfItsOwn("accepting after the close");
        PotInvitationView waiting = app.invite(pot.id(), ANKE, latecomer, "CONTRIBUTOR");
        app.closeThePot(pot.id(), ANKE);

        ResponseEntity<JsonNode> accepting =
                app.tryToAccept(pot.id(), waiting.id(), app.customerIdOf(latecomer));

        assertThat(accepting.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(accepting))
                .as("they are told about the pot rather than about the invitation, which is still "
                        + "perfectly good and simply has nothing left to let them into")
                .contains("was closed on")
                .contains("answer an invitation to it");
        assertThat(app.membersOfThePot(pot.id()))
                .as("and nobody joined a pot that had already ended")
                .hasSize(1);
    }

    /** Changing what somebody is to a pot that is over. */
    @Test
    void changing_a_role_in_a_closed_pot_is_refused() {
        SharedPotView pot = aPotWithBothOfThemThatHasBeenClosed("Roles are settled");

        Map<String, Object> change = new HashMap<>();
        change.put("role", "OWNER");
        change.put("customerId", app.customerIdOf(ANKE));
        ResponseEntity<JsonNode> changing =
                app.tryToChangeTheRole(pot.id(), app.customerIdOf(BRAM), change);

        assertThat(changing.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(changing)).contains("was closed on").contains("change a role in it");
    }

    /** Proposing to take money out of a pot that has none left in it. */
    @Test
    void proposing_a_withdrawal_from_a_closed_pot_is_refused() {
        SharedPotView pot = aPotWithBothOfThemThatHasBeenClosed("Nothing left to propose about");

        ResponseEntity<JsonNode> proposing = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("5.00", app.currentAccountOf(ANKE), app.customerIdOf(ANKE)));

        assertThat(proposing.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(proposing))
                .as("told that the pot is closed rather than that it holds less than five euros, "
                        + "which is true and is not what they should go and do something about")
                .contains("was closed on")
                .contains("propose taking money out of it");
    }

    /** Answering a proposal the close ended, which is a question nobody is asking any more. */
    @Test
    void approving_a_withdrawal_in_a_closed_pot_is_refused() {
        SharedPotView pot = app.openAPot(ANKE, "Approved after the end");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "30.00");
        app.deposit(pot.savingsAccountId(), BRAM, "30.00");
        WithdrawalProposalView waiting = app.proposeAWithdrawal(pot.id(), ANKE, "10.00");
        app.closeThePot(pot.id(), ANKE);

        ResponseEntity<JsonNode> approving =
                app.tryToApprove(pot.id(), waiting.id(), app.customerIdOf(BRAM));

        assertThat(approving.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(approving))
                .as("he is told the pot is closed rather than that the proposal is, which is the "
                        + "larger piece of news and the one that explains the other")
                .contains("was closed on")
                .contains("answer a withdrawal proposal in it");
    }

    /** Leaving a pot that everybody has already been settled out of. */
    @Test
    void leaving_a_closed_pot_is_refused() {
        SharedPotView pot = aPotWithBothOfThemThatHasBeenClosed("Already over");

        ResponseEntity<JsonNode> leaving = app.tryToLeaveThePot(pot.id(), app.customerIdOf(BRAM),
                app.customerIdOf(BRAM), app.currentAccountOf(BRAM));

        assertThat(leaving.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(leaving)).contains("was closed on").contains("leave it");
        assertThat(app.membersOfThePot(pot.id()))
                .as("and he is still on the pot's record, which is what a closed pot is for")
                .hasSize(2);
    }

    /** Closing a pot that is closed, which a second click on a stale page is. */
    @Test
    void closing_a_closed_pot_again_is_refused() {
        SharedPotView pot = aPotThatHasBeenClosed("Closed once already");

        ResponseEntity<JsonNode> again = app.tryToClose(pot.id(), app.customerIdOf(ANKE));

        assertThat(again.getStatusCode())
                .as("told what happened rather than quietly told it worked")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(again)).contains("was closed on").contains("close it");
    }

    /**
     * Opening a goal on a closed pot, which is an action too — and the one refused furthest from
     * this module, in front of the Goals endpoints that knew nothing about pots before this feature.
     */
    @Test
    void opening_a_goal_on_a_closed_pot_is_refused() {
        SharedPotView pot = aPotThatHasBeenClosed("Saving for nothing now");

        ResponseEntity<JsonNode> opening =
                app.tryToOpenAGoal(pot.savingsAccountId(), ANKE, "Another kitchen", "500.00");

        assertThat(opening.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(opening))
                .as("she owns it, so the refusal is about the pot and not about her: " + opening)
                .contains("was closed on");
        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .as("and the goals screen is still empty, because none was opened")
                .isEmpty();
    }

    /** A pot she opened, paid into and closed. */
    private static SharedPotView aPotThatHasBeenClosed(String name) {
        SharedPotView pot = app.openAPot(ANKE, name);
        app.deposit(pot.savingsAccountId(), ANKE, "20.00");
        app.closeThePot(pot.id(), ANKE);
        return pot;
    }

    /** The same, with a second member in it, for the refusals that need somebody else. */
    private static SharedPotView aPotWithBothOfThemThatHasBeenClosed(String name) {
        SharedPotView pot = app.openAPot(ANKE, name);
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "20.00");
        app.deposit(pot.savingsAccountId(), BRAM, "20.00");
        app.closeThePot(pot.id(), ANKE);
        return pot;
    }

    /** The sentence the customer is shown, which is the whole of what they get to act on. */
    private static String reasonIn(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody())
                .as("a refusal answers with a problem body")
                .isNotNull();
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered "
                        + refused.getBody())
                .isTrue();
        return refused.getBody().get("detail").asText();
    }
}
