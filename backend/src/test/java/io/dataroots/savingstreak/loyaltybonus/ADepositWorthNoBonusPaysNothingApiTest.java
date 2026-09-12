package io.dataroots.savingstreak.loyaltybonus;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deposit a tenth of which rounds down to nothing pays nothing on its anniversary, and a deposit
 * holding nothing pays nothing at all.
 *
 * <p>Two ways of being worth nothing, and only one of them is a rule of its own. A deposit holding
 * under ten euros earns no bonus because a tenth of nine euros rounds down to no points, exactly the
 * way EUR 0.99 has always earned no base point — rounding down is a rule a customer can see rather
 * than a bug they suspect. A deposit that has been emptied is worth nothing for the same arithmetic
 * and is not even considered: money never comes back into a deposit, so the sweep does not ask about
 * one at zero.
 *
 * <p>Both in one narrative, across two of the same customer's savings accounts, because the clock
 * only goes forward and a second test method in this class would find the year already moved on.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ADepositWorthNoBonusPaysNothingApiTest extends ApiIntegrationTest {

    private static final String THE_SWEEP = "payLoyaltyBonuses";

    /** Comfortably the far side of any anniversary the clock could land near. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-worth-no-bonus"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_deposit_under_ten_euros_and_a_deposit_emptied_both_pay_nothing() {
        long smallSavings = app.savingsAccountOf(ANKE);
        long emptiedSavings = app.otherSavingsAccountOf(ANKE);

        // One euro short of the ten a single point of bonus needs, and its own euros floor to nine.
        DepositView tooSmall = app.deposit(smallSavings, ANKE, "9.99");
        assertThat(tooSmall.pointsEarned())
                .as("nine whole euros, because the cents have never earned a point either")
                .isEqualTo(9);

        // And one with plenty in it, taken straight back out again.
        DepositView emptied = app.deposit(emptiedSavings, ANKE, "500.00");
        assertThat(emptied.pointsEarned()).isEqualTo(500);
        app.withdraw(emptiedSavings, ANKE, "500.00");
        assertThat(app.balancesOf(emptiedSavings).moneyBalance())
                .as("nothing is left in it, and nothing ever comes back into a deposit")
                .isEqualByComparingTo("0.00");

        long earnedByPayingIn = app.pointsBalanceOf(ANKE);
        assertThat(earnedByPayingIn)
                .as("the points both deposits earned when they landed, which a withdrawal does not "
                        + "take back")
                .isEqualTo(9 + 500);

        app.daysPass(DAYS_WELL_PAST_A_YEAR);

        app.runJob(THE_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("a tenth of nine euros is no points, and a tenth of nothing is nothing")
                .isEqualTo(earnedByPayingIn);
    }
}
