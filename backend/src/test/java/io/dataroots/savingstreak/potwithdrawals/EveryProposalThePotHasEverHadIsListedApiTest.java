package io.dataroots.savingstreak.potwithdrawals;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PotMemberView;
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
 * A pot lists every proposal it has ever had, in any state, with who asked, when, how much, where
 * the money would go and who has still to answer.
 *
 * <p>Story 58. A record rather than an inbox: a proposal that vanished the moment it stopped waiting
 * would leave the pot with no trace that anybody ever asked, and the member who took theirs back
 * looking at a list that says they never did. A decision leaves a record, and so does a decision
 * somebody unmade.
 *
 * <p>Oldest first, which is the order the asking happened in — the same order the pot's invitations
 * and its members read in, so that nobody has to learn a second way of reading a list here.
 *
 * <p>Its own application, for the reason the other shared-pot tests give.
 */
class EveryProposalThePotHasEverHadIsListedApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-every-proposal-a-pot-has-had"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_waiting_and_the_closed_alike_read_back_oldest_first() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "100.00");
        app.deposit(pot.savingsAccountId(), BRAM, "60.00");
        WithdrawalProposalView taken = app.proposeAWithdrawal(pot.id(), ANKE, "25.00");
        app.takeBackTheProposal(pot.id(), taken.id(), ANKE);
        WithdrawalProposalView waiting = app.proposeAWithdrawal(pot.id(), BRAM, "40.00");

        List<WithdrawalProposalView> listed = app.withdrawalProposalsOf(pot.id());

        assertThat(listed)
                .as("both of them, in the order they were made: " + listed)
                .extracting(WithdrawalProposalView::id)
                .containsExactly(taken.id(), waiting.id());
        assertThat(listed).extracting(WithdrawalProposalView::state)
                .as("and a proposal somebody took back is still a proposal the pot had")
                .containsExactly("WITHDRAWN", "PROPOSED");

        WithdrawalProposalView his = listed.get(1);
        assertThat(his.proposedByCustomerId()).isEqualTo(app.customerIdOf(BRAM));
        assertThat(his.proposedByName())
                .as("named, because the sentence this list writes is \"Bram is asking to take EUR "
                        + "40.00 out of Kitchen\"")
                .isEqualTo(BRAM);
        assertThat(his.potName()).isEqualTo("Kitchen");
        assertThat(his.amount()).isEqualByComparingTo("40.00");
        assertThat(his.toCurrentAccountId())
                .as("and where the money would go, which is his own current account")
                .isEqualTo(app.currentAccountOf(BRAM));
        assertThat(his.proposedAt())
                .as("when he asked, off the application's clock")
                .isNotNull();
        assertThat(his.whoseAssentItNeeds())
                .as("waiting for Anke, whose hundred euros his withdrawal would draw down first")
                .extracting(PotMemberView::customerId)
                .containsExactly(app.customerIdOf(ANKE));
        assertThat(his.answeredBy())
                .as("and nobody has answered it: nothing in this application can yet, and the "
                        + "empty list is the honest answer rather than a field left out")
                .isEmpty();
    }

    @Test
    void a_pot_nobody_has_proposed_anything_for_has_an_empty_list_rather_than_nothing() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");

        assertThat(app.withdrawalProposalsOf(pot.id()))
                .as("an answer about a pot that exists, and not the same thing as a pot that "
                        + "does not")
                .isEmpty();
    }

    @Test
    void a_pot_nobody_has_heard_of_is_told_so_in_the_words_this_application_uses() {
        ResponseEntity<JsonNode> refused =
                app.tryToReadTheProposalsOf(app.anIdNoCustomerHas() + 10_000);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody()).isNotNull();
        assertThat(refused.getBody().get("detail").asText())
                .as("the pot's own sentence for an absent pot, so that one wording answers for "
                        + "every read of one: " + refused.getBody())
                .startsWith("There is no shared pot ");
    }
}
