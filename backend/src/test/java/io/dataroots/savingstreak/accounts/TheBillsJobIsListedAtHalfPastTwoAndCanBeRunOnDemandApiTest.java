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
 * The bills job is a scheduled job of the application's own, findable under a name a trainer can
 * type, running at half past two — after the saving rules and before everything else in the night.
 *
 * <p>User stories 11 and 40. The affordance is the same one the income job needs: a monthly bill
 * cannot be reached inside a training day by waiting, so it is reached by winding the clock forward
 * and running the job out of turn, which needs the job to appear in the list, to answer to the name
 * it appears under, and to judge dates against the application's clock rather than the machine's.
 *
 * <p><strong>The half hour is asserted beside the saving rules' hour, in one place, on purpose.</strong>
 * The two expressions are a pair and neither means anything alone: bills at 02:30 is only the feature
 * because the rules are at 02:00, and a later tidy-up that moved either would not make the night
 * neater — it would delete the lesson, because a bill presented before the rules can never fail. A
 * test that asserted only the bills' own expression would keep passing while somebody moved the
 * rules to three. What the ordering actually costs a customer is asserted in money elsewhere.
 */
class TheBillsJobIsListedAtHalfPastTwoAndCanBeRunOnDemandApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "takeBillsDue";
    private static final String THE_SAVING_RULES_JOB = "fireSavingRulesDue";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseClockThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-bills-job"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_bills_job_is_listed_and_runs_after_the_saving_rules_every_night() {
        ScheduledJobView[] jobs = app.whatCanBeRun();

        assertThat(Arrays.stream(jobs).map(ScheduledJobView::name))
                .as("nobody should have to guess the name of the job that takes the bills")
                .contains(THE_JOB);
        assertThat(theJobCalled(jobs, THE_JOB).definedBy())
                .isEqualTo("BillsAreTakenWhenTheyFallDue");
        assertThat(theJobCalled(jobs, THE_JOB).schedule())
                .as("half past two in the morning: after the saving rules have taken their cut, so "
                        + "that the bills meet the balance the rules left behind")
                .isEqualTo("cron 0 30 2 * * *");
        assertThat(theJobCalled(jobs, THE_SAVING_RULES_JOB).schedule())
                .as("and the rules are still at two, which is the other half of the same sentence "
                        + "— move either and a bill can never fail, which is the whole feature")
                .isEqualTo("cron 0 0 2 * * *");
    }

    @Test
    void the_bills_job_judges_due_dates_against_the_clock_the_application_is_running_on() {
        app.daysPass(40);
        Instant theApplicationThinksItIs = app.theClockReads();

        JobRunView ran = app.runJob(THE_JOB);

        assertThat(ran.name()).isEqualTo(THE_JOB);
        assertThat(ran.definedBy()).isEqualTo("BillsAreTakenWhenTheyFallDue");
        assertThat(Duration.between(theApplicationThinksItIs, ran.ranAt()).abs())
                .as("the job ran at the moment the wound-forward clock reads, not at the machine's "
                        + "— a job that read Instant.now() would find nothing to do and say so "
                        + "convincingly")
                .isLessThan(Duration.ofMinutes(5));
    }

    private static ScheduledJobView theJobCalled(ScheduledJobView[] jobs, String name) {
        return Arrays.stream(jobs)
                .filter(listed -> listed.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("this application lists no job called " + name
                        + ", and the night it describes is not the night this test is about"));
    }
}
