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
 * A savings account answers with the year it has ahead of it: the window a bar is drawn in, and
 * every dated thing the deposits in that account have coming.
 *
 * <p>The two rules this application is about are both promises about dates, and until this resource
 * neither was legible as one. What expires next was a single figure on a single day; when a deposit
 * next pays was a date on a row, one row at a time, in an order decided by when the money went in
 * rather than by when anything is going to happen.
 *
 * <p>An account nobody has paid into has an empty year, and the window all the same. "Nothing is
 * coming" is an answer, and a different one from an account that could not be read.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: nothing that
 * shares a clock or a database with another test can have days pass in the middle of it.
 */
class ABarSaysWhatAnAccountHasComingApiTest extends ApiIntegrationTest {

    /** Long enough that the two deposits' dates are plainly different, and short of a week's worth. */
    private static final int A_MONTH = 30;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDaysThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-what-an-account-has-coming"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void an_account_answers_with_every_dated_thing_its_deposits_have_coming_in_the_year_ahead() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // An account nobody has paid into has nothing coming — and a window all the same, because a
        // bar with no markers on it is still a bar and "nothing is coming" is something to say.
        TimelineView beforeAnythingWasPaidIn = app.timelineOf(savingsAccount);
        assertThat(beforeAnythingWasPaidIn.events())
                .as("an account never paid into has no dated thing coming")
                .isEmpty();
        assertThat(beforeAnythingWasPaidIn.from())
                .as("the window opens on the day the application's clock reads, not the machine's")
                .isEqualTo(app.theDateTheClockReads());
        assertThat(beforeAnythingWasPaidIn.until())
                .as("and closes twelve months later, which is the span that holds everything")
                .isEqualTo(beforeAnythingWasPaidIn.from().plusYears(1));

        LocalDate fortyWentInOn = app.theDateTheClockReads();
        assertThat(app.deposit(savingsAccount, ANKE, "40.00").pointsEarned()).isEqualTo(40);

        app.daysPass(A_MONTH);
        LocalDate seventyWentInOn = app.theDateTheClockReads();
        assertThat(app.deposit(savingsAccount, ANKE, "70.00").pointsEarned()).isEqualTo(70);

        TimelineView bar = app.timelineOf(savingsAccount);

        // The whole bar, in the order it is read. Each deposit puts two markers on it: the points it
        // earned reaching their twelve months, and the anniversary that pays a tenth of its euros —
        // which fall on the same day, because both rules count twelve months from the day the money
        // landed. Two markers sharing a day come back points-going first, which is the order the
        // night runs in: the expiry sweep is scheduled before the loyalty sweep.
        assertThat(bar.events())
                .as("everything the account's two deposits have coming, in the order the year runs in")
                .containsExactly(
                        new Event(fortyWentInOn.plusYears(1), "POINTS_EXPIRE", 40),
                        new Event(fortyWentInOn.plusYears(1), "LOYALTY_BONUS", 4),
                        new Event(seventyWentInOn.plusYears(1), "POINTS_EXPIRE", 70),
                        new Event(seventyWentInOn.plusYears(1), "LOYALTY_BONUS", 7));

        // The window moved with the clock, and the later deposit's markers sit on its closing day
        // rather than past it — which is the property the twelve months are chosen for: nothing a
        // surviving batch or a deposit still holding money has coming falls outside the year ahead.
        assertThat(bar.from()).isEqualTo(seventyWentInOn);
        assertThat(bar.until()).isEqualTo(seventyWentInOn.plusYears(1));
        assertThat(bar.events())
                .allSatisfy(event -> assertThat(event.on()).isBetween(bar.from(), bar.until()));
    }
}
