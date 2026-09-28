package io.dataroots.savingstreak.notifications;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * How many days before an anniversary it is worth saying so comes from the scheme in force on the
 * night the sweep runs.
 *
 * <p>A deposit made today has its anniversary a year off, which is a long way outside the thirty-day
 * window this application is seeded with: the sweep says nothing about it, and the line it writes
 * says the anniversary is further off than the notice period tonight's scheme publishes. The bank
 * then publishes a notice period longer than a year, the Monday comes, and the very same deposit is
 * announced — the same anniversary, on the same day, worth the same points, said because the window
 * reaches it now.
 *
 * <p><strong>Longer rather than shorter, because it is the only direction a test can watch in an
 * afternoon.</strong> Shortening the window is demonstrated by the same code from the other side and
 * would need a year of clock winding to reach a deposit whose anniversary is near at all. What the
 * two share is the figure: the window is a count of days the scheme publishes, and the rule measures
 * against whatever it is handed.
 *
 * <p>The sweep is run again afterwards, because the uniqueness the database keeps over announced
 * anniversaries — the deposit, the reason and the day, in Java and in its own index — is not a thing
 * a published figure may touch.
 *
 * <p>An application of its own, because both the clock and a published version of the scheme move
 * one way only.
 */
class AChangeToTheNoticeBeforeAnAnniversaryIsUsedByTheNextSweepApiTest extends ApiIntegrationTest {

    /**
     * Enough that a tenth of it is worth whole points, which is the other condition an anniversary
     * has to meet before it is worth saying anything about.
     */
    private static final String A_DEPOSIT_WORTH_SAYING_SOMETHING_ABOUT = "100.00";

    /**
     * A window longer than the year a deposit's first anniversary is off, so that the deposit this
     * test has just made falls inside it the moment the Monday comes.
     */
    private static final String A_NOTICE_PERIOD_LONGER_THAN_A_YEAR = "400";

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseSchemeThisTestMayPublishVersionsOf() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-a-published-notice-before-an-anniversary"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void an_anniversary_a_year_off_is_announced_once_the_published_window_reaches_it() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        app.deposit(savingsAccount, ANKE, A_DEPOSIT_WORTH_SAYING_SOMETHING_ABOUT);

        sweep.runs();

        assertThat(sweep.whatWasSaidAboutAnAnniversaryIn(savingsAccount, ANKE))
                .as("an anniversary a year off is further off than the thirty days the scheme is "
                        + "seeded at")
                .isEmpty();

        LocalDate theMondayTheWindowWidens = app.theNextMondayStillToCome();
        Map<String, Object> aLongerWindow = ASchemeSomebodyAdministers.theSameSchemeAgain(
                app.theSchemeInForce(), theMondayTheWindowWidens,
                "A deposit's anniversary is worth saying something about more than a year ahead, "
                        + "so that nobody is surprised by the money that has been sitting still.");
        aLongerWindow.put("daysBeforeAnAnniversaryIsWorthSaying", A_NOTICE_PERIOD_LONGER_THAN_A_YEAR);
        app.publishAVersionOfTheScheme(aLongerWindow);
        app.theClockReaches(theMondayTheWindowWidens);

        sweep.runs();

        List<RaisedNotification> afterTheWindowWidened =
                sweep.whatWasSaidAboutAnAnniversaryIn(savingsAccount, ANKE);
        assertThat(afterTheWindowWidened)
                .as("the same anniversary, unmoved, is inside the window the bank published on "
                        + "Monday — and the deposit is the only one holding money, so it is the one "
                        + "the next withdrawal would empty first")
                .singleElement()
                .satisfies(said -> {
                    assertThat(said.reason()).isEqualTo(NotificationReason.LOYALTY_BONUS_AT_RISK);
                    assertThat(said.depositId())
                            .as("about one deposit, because an anniversary belongs to one")
                            .isNotNull();
                    assertThat(said.occursOn())
                            .as("the day Loyalty says it falls on, which nothing here works out "
                                    + "again")
                            .isAfter(app.theDateTheClockReads());
                    assertThat(said.points())
                            .as("and what it is worth, which is the other condition it had to meet")
                            .isPositive();
                });

        sweep.runs();

        assertThat(sweep.whatWasSaidAboutAnAnniversaryIn(savingsAccount, ANKE))
                .as("one occasion, one notification — the record's own index over the deposit, the "
                        + "reason and the day is unaffected by a window that moved")
                .isEqualTo(afterTheWindowWidened);
    }
}
