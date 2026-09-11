package io.dataroots.savingstreak.clock;

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
 * A minute of real time moves the wound-forward clock a minute on, in the one hour of the year where
 * a calendar day worked out afresh on every reading would move it backwards.
 *
 * <p>The clock's promise is that it never turns round: records already written carry moments off it,
 * and a reading that fell back would date the next ones before them. Counting the days through the
 * calendar on every reading breaks that promise once a year, because a local time landing in the
 * hour a spring clock change skips resolves forward to the far side of the gap and then waits there
 * while real time catches up — so a reading taken a minute later can be up to fifty-nine minutes
 * earlier. The clock therefore reads the calendar once, when it is moved, and adds the span it
 * worked out from then on.
 *
 * <p>Asserted the way the moving-the-clock tests assert everything else: over the endpoint a trainer
 * uses, and against what two deposits actually recorded rather than against the clock's own answer
 * about itself. Out of order, those two come back in an order they did not happen in, which is the
 * damage the promise exists to prevent.
 *
 * <p>Its own application, its own database, and a real clock this test stands where it needs it —
 * see {@link TheClockTheseTestsMove}. One test, because moving the clock cannot be undone.
 */
class TheMovedClockNeverReadsBackwardsApiTest extends ApiIntegrationTest {

    /** Seven days, asked for the way a trainer asks for the next week. */
    private static final int A_WEEK = 7;

    /**
     * A real moment whose local time, seven days on, is one the clocks skip: 02:59 on Sunday
     * 2027-03-21 in Brussels, seven days before the Sunday the clocks go forward at 02:00. Local
     * 02:59 does not exist on 2027-03-28, so a calendar day counted from a minute later lands an
     * hour earlier than one counted from here.
     */
    private static final Instant THE_REAL_MOMENT_A_CALENDAR_WEEK_ON_FALLS_IN_THE_SKIPPED_HOUR =
            ZonedDateTime.of(2027, 3, 21, 2, 59, 0, 0, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                    .toInstant();

    private static final Duration A_MINUTE = Duration.ofMinutes(1);

    private static ConfigurableApplicationContext application;

    /** Bound to the application this class moves the clock on, not to the shared one. */
    private static TestRestTemplate movableHttp;

    private static ARealClockATestStands realClock;

    private static SeededAccounts seeded;

    @BeforeAll
    static void startAnApplicationOnAClockThisTestStands() {
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the
        // real database.
        application = new SpringApplicationBuilder(
                SavingStreakApplication.class, TheClockTheseTestsMove.class)
                .run("--spring.datasource.url=jdbc:sqlite:"
                                + aDatabaseFileThatDoesNotExistYet("saving-streak-clock-forwards"),
                        "--spring.profiles.active=dev",
                        "--server.port=0");
        movableHttp = new TestRestTemplate();
        movableHttp.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        realClock = application.getBean(ARealClockATestStands.class);
        seeded = new SeededAccounts(movableHttp);
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    @Test
    void a_minute_of_real_time_dates_the_next_deposit_a_minute_later_and_not_an_hour_earlier() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        realClock.standAt(THE_REAL_MOMENT_A_CALENDAR_WEEK_ON_FALLS_IN_THE_SKIPPED_HOUR);
        ResponseEntity<ClockView> moved = advanceBy(A_WEEK);
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        Instant readingWhenItWasMoved = moved.getBody().now();

        DepositView first = deposit(savingsAccount, "1.00");
        realClock.letThisMuchTimePass(A_MINUTE);
        DepositView second = deposit(savingsAccount, "2.00");

        assertThat(second.depositedAt()).isEqualTo(first.depositedAt().plus(A_MINUTE));
        assertThat(whereTheClockIs().now()).isEqualTo(readingWhenItWasMoved.plus(A_MINUTE));
        // The damage the promise prevents: the listing is newest first, so a deposit stamped an hour
        // before the one it followed comes back on the wrong side of it.
        assertThat(depositsInto(savingsAccount))
                .extracting(DepositView::id)
                .containsExactly(second.id(), first.id());
    }

    private static ResponseEntity<ClockView> advanceBy(long days) {
        return movableHttp.postForEntity(
                "/api/dev/clock/advance", Map.of("days", days), ClockView.class);
    }

    private static ClockView whereTheClockIs() {
        return movableHttp.getForObject("/api/dev/clock", ClockView.class);
    }

    private static DepositView[] depositsInto(long savingsAccountId) {
        return movableHttp.getForObject(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
    }

    private static DepositView deposit(long savingsAccountId, String amount) {
        ResponseEntity<DepositView> response = movableHttp.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", seeded.currentAccountOf(ANKE)),
                DepositView.class,
                savingsAccountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
