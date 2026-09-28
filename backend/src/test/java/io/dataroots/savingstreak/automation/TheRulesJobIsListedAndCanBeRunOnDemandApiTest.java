package io.dataroots.savingstreak.automation;

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
 * The rules job is a scheduled job of the application's own, findable under a name a trainer can
 * type, and it runs on demand.
 *
 * <p>User story 39, and the affordance the whole feature is demonstrated through: a monthly rule
 * cannot be reached inside a training day by waiting, so it is reached by winding the clock forward
 * and running the job out of turn. For that to work the job has to appear in the list, answer to the
 * name it appears under, and judge occurrences against the application's clock rather than the
 * machine's.
 *
 * <p>The hour is asserted as well as the name, because it is load-bearing rather than decorative.
 * Income lands at one, the rules run at two so that a sweep sees the money it is supposed to sweep,
 * points expire at three and the notifications sweep reads a settled night at four. A job moved to
 * any other hour breaks one of those readings, and the list is where that is visible.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class TheRulesJobIsListedAndCanBeRunOnDemandApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String THE_INCOME_JOB = "creditMonthlyIncome";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseClockThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-rules-job"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_rules_job_is_listed_among_the_jobs_that_can_be_run_and_runs_after_the_income_job() {
        ScheduledJobView[] jobs = app.whatCanBeRun();

        assertThat(Arrays.stream(jobs).map(ScheduledJobView::name))
                .as("nobody should have to guess the name of the job that fires the saving rules")
                .contains(THE_JOB);
        ScheduledJobView job = Arrays.stream(jobs)
                .filter(listed -> listed.name().equals(THE_JOB)).findFirst().orElseThrow();
        assertThat(job.definedBy()).isEqualTo("SavingRulesRunNightly");
        assertThat(job.schedule())
                .as("two in the morning: after the income has landed, so a sweep sees the money it "
                        + "is supposed to sweep, and before the points expire at three")
                .isEqualTo("cron 0 0 2 * * *");
        assertThat(Arrays.stream(jobs)
                .filter(listed -> listed.name().equals(THE_INCOME_JOB)).findFirst().orElseThrow()
                .schedule())
                .as("and the income job is the hour before it, which is the whole reason these two "
                        + "hours are written down rather than chosen")
                .isEqualTo("cron 0 0 1 * * *");
    }

    @Test
    void the_rules_job_judges_occurrences_against_the_clock_the_application_is_running_on() {
        app.daysPass(40);
        Instant theApplicationThinksItIs = app.theClockReads();

        JobRunView ran = app.runJob(THE_JOB);

        assertThat(ran.name()).isEqualTo(THE_JOB);
        assertThat(ran.definedBy()).isEqualTo("SavingRulesRunNightly");
        assertThat(Duration.between(theApplicationThinksItIs, ran.ranAt()).abs())
                .as("the job ran at the moment the wound-forward clock reads, not at the machine's "
                        + "— a job that read Instant.now() would find nothing due and say so "
                        + "convincingly")
                .isLessThan(Duration.ofMinutes(5));
    }
}
