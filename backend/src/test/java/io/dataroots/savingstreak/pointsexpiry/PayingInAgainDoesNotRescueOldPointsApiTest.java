package io.dataroots.savingstreak.pointsexpiry;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paying in again earns new points and rescues none of the old ones.
 *
 * <p>The other half of what "twelve months of inactivity" means: the inactivity belongs to the batch
 * rather than to the customer. Money paid in on the very day of a sweep earns a batch with twelve
 * months of its own and says nothing whatever about the batch beside it whose twelve months are up.
 *
 * <p>Worth its own test because the opposite rule is the one most loyalty schemes have, and a reader
 * who assumed it would find nothing in the code to contradict them.
 *
 * <p>Its own application and one test, for the reasons {@link AnApplicationWithAClockToMove} gives.
 */
class PayingInAgainDoesNotRescueOldPointsApiTest extends ApiIntegrationTest {

    /** Comfortably the far side of any anniversary the clock could land near. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-paying-in-rescues-nothing"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void paying_in_again_does_not_extend_the_life_of_points_already_earned() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        app.deposit(savingsAccount, ANKE, "60.00");
        long withTheOldBatch = app.pointsBalanceOf(ANKE);

        app.daysPass(DAYS_WELL_PAST_A_YEAR);

        // Activity, of the only kind there is: money paid in on the day of the sweep. It earns its
        // own points and says nothing about the ones already there.
        app.deposit(savingsAccount, ANKE, "15.00");
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(withTheOldBatch + 15);

        app.runJob("expireOldPoints");

        // The sixty went anyway. Nothing but spending a batch keeps it, which is the difference
        // between twelve months of the batch's inactivity and twelve months of the customer's.
        assertThat(app.pointsBalanceOf(ANKE))
                .as("depositing on the day of the sweep earned 15 new points and rescued none of "
                        + "the old ones")
                .isEqualTo(15);
    }
}
