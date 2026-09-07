package io.dataroots.savingstreak.clock;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.clock.TheClockTheseTestsMove.ARealClockATestStands;
import io.dataroots.savingstreak.streaks.SavingsWeek;
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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A restart in the week real time crossed a clock change comes back up where the stopped application
 * was, and not an hour behind it.
 *
 * <p>The clock's promise is that it never turns round, and a restart is the other way of asking. What
 * survives one is a record of how far the clock was moved, and the trap is that the days in it are
 * not a position on their own: seven days are 169 hours in the week the clocks go back and 168 either
 * side of it. An application that put the days back and worked the span out afresh against the real
 * moment it happened to start at would come up an hour behind the one that went down — deposits made
 * after the restart dated before ones made before it, and a trainer who had advanced into the new
 * week returned to the week they had just left. So the span is written down with the days and put
 * back as it was written.
 *
 * <p>Asserted at the moments that make the two answers differ, which real time reaches for one hour a
 * year: the clock is moved on the Tuesday before the last Sunday in October 2026, real time then
 * passes over that Sunday's fall-back while the application is up, and the application is restarted a
 * second later. Both applications stand on a real clock this test places — see
 * {@link TheClockTheseTestsMove}, whose {@code stands-at} property is how real time is already
 * standing there when the second one puts the clock back, which happens before any test code could
 * reach in. Their own database, because the file the rest of the run shares is not one to leave wound
 * forward.
 */
class ARestartDoesNotWindTheMovedClockBackApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-clock-restart-clock-change");

    /** Seven days, asked for the way a trainer asks for the next week. */
    private static final long A_WEEK = 7;

    /**
     * Tuesday 2026-10-20 23:30 in Brussels, where the clock is moved from: five days before the
     * Sunday the clocks go back, so the seven calendar days asked for below span that Sunday and come
     * to 169 hours rather than 168.
     */
    private static final Instant REAL_TIME_BEFORE_THE_CLOCKS_WENT_BACK =
            ZonedDateTime.of(2026, 10, 20, 23, 30, 0, 0, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                    .toInstant();

    /**
     * Sunday 2026-10-25 23:30 in Brussels, where real time has got to by the restart — past 03:00
     * that morning, which became 02:00. Seven calendar days on from here is 168 hours, which is the
     * shorter span a restart must not adopt.
     */
    private static final Instant REAL_TIME_AFTER_THEY_DID =
            ZonedDateTime.of(2026, 10, 25, 23, 30, 0, 0, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                    .toInstant();

    /**
     * Where a clock 169 hours ahead reads at that moment: Monday 2026-11-02 00:30 in Brussels, half
     * an hour into the week the trainer advanced in order to reach. The 168-hour answer reads
     * 23:30 on the Sunday — an hour earlier, and the week before.
     */
    private static final Instant WHERE_THE_MOVED_CLOCK_STANDS =
            ZonedDateTime.of(2026, 11, 2, 0, 30, 0, 0, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                    .toInstant();

    /** How long the restart takes in real terms, which is what the reading should differ by. */
    private static final Duration A_RESTART = Duration.ofSeconds(1);

    private static ConfigurableApplicationContext application;

    /** Bound to the application that reopened the file, not to the shared one. */
    private static TestRestTemplate reopenedHttp;

    private static Instant readingBeforeTheRestart;

    private static DepositView depositBeforeTheRestart;

    private static long savingsAccount;

    @BeforeAll
    static void moveTheClockLetRealTimeCrossTheClockChangeThenRestart() {
        try (ConfigurableApplicationContext beforeTheRestart =
                     startAnApplicationWithRealTimeAt(REAL_TIME_BEFORE_THE_CLOCKS_WENT_BACK)) {
            TestRestTemplate http = boundTo(beforeTheRestart);
            SeededAccounts seeded = new SeededAccounts(http);
            savingsAccount = seeded.savingsAccountOf(ANKE);

            ResponseEntity<ClockView> moved = http.postForEntity(
                    "/api/dev/clock/advance", Map.of("days", A_WEEK), ClockView.class);
            assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Real time crosses the fall-back with the application up, which is what leaves the span
            // it is using and the span its days would come to now an hour apart.
            beforeTheRestart.getBean(ARealClockATestStands.class).standAt(REAL_TIME_AFTER_THEY_DID);
            readingBeforeTheRestart = whereTheClockIs(http).now();
            depositBeforeTheRestart = deposit(http, seeded, "1.00");
        }
        application = startAnApplicationWithRealTimeAt(REAL_TIME_AFTER_THEY_DID.plus(A_RESTART));
        reopenedHttp = boundTo(application);
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    /** The reading moves on by the second the restart took, rather than back by the lost hour. */
    @Test
    void the_reopened_application_reads_where_the_stopped_one_did_plus_the_restart() {
        assertThat(readingBeforeTheRestart)
                .as("the clock the stopped application was reading, 169 hours ahead of real time")
                .isEqualTo(WHERE_THE_MOVED_CLOCK_STANDS);

        ClockView reopened = whereTheClockIs(reopenedHttp);

        assertThat(reopened.movedForwardByDays()).isEqualTo(A_WEEK);
        assertThat(reopened.now()).isEqualTo(WHERE_THE_MOVED_CLOCK_STANDS.plus(A_RESTART));
    }

    /**
     * And it is the clock that came back, not only the figure reported about it. A deposit is what a
     * participant would notice this with: two of them either side of the restart, in the order they
     * were made.
     */
    @Test
    void a_deposit_made_after_the_restart_is_dated_after_one_made_before_it() {
        DepositView afterTheRestart =
                deposit(reopenedHttp, new SeededAccounts(reopenedHttp), "2.00");

        assertThat(afterTheRestart.depositedAt())
                .isEqualTo(depositBeforeTheRestart.depositedAt().plus(A_RESTART));
        // The damage a rewind does: the listing is newest first, so a deposit stamped an hour before
        // the one it followed comes back on the wrong side of it.
        assertThat(depositsInto(savingsAccount))
                .extracting(DepositView::id)
                .containsExactly(afterTheRestart.id(), depositBeforeTheRestart.id());
    }

    private static ConfigurableApplicationContext startAnApplicationWithRealTimeAt(Instant realTime) {
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the
        // real database.
        return new SpringApplicationBuilder(
                SavingStreakApplication.class, TheClockTheseTestsMove.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + DATABASE,
                        "--spring.profiles.active=dev",
                        "--" + TheClockTheseTestsMove.WHERE_REAL_TIME_STANDS + "=" + realTime,
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

    private static DepositView[] depositsInto(long savingsAccountId) {
        return reopenedHttp.getForObject(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
    }

    private static DepositView deposit(TestRestTemplate http, SeededAccounts seeded, String amount) {
        ResponseEntity<DepositView> response = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", seeded.currentAccountOf(ANKE)),
                DepositView.class,
                seeded.savingsAccountOf(ANKE));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
