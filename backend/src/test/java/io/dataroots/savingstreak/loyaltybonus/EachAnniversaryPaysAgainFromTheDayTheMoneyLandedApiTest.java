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
 * The clock recurs and never resets: a deposit left alone pays again on its second and third
 * anniversaries, each counted from the day the money landed rather than from the last payment.
 *
 * <p>Which is the difference this test is built to catch. The second anniversary is asserted at a
 * point where it has plainly arrived counted from the deposit, and has plainly <em>not</em> arrived
 * counted from the sweep that paid the first — a fortnight of drift is enough to tell the two
 * arrangements apart, and a clock restarted at each payment would pay nothing here.
 *
 * <p>One test, because the clock only goes forward: a second method in this class would find the
 * years already moved on and would be asserting against whatever order the two happened to run in.
 * The narrative is asserted on after every step instead, which is what a recurring clock is.
 *
 * <p>Its own application on a database nothing has ever been written to, because three years of
 * anniversaries need a deposit nothing else has touched and a clock only this class moves — see
 * {@link AnApplicationWithAClockToMove}.
 */
class EachAnniversaryPaysAgainFromTheDayTheMoneyLandedApiTest extends ApiIntegrationTest {

    private static final String THE_SWEEP = "payLoyaltyBonuses";

    /** A fortnight past the first anniversary, whichever day of which month the run lands on. */
    private static final int DAYS_PAST_THE_FIRST_ANNIVERSARY = 379;

    /**
     * Past the second anniversary counted from the day the money landed — twenty-four calendar
     * months is 730 or 731 days — and short of a year on from the sweep that paid the first, which
     * would fall on day 744 at the earliest. A clock restarted at each payment pays nothing on this
     * day; a clock counted from the deposit pays.
     */
    private static final int DAYS_PAST_THE_SECOND_ANNIVERSARY = 733;

    /** Short of the third anniversary, which is 1095 or 1096 days after the money landed. */
    private static final int DAYS_SHORT_OF_THE_THIRD_ANNIVERSARY = 1085;

    /** And past it, with a fortnight's room either side of the calendar's own variation. */
    private static final int DAYS_PAST_THE_THIRD_ANNIVERSARY = 1110;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-each-anniversary-pays-again"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_second_and_third_anniversaries_each_pay_as_much_as_the_first() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        DepositView paidIn = app.deposit(savingsAccount, ANKE, "500.00");
        assertThat(paidIn.pointsEarned()).isEqualTo(500);

        app.daysPass(DAYS_PAST_THE_FIRST_ANNIVERSARY);
        app.runJob(THE_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the first year of leaving the money alone pays a tenth of its euros")
                .isEqualTo(500 + 50);

        app.daysPass(DAYS_PAST_THE_SECOND_ANNIVERSARY - DAYS_PAST_THE_FIRST_ANNIVERSARY);

        app.runJob(THE_SWEEP);

        // The point of the whole arrangement. Two years after the money landed is not yet a year
        // after the payment it earned, so a deposit whose clock had restarted would owe nothing
        // today — and this one owes its second anniversary, worth exactly what the first was.
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the second anniversary is counted from the day the money landed, not from the "
                        + "sweep that paid the first")
                .isEqualTo(500 + 50 + 50);

        app.daysPass(DAYS_SHORT_OF_THE_THIRD_ANNIVERSARY - DAYS_PAST_THE_SECOND_ANNIVERSARY);
        app.runJob(THE_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("a third year not quite served pays nothing, which is what says the clock is a "
                        + "calendar and not a counter of sweeps")
                .isEqualTo(500 + 50 + 50);

        app.daysPass(DAYS_PAST_THE_THIRD_ANNIVERSARY - DAYS_SHORT_OF_THE_THIRD_ANNIVERSARY);

        app.runJob(THE_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("and the third year is worth as much as the first two")
                .isEqualTo(500 + 50 + 50 + 50);
    }
}
