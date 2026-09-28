package io.dataroots.savingstreak.potwithdrawals;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.PotMemberView;
import io.dataroots.savingstreak.support.ProposalAnswerView;
import io.dataroots.savingstreak.support.SharedPotView;
import io.dataroots.savingstreak.support.WithdrawalProposalView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * One rejection ends a proposal, and nothing moves.
 *
 * <p><strong>Story 52.</strong> A no is a no and does not have to be repeated: the euros a
 * withdrawal would spend are partly the rejecting member's, and a veto that waited to see whether
 * anybody else objected — or that could be outvoted — would be a veto in name only. So the first
 * rejection closes it, everybody else who had still to answer is no longer being asked anything, and
 * the pot holds exactly what it held.
 *
 * <p>The rejection stays in the pot's record rather than vanishing, for the reason a withdrawn
 * proposal does: a decision leaves a record, and a proposal that disappeared when somebody said no
 * would leave the member who asked looking at a list saying they never had.
 *
 * <p>Its own application, for the reason the other shared-pot tests give.
 */
class OneRejectionEndsAProposalApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-one-rejection-ends-a-proposal"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_member_whose_money_it_would_have_spent_says_no_and_nothing_moves() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "30.00");
        BigDecimal inThePot = app.potWith(pot.id()).moneyBalance();
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);
        List<DepositView> contributions = List.of(app.depositsInto(pot.savingsAccountId()));

        WithdrawalProposalView rejected = app.reject(pot.id(), proposed.id(), BRAM);

        assertThat(rejected.state())
                .as("his forty euros were going to pay for part of it, and he says not: " + rejected)
                .isEqualTo("REJECTED");
        assertThat(rejected.closedAt())
                .as("closed at a moment, the way every proposal that has stopped waiting is")
                .isNotNull();
        assertThat(rejected.whoseAssentItNeeds())
                .as("nobody is being asked anything any more, and the state beside it says why")
                .isEmpty();
        assertThat(rejected.answeredBy())
                .as("and the record says who said no, which is what tells this apart from a "
                        + "proposal the pot could not pay for")
                .extracting(ProposalAnswerView::name, ProposalAnswerView::answer)
                .containsExactly(tuple(BRAM, "REJECTED"));
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("the pot holds exactly what it held")
                .isEqualByComparingTo(inThePot);
        assertThat(app.depositsInto(pot.savingsAccountId()))
                .as("and every contribution in it is the contribution it was: no deposit has been "
                        + "drawn down, which is what an approval would have done to the oldest first")
                .containsExactlyElementsOf(contributions);
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("nothing arrived in the proposer's current account either")
                .isEqualByComparingTo(inHerCurrentAccount);
    }

    @Test
    void one_no_is_enough_even_when_somebody_else_had_still_to_answer() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        String carla = app.aCustomerOfItsOwn("never got to answer");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, carla, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        app.deposit(pot.savingsAccountId(), carla, "30.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "60.00");
        assertThat(proposed.whoseAssentItNeeds())
                .as("two people have money in it, so two people have a veto")
                .extracting(PotMemberView::name)
                .containsExactly(BRAM, carla);

        WithdrawalProposalView rejected = app.reject(pot.id(), proposed.id(), BRAM);

        assertThat(rejected.state())
                .as("one of the two was enough, and the other was never asked: " + rejected)
                .isEqualTo("REJECTED");
        assertThat(rejected.answeredBy())
                .extracting(ProposalAnswerView::name, ProposalAnswerView::answer)
                .containsExactly(tuple(BRAM, "REJECTED"));
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("120.00");
    }

    @Test
    void the_pot_keeps_the_record_of_the_no() {
        SharedPotView pot = app.openAPot(ANKE, "A boat");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "30.00");
        app.deposit(pot.savingsAccountId(), BRAM, "20.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "10.00");

        app.reject(pot.id(), proposed.id(), BRAM);

        List<WithdrawalProposalView> listed = app.withdrawalProposalsOf(pot.id());
        assertThat(listed)
                .as("somebody asked and somebody said no, and both of those are things that "
                        + "happened: " + listed)
                .extracting(WithdrawalProposalView::id, WithdrawalProposalView::state)
                .containsExactly(tuple(proposed.id(), "REJECTED"));
        assertThat(listed.get(0).answeredBy())
                .as("read back off the pot's own list, not only off the answer he was handed")
                .extracting(ProposalAnswerView::name, ProposalAnswerView::answer)
                .containsExactly(tuple(BRAM, "REJECTED"));
    }
}
