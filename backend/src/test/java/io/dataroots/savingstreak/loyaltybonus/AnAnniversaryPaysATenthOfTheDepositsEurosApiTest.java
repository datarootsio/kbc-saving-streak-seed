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
 * Money that stays in savings for twelve months earns again: a tenth of the whole euros still
 * sitting in the deposit, in the customer's pot like any other points.
 *
 * <p>The one test that says what the rule is. A EUR 500 deposit is made, a sweep run well inside the
 * twelve months takes nothing, and a sweep run the far side of the anniversary pays 50. Both halves
 * matter: a rule that paid on every sweep whatever the date would pass a test that asserted the
 * second half alone.
 *
 * <p>And it is run a third time, because a nightly job runs nightly. What has been paid is written
 * down against the deposit and the anniversary it was paid for, so a second pass over the same rows
 * pays nothing — which is what makes the job safe for a trainer to type whenever they like.
 *
 * <p>Its own application and its own database, for the reason {@link AnApplicationWithAClockToMove}
 * gives and then some: this test winds the clock more than a year forward, which nothing else in the
 * run could survive sharing.
 */
class AnAnniversaryPaysATenthOfTheDepositsEurosApiTest extends ApiIntegrationTest {

    /** The job by the name a trainer types into the development jobs endpoint. */
    private static final String THE_SWEEP = "payLoyaltyBonuses";

    /**
     * A fortnight past a year. Comfortably the far side of any anniversary the clock could land
     * near, so the test is about the rule rather than about which day of which month the run happens
     * on — and comfortably short of the second anniversary, which pays separately.
     */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    /** Far enough into the year that no anniversary has arrived, and nowhere near one. */
    private static final int DAYS_WELL_SHORT_OF_A_YEAR = 300;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-anniversary-pays-a-tenth"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_deposit_left_alone_for_twelve_months_pays_a_tenth_of_its_euros() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // What the deposit is worth on the day it lands, which is what it has always been worth:
        // one point per whole euro. Nothing about loyalty has happened yet.
        DepositView paidIn = app.deposit(savingsAccount, ANKE, "500.00");
        assertThat(paidIn.pointsEarned()).isEqualTo(500);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(500);

        // Ten months on, nothing is owed, and a sweep run now says so by paying nothing.
        app.daysPass(DAYS_WELL_SHORT_OF_A_YEAR);
        app.runJob(THE_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("ten months is not twelve, and a sweep run inside the twelve months pays nothing")
                .isEqualTo(500);

        // The far side of the anniversary. The money stayed where it was put for a year, so the
        // deposit pays a tenth of the 500 whole euros still sitting in it.
        app.daysPass(DAYS_WELL_PAST_A_YEAR - DAYS_WELL_SHORT_OF_A_YEAR);

        app.runJob(THE_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("a tenth of the 500 euros still in the deposit, in the same pot as the rest")
                .isEqualTo(500 + 50);

        // And again, on the same deposit and the same anniversary, because a nightly job runs
        // nightly. What has been paid is written down against the deposit and the anniversary it was
        // paid for, so nothing is ever paid twice.
        app.runJob(THE_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("a second sweep over the same anniversary pays nothing")
                .isEqualTo(500 + 50);
    }
}
