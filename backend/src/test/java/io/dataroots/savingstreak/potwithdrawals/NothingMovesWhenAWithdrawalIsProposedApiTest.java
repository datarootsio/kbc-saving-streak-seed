package io.dataroots.savingstreak.potwithdrawals;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SharedPotView;
import io.dataroots.savingstreak.support.WithdrawalProposalView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proposing is not deciding: when a member proposes a withdrawal from a shared pot, not a cent
 * moves.
 *
 * <p><strong>Story 47, and the assertion that makes the approval gate worth having.</strong> A gate
 * that let the money out while it asked would be a notification rather than a gate. So the pot holds
 * exactly what it held, every deposit in it is untouched, nothing has arrived in the proposer's
 * current account, and neither member's points, mark or savings have moved — all read back through
 * the same screens anybody else reads them through.
 *
 * <p>The figures are read before and compared after rather than asserted as constants, because the
 * claim is that <em>nothing changed</em> and a constant would only say that one number is what this
 * test expected it to be.
 *
 * <p>Its own application, for the reason the other shared-pot tests give.
 */
class NothingMovesWhenAWithdrawalIsProposedApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-nothing-moves-when-a-withdrawal-is-proposed"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_pot_holds_what_it_held_and_every_member_has_what_they_had() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "100.00");
        app.deposit(pot.savingsAccountId(), BRAM, "50.00");

        BigDecimal inThePot = app.potWith(pot.id()).moneyBalance();
        List<DepositView> contributions = List.of(app.depositsInto(pot.savingsAccountId()));
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);
        BigDecimal inHisCurrentAccount = app.currentAccountBalanceOf(BRAM);
        BigDecimal herOwnSavings = app.stillSavedBy(ANKE);
        BigDecimal hisOwnSavings = app.stillSavedBy(BRAM);
        long herPoints = app.pointsBalanceOf(ANKE);
        long hisPoints = app.pointsBalanceOf(BRAM);

        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "120.00");

        assertThat(proposed.state())
                .as("what now exists is the question and not the movement: " + proposed)
                .isEqualTo("PROPOSED");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("the pot holds exactly what it held — a proposal for one hundred and twenty "
                        + "euros has taken none of them")
                .isEqualByComparingTo(inThePot);
        assertThat(app.depositsInto(pot.savingsAccountId()))
                .as("and every contribution in it is the contribution it was: nothing has been "
                        + "drawn down, which is what a withdrawal would have done to the oldest of "
                        + "them first")
                .containsExactlyElementsOf(contributions);
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("nothing arrived in the proposer's current account either")
                .isEqualByComparingTo(inHerCurrentAccount);
        assertThat(app.currentAccountBalanceOf(BRAM)).isEqualByComparingTo(inHisCurrentAccount);
        assertThat(app.stillSavedBy(ANKE))
                .as("nor did anything happen to what either of them holds in savings of their own")
                .isEqualByComparingTo(herOwnSavings);
        assertThat(app.stillSavedBy(BRAM)).isEqualByComparingTo(hisOwnSavings);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("and no points were earned or lost by anybody, because no euro moved")
                .isEqualTo(herPoints);
        assertThat(app.pointsBalanceOf(BRAM)).isEqualTo(hisPoints);
    }

    /**
     * And a second proposal changes nothing either, including the first one — two members can each
     * be asking, and a pot that quietly cancelled one with the other would be deciding on their
     * behalf.
     */
    @Test
    void a_second_proposal_leaves_the_first_one_and_the_money_exactly_as_they_were() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "60.00");
        app.deposit(pot.savingsAccountId(), BRAM, "60.00");
        WithdrawalProposalView hers = app.proposeAWithdrawal(pot.id(), ANKE, "20.00");
        BigDecimal inThePot = app.potWith(pot.id()).moneyBalance();

        WithdrawalProposalView his = app.proposeAWithdrawal(pot.id(), BRAM, "30.00");

        assertThat(his.id()).isNotEqualTo(hers.id());
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("still nothing has moved, with a hundred and twenty euros in the pot and fifty "
                        + "of them spoken for by two proposals nobody has answered")
                .isEqualByComparingTo(inThePot);
        assertThat(app.withdrawalProposalsOf(pot.id()))
                .as("and both are waiting, each for the other member")
                .extracting(WithdrawalProposalView::state)
                .containsExactly("PROPOSED", "PROPOSED");
    }
}
