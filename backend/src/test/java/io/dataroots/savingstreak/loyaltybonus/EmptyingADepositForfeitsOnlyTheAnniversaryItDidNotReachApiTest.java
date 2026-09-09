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
 * Taking the money out costs the customer the coming year's bonus and nothing else. An anniversary
 * the money did not reach pays nothing; an anniversary it did reach was paid, and is theirs.
 *
 * <p>The forfeit and its limit in one narrative, because they are one rule read from both ends.
 * Two identical EUR 500 deposits land on the same afternoon into two of the same customer's savings
 * accounts, and the only difference between them is when the money left:
 *
 * <ul>
 *   <li>the first is emptied a month in, well inside its twelve months, and pays nothing on the
 *       anniversary it never reached — that is the forfeit;
 *   <li>the second is left alone, is paid on its first anniversary, and is emptied the moment
 *       afterwards. The 50 points it was paid are not taken back, and its second anniversary — an
 *       anniversary its money did not reach — pays nothing.
 * </ul>
 *
 * <p>Two accounts rather than two test methods, for the reason the rest of this package gives: the
 * clock only goes forward, so a second method would find the year already moved on. Two accounts of
 * one customer rather than two customers, because the points are the customer's and one balance is
 * then the whole of the arithmetic: a sweep that wrongly paid the emptied deposit would show up as
 * 100 where the test expects 50.
 *
 * <p>Nothing here needed a rule of its own to be written. What an anniversary pays is worked out
 * from what is still in the deposit at that moment, so a deposit drawn down to nothing is worth
 * nothing on the anniversaries that follow, and a payment already made is a row nobody rewrites.
 * This test exists so that ceasing to be true would fail something.
 *
 * <p>Its own application and its own database, for the reason {@link AnApplicationWithAClockToMove}
 * gives and then some: this test winds the clock past two anniversaries.
 */
class EmptyingADepositForfeitsOnlyTheAnniversaryItDidNotReachApiTest extends ApiIntegrationTest {

    /** The job by the name a trainer types into the development jobs endpoint. */
    private static final String THE_LOYALTY_SWEEP = "payLoyaltyBonuses";

    /** The sweep that ends points twelve months old, by the same name a trainer types. */
    private static final String THE_EXPIRY_SWEEP = "expireOldPoints";

    /** A month in: comfortably inside the twelve months, so what the money did not do is stay. */
    private static final int DAYS_WELL_INSIDE_THE_YEAR = 30;

    /**
     * A fortnight past a year. Comfortably the far side of the first anniversary whatever day of
     * whatever month the run happens on, and comfortably short of the second.
     */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    /** A fortnight past two years, on the same reasoning, so the second anniversary has arrived. */
    private static final int DAYS_WELL_PAST_TWO_YEARS = 744;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-emptying-forfeits-one-anniversary"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void an_anniversary_the_money_did_not_reach_pays_nothing_and_one_it_did_stands() {
        long leftAlone = app.savingsAccountOf(ANKE);
        long emptiedEarly = app.otherSavingsAccountOf(ANKE);

        // Two deposits alike in everything but what happens to them next, so that the only thing
        // this test can be measuring is when the money left.
        LocalDate paidInOn = app.theDateTheClockReads();
        DepositView stays = app.deposit(leftAlone, ANKE, "500.00");
        DepositView goes = app.deposit(emptiedEarly, ANKE, "500.00");
        assertThat(stays.pointsEarned()).isEqualTo(500);
        assertThat(goes.pointsEarned()).isEqualTo(500);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(1000);

        // One of them is emptied a month in, which is the forfeit being set up: this money will not
        // be there when its anniversary is judged.
        app.daysPass(DAYS_WELL_INSIDE_THE_YEAR);
        app.withdraw(emptiedEarly, ANKE, "500.00");
        assertThat(app.balancesOf(emptiedEarly).moneyBalance())
                .as("nothing is left in it, and nothing ever comes back into a deposit")
                .isEqualByComparingTo("0.00");
        assertThat(app.pointsBalanceOf(ANKE))
                .as("what a deposit earned on the day it landed is not taken back by a withdrawal")
                .isEqualTo(1000);

        // The far side of both deposits' first anniversary. One of them still holds its money.
        app.daysPass(DAYS_WELL_PAST_A_YEAR - DAYS_WELL_INSIDE_THE_YEAR);

        app.runJob(THE_LOYALTY_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("a tenth of the 500 euros that stayed, and nothing at all for the 500 that left "
                        + "— two deposits paid would be 100 here, not 50")
                .isEqualTo(1000 + 50);

        // And now the second one is emptied too, the day after being paid. This is the half of the
        // rule that says a bonus already paid is the customer's: the money going does not unmake the
        // year it stayed for.
        app.withdraw(leftAlone, ANKE, "500.00");
        assertThat(app.balancesOf(leftAlone).moneyBalance()).isEqualByComparingTo("0.00");
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the withdrawal takes back none of the 50 the anniversary paid")
                .isEqualTo(1000 + 50);

        // Nor does a sweep run after the withdrawal reconsider an anniversary it has already paid.
        app.runJob(THE_LOYALTY_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("a sweep run after the money left neither pays again nor claws anything back")
                .isEqualTo(1000 + 50);

        // What that past anniversary was worked out from is still there to be read, after the
        // deposit it was worked out from has been emptied. The batch it credited is dated at the
        // anniversary itself, so ending the two year-old batches the deposits earned on the day they
        // landed leaves exactly the bonus behind, running to twelve months after the anniversary
        // that paid it — 50 points, which is a tenth of the 500 euros that were in the deposit then
        // and are not in it now.
        app.runJob(THE_EXPIRY_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the points the deposits earned when they landed have reached twelve months; the "
                        + "bonus was earned a year later and has not")
                .isEqualTo(50);
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("the bonus the first anniversary paid, still the figure it was paid at")
                .isEqualTo(50);
        assertThat(app.pointsExpiringNextOnOf(ANKE))
                .as("twelve months after the anniversary that paid it, which is two years after the "
                        + "money landed — so the moment it was earned at is unchanged by the "
                        + "withdrawal")
                .isEqualTo(paidInOn.plusYears(2));

        // The second anniversary of a deposit holding nothing. This is the forfeit for a second
        // time, and the last word on how far it reaches: the coming year, never a year already
        // served.
        app.daysPass(DAYS_WELL_PAST_TWO_YEARS - DAYS_WELL_PAST_A_YEAR);

        app.runJob(THE_LOYALTY_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("neither emptied deposit pays a second anniversary, and the first anniversary's "
                        + "50 are still the customer's")
                .isEqualTo(50);
    }
}
