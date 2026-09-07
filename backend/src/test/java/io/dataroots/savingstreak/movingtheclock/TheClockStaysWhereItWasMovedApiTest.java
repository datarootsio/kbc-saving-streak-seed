package io.dataroots.savingstreak.movingtheclock;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.TheMovedClock.daysOnFrom;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An application stopped halfway through an exercise comes back up where it was in time.
 *
 * <p>A participant advances the clock a year to watch the job they just wrote fire, then restarts the
 * application to pick up their next change. If that returned them to day zero, every record they had
 * dated a year out would be in a future the application no longer believed in, and the exercise would
 * have to be started again.
 *
 * <p>Asserted by genuinely stopping the application and starting another one against the same file,
 * the way the walking skeleton asserts what survives a restart. Its own database, because the file
 * the rest of the run shares is not one to leave wound forward.
 */
class TheClockStaysWhereItWasMovedApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-clock-restart");

    /** A year and a fortnight, in two moves, so the restart has a total to remember rather than a step. */
    private static final long DAYS_MOVED_BEFORE_THE_RESTART = 365 + 14;

    private static ConfigurableApplicationContext application;

    /** Bound to the application that reopened the file, not to the shared one. */
    private static TestRestTemplate reopenedHttp;

    /** What the application that was stopped last reported about where it stood. */
    private static ClockView asItStoodBeforeTheRestart;

    @BeforeAll
    static void moveTheClockThenStopAndStartTheApplication() {
        try (ConfigurableApplicationContext beforeTheRestart = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = boundTo(beforeTheRestart);
            advanceBy(http, 365);
            advanceBy(http, 14);
            asItStoodBeforeTheRestart = whereTheClockIs(http);
        }
        application = startAnApplicationAgainstTheFile();
        reopenedHttp = boundTo(application);
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    /** The figure the participant was told before the restart is the figure they are told after it. */
    @Test
    void the_reopened_application_is_standing_where_the_stopped_one_was() {
        assertThat(asItStoodBeforeTheRestart.movedForwardByDays()).isEqualTo(DAYS_MOVED_BEFORE_THE_RESTART);
        assertThat(whereTheClockIs(reopenedHttp).movedForwardByDays())
                .isEqualTo(DAYS_MOVED_BEFORE_THE_RESTART);
    }

    /**
     * And the clock itself came back moved, rather than only the figure reported about it. A deposit
     * is what a participant checks it with, so it is what this checks it with.
     */
    @Test
    void a_deposit_made_after_the_restart_is_still_dated_a_year_on() {
        SeededAccounts seeded = new SeededAccounts(reopenedHttp);

        Instant realMomentBefore = Instant.now();
        DepositView made = deposit(reopenedHttp, seeded.savingsAccountOf(ANKE),
                seeded.currentAccountOf(ANKE), "5.00");
        Instant realMomentAfter = Instant.now();

        assertThat(made.depositedAt())
                // A moment is kept to the millisecond, so it can sit up to a millisecond below the
                // real moment this test read just before making it.
                .isAfterOrEqualTo(
                        daysOnFrom(realMomentBefore, DAYS_MOVED_BEFORE_THE_RESTART).minusMillis(1))
                .isBeforeOrEqualTo(daysOnFrom(realMomentAfter, DAYS_MOVED_BEFORE_THE_RESTART));
    }

    private static ConfigurableApplicationContext startAnApplicationAgainstTheFile() {
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the
        // real database.
        return new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + DATABASE,
                        "--spring.profiles.active=dev",
                        "--server.port=0");
    }

    private static TestRestTemplate boundTo(ConfigurableApplicationContext application) {
        TestRestTemplate http = new TestRestTemplate();
        http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        return http;
    }

    private static ClockView whereTheClockIs(TestRestTemplate http) {
        ResponseEntity<ClockView> response = http.getForEntity("/api/dev/clock", ClockView.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static ClockView advanceBy(TestRestTemplate http, long days) {
        ResponseEntity<ClockView> response = http.postForEntity(
                "/api/dev/clock/advance", Map.of("days", days), ClockView.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static DepositView deposit(TestRestTemplate http, long savingsAccountId,
                                       long fromCurrentAccountId, String amount) {
        ResponseEntity<DepositView> response = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", fromCurrentAccountId),
                DepositView.class,
                savingsAccountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
