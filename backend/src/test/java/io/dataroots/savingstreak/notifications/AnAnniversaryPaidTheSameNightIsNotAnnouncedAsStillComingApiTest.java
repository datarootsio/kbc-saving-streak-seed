package io.dataroots.savingstreak.notifications;

import java.time.Period;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An anniversary the loyalty sweep has already paid is never announced as still coming.
 *
 * <p>User story 21, and what the hour on the nightly sweep is for. Points expire at three, loyalty
 * pays at half past, and this sweep runs at four — so by the time it looks, an anniversary that
 * arrived last night has been paid and moved on, and what the deposit next pays is a year away. A
 * sweep that ran first would announce a bonus that was about to be credited an hour later, which is
 * a warning about money nobody is going to lose.
 *
 * <p>Run in that order rather than left to the cron strings, because a test that only read the two
 * cron expressions would be asserting that two constants are half an hour apart rather than that
 * running them in that order produces the silence. The two jobs are run by the names a trainer
 * types.
 *
 * <p>The control is a second pot, whose deposit landed a few weeks later and whose anniversary is
 * still weeks off — so the same run of the same sweep announces one anniversary and stays silent
 * about the other. Without it a sweep that had thrown, or a window that reached nothing that night,
 * would satisfy the assertion.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test
 * winds the clock past a year.
 */
class AnAnniversaryPaidTheSameNightIsNotAnnouncedAsStillComingApiTest extends ApiIntegrationTest {

    /** A tenth of these euros is 40 points, which is what the arrived anniversary pays. */
    private static final String WHAT_EACH_DEPOSIT_HOLDS = "400.00";

    /**
     * Twenty days between the two deposits, so that when the first one's anniversary has just
     * arrived the second one's is twenty days out — inside the window, and the run's control.
     */
    private static final int DAYS_BETWEEN_THE_TWO_DEPOSITS = 20;

    /** A day past the older deposit's first anniversary, so the sweep that pays it has work to do. */
    private static final int DAYS_UNTIL_THE_FIRST_ANNIVERSARY_HAS_ARRIVED = 366;

    private static final String THE_SWEEP_THAT_PAYS = "payLoyaltyBonuses";

    /**
     * The anniversary window version 1 of the scheme publishes, written out rather than read off the
     * rule: the rule holds no constant any more, because the bank publishes the figure and the sweep
     * is handed the one in force on the night it runs. Thirty days is what this application is
     * seeded at, and this test is running under the seeded scheme.
     */
    private static final Period THE_WINDOW_THE_SCHEME_PUBLISHES = Period.ofDays(30);

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-paid-the-same-night"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_anniversary_loyalty_settled_an_hour_earlier_is_not_announced_as_coming() {
        long paidLastNight = app.savingsAccountOf(ANKE);
        app.deposit(paidLastNight, ANKE, WHAT_EACH_DEPOSIT_HOLDS);
        app.daysPass(DAYS_BETWEEN_THE_TWO_DEPOSITS);
        long stillWeeksOff = app.otherSavingsAccountOf(ANKE);
        app.deposit(stillWeeksOff, ANKE, WHAT_EACH_DEPOSIT_HOLDS);
        app.daysPass(
                DAYS_UNTIL_THE_FIRST_ANNIVERSARY_HAS_ARRIVED - DAYS_BETWEEN_THE_TWO_DEPOSITS);

        long beforeTheNight = app.pointsBalanceOf(ANKE);

        // Half past three: loyalty pays what has arrived.
        app.runJob(THE_SWEEP_THAT_PAYS);
        assertThat(app.pointsBalanceOf(ANKE) - beforeTheNight)
                .as("the anniversary this test is about genuinely arrived and was genuinely paid, "
                        + "so the silence below is the settled state and not an anniversary that "
                        + "never came")
                .isEqualTo(40);

        // Four o'clock: the notifications sweep reads what the night has settled.
        sweep.runs();

        assertThat(sweep.whatWasSaidAboutAnAnniversaryIn(paidLastNight, ANKE))
                .as("the bonus has been paid and the deposit's next anniversary is a year away, so "
                        + "there is nothing coming to warn anybody about")
                .isEmpty();
        assertThat(app.depositsInto(paidLastNight)[0].nextAnniversaryOn())
                .as("what it next pays is the anniversary a year on, which is why the window "
                        + "reaches nothing")
                .isAfter(app.theDateTheClockReads()
                        .plus(THE_WINDOW_THE_SCHEME_PUBLISHES));

        assertThat(sweep.whatWasSaidAboutAnAnniversaryIn(stillWeeksOff, ANKE))
                .as("the same run announced the pot whose anniversary the night did not settle, so "
                        + "the silence above is this rule and not a sweep that said nothing")
                .hasSize(1)
                .allSatisfy(raised -> assertThat(raised.reason())
                        .isEqualTo(NotificationReason.LOYALTY_BONUS_AT_RISK));
    }
}
