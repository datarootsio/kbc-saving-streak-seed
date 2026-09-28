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
 * A customer saving towards two goals gets two different years, and each account's bar carries only
 * what its own deposits have coming.
 *
 * <p>The test the whole resource turns on. Every other figure about points in this application is
 * the customer's and reads the same beside every account they hold — the balance, and what expires
 * next — because a balance repeated per account would claim they held as many pots of points as they
 * hold accounts. A date is not a balance: "these points go on the 14th" stays true however it is
 * grouped, and grouping it by the deposits that earned it is exactly what makes a bar about the pot
 * on the screen rather than about the person.
 *
 * <p>So this asserts both halves at once: each bar is its own account's, and the customer-wide
 * figures beside them are untouched and still say what they always said — which is the larger
 * figure, being every point they hold wherever they earned it.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class EachAccountsBarCarriesOnlyItsOwnDepositsApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithTwoPotsToTellApart() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-bar-per-account"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void two_pots_of_one_customer_have_two_different_years_ahead_of_them() {
        long onePot = app.savingsAccountOf(ANKE);
        long theOtherPot = app.otherSavingsAccountOf(ANKE);

        LocalDate paidInOn = app.theDateTheClockReads();
        assertThat(app.deposit(onePot, ANKE, "40.00").pointsEarned()).isEqualTo(40);
        assertThat(app.deposit(theOtherPot, ANKE, "70.00").pointsEarned()).isEqualTo(70);

        // Each bar carries its own deposit's points going and its own deposit's anniversary, and
        // neither carries the other's — on one afternoon, so the two accounts' markers fall on the
        // same day and only what they are worth can tell them apart.
        assertThat(app.timelineOf(onePot).events())
                .as("the pot that took EUR 40 has EUR 40 of a year ahead of it")
                .containsExactly(
                        new Event(paidInOn.plusYears(1), "POINTS_EXPIRE", 40),
                        new Event(paidInOn.plusYears(1), "LOYALTY_BONUS", 4));
        assertThat(app.timelineOf(theOtherPot).events())
                .as("and the pot that took EUR 70 has its own, with nothing of the first one's on it")
                .containsExactly(
                        new Event(paidInOn.plusYears(1), "POINTS_EXPIRE", 70),
                        new Event(paidInOn.plusYears(1), "LOYALTY_BONUS", 7));

        // And the customer's own figures are what they have always been: their points, wherever they
        // earned them, which is both deposits at once. A bar being an account's is not a claim that
        // the points are.
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the balance is the customer's and is both deposits")
                .isEqualTo(110);
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("and so is what expires next, which is larger than either bar says")
                .isEqualTo(110);
        assertThat(app.pointsExpiringNextOnOf(ANKE)).isEqualTo(paidInOn.plusYears(1));
    }
}
