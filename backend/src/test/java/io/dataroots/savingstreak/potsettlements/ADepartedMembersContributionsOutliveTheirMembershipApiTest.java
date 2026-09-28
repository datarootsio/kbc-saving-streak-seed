package io.dataroots.savingstreak.potsettlements;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.PotContributionView;
import io.dataroots.savingstreak.support.PotMemberView;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * Story 26 and the other side of it: what a departed member paid in survives their leaving, and
 * everything they were allowed to do because they were a member stops the moment they are not one.
 *
 * <p><strong>The history has to go on adding up.</strong> A departure draws their deposits down; it
 * does not delete them. The pot's history still shows what was paid into it and when, and the
 * answers they gave to the pot's proposals still name them as the person who decided — an answer
 * outlives the membership that gave it, which is the only way a record of a decision can be worth
 * keeping.
 *
 * <p><strong>And the membership is the whole of what being in a pot is.</strong> Once the row has
 * gone they may not pay in, propose or approve, and the pot is no longer in their list of pots —
 * each of them refused by the rule that was already there, asked again about somebody who is no
 * longer a member, rather than by anything this slice had to add.
 *
 * <p><strong>And the contributions screen is where story 26 is really read.</strong> The pot's
 * deposit history proves the deposits are still there; the screen two people actually look at to
 * settle who carried what is the contributions list, and a departed member who dropped off it would
 * take their share of the story with them however intact the rows underneath were. So they are on it
 * — with everything they paid in and everything it earned them — and with no role beside their name,
 * because a role is what somebody is to the pot and they are not in it any more. An answer they gave
 * to a proposal reads back the same way, for the same reason.
 *
 * <p><strong>The last test is the one the spec asked for.</strong> Whose assent a withdrawal needs
 * is derived on every read and worked out again when the last approval lands, because a settlement
 * in between changes who has money in the pot. The proposals slice reached that state through a
 * second proposal, which was all it could do; this is the same rule reached by the route it was
 * really written for — a member settled out, and a proposal that stops waiting on them.
 *
 * <p>Its own application and its own pot per test, for the reason every other shared-pot test gives.
 */
class ADepartedMembersContributionsOutliveTheirMembershipApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-departed-members-contributions"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** Story 26: the deposits they made are still in the pot's history, drawn down rather than gone. */
    @Test
    void what_they_paid_in_is_still_in_the_pots_history_after_they_have_left() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        DepositView hers = app.deposit(pot.savingsAccountId(), ANKE, "60.00");
        DepositView his = app.deposit(pot.savingsAccountId(), BRAM, "35.00");

        app.leaveThePot(pot.id(), BRAM, BRAM);

        assertThat(app.depositsInto(pot.savingsAccountId()))
                .as("both payments into the pot are still in its history: a settlement empties a "
                        + "deposit and never unmakes it")
                .extracting(DepositView::id)
                .contains(his.id(), hers.id());
        assertThat(amountOf(pot, his.id()))
                .as("and his reads as the thirty-five he paid in rather than as what is left of it")
                .isEqualByComparingTo("35.00");
        assertThat(amountOf(pot, hers.id())).isEqualByComparingTo("60.00");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and what the pot holds is hers alone, because his came back to him")
                .isEqualByComparingTo("60.00");
        assertThat(app.membersOfThePot(pot.id()))
                .extracting(PotMemberView::name)
                .containsExactly(ANKE);
    }

    /** The decision they made before they left still names them, with no role beside it any more. */
    @Test
    void an_answer_they_gave_still_says_they_were_the_person_who_decided() {
        SharedPotView pot = app.openAPot(ANKE, "Decided and gone");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        app.deposit(pot.savingsAccountId(), BRAM, "50.00");
        WithdrawalProposalView hers = app.proposeAWithdrawal(pot.id(), ANKE, "20.00");
        app.approve(pot.id(), hers.id(), BRAM);

        app.leaveThePot(pot.id(), BRAM, BRAM);

        assertThat(app.withdrawalProposalsOf(pot.id()).get(0).answeredBy())
                .as("he approved it and he is still the person who approved it, with no role "
                        + "beside his name because he holds none any more")
                .extracting(ProposalAnswerView::name, ProposalAnswerView::answer,
                        ProposalAnswerView::role)
                .containsExactly(tuple(BRAM, "APPROVED", null));
    }

    /** Everything the membership allowed stops with it, each refused by the rule that was there. */
    @Test
    void a_departed_member_may_no_longer_pay_in_propose_or_approve() {
        SharedPotView pot = app.openAPot(ANKE, "Out of it");
        String carla = app.aCustomerOfItsOwn("departing and locked out");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AMemberOfThePot.joins(app, pot.id(), ANKE, carla, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        app.deposit(pot.savingsAccountId(), carla, "30.00");
        WithdrawalProposalView waiting = app.proposeAWithdrawal(pot.id(), ANKE, "10.00");

        app.leaveThePot(pot.id(), carla, carla);

        ResponseEntity<JsonNode> payingIn = app.tryToDeposit(pot.savingsAccountId(), carla, "5.00");
        assertThat(payingIn.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(payingIn))
                .as("she is a stranger to the pot again, told what a stranger is told")
                .contains("shared pot you are not a member of");

        ResponseEntity<JsonNode> proposing = app.tryToPropose(pot.id(),
                app.aWithdrawalOf("10.00", app.currentAccountOf(carla), app.customerIdOf(carla)));
        assertThat(proposing.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(proposing)).contains("Only an owner or a contributor may");

        ResponseEntity<JsonNode> approving = app.tryToApprove(pot.id(), waiting.id(),
                app.customerIdOf(carla));
        assertThat(approving.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(approving))
                .as("none of her money is in the pot, because all of it came back to her")
                .contains("Only the members whose money is still in this pot");

        assertThat(app.potsOf(carla))
                .as("and the pot is not in her list of pots, which is a list of what she belongs to")
                .extracting(SharedPotView::id)
                .doesNotContain(pot.id());
    }

    /**
     * The settlement version of the recomputation: a proposal waiting on somebody stops waiting the
     * moment their money leaves the pot with them, and the next approval is the last one it needed.
     */
    @Test
    void a_proposal_waiting_on_a_departed_member_no_longer_waits_on_them() {
        SharedPotView pot = app.openAPot(ANKE, "Waiting on somebody who left");
        String carla = app.aCustomerOfItsOwn("settled out mid-proposal");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AMemberOfThePot.joins(app, pot.id(), ANKE, carla, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        app.deposit(pot.savingsAccountId(), carla, "30.00");
        WithdrawalProposalView hers = app.proposeAWithdrawal(pot.id(), ANKE, "20.00");
        assertThat(hers.whoseAssentItNeeds())
                .as("when she asked, both of the others had money in the pot and both had a veto")
                .extracting(PotMemberView::name)
                .containsExactly(BRAM, carla);

        app.leaveThePot(pot.id(), carla, carla);

        assertThat(app.withdrawalProposalsOf(pot.id()).get(0).whoseAssentItNeeds())
                .as("she has gone and taken her thirty euros with her, so the proposal is waiting "
                        + "on him and on nobody else")
                .extracting(PotMemberView::name)
                .containsExactly(BRAM);

        WithdrawalProposalView answered = app.approve(pot.id(), hers.id(), BRAM);

        assertThat(answered.state())
                .as("his was the last approval it needed after all: " + answered)
                .isEqualTo("APPROVED");
        assertThat(answered.answeredBy())
                .as("and she never answered it, because she was never going to be asked again")
                .extracting(ProposalAnswerView::name)
                .containsExactly(BRAM);
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("a hundred and twenty less the thirty that left with her, less the twenty the "
                        + "proposal finally paid out")
                .isEqualByComparingTo("70.00");
    }

    /**
     * Story 26 on the screen it is really read from: what a departed member paid in is still on the
     * pot's contributions list, so the arithmetic two people check still adds up to the whole story.
     *
     * <p>Their row carries no role, which is how a page tells them from the people still in the pot.
     * A word like "LEFT" would be a fourth role for every rule and every screen to have an opinion
     * about; an absence says exactly what is true.
     */
    @Test
    void what_a_departed_member_paid_in_still_reads_back_on_the_contributions_screen() {
        SharedPotView pot = app.openAPot(ANKE, "Who carried what");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "90.00");
        app.deposit(pot.savingsAccountId(), BRAM, "45.50");

        app.leaveThePot(pot.id(), BRAM, BRAM);

        List<PotContributionView> contributions = app.contributionsTo(pot.id(), ANKE);
        assertThat(contributions)
                .as("he is still on the list, after the members who are still in the pot: "
                        + contributions)
                .extracting(PotContributionView::name)
                .containsExactly(ANKE, BRAM);
        PotContributionView his = contributions.get(1);
        assertThat(his.paidInAltogether())
                .as("forty-five euros fifty is what he carried and leaving did not unmake it")
                .isEqualByComparingTo("45.50");
        assertThat(his.stillTheirs())
                .as("and none of it is still in the pot, because it came back to him when he left")
                .isEqualByComparingTo("0.00");
        assertThat(his.pointsEarned())
                .as("the points his saving earned him are his, and were never the pot's to lose")
                .isEqualTo(45);
        assertThat(his.role())
                .as("with no role beside his name, because he is not anything to this pot now")
                .isNull();
        assertThat(contributions.get(0).role())
                .as("while the member who is still in it reads as what she is")
                .isEqualTo("OWNER");
        assertThat(contributions.get(0).stillTheirs())
                .as("and what is still hers is what the pot holds, which is the arithmetic the "
                        + "screen exists for")
                .isEqualByComparingTo(app.potWith(pot.id()).moneyBalance());
    }

    /** A pot somebody has left is no longer one of their pots, whichever way the list is read. */
    @Test
    void the_pot_leaves_the_departed_members_list_of_pots() {
        SharedPotView pot = app.openAPot(ANKE, "Once mine");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), BRAM, "10.00");
        assertThat(app.potsOf(BRAM)).extracting(SharedPotView::id).contains(pot.id());

        app.leaveThePot(pot.id(), BRAM, BRAM);

        assertThat(app.potsOf(BRAM))
                .as("a pot is in somebody's list because they belong to it")
                .extracting(SharedPotView::id)
                .doesNotContain(pot.id());
        assertThat(app.potsOf(ANKE))
                .as("and it is still in the list of the member who is still in it")
                .extracting(SharedPotView::id)
                .contains(pot.id());
    }

    /** What one deposit into the pot says it was for, read off the pot's own history. */
    private static BigDecimal amountOf(SharedPotView pot, Long depositId) {
        return Arrays.stream(app.depositsInto(pot.savingsAccountId()))
                .filter(deposit -> depositId.equals(deposit.id()))
                .map(DepositView::amount)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "deposit " + depositId + " is not in the pot's history at all"));
    }

    /** The sentence the customer is shown, which is the whole of what they get to act on. */
    private static String reasonIn(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody())
                .as("a refusal answers with a problem body")
                .isNotNull();
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + refused.getBody())
                .isTrue();
        return refused.getBody().get("detail").asText();
    }
}
