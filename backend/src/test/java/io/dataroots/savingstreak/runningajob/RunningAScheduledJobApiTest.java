package io.dataroots.savingstreak.runningajob;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.JobRunView;
import io.dataroots.savingstreak.support.ScheduledJobView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A trainer runs a scheduled job now instead of waiting for its schedule to come round, and a job
 * that was never asked for runs anyway because scheduling is switched on.
 *
 * <p>Both halves matter and they are different claims. Scheduling being on is what makes a
 * participant's job run at all — without it the job sits in silence and there is no error to read.
 * Running one by name is what makes a twelve-month rule demonstrable in a coffee break: wind the
 * clock a year on, run the job, look at what it did.
 *
 * <p>Against jobs this test brings with it, because the application deliberately ships none of its
 * own. Those jobs stand in for the one a participant writes during an exercise, and this test asserts
 * on their counters — which is reading the test's own fixture rather than reaching inside the
 * application, since whether a job ran is not a thing any HTTP response of the application's can
 * report.
 *
 * <p>Its own application and its own database, like the clock tests, so that a ticking job and a
 * moved clock stay inside this class rather than turning up in the run's shared one.
 */
class RunningAScheduledJobApiTest extends ApiIntegrationTest {

    /** Long enough that a 200ms job has had many chances, short enough to fail fast when it has none. */
    private static final long LONGER_THAN_A_JOB_NEEDS_MILLIS = 10_000;

    private static ConfigurableApplicationContext application;

    /** Bound to the application this class runs jobs in, not to the shared one. */
    private static TestRestTemplate withJobsHttp;

    @BeforeAll
    static void startAnApplicationWithJobsInIt() {
        // Registered as a source as well as guarded by a profile: the source is what puts these jobs
        // in this application, and the profile is what keeps them out of everybody else's.
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the real
        // database.
        application = new SpringApplicationBuilder(SavingStreakApplication.class, JobsThisTestDefines.class)
                .run("--spring.datasource.url=jdbc:sqlite:"
                                + aDatabaseFileThatDoesNotExistYet("saving-streak-scheduled-jobs"),
                        "--spring.profiles.active=dev," + JobsThisTestDefines.PROFILE,
                        "--server.port=0");
        withJobsHttp = new TestRestTemplate();
        withJobsHttp.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    /**
     * Nobody asked for this one. It runs because scheduling is enabled in the application, which is
     * the whole of the first acceptance criterion: a participant's {@code @Scheduled} method fires.
     */
    @Test
    void a_scheduled_job_runs_without_anybody_asking() throws InterruptedException {
        JobsThisTestDefines.AJobThatRunsOnItsOwn ticking =
                application.getBean(JobsThisTestDefines.AJobThatRunsOnItsOwn.class);
        int alreadyRun = ticking.runs();

        waitUntil(() -> ticking.runs() > alreadyRun);

        assertThat(ticking.runs())
                .as("a job scheduled every 200ms should have run again inside "
                        + LONGER_THAN_A_JOB_NEEDS_MILLIS + "ms; scheduling is off if it has not")
                .isGreaterThan(alreadyRun);
    }

    /** So that nobody has to guess a name, or go reading the source for one. */
    @Test
    void the_jobs_that_can_be_run_are_listed() {
        List<ScheduledJobView> jobs = whatCanBeRun();

        assertThat(jobs).extracting(ScheduledJobView::name)
                .contains("sweepUpTheOldPoints", "tick", "fallOver");
        ScheduledJobView sweep = jobs.stream().filter(job -> job.name().equals("sweepUpTheOldPoints"))
                .findFirst().orElseThrow();
        // Enough to tell one job from another with the same method name, and to see when it would
        // otherwise have run — which is the reason somebody is running it by hand.
        assertThat(sweep.definedBy()).isEqualTo("AJobThatWaitsForTheNewYear");
        assertThat(sweep.schedule()).isEqualTo("cron 0 0 3 1 1 *");
    }

    /**
     * The job whose schedule is a year away, run now. This is the point of the slice: an expiry job
     * is watched during an exercise rather than at three in the morning on New Year's Day.
     */
    @Test
    void a_job_runs_when_it_is_named() {
        JobsThisTestDefines.AJobThatWaitsForTheNewYear sweeper =
                application.getBean(JobsThisTestDefines.AJobThatWaitsForTheNewYear.class);
        int alreadyRun = sweeper.runs();

        ResponseEntity<JobRunView> response = run("sweepUpTheOldPoints");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("sweepUpTheOldPoints");
        assertThat(response.getBody().definedBy()).isEqualTo("AJobThatWaitsForTheNewYear");
        // Exactly once. A job run twice by one request would quietly double whatever it does.
        assertThat(sweeper.runs()).isEqualTo(alreadyRun + 1);
        // And it had finished by the time the answer came back, rather than being promised.
        assertThat(sweeper.lastRunObservedAt()).isNotNull();
    }

    /**
     * Two jobs can have methods of the same name, so every job also answers to the longer name that
     * says which class it is on.
     */
    @Test
    void a_job_can_be_named_by_the_class_it_is_defined_on() {
        JobsThisTestDefines.AJobThatWaitsForTheNewYear sweeper =
                application.getBean(JobsThisTestDefines.AJobThatWaitsForTheNewYear.class);
        int alreadyRun = sweeper.runs();

        ResponseEntity<JobRunView> response = run("AJobThatWaitsForTheNewYear.sweepUpTheOldPoints");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sweeper.runs()).isEqualTo(alreadyRun + 1);
    }

    /**
     * The two halves of the demonstration together: the clock is wound a year on, and the job is told
     * it is running a year on. A job that read the machine's clock would decide a twelve-month rule
     * against today and the demonstration would show nothing.
     */
    @Test
    void a_job_run_after_the_clock_moved_runs_on_the_moved_clock() {
        advanceTheClockBy(400);
        long movedForwardByDays = whereTheClockIs().movedForwardByDays();

        Instant realMomentBefore = Instant.now();
        ResponseEntity<JobRunView> response = run("sweepUpTheOldPoints");
        Instant realMomentAfter = Instant.now();

        assertThat(response.getBody().ranAt())
                .isAfterOrEqualTo(realMomentBefore.plus(movedForwardByDays, ChronoUnit.DAYS).minusMillis(1))
                .isBeforeOrEqualTo(realMomentAfter.plus(movedForwardByDays, ChronoUnit.DAYS));
    }

    /**
     * A name nothing answers to is refused with a sentence saying so, rather than answered as though
     * a job had run — which would have a participant hunting a job that never fired for a bug that is
     * a typo.
     */
    @Test
    void a_job_that_does_not_exist_is_refused_with_a_reason() {
        JobsThisTestDefines.AJobThatWaitsForTheNewYear sweeper =
                application.getBean(JobsThisTestDefines.AJobThatWaitsForTheNewYear.class);
        int alreadyRun = sweeper.runs();

        ResponseEntity<JsonNode> response = tryToRun("expirePointz");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + response.getBody())
                .isTrue();
        String reason = response.getBody().get("detail").asText();
        // The name that was asked for, so the reader can see their own typo, and the names that would
        // have worked, so the next attempt is the right one.
        assertThat(reason).contains("expirePointz").contains("sweepUpTheOldPoints");
        // And nothing ran while it was being refused.
        assertThat(sweeper.runs()).isEqualTo(alreadyRun);
    }

    /**
     * A job that throws is reported as a job that threw, with what it said. A participant's own job
     * failing is the likeliest thing to happen here, and an empty 500 would tell them nothing.
     */
    @Test
    void a_job_that_throws_answers_with_what_it_threw() {
        ResponseEntity<JsonNode> response = tryToRun("fallOver");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("detail").asText())
                .contains("fallOver")
                .contains("this job was written to fall over");
    }

    private static void waitUntil(java.util.function.BooleanSupplier happened) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + LONGER_THAN_A_JOB_NEEDS_MILLIS;
        while (!happened.getAsBoolean() && System.currentTimeMillis() < giveUpAt) {
            Thread.sleep(50);
        }
    }

    private static List<ScheduledJobView> whatCanBeRun() {
        ResponseEntity<List<ScheduledJobView>> response = withJobsHttp.exchange(
                "/api/dev/jobs", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<ScheduledJobView>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static ResponseEntity<JobRunView> run(String name) {
        return withJobsHttp.postForEntity("/api/dev/jobs/{name}/run", null, JobRunView.class, name);
    }

    private static ResponseEntity<JsonNode> tryToRun(String name) {
        return withJobsHttp.postForEntity("/api/dev/jobs/{name}/run", null, JsonNode.class, name);
    }

    private static void advanceTheClockBy(long days) {
        assertThat(withJobsHttp.postForEntity("/api/dev/clock/advance", Map.of("days", days), ClockView.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static ClockView whereTheClockIs() {
        return withJobsHttp.getForEntity("/api/dev/clock", ClockView.class).getBody();
    }
}
