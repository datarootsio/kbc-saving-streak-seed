package io.dataroots.savingstreak.streakofweeks;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A week nothing landed in ends a run exactly as a week short of the minimum does. Skipping a week
 * and paying in too little are the same thing to a streak, which is what makes the threshold mean
 * what it says.
 *
 * <p>The companion to {@link AWeekShortOfTheMinimumEndsTheStreakApiTest}, and separate from it
 * because the two narratives need the same weeks: one clock cannot run both.
 *
 * <p>Its own application, its own database and its own clock, for the reason
 * {@link AnApplicationWithAClockToMove} gives. One test, because the clock only goes forward.
 */
class AWeekWithNothingInItEndsTheStreakApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-week-with-nothing-in-it"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_week_nobody_paid_into_ends_the_run_and_leaves_the_record_standing() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // Two weeks secured, one after the other, each comfortably past the minimum.
        app.deposit(savingsAccount, ANKE, "60.00");
        assertThat(app.balancesOf(savingsAccount).currentStreakWeeks()).isEqualTo(1);
        app.aWeekPasses();
        app.deposit(savingsAccount, ANKE, "60.00");
        BalancesView twoWeeksIn = app.balancesOf(savingsAccount);
        assertThat(twoWeeksIn.currentStreakWeeks()).isEqualTo(2);
        assertThat(twoWeeksIn.bestStreakWeeks()).isEqualTo(2);

        app.aWeekPasses();

        // The third week, with nothing in it and still running. It has ended nothing yet: the last
        // secured week is the one immediately before this one, so the run is alive and a deposit
        // made before Sunday would still continue it.
        BalancesView theEmptyWeekWhileItRuns = app.balancesOf(savingsAccount);
        assertThat(theEmptyWeekWhileItRuns.newSavingsThisWeek()).isEqualByComparingTo("0.00");
        assertThat(theEmptyWeekWhileItRuns.currentStreakWeeks()).isEqualTo(2);
        assertThat(theEmptyWeekWhileItRuns.bestStreakWeeks()).isEqualTo(2);

        app.aWeekPasses();

        // Nobody paid into it, so the week passed unsecured, and the run is gone the same way EUR 30
        // would have ended it — before anything has landed in this week and without waiting for it
        // to end.
        BalancesView theWeekAfterTheEmptyOne = app.balancesOf(savingsAccount);
        assertThat(theWeekAfterTheEmptyOne.currentStreakWeeks()).isZero();
        assertThat(theWeekAfterTheEmptyOne.bestStreakWeeks()).isEqualTo(2);
        // Nothing else moved: no job runs, no history is rewritten, and the money and points are
        // where two weeks of saving left them.
        assertThat(theWeekAfterTheEmptyOne.moneyBalance()).isEqualByComparingTo("120.00");
        assertThat(theWeekAfterTheEmptyOne.pointsBalance()).isEqualTo(120);
    }
}
