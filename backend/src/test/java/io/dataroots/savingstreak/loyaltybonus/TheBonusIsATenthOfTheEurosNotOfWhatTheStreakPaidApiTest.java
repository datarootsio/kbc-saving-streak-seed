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
 * The bonus is a tenth of the deposit's own euros, never a tenth of what the streak multiplier paid.
 *
 * <p>So the ten percent means the same thing whatever week a customer paid in, and the two schemes
 * never compound: a run of weeks pays more for the act of saving, and loyalty pays for the money
 * staying, and neither multiplies the other. A customer can still work the figure out in their head
 * from the euros they put in.
 *
 * <p>The discriminating deposit is the third: EUR 500 paid in on a run of three consecutive secured
 * weeks, so the ledger paid it 600 points. Its anniversary is worth 50 — a tenth of its 500 euros —
 * and not 60. Worked out from what the streak paid, the three deposits below would come to 70
 * between them instead of 60, which is what this test would catch.
 *
 * <p>One test, because a run of weeks is a sequence of deposits separated by weeks passing and the
 * clock only goes forward. Its own application, for the reason
 * {@link AnApplicationWithAClockToMove} gives.
 */
class TheBonusIsATenthOfTheEurosNotOfWhatTheStreakPaidApiTest extends ApiIntegrationTest {

    private static final String THE_SWEEP = "payLoyaltyBonuses";

    /**
     * Past the first anniversary of the last of three weekly deposits, which landed fourteen days
     * after the first, and short of the second anniversary of any of them.
     */
    private static final int DAYS_PAST_EVERY_FIRST_ANNIVERSARY = 390;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksAndYearsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-tenth-of-the-euros"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_deposit_paid_at_a_streak_rate_still_pays_a_tenth_of_its_euros() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // Three consecutive secured weeks, so the ladder climbs a step each week and the third
        // deposit is paid at 1.20 — the case where a tenth of the euros and a tenth of what the
        // deposit earned are visibly different figures.
        DepositView weekOne = app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(weekOne.multiplierApplied()).isEqualByComparingTo("1.00");
        assertThat(weekOne.pointsEarned()).isEqualTo(50);

        app.aWeekPasses();
        DepositView weekTwo = app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(weekTwo.multiplierApplied()).isEqualByComparingTo("1.10");
        assertThat(weekTwo.pointsEarned()).isEqualTo(55);

        app.aWeekPasses();
        DepositView weekThree = app.deposit(savingsAccount, ANKE, "500.00");
        assertThat(weekThree.multiplierApplied()).isEqualByComparingTo("1.20");
        assertThat(weekThree.basePoints()).isEqualTo(500);
        assertThat(weekThree.streakBonusPoints()).isEqualTo(100);
        assertThat(weekThree.pointsEarned()).isEqualTo(600);

        long earnedByPayingIn = app.pointsBalanceOf(ANKE);
        assertThat(earnedByPayingIn).isEqualTo(50 + 55 + 600);

        app.daysPass(DAYS_PAST_EVERY_FIRST_ANNIVERSARY);

        app.runJob(THE_SWEEP);

        // Five for each of the EUR 50 deposits and fifty for the EUR 500 one: a tenth of the euros
        // in each, and the 1.10 and 1.20 those two were paid at do not appear anywhere in the sum.
        // A tenth of what the streak paid would be 5 + 5 + 60, so a balance ten points higher would
        // mean the multiplier had leaked into the loyalty rule.
        assertThat(app.pointsBalanceOf(ANKE))
                .as("a tenth of the euros in each deposit, never a tenth of what the streak paid")
                .isEqualTo(earnedByPayingIn + 5 + 5 + 50);
    }
}
