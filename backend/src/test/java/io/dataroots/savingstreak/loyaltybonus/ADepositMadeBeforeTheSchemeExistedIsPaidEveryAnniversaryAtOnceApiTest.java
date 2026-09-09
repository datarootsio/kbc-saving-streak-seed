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
 * A deposit whose anniversaries have all passed unpaid is paid every one of them in a single sweep,
 * each dated at the anniversary it is for.
 *
 * <p>Which is two promises at once. It is how a customer who paid in before this scheme existed is
 * not penalised for having saved early — their money has served its years and is owed for all of
 * them. It is also what makes the recurring clock demonstrable rather than merely described: a
 * trainer winds the clock three years forward, runs the job once, and watches three bonuses arrive.
 *
 * <p>That each batch is dated at its own anniversary is asserted through the rule that cares about
 * dates. Two of these three anniversaries are more than twelve months in the past by the time they
 * are paid, so the batches they credited are already beyond their own twelve months — and the next
 * night's expiry sweep takes exactly those two and leaves the third. A sweep that had dated all
 * three at the moment it ran would leave all three standing, and the balance below would be 150
 * rather than 50.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: three years
 * pass in it.
 */
class ADepositMadeBeforeTheSchemeExistedIsPaidEveryAnniversaryAtOnceApiTest extends ApiIntegrationTest {

    private static final String THE_LOYALTY_SWEEP = "payLoyaltyBonuses";

    private static final String THE_EXPIRY_SWEEP = "expireOldPoints";

    /**
     * Past the third anniversary — thirty-six calendar months is 1095 or 1096 days — and well short
     * of the fourth, which is 1460 days out.
     */
    private static final int DAYS_PAST_THE_THIRD_ANNIVERSARY = 1110;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-every-anniversary-at-once"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void three_years_of_anniversaries_are_paid_by_one_sweep_and_each_is_dated_at_its_own() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        DepositView paidIn = app.deposit(savingsAccount, ANKE, "500.00");
        assertThat(paidIn.pointsEarned()).isEqualTo(500);

        // Three years in which nothing was swept at all, which is the state a database written
        // before this scheme existed is in.
        app.daysPass(DAYS_PAST_THE_THIRD_ANNIVERSARY);

        app.runJob(THE_LOYALTY_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("every anniversary the money has already served is paid, in one pass")
                .isEqualTo(500 + 50 + 50 + 50);

        // The next night's expiry sweep, which is the rule that can see what each batch is dated at.
        app.runJob(THE_EXPIRY_SWEEP);

        // The original 500 went twelve months after the money moved, and the first two bonuses went
        // twelve months after the anniversaries that paid them. Only the third anniversary's 50 is
        // still inside its year, because only the third anniversary fell inside the last twelve
        // months. Both rules holding at once, and neither of them misbehaving.
        assertThat(app.pointsBalanceOf(ANKE))
                .as("each batch was dated at its own anniversary, so the two older ones have "
                        + "already run out and the newest has not")
                .isEqualTo(50);
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("and what the customer stands to lose next is that third anniversary's bonus")
                .isEqualTo(50);
    }
}
