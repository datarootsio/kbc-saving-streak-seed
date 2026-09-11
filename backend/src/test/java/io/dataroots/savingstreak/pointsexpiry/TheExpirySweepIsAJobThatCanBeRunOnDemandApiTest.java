package io.dataroots.savingstreak.pointsexpiry;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

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
 * The sweep is a scheduled job of the application's own, and a trainer can run it now.
 *
 * <p>Which is the point of the whole arrangement. A rule measured in twelve months cannot be reached
 * inside a training day by waiting, so it is reached by winding the clock forward and running the job
 * out of turn — and for that to work the job has to be findable by name and has to judge anniversaries
 * against the application's clock rather than the machine's. A job that read {@code Instant.now()}
 * would find nothing to do on a clock wound a year forward and would say so convincingly.
 *
 * <p>Until this feature the application shipped no jobs of its own and the list was empty; this is the
 * first entry in it.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class TheExpirySweepIsAJobThatCanBeRunOnDemandApiTest extends ApiIntegrationTest {

    private static final String THE_SWEEP = "expireOldPoints";

    /** Comfortably the far side of any anniversary the clock could land near. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-sweep-is-a-job"));
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

        // Enough to find it and to see when it would otherwise have run, which is the reason
        // somebody is looking at this list at all.
        assertThat(Arrays.stream(jobs).map(ScheduledJobView::name))
                .as("the application ships this job of its own, so the list is no longer empty")
                .contains(THE_SWEEP);
        ScheduledJobView sweep = Arrays.stream(jobs)
                .filter(job -> job.name().equals(THE_SWEEP)).findFirst().orElseThrow();
        assertThat(sweep.definedBy()).isEqualTo("OldPointsExpireNightly");
        assertThat(sweep.schedule())
                .as("nightly, so a batch is retired within a day of its anniversary")
                .isEqualTo("cron 0 0 3 * * *");
    }

    @Test
    void the_sweep_judges_anniversaries_against_the_clock_the_application_is_running_on() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        long before = app.pointsBalanceOf(ANKE);
        app.deposit(savingsAccount, ANKE, "18.00");
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(before + 18);

        app.daysPass(DAYS_WELL_PAST_A_YEAR);
        Instant theApplicationThinksItIs = app.theClockReads();

        JobRunView ran = app.runJob(THE_SWEEP);

        // The answer came back after the job had finished rather than promising it would, and the
        // moment it reports is the one the application's clock reads — a year on from the machine's.
        assertThat(ran.name()).isEqualTo(THE_SWEEP);
        assertThat(ran.definedBy()).isEqualTo("OldPointsExpireNightly");
        assertThat(Duration.between(theApplicationThinksItIs, ran.ranAt()).abs())
                .as("the job ran at the moment the wound-forward clock reads, not at the machine's")
                .isLessThan(Duration.ofMinutes(5));

        // And it actually acted on that moment: a batch eighteen points and a year old is gone.
        assertThat(app.pointsBalanceOf(ANKE))
                .as("winding the clock a year on and running the job is how a twelve-month rule is "
                        + "demonstrated in an afternoon")
                .isZero();
    }
}
