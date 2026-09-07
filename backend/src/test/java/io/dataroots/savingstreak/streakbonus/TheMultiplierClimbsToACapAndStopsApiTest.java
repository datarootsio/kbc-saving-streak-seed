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
 * Seven consecutive secured weeks, walked one at a time: the rate climbs a step a week to the cap in
 * the sixth, and then stops.
 *
 * <p>The whole ladder in one narrative, because the ladder is one function and the interesting part
 * of it is where it stops climbing: a seventh week paying more than a sixth would be a scheme the
 * bank cannot price, and a seventh week paying less would make a long run worse than a short one.
 *
 * <p>The flooring is watched here too, at the rates where it bites. Points are whole and round down
 * twice — the amount to whole euros, then the product of those and the rate — so EUR 7.60 at 1.30 is
 * seven base points and nine altogether, and a deposit whose euros floor away earns nothing at any
 * rate at all.
 *
 * <p>Its own application and one test, for the reasons {@link AnApplicationWithAClockToMove} gives.
 */
class TheMultiplierClimbsToACapAndStopsApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-ladder-to-the-cap"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void each_further_consecutive_week_pays_a_step_more_until_the_cap_holds_it() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // Week one, at the ordinary rate: a run of one week is somebody's first week.
        secureTheWeekAt(savingsAccount, "1.00", 50, 0);
        assertThat(app.balancesOf(savingsAccount).currentStreakWeeks()).isEqualTo(1);

        // Each further consecutive week adds a step of 0.10, and the step is visible as the bonus:
        // fifty euros at 1.10 is 55 points, at 1.20 is 60, and so on.
        app.aWeekPasses();
        secureTheWeekAt(savingsAccount, "1.10", 50, 5);
        app.aWeekPasses();
        secureTheWeekAt(savingsAccount, "1.20", 50, 10);
        app.aWeekPasses();
        secureTheWeekAt(savingsAccount, "1.30", 50, 15);

        // Still in the fourth week, so still at 1.30. EUR 7.60 floors to seven whole euros, seven at
        // 1.30 is 9.10, and that floors to nine points — seven earned as euros and two as the run.
        DepositView withCents = app.deposit(savingsAccount, ANKE, "7.60");
        assertThat(withCents.multiplierApplied()).isEqualByComparingTo("1.30");
        assertThat(withCents.basePoints()).isEqualTo(7);
        assertThat(withCents.streakBonusPoints()).isEqualTo(2);
        assertThat(withCents.pointsEarned()).isEqualTo(9);

        app.aWeekPasses();
        secureTheWeekAt(savingsAccount, "1.40", 50, 20);
        app.aWeekPasses();
        // The sixth consecutive week is where the ladder reaches what it is allowed to pay.
        secureTheWeekAt(savingsAccount, "1.50", 50, 25);
        BalancesView atTheCap = app.balancesOf(savingsAccount);
        assertThat(atTheCap.currentStreakWeeks()).isEqualTo(6);
        assertThat(atTheCap.currentMultiplier()).isEqualByComparingTo("1.50");

        // Three euros at 1.50 is 4.50, floored to four: three earned as euros and one as the run.
        DepositView threeEuros = app.deposit(savingsAccount, ANKE, "3.00");
        assertThat(threeEuros.multiplierApplied()).isEqualByComparingTo("1.50");
        assertThat(threeEuros.basePoints()).isEqualTo(3);
        assertThat(threeEuros.streakBonusPoints()).isEqualTo(1);
        assertThat(threeEuros.pointsEarned()).isEqualTo(4);

        // And under a euro is nothing at any rate, bonus included: the amount floors to no whole
        // euros first, and the best rate in the scheme multiplies that into no points at all.
        DepositView underAEuro = app.deposit(savingsAccount, ANKE, "0.90");
        assertThat(underAEuro.multiplierApplied()).isEqualByComparingTo("1.50");
        assertThat(underAEuro.basePoints()).isZero();
        assertThat(underAEuro.streakBonusPoints()).isZero();
        assertThat(underAEuro.pointsEarned()).isZero();

        app.aWeekPasses();

        // The seventh consecutive week, and the cap holds: still 1.50, and no more. A run that kept
        // climbing would be a scheme nobody could price; a run that fell back would make seven weeks
        // worse than six.
        secureTheWeekAt(savingsAccount, "1.50", 50, 25);
        BalancesView pastTheCap = app.balancesOf(savingsAccount);
        assertThat(pastTheCap.currentStreakWeeks()).isEqualTo(7);
        assertThat(pastTheCap.bestStreakWeeks()).isEqualTo(7);
        assertThat(pastTheCap.currentMultiplier()).isEqualByComparingTo("1.50");
        // Every deposit above, added up: the seven weekly fifties at their own rates, plus the three
        // that were about the flooring.
        assertThat(pastTheCap.pointsBalance())
                .isEqualTo(50 + 55 + 60 + 65 + 70 + 75 + 75 + 9 + 4 + 0);
    }

    /**
     * A deposit of EUR 50 that secures the week it lands in, insisted on down to what it was paid:
     * the rate, the euros, the uplift, and the total being the two added together.
     */
    private static void secureTheWeekAt(long savingsAccountId, String rate, long basePoints, long bonus) {
        DepositView made = app.deposit(savingsAccountId, ANKE, "50.00");
        assertThat(made.multiplierApplied()).as("the rate the week's deposit was paid at")
                .isEqualByComparingTo(rate);
        assertThat(made.basePoints()).isEqualTo(basePoints);
        assertThat(made.streakBonusPoints()).isEqualTo(bonus);
        assertThat(made.pointsEarned()).isEqualTo(basePoints + bonus);
    }
}
