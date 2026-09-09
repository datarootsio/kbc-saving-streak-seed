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
 * A loyalty bonus runs out twelve months after the anniversary that paid it — not twelve months
 * after the sweep happened to notice it — and the customer is told so before it goes.
 *
 * <p>Three sweeps say it. The first, run the day the bonus is paid, takes the deposit's own base
 * points and leaves the bonus, because twelve months after the money moved is the same day as the
 * deposit's first anniversary: the batch the anniversary paid is a year younger than the batch the
 * deposit paid, and one sweep separates them. The second, run a month short of the bonus's own
 * twelve months, leaves it alone. The third, run the far side of them, takes it.
 *
 * <p>Between the first and the second, the bonus is the only thing the customer has left to lose, so
 * what they are told expires next is the bonus and the day it goes — two years to the day after the
 * money landed, which is twelve months after the anniversary rather than twelve months after the
 * sweep that paid it. A batch dated at the sweep would go a fortnight later, on a different day, and
 * this assertion would fail.
 *
 * <p>The loyalty sweep is deliberately run once and never again, so that the only bonus in play is
 * the one whose twelve months this test is about; the second anniversary arrives during the last
 * stretch and is left unpaid.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: more than two
 * years pass in it.
 */
class ALoyaltyBonusExpiresTwelveMonthsAfterItsAnniversaryApiTest extends ApiIntegrationTest {

    private static final String THE_LOYALTY_SWEEP = "payLoyaltyBonuses";

    private static final String THE_EXPIRY_SWEEP = "expireOldPoints";

    /**
     * A fortnight past the first anniversary, whichever day of which month the run lands on — so
     * the anniversary has plainly fallen and the deposit's own points are plainly out of time.
     */
    private static final int DAYS_PAST_THE_FIRST_ANNIVERSARY = 379;

    /**
     * A month short of the bonus's own twelve months, which fall twenty-four calendar months after
     * the money landed — 730 or 731 days, so 700 is short of them whichever way the calendar falls.
     */
    private static final int DAYS_SHORT_OF_THE_BONUSES_OWN_YEAR = 700;

    /** And a fortnight past them, with the same room either side of the calendar's variation. */
    private static final int DAYS_PAST_THE_BONUSES_OWN_YEAR = 758;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-bonus-runs-out-too"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_bonus_survives_its_first_twelve_months_and_no_more_and_is_warned_of_before_it_goes() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        LocalDate theMoneyLandedOn = app.theDateTheClockReads();
        DepositView paidIn = app.deposit(savingsAccount, ANKE, "500.00");
        assertThat(paidIn.pointsEarned()).isEqualTo(500);

        // A year on, the anniversary pays a tenth of the euros still sitting there.
        app.daysPass(DAYS_PAST_THE_FIRST_ANNIVERSARY);
        app.runJob(THE_LOYALTY_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(500 + 50);

        // The same night's expiry sweep, run in the order the nightly jobs run in. The deposit's own
        // 500 reached their twelve months on the very day this anniversary fell, so they go; the
        // bonus the anniversary just paid has a year of its own ahead of it, so it stays.
        app.runJob(THE_EXPIRY_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the deposit's own points are twelve months old and the bonus is a day old")
                .isEqualTo(50);

        // Which makes the bonus the only thing left to lose, and the day it goes the promise being
        // made. Two years to the day after the money landed: twelve months to the anniversary, and
        // twelve months from the anniversary. A batch dated at the sweep that paid it would go a
        // fortnight later than this.
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("a bonus is in what the customer is told they stand to lose next")
                .isEqualTo(50);
        assertThat(app.pointsExpiringNextOnOf(ANKE))
                .as("twelve months after the anniversary that paid it, not after the sweep that "
                        + "noticed it")
                .isEqualTo(theMoneyLandedOn.plusYears(2));

        // A month short of that day, a sweep leaves it exactly where it is.
        app.daysPass(DAYS_SHORT_OF_THE_BONUSES_OWN_YEAR - DAYS_PAST_THE_FIRST_ANNIVERSARY);
        app.runJob(THE_EXPIRY_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("eleven months is not twelve, and a sweep inside the bonus's own year takes "
                        + "nothing from it")
                .isEqualTo(50);
        assertThat(app.pointsExpiringNextOnOf(ANKE)).isEqualTo(theMoneyLandedOn.plusYears(2));

        // And the far side of it, the same sweep takes it. The deposit has passed its second
        // anniversary by now and the loyalty sweep is not run again, so the 50 that go are the only
        // bonus this customer has ever been paid and nothing replaces them.
        app.daysPass(DAYS_PAST_THE_BONUSES_OWN_YEAR - DAYS_SHORT_OF_THE_BONUSES_OWN_YEAR);

        app.runJob(THE_EXPIRY_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("a bonus twelve months past its anniversary is gone, like every other batch")
                .isZero();
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("and a customer with nothing left has nothing to lose next")
                .isNull();
        assertThat(app.pointsExpiringNextOnOf(ANKE)).isNull();
    }
}
