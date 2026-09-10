package io.dataroots.savingstreak.notifications;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.JobRunView;
import io.dataroots.savingstreak.support.ScheduledJobView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sweep is a scheduled job of the application's own, findable by name, and a trainer can run it
 * now.
 *
 * <p>Which is how this feature is demonstrated at all. A trainer winds the clock forward and runs
 * one named job; for that to work the job has to appear in the list of jobs that can be run, has to
 * answer to the name it appears under, and has to raise everything against the application's clock
 * rather than the machine's. A sweep that read {@code Instant.now()} would stamp tonight's date on a
 * notification raised in a year the application thinks it is living in.
 *
 * <p>The hour it is scheduled at is asserted because it is load-bearing. Points expire at three and
 * loyalty pays at half past, so four means this sweep reads state the night has already settled.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: a year passes
 * in it.
 */
class TheNotificationSweepIsAJobThatCanBeRunOnDemandApiTest extends ApiIntegrationTest {

    /** Comfortably a year on, so that the moment the sweep stamps is unmistakably the clock's. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    /** Enough to be worth announcing. */
    private static final String ENOUGH_TO_REACH_A_RUNG = "500.00";

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-notification-sweep-is-a-job"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_sweep_is_listed_among_the_jobs_that_can_be_run() {
        ScheduledJobView[] jobs = app.whatCanBeRun();

        assertThat(Arrays.stream(jobs).map(ScheduledJobView::name))
                .as("nobody should have to read the source to find what to type")
                .contains(TheNotificationSweep.THE_JOB);
        ScheduledJobView theSweep = Arrays.stream(jobs)
                .filter(job -> job.name().equals(TheNotificationSweep.THE_JOB))
                .findFirst()
                .orElseThrow();
        assertThat(theSweep.definedBy()).isEqualTo("NotificationsAreRaisedNightly");
        assertThat(theSweep.schedule())
                .as("four in the morning: an hour after points expire and half an hour after "
                        + "loyalty pays, so the sweep reads state the night has settled")
                .isEqualTo("cron 0 0 4 * * *");
    }

    @Test
    void the_sweep_raises_notifications_against_the_clock_the_application_is_running_on() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        app.deposit(savingsAccount, ANKE, ENOUGH_TO_REACH_A_RUNG);

        app.daysPass(DAYS_WELL_PAST_A_YEAR);
        Instant theApplicationThinksItIs = app.theClockReads();

        JobRunView ran = app.runJob(TheNotificationSweep.THE_JOB);

        assertThat(ran.name()).isEqualTo(TheNotificationSweep.THE_JOB);
        assertThat(ran.definedBy()).isEqualTo("NotificationsAreRaisedNightly");
        assertThat(Duration.between(theApplicationThinksItIs, ran.ranAt()).abs())
                .as("the job ran at the moment the wound-forward clock reads")
                .isLessThan(Duration.ofMinutes(5));

        List<RaisedNotification> said = sweep.whatWasSaidAbout(savingsAccount, ANKE);
        assertThat(said)
                .as("and it acted on that moment rather than reporting that it had: both of the "
                        + "sweep's rules had something to say about this account, one about the "
                        + "rung the balance landed on and one about the deposit's anniversary — "
                        + "which the wound clock has carried the application past and which no "
                        + "loyalty sweep has been run to settle, so it is outstanding and the "
                        + "euros behind it are exposed right now")
                .hasSize(2);
        assertThat(said).map(RaisedNotification::reason)
                .containsExactlyInAnyOrder(
                        NotificationReason.BALANCE_THRESHOLD_REACHED,
                        NotificationReason.LOYALTY_BONUS_AT_RISK);
        assertThat(said).allSatisfy(raised ->
                assertThat(Duration.between(theApplicationThinksItIs, raised.raisedAt()).abs())
                        .as("the moment stamped on the notification is the application's, not the "
                                + "machine's")
                        .isLessThan(Duration.ofMinutes(5)));
    }
}
