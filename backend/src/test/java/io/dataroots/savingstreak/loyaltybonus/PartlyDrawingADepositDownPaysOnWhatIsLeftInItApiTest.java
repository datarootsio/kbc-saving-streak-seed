package io.dataroots.savingstreak.loyaltybonus;

import java.math.BigDecimal;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spending some of the savings costs some of the year's bonus, not the whole of it. A deposit drawn
 * halfway down pays a tenth of the half that is left.
 *
 * <p>The generous half of the forfeit, and the largest judgement call in this feature. The strict
 * reading — any touch at all forfeits the whole year — would let a EUR 1 withdrawal destroy a
 * hundred points on a EUR 1,000 deposit, and would turn drawing the oldest deposit down first into a
 * trap rather than a protection. So what an anniversary pays is a tenth of what is still in the
 * deposit when the anniversary is judged, and half a deposit left alone is half a year's bonus.
 *
 * <p>Beside it, the far end of the same arithmetic: a deposit drawn below ten euros pays nothing,
 * because a tenth of nine euros rounds down to no points in the way EUR 0.99 has always earned no
 * base point. Nothing is taken from the customer for that — the nine euros stay in the account,
 * still theirs, still earning nothing until they are added to.
 *
 * <p>Two accounts of one customer on one clock rather than two test methods, for the reason the
 * rest of this package gives: the clock only goes forward. One balance is then the whole of the
 * arithmetic — and both deposits started at EUR 500, so paying an anniversary on what a deposit
 * started with rather than on what is left in it would pay 50 apiece and put that balance at 1100
 * where this test expects 1025.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class PartlyDrawingADepositDownPaysOnWhatIsLeftInItApiTest extends ApiIntegrationTest {

    private static final String THE_SWEEP = "payLoyaltyBonuses";

    /** A month in, comfortably inside the twelve months, so the money is gone before the clock. */
    private static final int DAYS_WELL_INSIDE_THE_YEAR = 30;

    /** A fortnight past a year: comfortably the far side of the anniversary, and short of the next. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-drawn-down-pays-on-what-is-left"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_deposit_half_drawn_down_pays_half_and_one_drawn_under_ten_euros_pays_nothing() {
        long drawnHalfway = app.savingsAccountOf(ANKE);
        long drawnUnderTen = app.otherSavingsAccountOf(ANKE);

        DepositView half = app.deposit(drawnHalfway, ANKE, "500.00");
        DepositView nearlyAll = app.deposit(drawnUnderTen, ANKE, "500.00");
        assertThat(half.pointsEarned()).isEqualTo(500);
        assertThat(nearlyAll.pointsEarned()).isEqualTo(500);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(1000);

        // A month in, one is halved and the other is taken down to nine euros — one euro short of
        // the ten a single point of bonus needs.
        app.daysPass(DAYS_WELL_INSIDE_THE_YEAR);
        app.withdraw(drawnHalfway, ANKE, "250.00");
        app.withdraw(drawnUnderTen, ANKE, "491.00");
        assertThat(app.balancesOf(drawnHalfway).moneyBalance())
                .as("half of it stayed, which is what its anniversary will be worked out from")
                .isEqualByComparingTo("250.00");
        assertThat(app.balancesOf(drawnUnderTen).moneyBalance())
                .as("nine euros stayed, which is not enough for a point")
                .isEqualByComparingTo("9.00");
        assertThat(app.pointsBalanceOf(ANKE))
                .as("neither withdrawal takes back what the deposits earned when they landed")
                .isEqualTo(1000);

        BigDecimal inTheCurrentAccountBeforeTheSweep = app.currentAccountBalanceOf(ANKE);

        app.daysPass(DAYS_WELL_PAST_A_YEAR - DAYS_WELL_INSIDE_THE_YEAR);

        app.runJob(THE_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("a tenth of the 250 euros still in the halved deposit — not the 50 it would have "
                        + "paid untouched, and not the nothing the strict reading would pay — and "
                        + "nothing at all on nine euros")
                .isEqualTo(1000 + 25);

        // Being worth no bonus costs the customer nothing else. The nine euros are still in the
        // account after the sweep looked at them and declined, and no euros moved anywhere.
        assertThat(app.balancesOf(drawnUnderTen).moneyBalance())
                .as("a deposit the sweep passed over keeps its money; being worth no points is not "
                        + "a reason to take any")
                .isEqualByComparingTo("9.00");
        assertThat(app.balancesOf(drawnHalfway).moneyBalance())
                .as("nor does being paid a bonus move the euros it was worked out from")
                .isEqualByComparingTo("250.00");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("the other end of every movement this application makes, untouched by a sweep "
                        + "that pays in points")
                .isEqualByComparingTo(inTheCurrentAccountBeforeTheSweep);
    }
}
