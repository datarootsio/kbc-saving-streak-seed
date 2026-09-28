package io.dataroots.savingstreak.accounts;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The income job is a scheduled job of the application's own, findable under a name a trainer can
 * type, and it runs on demand.
 *
 * <p>User story 39 read against the income half of the feature, and the affordance the whole of it
 * is demonstrated through: a monthly rule cannot be reached inside a training day by waiting, so it
 * is reached by winding the clock forward and running the job out of turn. For that to work the job
 * has to appear in the list, answer to the name it appears under, and judge paydays against the
 * application's clock rather than the machine's.
 *
 * <p>The hour is asserted as well as the name, because it is load-bearing rather than decorative.
 * The night runs points expiry at three, loyalty at half past and notifications at four, and money
 * arriving is the first thing that happens in a night because everything else reads a balance.
 */
class TheIncomeJobIsListedAndCanBeRunOnDemandApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "creditMonthlyIncome";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseClockThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-income-job"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_income_job_is_listed_among_the_jobs_that_can_be_run() {
        ScheduledJobView[] jobs = app.whatCanBeRun();

        assertThat(Arrays.stream(jobs).map(ScheduledJobView::name))
                .as("nobody should have to guess the name of the job that pays an income")
                .contains(THE_JOB);
        ScheduledJobView job = Arrays.stream(jobs)
                .filter(listed -> listed.name().equals(THE_JOB)).findFirst().orElseThrow();
        assertThat(job.definedBy()).isEqualTo("IncomeLandsOnPayday");
        assertThat(job.schedule())
                .as("one in the morning: first in the night, because everything else that runs "
                        + "overnight reads a balance and this is what changes one")
                .isEqualTo("cron 0 0 1 * * *");
    }

    @Test
    void the_income_job_judges_paydays_against_the_clock_the_application_is_running_on() {
        app.daysPass(40);
        Instant theApplicationThinksItIs = app.theClockReads();

        JobRunView ran = app.runJob(THE_JOB);

        assertThat(ran.name()).isEqualTo(THE_JOB);
        assertThat(ran.definedBy()).isEqualTo("IncomeLandsOnPayday");
        assertThat(Duration.between(theApplicationThinksItIs, ran.ranAt()).abs())
                .as("the job ran at the moment the wound-forward clock reads, not at the machine's "
                        + "— a job that read Instant.now() would find nothing to do and say so "
                        + "convincingly")
                .isLessThan(Duration.ofMinutes(5));
    }
}
