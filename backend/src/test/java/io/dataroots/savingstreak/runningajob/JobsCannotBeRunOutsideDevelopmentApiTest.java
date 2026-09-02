package io.dataroots.savingstreak.runningajob;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An application started without the development profile has no way to run a job out of turn in it —
 * and its jobs still run on their own schedule, because that half is the application rather than the
 * lab.
 *
 * <p>The distinction is the whole of this class. Scheduling is a feature and ships everywhere; making
 * a job fire because somebody asked is a demonstration aid, and the seed repo is read by participants
 * as an example of how to build things, so an application that shipped that and merely hid it would
 * be teaching that. The endpoints are not built at all outside development.
 *
 * <p>Its own application, started with no profile beyond the one that brings this run's test jobs,
 * because that is the only way to ask the question — every other test in this run boots the
 * development profile deliberately.
 */
class JobsCannotBeRunOutsideDevelopmentApiTest extends ApiIntegrationTest {

    private static final long LONGER_THAN_A_JOB_NEEDS_MILLIS = 10_000;

    private static ConfigurableApplicationContext application;

    /** Bound to the application without the development profile, not to the shared one. */
    private static TestRestTemplate withoutDevelopmentHttp;

    @BeforeAll
    static void startAnApplicationWithoutTheDevelopmentProfile() {
        // The test jobs are here, and the development profile is not, which is a deployment with
        // scheduled work in it and no time machine — the arrangement this class is about.
        application = new SpringApplicationBuilder(SavingStreakApplication.class, JobsThisTestDefines.class)
                .run("--spring.datasource.url=jdbc:sqlite:"
                                + aDatabaseFileThatDoesNotExistYet("saving-streak-jobs-no-dev-profile"),
                        "--spring.profiles.active=" + JobsThisTestDefines.PROFILE,
                        "--server.port=0");
        withoutDevelopmentHttp = new TestRestTemplate();
        withoutDevelopmentHttp.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    @Test
    void there_is_nowhere_to_ask_which_jobs_there_are() {
        assertThat(withoutDevelopmentHttp.getForEntity("/api/dev/jobs", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void there_is_nowhere_to_run_a_job_on_demand() {
        assertThat(withoutDevelopmentHttp
                .postForEntity("/api/dev/jobs/{name}/run", null, JsonNode.class, "sweepUpTheOldPoints")
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** The jobs themselves are untouched by the profile: they still run when their schedule says. */
    @Test
    void a_scheduled_job_still_runs_on_its_own_schedule() throws InterruptedException {
        JobsThisTestDefines.AJobThatRunsOnItsOwn ticking =
                application.getBean(JobsThisTestDefines.AJobThatRunsOnItsOwn.class);
        int alreadyRun = ticking.runs();

        long giveUpAt = System.currentTimeMillis() + LONGER_THAN_A_JOB_NEEDS_MILLIS;
        while (ticking.runs() <= alreadyRun && System.currentTimeMillis() < giveUpAt) {
            Thread.sleep(50);
        }

        assertThat(ticking.runs())
                .as("scheduling is a feature and ships in every profile; a job scheduled every 200ms"
                        + " should have run again inside " + LONGER_THAN_A_JOB_NEEDS_MILLIS + "ms")
                .isGreaterThan(alreadyRun);
    }

    /**
     * And the two absent endpoints are absent because the profile is, rather than because this test is
     * asking for paths that were never there. The same two requests against the run's own application
     * — which does boot the development profile — find something at both.
     */
    @Test
    void the_same_paths_are_there_when_the_development_profile_is() {
        // Empty, because the application ships no scheduled jobs of its own: expiry and the loyalty
        // bonus are exercises. The list being there and empty is the answer, not a missing feature.
        assertThat(http.getForEntity("/api/dev/jobs", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/api/dev/jobs", JsonNode.class).getBody()).isEmpty();
        // Refused rather than honoured, because nothing in the shipped application answers to that
        // name — but refused by something that had to be there to refuse it.
        assertThat(http.postForEntity("/api/dev/jobs/{name}/run", null, JsonNode.class, "expirePoints")
                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
