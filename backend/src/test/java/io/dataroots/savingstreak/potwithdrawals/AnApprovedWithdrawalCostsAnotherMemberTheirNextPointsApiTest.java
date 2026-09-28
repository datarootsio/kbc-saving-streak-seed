package io.dataroots.savingstreak.potwithdrawals;

import java.math.BigDecimal;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
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
 * An approved withdrawal draws the pot's oldest deposits down first, across members — so it spends
 * another member's euros, and because what they have already earned points on does not fall with
 * what they hold, their next contribution earns them <strong>nothing</strong> until they have
 * climbed back to where they already were.
 *
 * <p><strong>This is the reason the approval gate exists, proven rather than asserted in prose.</strong>
 * It is the sharpest thing in the whole spec and it is invisible in the code: nothing in
 * {@code SharedPotsService} mentions points, nothing in the points ledger mentions pots, and the
 * consequence falls out of two rules that were each written years apart from this feature — a
 * withdrawal takes the oldest deposits in the account whoever paid them in, and the mark a deposit
 * is judged against is the most the customer has <em>ever</em> held rather than what they hold now.
 * Put a second person's money in the same account and one member's withdrawal quietly spends
 * another member's points. Every member whose euros are in the pot gets a veto because of what is
 * asserted below.
 *
 * <p>The proof is end to end and every figure is read back through a screen somebody really looks
 * at: the deposit that earned, the pot's balance, the mark on the account overview, and the deposit
 * that earned nothing.
 *
 * <p>Its own application and its own clock, for the reason {@link AnApplicationWithAClockToMove}
 * documents: what a deposit earns is judged against everything its customer has ever saved, so a
 * test measuring an earning of nought needs a customer nothing has ever landed for. Doubly so here —
 * a pot on the shared database would hand every other test class a savings account identifier that
 * exists and that no customer holds.
 *
 * <p>One test method, because the story is one narrative and each step depends on the last.
 *
 * <p><strong>What is still each member's, per pot,</strong> is the figure the pot's contributions
 * screen will put beside this, and that screen is its own slice. What can be read today is the pair
 * this test asserts on: the pot holds less than the member paid into it, and their mark has not
 * moved — which is the whole of the arithmetic that costs them the points.
 */
class AnApprovedWithdrawalCostsAnotherMemberTheirNextPointsApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseSaversHaveNoHistory() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-an-approved-withdrawal-costs-another-member"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_member_whose_oldest_deposits_were_spent_earns_nothing_on_their_next_contribution() {
        // Each member's own savings account, which is where their own figures are read: the mark
        // and the points are the customer's, and every account they hold reports them. The pot's
        // account reports nobody's, because nobody holds it.
        long hisOwnAccount = app.savingsAccountOf(BRAM);
        long herOwnAccount = app.savingsAccountOf(ANKE);
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        assertThat(app.balancesOf(hisOwnAccount).mostEverSaved())
                .as("he has never saved anything, and this nought is what the whole story is "
                        + "measured from")
                .isEqualByComparingTo("0.00");

        // His money goes in first, which is what makes it the oldest in the pot — and the oldest is
        // what a withdrawal takes, whoever paid it in.
        DepositView his = app.deposit(pot.savingsAccountId(), BRAM, "100.00");
        assertThat(his.newSavings())
                .as("every euro of it is above his mark, because he had none")
                .isEqualByComparingTo("100.00");
        assertThat(his.pointsEarned())
                .as("and a euro saved is a point earned, at his own ordinary rate")
                .isEqualTo(100);

        app.deposit(pot.savingsAccountId(), ANKE, "50.00");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("a hundred and fifty euros in one pot, a hundred of them his")
                .isEqualByComparingTo("150.00");

        BalancesView hisMarkBefore = app.balancesOf(hisOwnAccount);
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);

        // And now she takes a hundred out, with his approval — which is the only way she can, and
        // the whole point of asking him.
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "100.00");
        assertThat(proposed.whoseAssentItNeeds())
                .as("his money is in the pot and the oldest goes first, so this spends his")
                .isNotEmpty();
        WithdrawalProposalView approved = app.approve(pot.id(), proposed.id(), BRAM);

        assertThat(approved.state()).isEqualTo("APPROVED");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("the pot is down to her fifty, and every euro that left was one of his: the "
                        + "draw-down took the oldest deposits first and his was the oldest")
                .isEqualByComparingTo("50.00");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and all hundred of them are in her current account")
                .isEqualByComparingTo(inHerCurrentAccount.add(new BigDecimal("100.00")));

        BalancesView hisMarkAfter = app.balancesOf(hisOwnAccount);
        assertThat(hisMarkAfter.mostEverSaved())
                .as("**and here is the whole feature**: what he has ever earned points on has not "
                        + "fallen by a cent, although what he holds has fallen to nothing")
                .isEqualByComparingTo(hisMarkBefore.mostEverSaved())
                .isEqualByComparingTo("100.00");
        assertThat(hisMarkAfter.pointsBalance())
                .as("his hundred points are still his — money coming back out costs nobody points, "
                        + "it simply does not earn a second time on the way back in")
                .isEqualTo(100);

        // So his next contribution fills a hole that his own euros used to fill, and earns nothing.
        DepositView againstTheHole = app.deposit(pot.savingsAccountId(), BRAM, "60.00");

        assertThat(againstTheHole.newSavings())
                .as("sixty euros of real money, and not one of them is new saving: he is still "
                        + "forty below the most he has ever had")
                .isEqualByComparingTo("0.00");
        assertThat(againstTheHole.pointsEarned())
                .as("**the consequence the approval gate exists for**: a withdrawal from a shared "
                        + "pot costs another member points and not only euros, which is why his "
                        + "approval had to be asked for")
                .isZero();
        assertThat(app.balancesOf(hisOwnAccount).pointsBalance())
                .as("and his balance has not moved, although he has just paid in sixty euros")
                .isEqualTo(100);

        // And it is a hole rather than a punishment: the euros above the mark earn again.
        DepositView backAbove = app.deposit(pot.savingsAccountId(), BRAM, "60.00");

        assertThat(backAbove.newSavings())
                .as("a hundred and twenty in, a hundred already earned on, so twenty of these are "
                        + "genuinely new saving")
                .isEqualByComparingTo("20.00");
        assertThat(backAbove.pointsEarned())
                .as("which earn, at whatever his own run is worth by then")
                .isPositive();

        assertThat(app.balancesOf(herOwnAccount).mostEverSaved())
                .as("and none of it happened to her: her mark is the fifty she paid in, untouched "
                        + "by the hundred she took out")
                .isEqualByComparingTo("50.00");
    }
}
