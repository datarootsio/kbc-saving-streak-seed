package io.dataroots.savingstreak.pointsexpiry;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Claiming something spends exactly the points that were about to expire, so the sweep finds nothing
 * left in them.
 *
 * <p>What makes the rule fair rather than punitive, and the reason this ledger has kept dated batches
 * and spent the oldest first since before anything expired. A customer who claims anything at all is
 * spending the points nearest their twelve months, so a batch only ever expires because it survived
 * twelve months of the customer not spending that far down their pot.
 *
 * <p>Its own application and one test, for the reasons {@link AnApplicationWithAClockToMove} gives.
 */
class SpendingBeforeAnAnniversarySavesThePointsApiTest extends ApiIntegrationTest {

    /** Comfortably the far side of any anniversary the clock could land near. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    /** Ten months, which leaves an anniversary close enough to be worth beating and not yet passed. */
    private static final int DAYS_WELL_SHORT_OF_A_YEAR = 300;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-spending-beats-expiry"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_oldest_points_pay_for_the_reward_and_so_have_nothing_left_to_expire() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // The batch that is going to age: exactly what a snack voucher costs.
        app.deposit(savingsAccount, ANKE, "40.00");
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(40);

        // Ten months on, a second batch, which is young and stays young.
        app.daysPass(DAYS_WELL_SHORT_OF_A_YEAR);
        app.deposit(savingsAccount, ANKE, "30.00");
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(70);

        // Claimed before the anniversary, and paid for out of the oldest batch — which is the batch
        // that was about to go.
        ClaimedRewardView snack = app.claim(ANKE, "SNACK_VOUCHER");
        assertThat(snack.pointsSpent()).isEqualTo(40);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(30);

        // Past the old batch's anniversary, and nowhere near the young one's.
        app.daysPass(DAYS_WELL_PAST_A_YEAR - DAYS_WELL_SHORT_OF_A_YEAR);

        app.runJob("expireOldPoints");

        // Nothing was lost. The batch whose twelve months ran out had already been spent down to
        // nothing, and a batch with nothing in it has nothing to expire.
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the points that aged had already bought a snack voucher, so the sweep found "
                        + "nothing in them and left the younger batch alone")
                .isEqualTo(30);
    }
}
