package io.dataroots.savingstreak.loyaltybonus;

import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expiry is a rule about points and loyalty is a reward for money staying put, so a deposit keeps
 * paying its anniversaries long after everything it has ever earned has run out.
 *
 * <p>The state this test is built to reach is a customer holding nothing at all: the deposit's own
 * 500 points went twelve months after the money moved, and the first anniversary's 50 went twelve
 * months after that anniversary. The euros never moved, so the deposit is still on its clock — and
 * the second anniversary pays as much as the first did, into a pot that was empty a moment before.
 * A ledger that had tied the reward to the points the deposit earned would pay nothing here.
 *
 * <p>The two sweeps are run in the order the nightly jobs run in, expiry and then loyalty, so what
 * the balance says after each is what a customer would see the morning after that night.
 *
 * <p>Two years are then skipped with the loyalty sweep never run, which is what lets the last part
 * say the other half of the same arithmetic. The third and fourth anniversaries are both paid at
 * once, and the third's twelve months were already up when it was paid — twelve months after the
 * third anniversary is the fourth — so that batch arrives beyond its own year and the next night's
 * expiry sweep takes it, while the fourth anniversary's is inside its year and stays. Both rules
 * holding at once rather than either misbehaving.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: four years
 * pass in it.
 */
class ADepositKeepsPayingAfterItsOwnPointsHaveExpiredApiTest extends ApiIntegrationTest {

    private static final String THE_LOYALTY_SWEEP = "payLoyaltyBonuses";

    private static final String THE_EXPIRY_SWEEP = "expireOldPoints";

    /** A fortnight past the first anniversary, whichever day of which month the run lands on. */
    private static final int DAYS_PAST_THE_FIRST_ANNIVERSARY = 379;

    /**
     * Four weeks past the second anniversary — twenty-four calendar months is 730 or 731 days — and
     * so also past the first anniversary's own twelve months, which fall on that same day.
     */
    private static final int DAYS_PAST_THE_SECOND_ANNIVERSARY = 758;

    /**
     * Past the fourth anniversary — forty-eight calendar months is 1460 or 1461 days — and well
     * short of the fifth, which is 1825 days out. The third anniversary is 1095 days out, so by this
     * day its own twelve months are up as well.
     */
    private static final int DAYS_PAST_THE_FOURTH_ANNIVERSARY = 1500;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-clock-outlives-the-points"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_deposit_pays_its_second_anniversary_into_a_pot_its_own_points_have_already_left() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        LocalDate theMoneyLandedOn = app.theDateTheClockReads();
        DepositView paidIn = app.deposit(savingsAccount, ANKE, "500.00");
        assertThat(paidIn.pointsEarned()).isEqualTo(500);

        // The first year: the anniversary pays 50, and the same night the deposit's own 500 reach
        // their twelve months and go.
        app.daysPass(DAYS_PAST_THE_FIRST_ANNIVERSARY);
        app.runJob(THE_EXPIRY_SWEEP);
        app.runJob(THE_LOYALTY_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the deposit's own points have gone and the first anniversary's bonus is all "
                        + "that is left")
                .isEqualTo(50);

        // The second year. The expiry sweep runs first, as it does every night, and takes the first
        // anniversary's bonus: twelve months after that anniversary fell on the second anniversary,
        // four weeks before this run.
        app.daysPass(DAYS_PAST_THE_SECOND_ANNIVERSARY - DAYS_PAST_THE_FIRST_ANNIVERSARY);
        app.runJob(THE_EXPIRY_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("everything this deposit has ever earned has now run out")
                .isZero();
        assertThat(app.pointsExpiringNextOf(ANKE)).isNull();

        // And then the loyalty sweep, on a deposit whose points are all gone and whose euros never
        // moved. The clock belongs to the money, so the second anniversary pays what the first did.
        app.runJob(THE_LOYALTY_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("a deposit whose points have all expired still pays its second anniversary")
                .isEqualTo(50);
        assertThat(app.pointsExpiringNextOf(ANKE)).isEqualTo(50);
        assertThat(app.pointsExpiringNextOnOf(ANKE))
                .as("dated at the second anniversary, so its own twelve months run out three years "
                        + "after the money landed")
                .isEqualTo(theMoneyLandedOn.plusYears(3));

        // Two years in which no sweep of either kind ran, which is the state a database left alone
        // over a long demo is in. Both the third and the fourth anniversary are owed.
        app.daysPass(DAYS_PAST_THE_FOURTH_ANNIVERSARY - DAYS_PAST_THE_SECOND_ANNIVERSARY);

        app.runJob(THE_LOYALTY_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("the third and fourth anniversaries are both owed and both paid, and the "
                        + "second's bonus is still standing because nothing has swept it yet")
                .isEqualTo(50 + 50 + 50);

        // The next night's expiry sweep, which is the rule that can see what each batch is dated at.
        // The second anniversary's bonus is two years past its day and the third anniversary's was
        // already out of time when it was credited an instant ago; the fourth anniversary's is
        // inside its year. So a bonus paid for an anniversary long past is credited and then swept,
        // which is both rules holding at once.
        app.runJob(THE_EXPIRY_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("the third anniversary's bonus arrived beyond its own twelve months and has "
                        + "gone again, and only the fourth anniversary's is still inside its year")
                .isEqualTo(50);
        assertThat(app.pointsExpiringNextOf(ANKE)).isEqualTo(50);
        assertThat(app.pointsExpiringNextOnOf(ANKE))
                .as("dated at the fourth anniversary, so it runs out five years after the money "
                        + "landed")
                .isEqualTo(theMoneyLandedOn.plusYears(5));
    }
}
