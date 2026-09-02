package io.dataroots.savingstreak.movingtheclock;

import java.util.Map;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An application started without the development profile has no way to move time in it.
 *
 * <p>The clock is a lab affordance, and the seed repo is read by participants as an example of how to
 * build things: an application that shipped a time machine and merely hid it would be teaching that.
 * So the endpoints are not built at all outside development, and there is nothing behind the paths.
 *
 * <p>Its own application, started with no profile active, because that is the only way to ask the
 * question — every other test in this run boots the development profile deliberately.
 */
class TheClockCannotBeMovedOutsideDevelopmentApiTest extends ApiIntegrationTest {

    private static ConfigurableApplicationContext application;

    /** Bound to the application without the development profile, not to the shared one. */
    private static TestRestTemplate withoutDevelopmentHttp;

    @BeforeAll
    static void startAnApplicationWithoutTheDevelopmentProfile() {
        // No --spring.profiles.active at all, which is what a deployment that never heard of the
        // development profile looks like. Command-line arguments rather than default properties, for
        // the reason the walking skeleton gives.
        application = new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:"
                                + aDatabaseFileThatDoesNotExistYet("saving-streak-no-dev-profile"),
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
    void there_is_nowhere_to_ask_where_the_clock_is_standing() {
        assertThat(withoutDevelopmentHttp.getForEntity("/api/dev/clock", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void there_is_nowhere_to_move_the_clock_forward() {
        ResponseEntity<JsonNode> response = withoutDevelopmentHttp.postForEntity(
                "/api/dev/clock/advance", Map.of("days", 365), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * And the two above are absent because the profile is, rather than because this test is asking
     * for paths that were never there. The same two requests against the run's own application —
     * which does boot the development profile — find something at both.
     */
    @Test
    void the_same_paths_are_there_when_the_development_profile_is() {
        assertThat(http.getForEntity("/api/dev/clock", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // Refused rather than honoured, because nought days is not a move — but refused by the clock,
        // which is a thing that had to be there to refuse it. Nothing about the run's shared clock
        // moves.
        assertThat(http.postForEntity("/api/dev/clock/advance", Map.of("days", 0), JsonNode.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** The application still works without the profile; it is the clock that is missing, not the app. */
    @Test
    void the_application_still_serves_the_rest_of_its_api() {
        assertThat(withoutDevelopmentHttp.getForEntity("/api/customers", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
