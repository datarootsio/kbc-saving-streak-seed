package io.dataroots.savingstreak.potsettlements;

import java.math.BigDecimal;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PotMemberView;
import io.dataroots.savingstreak.support.PotSettlementView;
import io.dataroots.savingstreak.support.SharedPotView;
import io.dataroots.savingstreak.support.WithdrawalProposalView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stories 22, 23, 24 and 25, and the third of the three tests the spec says are the point of the
 * whole feature: a member who leaves gets back exactly their own remaining euros, to the cent, and
 * the other member's remaining euros are untouched by it.
 *
 * <p><strong>Their own, and not a share.</strong> Nothing here is a proportion of what the pot holds
 * or of what anybody paid in: what comes back is the sum of what is left of that member's own
 * deposits. That is what makes the figure exact to the cent with no rounding rule to state, and it is
 * what makes the settlement safe to do without anybody's approval — the euros it moves were this
 * member's all along, so there is nobody whose money could be spent and nobody to ask.
 *
 * <p><strong>Which is the approval gate from the other side.</strong> A withdrawal from the pot waits
 * on every other member with money in it, because it draws the account's oldest deposits down first
 * whoever paid them in. A settlement cannot reach those deposits at all, so the same principle — you
 * can always take back your own and never somebody else's — lets this through without a proposal.
 * The test that proves it is the one where the other member has money in the pot and is never asked
 * anything.
 *
 * <p>And a member whose contributions have already been spent by an approved withdrawal gets back
 * what is left of them and not what they paid in, which is the same arithmetic read at its sharpest:
 * the oldest deposits went first, they were hers, and leaving cannot undo that.
 *
 * <p>Its own application, for the reason every other shared-pot test gives: a pot's savings account
 * is held by nobody, and handing one to the shared database would hand every other test class an
 * account identifier that exists and that no customer holds.
 *
 * <p>A pot of its own per test, so that what one of them paid in cannot be what another is asking
 * about. The pots are independent and the clock never moves here, so the methods may run in any
 * order.
 */
class AMemberLeavesWithExactlyTheirOwnRemainingEurosApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-member-leaves-with-their-own"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * Stories 23 and 24, and the heart of the slice: his own euros come back, hers do not move, and
     * nobody approved anything.
     */
    @Test
    void the_leaving_member_gets_their_own_remaining_euros_and_nobody_elses() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");
        app.deposit(pot.savingsAccountId(), BRAM, "45.50");
        BigDecimal inHisCurrentAccount = app.currentAccountBalanceOf(BRAM);
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);

        PotSettlementView left = app.leaveThePot(pot.id(), BRAM, BRAM);

        assertThat(left.settled())
                .as("forty-five euros fifty is what he paid in and what was still there, to the "
                        + "cent: " + left)
                .isEqualByComparingTo("45.50");
        assertThat(left.role())
                .as("and the answer says what he was, because the membership has gone by the time "
                        + "anybody reads it")
                .isEqualTo("CONTRIBUTOR");
        assertThat(left.withdrawalId())
                .as("money moved, so there is a movement in the account's history to point at")
                .isNotNull();
        assertThat(app.currentAccountBalanceOf(BRAM))
                .as("and it landed in his own current account, which is where a settlement goes")
                .isEqualByComparingTo(inHisCurrentAccount.add(new BigDecimal("45.50")));
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("hers is exactly what it was: somebody else leaving is not a payment to her")
                .isEqualByComparingTo(inHerCurrentAccount);
        assertThat(left.thePotNowHolds())
                .as("what the pot holds is now her eighty euros and nothing else")
                .isEqualByComparingTo("80.00");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and the pot read back says the same, which is the point of saying it twice")
                .isEqualByComparingTo("80.00");
        assertThat(app.membersOfThePot(pot.id()))
                .as("he is not in the pot any more, which is what leaving one is")
                .extracting(PotMemberView::name)
                .containsExactly(ANKE);
    }

    /**
     * Story 24 said out loud: the other member has money in the pot and is never asked anything. A
     * settlement is not a withdrawal, so there is no proposal, no approval and nothing to wait for.
     */
    @Test
    void leaving_needs_nobody_s_approval_even_when_somebody_else_has_money_in_the_pot() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "200.00");
        app.deposit(pot.savingsAccountId(), BRAM, "30.00");

        PotSettlementView left = app.leaveThePot(pot.id(), BRAM, BRAM);

        assertThat(left.settled())
                .as("he took his thirty euros back in the one request, with two hundred of hers "
                        + "sitting beside them: " + left)
                .isEqualByComparingTo("30.00");
        assertThat(app.withdrawalProposalsOf(pot.id()))
                .as("and there was never a proposal, because there was nobody's money to ask about")
                .isEmpty();
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("200.00");
    }

    /**
     * Story 25: being removed and leaving differ in who decided, not in what happens to the money —
     * and the money comes back to the departing member rather than to the owner who removed them.
     */
    @Test
    void an_owner_removing_a_member_settles_them_exactly_as_leaving_would() {
        SharedPotView pot = app.openAPot(ANKE, "The extension");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "60.00");
        app.deposit(pot.savingsAccountId(), BRAM, "25.25");
        BigDecimal inHisCurrentAccount = app.currentAccountBalanceOf(BRAM);
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);

        PotSettlementView removed = app.leaveThePot(pot.id(), BRAM, ANKE);

        assertThat(removed.settled())
                .as("she decided it and he is settled what he would have settled himself: "
                        + removed)
                .isEqualByComparingTo("25.25");
        assertThat(app.currentAccountBalanceOf(BRAM))
                .as("into his own current account, because it is his money whoever decided")
                .isEqualByComparingTo(inHisCurrentAccount.add(new BigDecimal("25.25")));
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and removing somebody is not a way of being paid: hers has not moved")
                .isEqualByComparingTo(inHerCurrentAccount);
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("60.00");
    }

    /** Leaving with nothing in the pot works, and there is no movement of money to record. */
    @Test
    void a_member_with_nothing_in_the_pot_leaves_and_no_money_moves() {
        SharedPotView pot = app.openAPot(ANKE, "Watched and left");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "VIEWER");
        app.deposit(pot.savingsAccountId(), ANKE, "40.00");
        BigDecimal inHisCurrentAccount = app.currentAccountBalanceOf(BRAM);

        PotSettlementView left = app.leaveThePot(pot.id(), BRAM, BRAM);

        assertThat(left.settled())
                .as("he never paid anything in, so there is nothing of his to come back: " + left)
                .isEqualByComparingTo("0.00");
        assertThat(left.withdrawalId())
                .as("and no withdrawal was recorded, because nothing happened to any money — an "
                        + "absence that says something rather than nothing")
                .isNull();
        assertThat(app.currentAccountBalanceOf(BRAM)).isEqualByComparingTo(inHisCurrentAccount);
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and the pot holds what it held")
                .isEqualByComparingTo("40.00");
        assertThat(app.membersOfThePot(pot.id()))
                .as("he has left it all the same, which is the whole of what he asked for")
                .extracting(PotMemberView::name)
                .containsExactly(ANKE);
    }

    /**
     * The sharpest reading of "their own remaining euros": an approved withdrawal has already spent
     * the oldest money in the pot, which was hers, so what she gets back on leaving is what is left
     * of her deposits and not what she put in.
     *
     * <p>This is the arithmetic the whole approval gate exists because of, seen from the settlement's
     * side — and it is exact to the cent without any share of anything being worked out.
     */
    @Test
    void what_an_approved_withdrawal_already_spent_does_not_come_back_on_leaving() {
        SharedPotView pot = app.openAPot(ANKE, "The loft");
        // An owner beside her, because the member leaving here is the one who opened the pot and a
        // pot may never lose its last owner. What is being asserted is the arithmetic, so the pot is
        // set up so that the rule about owners has nothing to say about it.
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "OWNER");
        // Hers is the oldest money in the pot, so it is the money the withdrawal spends first.
        app.deposit(pot.savingsAccountId(), ANKE, "100.00");
        app.deposit(pot.savingsAccountId(), BRAM, "50.00");
        WithdrawalProposalView his = app.proposeAWithdrawal(pot.id(), BRAM, "70.00");
        app.approve(pot.id(), his.id(), ANKE);
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("seventy of the hundred and fifty has gone, and all of it out of her deposit")
                .isEqualByComparingTo("80.00");
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);

        PotSettlementView left = app.leaveThePot(pot.id(), ANKE, ANKE);

        assertThat(left.settled())
                .as("thirty euros: her hundred less the seventy that was already spent out of it, "
                        + "and not a share of the eighty the pot holds: " + left)
                .isEqualByComparingTo("30.00");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .isEqualByComparingTo(inHerCurrentAccount.add(new BigDecimal("30.00")));
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and his fifty is still there, untouched by her leaving")
                .isEqualByComparingTo("50.00");
    }
}
