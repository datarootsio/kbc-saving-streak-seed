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
 * A week in which EUR 30 landed does not count. The run of two weeks that led up to it is over, the
 * current run reads zero, the best-ever run still reports the two, and securing the next week starts
 * a new run of one.
 *
 * <p>The reading that has to be right is <em>when</em> the run reads zero. While the EUR 30 week is
 * still running it has ended nothing: money can still land in it, the last secured week is the one
 * immediately before, and the run is alive. It is the week after that where a customer finds the run
 * gone — on whatever day of the week this test happens to run, before anything at all has landed in
 * that week, and without waiting for it to end.
 *
 * <p>Its own application, its own database and its own clock, for the reason
 * {@link AnApplicationWithAClockToMove} gives. One test, because the clock only goes forward.
 */
class AWeekShortOfTheMinimumEndsTheStreakApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-week-short-of-the-minimum"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_week_that_took_in_thirty_euros_ends_the_run_and_leaves_the_record_standing() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // Two weeks secured, one after the other.
        app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(app.balancesOf(savingsAccount).currentStreakWeeks()).isEqualTo(1);
        app.aWeekPasses();
        app.deposit(savingsAccount, ANKE, "50.00");
        BalancesView twoWeeksIn = app.balancesOf(savingsAccount);
        assertThat(twoWeeksIn.currentStreakWeeks()).isEqualTo(2);
        assertThat(twoWeeksIn.bestStreakWeeks()).isEqualTo(2);

        app.aWeekPasses();
        app.deposit(savingsAccount, ANKE, "30.00");

        // The third week is short of the minimum and is still running, so it has ended nothing: the
        // last secured week is the one immediately before this one, and the run is alive.
        BalancesView theShortWeekWhileItRuns = app.balancesOf(savingsAccount);
        assertThat(theShortWeekWhileItRuns.newSavingsThisWeek()).isEqualByComparingTo("30.00");
        assertThat(theShortWeekWhileItRuns.stillNeededThisWeek()).isEqualByComparingTo("20.00");
        assertThat(theShortWeekWhileItRuns.currentStreakWeeks()).isEqualTo(2);
        assertThat(theShortWeekWhileItRuns.bestStreakWeeks()).isEqualTo(2);

        app.aWeekPasses();

        // Now the EUR 30 week has passed unsecured, and the run is gone the moment the week after it
        // begins: nothing has been deposited into this week, nothing has run, and the figure is
        // already zero. The minimum is a real threshold and EUR 30 is not it.
        BalancesView theWeekAfterTheShortOne = app.balancesOf(savingsAccount);
        assertThat(theWeekAfterTheShortOne.newSavingsThisWeek()).isEqualByComparingTo("0.00");
        assertThat(theWeekAfterTheShortOne.currentStreakWeeks()).isZero();
        // Losing the run costs the run and not the record.
        assertThat(theWeekAfterTheShortOne.bestStreakWeeks()).isEqualTo(2);

        app.deposit(savingsAccount, ANKE, "50.00");

        // A fresh run starts at one, and the best-ever figure keeps the higher of the two.
        BalancesView afterTheLapse = app.balancesOf(savingsAccount);
        assertThat(afterTheLapse.currentStreakWeeks()).isEqualTo(1);
        assertThat(afterTheLapse.bestStreakWeeks()).isEqualTo(2);
        // Every euro that moved still earned its point, whatever the run was doing.
        assertThat(afterTheLapse.pointsBalance()).isEqualTo(50 + 50 + 30 + 50);
    }
}
