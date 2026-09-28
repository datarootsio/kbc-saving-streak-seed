package io.dataroots.savingstreak.potwithdrawals;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
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
 * The withdrawal happens the moment the last required approval arrives: the pot's balance falls, the
 * proposer's current account rises, and the proposal reads {@code APPROVED} — all in the one
 * request, with no separate step for anybody to remember.
 *
 * <p><strong>Stories 51 and 50, and the point of the whole feature.</strong> A gate that collected
 * approvals and then waited for somebody to press "now do it" would be a gate with a second gate
 * behind it, and the members who had all agreed would be looking at a proposal that had not
 * happened. So the last approval <em>is</em> the withdrawal, and a proposal that needs nobody's
 * assent is born with none outstanding and goes through as it is made.
 *
 * <p>And the set of people who must answer is worked out again when that last approval lands, from
 * the membership and the deposits as they stand at that instant: a member whose euros have gone out
 * of the pot in the meantime has nothing left at stake and is no longer asked. A list written down
 * when the proposal was made would hold a withdrawal everybody concerned had approved for ever.
 *
 * <p>The last thing here is the other half of the second weighing — story 57 — where the pot can no
 * longer cover a proposal it could when it was made, and says so in a sentence about what it now
 * holds rather than leaving the proposal waiting for an approval it already has.
 *
 * <p>Its own application, for the reason the other shared-pot tests give: a pot's savings account is
 * held by nobody, and handing one to the shared database would hand every other test class an
 * account identifier that exists and that no customer holds.
 *
 * <p>A pot of its own per test, so that what one of them paid in cannot be what another one is
 * asking about. The pots are independent and the clock never moves here, so the methods may run in
 * any order.
 */
class TheLastApprovalMovesTheMoneyApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-last-approval-moves-the-money"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** Story 51: the approval and the movement are one thing, not two. */
    @Test
    void the_only_approval_it_needed_takes_the_money_out_in_the_same_breath() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "30.00");
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);

        WithdrawalProposalView approved = app.approve(pot.id(), proposed.id(), BRAM);

        assertThat(approved.state())
                .as("his approval was the last one needed, so it was also the withdrawal: "
                        + approved)
                .isEqualTo("APPROVED");
        assertThat(approved.closedAt())
                .as("and it says when it stopped waiting, which is what the absence on a waiting "
                        + "one means")
                .isNotNull();
        assertThat(approved.whoseAssentItNeeds())
                .as("nobody is being asked anything any more")
                .isEmpty();
        assertThat(approved.answeredBy())
                .as("and the record says who decided it and which way they went")
                .extracting(ProposalAnswerView::customerId, ProposalAnswerView::name,
                        ProposalAnswerView::role, ProposalAnswerView::answer)
                .containsExactly(tuple(app.customerIdOf(BRAM), BRAM, "CONTRIBUTOR", "APPROVED"));
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("the pot is thirty euros lighter, which it was not a request ago")
                .isEqualByComparingTo("90.00");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and the thirty euros are in the proposer's own current account, which is "
                        + "where she said they should go")
                .isEqualByComparingTo(inHerCurrentAccount.add(new BigDecimal("30.00")));
        assertThat(app.withdrawalProposalsOf(pot.id()))
                .as("and the pot's record reads the same as the answer she was handed")
                .extracting(WithdrawalProposalView::id, WithdrawalProposalView::state)
                .containsExactly(tuple(proposed.id(), "APPROVED"));
    }

    /** Story 50: the sole contributor withdraws without ceremony, and without a second click. */
    @Test
    void a_proposal_nobody_has_to_approve_goes_through_the_moment_it_is_made() {
        SharedPotView pot = app.openAPot(ANKE, "A boat");
        app.deposit(pot.savingsAccountId(), ANKE, "60.00");
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);

        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "25.00");

        assertThat(proposed.whoseAssentItNeeds())
                .as("there is nobody else's money in the pot, so the gate has nobody to protect")
                .isEmpty();
        assertThat(proposed.state())
                .as("and a gate with nobody on either side of it is not a gate to wait at: "
                        + proposed)
                .isEqualTo("APPROVED");
        assertThat(proposed.answeredBy())
                .as("nobody answered it, because nobody was asked — which is a permanent truth "
                        + "about this proposal rather than a stage it is passing through")
                .isEmpty();
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("35.00");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .isEqualByComparingTo(inHerCurrentAccount.add(new BigDecimal("25.00")));
    }

    /** Story 48, from the side that makes it a gate: every one of them, and not the first of them. */
    @Test
    void nothing_moves_until_the_last_of_the_members_at_stake_has_approved() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        String carla = app.aCustomerOfItsOwn("a third member approving");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, carla, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        app.deposit(pot.savingsAccountId(), carla, "30.00");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "20.00");
        assertThat(proposed.whoseAssentItNeeds())
                .as("both of the others have money in the pot, so both of them have a veto")
                .extracting(PotMemberView::name)
                .containsExactly(BRAM, carla);

        WithdrawalProposalView afterHis = app.approve(pot.id(), proposed.id(), BRAM);

        assertThat(afterHis.state())
                .as("one yes out of two is not a decision: " + afterHis)
                .isEqualTo("PROPOSED");
        assertThat(afterHis.whoseAssentItNeeds())
                .as("and the list is now exactly the people who have not answered")
                .extracting(PotMemberView::name)
                .containsExactly(carla);
        assertThat(afterHis.answeredBy())
                .extracting(ProposalAnswerView::name, ProposalAnswerView::answer)
                .containsExactly(tuple(BRAM, "APPROVED"));
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and not a cent has moved, which is the whole of what a second veto means")
                .isEqualByComparingTo("120.00");

        WithdrawalProposalView afterHers = app.approve(pot.id(), proposed.id(), carla);

        assertThat(afterHers.state())
                .as("hers was the last one needed, so hers was the withdrawal: " + afterHers)
                .isEqualTo("APPROVED");
        assertThat(afterHers.answeredBy())
                .extracting(ProposalAnswerView::name, ProposalAnswerView::answer)
                .containsExactly(tuple(BRAM, "APPROVED"), tuple(carla, "APPROVED"));
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("100.00");
    }

    /**
     * The recomputation the spec asks for, in the only way this slice can bring it about: a member
     * whose euros have all left the pot while a proposal waited is no longer asked about it.
     *
     * <p>Here the euros leave through another proposal going through, because that is what this
     * slice can do; a member settled out of the pot is the same fact arriving by the next slice's
     * route, and the rule that answers both is the one being asserted on.
     */
    @Test
    void who_must_answer_is_worked_out_again_when_the_last_approval_lands() {
        SharedPotView pot = app.openAPot(ANKE, "The extension");
        String carla = app.aCustomerOfItsOwn("drawn down to nothing");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, carla, "CONTRIBUTOR");
        // Hers is the oldest money in the pot, which is what the next withdrawal will spend first.
        app.deposit(pot.savingsAccountId(), carla, "30.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        WithdrawalProposalView hers = app.proposeAWithdrawal(pot.id(), ANKE, "20.00");
        assertThat(hers.whoseAssentItNeeds())
                .as("when she asked, both of them had money in the pot — listed in the order they "
                        + "joined it, which is how the pot lists everything")
                .extracting(PotMemberView::name)
                .containsExactly(BRAM, carla);

        // And now his goes through, and it eats the whole of the oldest money in the pot, which is
        // Carla's.
        WithdrawalProposalView his = app.proposeAWithdrawal(pot.id(), BRAM, "30.00");
        app.approve(pot.id(), his.id(), ANKE);
        app.approve(pot.id(), his.id(), carla);
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("90.00");

        WithdrawalProposalView answered = app.approve(pot.id(), hers.id(), BRAM);

        assertThat(answered.state())
                .as("Carla has nothing left in the pot, so there is nothing of hers to protect and "
                        + "his was the last approval this needed after all: " + answered)
                .isEqualTo("APPROVED");
        assertThat(answered.answeredBy())
                .as("and she never answered it, because she was never going to be asked again")
                .extracting(ProposalAnswerView::name)
                .containsExactly(BRAM);
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("ninety less her twenty, and the pot has paid out both proposals")
                .isEqualByComparingTo("70.00");
    }

    /**
     * Story 57: affordable when it was made, and not when the last approval lands. It is rejected
     * there and then, in a sentence saying what the pot now holds.
     */
    @Test
    void a_proposal_the_pot_can_no_longer_afford_is_rejected_when_the_last_approval_lands() {
        SharedPotView pot = app.openAPot(ANKE, "The loft");
        String carla = app.aCustomerOfItsOwn("approving the unaffordable");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, carla, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        app.deposit(pot.savingsAccountId(), carla, "30.00");
        app.deposit(pot.savingsAccountId(), ANKE, "30.00");
        // Seventy of the hundred in the pot, which it holds perfectly well today.
        WithdrawalProposalView hers = app.proposeAWithdrawal(pot.id(), ANKE, "70.00");
        // And then fifty of them leave, with everybody's agreement, while hers waits.
        WithdrawalProposalView his = app.proposeAWithdrawal(pot.id(), BRAM, "50.00");
        app.approve(pot.id(), his.id(), ANKE);
        app.approve(pot.id(), his.id(), carla);
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("50.00");
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);

        ResponseEntity<JsonNode> refused =
                app.tryToApprove(pot.id(), hers.id(), app.customerIdOf(carla));

        assertThat(refused.getStatusCode())
                .as("the pot is in no unexpected state, there is simply less in it than she asked "
                        + "for: " + refused.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("and the sentence says how much, so there is nothing left to work out")
                .isEqualTo("This pot now holds EUR 50.00, and the withdrawal of EUR 70.00 it was "
                        + "approved for is more than that. The proposal has been rejected.");
        List<WithdrawalProposalView> listed = app.withdrawalProposalsOf(pot.id());
        assertThat(listed)
                .as("and it is finished rather than left waiting for an approval it already has: "
                        + listed)
                .extracting(WithdrawalProposalView::id, WithdrawalProposalView::state)
                .contains(tuple(hers.id(), "REJECTED"));
        assertThat(listed.stream().filter(one -> one.id().equals(hers.id())).findFirst())
                .get()
                .extracting(WithdrawalProposalView::closedAt)
                .as("closed at a moment, the way every proposal that has stopped waiting is")
                .isNotNull();
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and nothing moved on the way: a withdrawal the pot could not pay for paid out "
                        + "nothing at all")
                .isEqualByComparingTo("50.00");
        assertThat(app.currentAccountBalanceOf(ANKE)).isEqualByComparingTo(inHerCurrentAccount);
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
