package io.dataroots.savingstreak.sharedpots;

import java.math.BigDecimal;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A member pays into a shared pot from their own current account, using the deposit call they
 * already use for their own savings, and the euros land in the pot.
 *
 * <p>This is what makes a shared pot a pot rather than a list of names, and it is one sentence's
 * worth of new rule: a current account pairs with a savings account nobody holds when its holder is
 * a member of the pot that does hold it. Everything else about the deposit is the deposit that
 * already existed — the same endpoint, the same body, the same answer — which is the point of story
 * 28: there is one way to put money into savings and not two.
 *
 * <p>The money is asserted from both ends, because a balance that went up somewhere is only half the
 * claim. The pot holds what was paid in and the member's current account holds that much less; the
 * member's own savings accounts hold exactly what they held, because the pot's account is not one of
 * theirs and never appears in their list.
 *
 * <p>Its own application, for the reason ticket 01's pot tests give: a savings account nobody holds
 * has no business on the run's shared database, where a test that walks a customer's accounts
 * looking for an identifier nobody uses would find the pot's and hand it to five other classes.
 */
class AMemberPaysIntoTheSharedPotFromTheirOwnCurrentAccountApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-paying-into-a-pot"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The story the whole feature is for: the euros leave the member's current account and are in
     * the pot, where both of them can see them.
     *
     * <p>Asserted against what the balances were rather than against figures this test knows
     * absolutely, because what the contribution changed is the only thing it can honestly claim on a
     * database other methods in this class are also paying into.
     */
    @Test
    void the_euros_leave_the_members_current_account_and_land_in_the_pot() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        assertThat(pot.moneyBalance())
                .as("a pot starts empty, which is what makes the figure below the contribution")
                .isEqualByComparingTo("0.00");
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);
        BigDecimal inHerOwnSavings = app.stillSavedBy(ANKE);

        DepositView paidIn = app.deposit(pot.savingsAccountId(), ANKE, "40.00");

        assertThat(paidIn.amount()).isEqualByComparingTo("40.00");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("the pot holds what was paid into it")
                .isEqualByComparingTo("40.00");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and her current account holds that much less")
                .isEqualByComparingTo(inHerCurrentAccount.subtract(new BigDecimal("40.00")));
        assertThat(app.stillSavedBy(ANKE))
                .as("and not one euro of it is in an account of her own")
                .isEqualByComparingTo(inHerOwnSavings);
    }

    /**
     * Twice, because a pot that could take one contribution and not a second would be a pot in name
     * only — and because the balance is summed from the deposits rather than kept as a figure, which
     * is a claim worth making with more than one deposit in the sum.
     */
    @Test
    void two_contributions_add_up_in_the_pot() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");

        app.deposit(pot.savingsAccountId(), ANKE, "125.50");
        app.deposit(pot.savingsAccountId(), ANKE, "74.50");

        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("two contributions, to the cent")
                .isEqualByComparingTo("200.00");
    }

    /**
     * And the contribution is a deposit like any other, listed in the pot's account's own history
     * with what it earned — which is what "one way to put money into savings and not two" means when
     * you go and look at it afterwards.
     */
    @Test
    void the_contribution_is_a_deposit_and_is_listed_as_one() {
        SharedPotView pot = app.openAPot(ANKE, "New bike");

        DepositView paidIn = app.deposit(pot.savingsAccountId(), ANKE, "30.00");

        assertThat(app.depositsInto(pot.savingsAccountId()))
                .extracting(DepositView::id)
                .as("the pot's account lists the contribution the way any account lists a deposit")
                .containsExactly(paidIn.id());
    }
}
