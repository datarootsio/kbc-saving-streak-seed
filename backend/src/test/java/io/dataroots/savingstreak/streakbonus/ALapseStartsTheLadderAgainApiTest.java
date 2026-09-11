package io.dataroots.savingstreak.streakbonus;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A run is lost, and the next week that secures starts the ladder again at the ordinary rate.
 *
 * <p>The rate is a state and the record is not, which is the thing this class is here to show. A
 * customer who skipped a week is paying the ordinary rate <em>now</em> — on a Wednesday, before this
 * week has ended and before they have paid anything in — and the deposit that starts their new run
 * earns exactly what a first week earns. What they lose is the rate; what they keep is the figure to
 * beat.
 *
 * <p>Its own application and one test, for the reasons {@link AnApplicationWithAClockToMove} gives.
 */
class ALapseStartsTheLadderAgainApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-lapse-starts-again"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_first_week_of_a_run_started_after_a_lapse_pays_the_ordinary_rate_again() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // Two consecutive secured weeks, so there is a rate above the ordinary one to lose.
        DepositView theFirstWeek = app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(theFirstWeek.multiplierApplied()).isEqualByComparingTo("1.00");
        app.aWeekPasses();
        DepositView theSecondWeek = app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(theSecondWeek.multiplierApplied()).isEqualByComparingTo("1.10");
        assertThat(theSecondWeek.streakBonusPoints()).isEqualTo(5);
        BalancesView onARun = app.balancesOf(savingsAccount);
        assertThat(onARun.currentStreakWeeks()).isEqualTo(2);
        assertThat(onARun.currentMultiplier()).isEqualByComparingTo("1.10");

        app.aWeekPasses();

        // The third week is still running with nothing in it, so it has ended nothing yet: the week
        // immediately before it is secured, and a deposit before Sunday would carry the run on at
        // the rate it is still paying.
        BalancesView theEmptyWeekWhileItRuns = app.balancesOf(savingsAccount);
        assertThat(theEmptyWeekWhileItRuns.currentStreakWeeks()).isEqualTo(2);
        assertThat(theEmptyWeekWhileItRuns.currentMultiplier()).isEqualByComparingTo("1.10");

        app.aWeekPasses();

        // Now the empty week has passed unsecured, and the rate is the ordinary one again straight
        // away — before anything has landed in this week, and without any job having run. A customer
        // who skipped last week does not carry last month's rate into a Wednesday deposit.
        BalancesView afterTheLapse = app.balancesOf(savingsAccount);
        assertThat(afterTheLapse.currentStreakWeeks()).isZero();
        assertThat(afterTheLapse.currentMultiplier()).isEqualByComparingTo("1.00");
        // Losing the run costs the run and not the record.
        assertThat(afterTheLapse.bestStreakWeeks()).isEqualTo(2);

        // And the deposit that starts the new run is paid what a first week is paid, with no uplift:
        // a lapse is a setback rather than a lockout, and the ladder is climbed again from the
        // bottom.
        DepositView theFirstWeekOfTheNewRun = app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(theFirstWeekOfTheNewRun.multiplierApplied()).isEqualByComparingTo("1.00");
        assertThat(theFirstWeekOfTheNewRun.basePoints()).isEqualTo(50);
        assertThat(theFirstWeekOfTheNewRun.streakBonusPoints()).isZero();
        assertThat(theFirstWeekOfTheNewRun.pointsEarned()).isEqualTo(50);

        BalancesView onTheNewRun = app.balancesOf(savingsAccount);
        assertThat(onTheNewRun.currentStreakWeeks()).isEqualTo(1);
        assertThat(onTheNewRun.bestStreakWeeks()).isEqualTo(2);
        assertThat(onTheNewRun.currentMultiplier()).isEqualByComparingTo("1.00");
        // The bonus the lost run paid is still in the balance: nothing about losing a run takes back
        // points that were already earned.
        assertThat(onTheNewRun.pointsBalance()).isEqualTo(50 + 55 + 50);
    }
}
