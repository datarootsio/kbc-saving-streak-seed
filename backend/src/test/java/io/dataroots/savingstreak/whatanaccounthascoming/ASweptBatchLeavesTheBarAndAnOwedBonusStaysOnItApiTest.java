package io.dataroots.savingstreak.whatanaccounthascoming;

import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.TimelineView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the two nightly sweeps do is visible on the bar: points the expiry sweep took are off it, and
 * a bonus that is owed stays on it, dated the day it was promised on.
 *
 * <p>A date on this bar that has already gone is not a mistake. An anniversary falls at the moment
 * the money landed and a batch's twelve months are up at the moment it was earned, while the sweeps
 * that act on them run at three and half past three in the morning. Between the promise falling and
 * the sweep keeping it, the application owes a payment — and the bar says so by leaving the marker
 * on the day it was promised on rather than skipping forward a year and showing a customer nothing
 * owed beside a date twelve months out.
 *
 * <p>Then the sweep runs and the bar moves on: the bonus is paid, its deposit's marker jumps to the
 * anniversary after it, and the points that bonus is made of appear as a departure of their own —
 * because a loyalty bonus is an ordinary batch with twelve months of its own, dated at the
 * anniversary that paid it.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives, and this class
 * is the sharpest case of it: it winds the clock more than a year forward.
 */
class ASweptBatchLeavesTheBarAndAnOwedBonusStaysOnItApiTest extends ApiIntegrationTest {

    /** Comfortably the far side of any anniversary the clock could land near. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-sweeps-on-the-bar"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_expiry_sweep_takes_a_marker_off_the_bar_and_the_loyalty_sweep_moves_one_along() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        LocalDate paidInOn = app.theDateTheClockReads();
        assertThat(app.deposit(savingsAccount, ANKE, "40.00").pointsEarned()).isEqualTo(40);
        LocalDate theAnniversary = paidInOn.plusYears(1);

        app.daysPass(DAYS_WELL_PAST_A_YEAR);

        // A fortnight past both promises and before either sweep has run: the points are still there
        // and still spendable, the bonus is still owed, and the bar carries both on the day they
        // were promised on — which is now behind the window it opens.
        TimelineView owedBoth = app.timelineOf(savingsAccount);
        assertThat(theAnniversary)
                .as("the promised day is behind the window, which is what being owed looks like")
                .isBefore(owedBoth.from());
        assertThat(owedBoth.pointsGoingOn(theAnniversary))
                .as("the points have not gone until the sweep goes and takes them")
                .isEqualTo(40);
        assertThat(owedBoth.pointsArrivingOn(theAnniversary))
                .as("and the anniversary that fell a fortnight ago is still owed four points")
                .isEqualTo(4);

        app.runJob("expireOldPoints");

        TimelineView afterTheExpirySweep = app.timelineOf(savingsAccount);
        assertThat(afterTheExpirySweep.pointsGoingOn(theAnniversary))
                .as("the sweep took them, so nothing goes on that day any more")
                .isNull();
        assertThat(afterTheExpirySweep.pointsArrivingOn(theAnniversary))
                .as("the bonus is a different rule and is still owed: money that stayed still stayed")
                .isEqualTo(4);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(0);

        app.runJob("payLoyaltyBonuses");

        TimelineView afterTheLoyaltySweep = app.timelineOf(savingsAccount);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the anniversary paid a tenth of the deposit's forty euros")
                .isEqualTo(4);
        assertThat(afterTheLoyaltySweep.pointsArrivingOn(theAnniversary))
                .as("the day that was owed is settled, so it is no longer a day anything arrives on")
                .isNull();

        // The deposit still holds its money, so it has a second anniversary coming — a year after
        // the first, which is inside this window because the window moved with the clock.
        assertThat(afterTheLoyaltySweep.pointsArrivingOn(theAnniversary.plusYears(1)))
                .as("and the anniversary after it is the next thing the deposit pays")
                .isEqualTo(4);

        // And the bonus just paid is an ordinary batch dated at the anniversary that paid it, so it
        // goes twelve months after that day — which is the same day the deposit next pays on.
        assertThat(afterTheLoyaltySweep.pointsGoingOn(theAnniversary.plusYears(1)))
                .as("a loyalty bonus runs its own twelve months from the anniversary that paid it")
                .isEqualTo(4);
    }
}
