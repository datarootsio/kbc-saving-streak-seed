package io.dataroots.savingstreak.pointsexpiry;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Points that have gone twelve months unspent are gone, and the sweep that ends them takes nothing
 * that has not.
 *
 * <p>The one test that says what the rule is. A batch is earned, a year and a bit passes, the sweep
 * runs, and the balance those points were in has dropped by exactly what the deposit earned — while a
 * batch earned a fortnight before the same sweep is untouched by it. Both halves matter: a rule that
 * only ever expired everything would pass a test that asserted the first half alone.
 *
 * <p>Its own application and its own database, for the reason {@link AnApplicationWithAClockToMove}
 * gives and then some: this test winds the clock more than a year forward, which nothing else in the
 * run could survive sharing.
 */
class PointsExpireTwelveMonthsAfterTheyWereEarnedApiTest extends ApiIntegrationTest {

    /**
     * A fortnight past a year. Comfortably the far side of any anniversary the clock could land near,
     * so the test is about the rule rather than about which day of which month the run happens on.
     */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    /** Far enough into the year that nothing has expired, and nowhere near an anniversary. */
    private static final int DAYS_WELL_SHORT_OF_A_YEAR = 300;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-points-expire"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_batch_a_year_old_expires_and_a_batch_a_fortnight_old_does_not() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        DepositView lastYear = app.deposit(savingsAccount, ANKE, "40.00");
        assertThat(lastYear.pointsEarned()).isEqualTo(40);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(40);

        // Nothing has expired yet, ten months on, and a sweep run now says so by taking nothing.
        app.daysPass(DAYS_WELL_SHORT_OF_A_YEAR);
        app.runJob("expireOldPoints");
        assertThat(app.pointsBalanceOf(ANKE))
                .as("ten months is not twelve, and a sweep run inside the twelve months takes nothing")
                .isEqualTo(40);

        // The far side of the anniversary, with a second, much younger batch beside the old one.
        app.daysPass(DAYS_WELL_PAST_A_YEAR - DAYS_WELL_SHORT_OF_A_YEAR);
        DepositView thisWeek = app.deposit(savingsAccount, ANKE, "25.00");
        assertThat(thisWeek.pointsEarned()).isEqualTo(25);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(65);

        app.runJob("expireOldPoints");

        // Exactly the old batch, and exactly the whole of it. The young one is untouched by the same
        // sweep, which is the half of the rule that says twelve months means twelve months.
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the year-old 40 points have gone and this week's 25 have not")
                .isEqualTo(25);

        // And again, on the same batches, because a nightly job runs nightly. A batch that has gone
        // carries the moment it went and the sweep asks only for batches that have not, so nothing
        // is ever expired twice.
        app.runJob("expireOldPoints");
        assertThat(app.pointsBalanceOf(ANKE))
                .as("a second sweep over the same batches takes nothing")
                .isEqualTo(25);
    }
}

