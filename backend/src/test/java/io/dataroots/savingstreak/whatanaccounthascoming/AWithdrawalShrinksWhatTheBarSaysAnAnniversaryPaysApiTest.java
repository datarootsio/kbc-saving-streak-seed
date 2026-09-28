package io.dataroots.savingstreak.whatanaccounthascoming;

import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.TimelineView;
import io.dataroots.savingstreak.support.TimelineView.Event;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Taking money back out shrinks what the bar says the anniversary pays, and leaves the points
 * already earned exactly where they were.
 *
 * <p>The two markers a deposit puts on a bar look alike and behave nothing alike, and this is the
 * test that says so. What an anniversary pays is worked out from the euros still in the deposit, so
 * a withdrawal is visible on it immediately — that is the forfeit, reported before it is suffered
 * rather than explained afterwards. What the deposit earned when it landed is already in the
 * customer's pot and is not clawed back by anything: the day those points go, and how many go on it,
 * do not move when the money does.
 *
 * <p>So the bar answers the question a customer asks before withdrawing — what would this cost me —
 * and answers it in the one place where the cost and the thing it is a cost against are side by side.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class AWithdrawalShrinksWhatTheBarSaysAnAnniversaryPaysApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationToWithdrawFrom() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-withdrawal-on-the-bar"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_anniversary_marker_falls_with_the_money_and_the_expiry_marker_does_not() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        LocalDate paidInOn = app.theDateTheClockReads();
        assertThat(app.deposit(savingsAccount, ANKE, "100.00").pointsEarned()).isEqualTo(100);

        assertThat(app.timelineOf(savingsAccount).events())
                .as("a hundred euros left alone are worth ten on their anniversary")
                .containsExactly(
                        new Event(paidInOn.plusYears(1), "POINTS_EXPIRE", 100),
                        new Event(paidInOn.plusYears(1), "LOYALTY_BONUS", 10));

        app.withdraw(savingsAccount, ANKE, "60.00");

        TimelineView afterTakingSomeBack = app.timelineOf(savingsAccount);

        // Forty euros are left in the deposit, so its anniversary is now worth four — the cost of
        // the withdrawal, on the screen, the moment it is made.
        assertThat(afterTakingSomeBack.pointsArrivingOn(paidInOn.plusYears(1)))
                .as("a tenth of what is still in the deposit, which is what the anniversary pays")
                .isEqualTo(4);

        // And the hundred points it earned when it landed are untouched. Points are not taken back
        // when money is: they were earned, they are in the pot, and their twelve months run from the
        // day they were earned whatever became of the euros.
        assertThat(afterTakingSomeBack.pointsGoingOn(paidInOn.plusYears(1)))
                .as("the points already earned neither leave nor move when the money does")
                .isEqualTo(100);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(100);
    }
}
