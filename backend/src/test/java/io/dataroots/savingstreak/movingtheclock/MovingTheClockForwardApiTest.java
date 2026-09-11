package io.dataroots.savingstreak.movingtheclock;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

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
import static io.dataroots.savingstreak.support.TheMovedClock.earliestReadingOf;
import static io.dataroots.savingstreak.support.TheMovedClock.latestReadingOf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A trainer moves the application's clock forward, and everything the application dates afterwards
 * moves with it.
 *
 * <p>This is the point of the whole slice: a rule measured in months — points expiring twelve months
 * on, a loyalty bonus vesting on an anniversary — is reachable during a coffee break rather than
 * during a calendar year. Asserted over HTTP, the way a trainer would do it, and against what a
 * deposit made afterwards actually recorded rather than against the endpoint's own answer about
 * itself.
 *
 * <p>Its own application and its own database, like the fixed-clock test. The rest of the run shares
 * one file, and a run whose clock had been wound a year on halfway through would date some of its
 * records in the future and leave the next reader wondering which tests had done it.
 *
 * <p>Every test here reads where the clock is standing before it moves it and asserts on the
 * difference. Moving the clock is the one thing in this application a test cannot undo, so no test
 * may depend on the order it ran in.
 */
class MovingTheClockForwardApiTest extends ApiIntegrationTest {

    /** Past a twelve-month rule, which is the shortest thing anybody would move the clock to reach. */
    private static final long A_YEAR_AND_A_BIT = 400;

    private static ConfigurableApplicationContext application;

    /** Bound to the application this class moves the clock on, not to the shared one. */
    private static TestRestTemplate movableHttp;

    private static SeededAccounts seeded;

    @BeforeAll
    static void startAnApplicationWhoseClockCanBeMoved() {
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the
        // real database.
        application = new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:"
                                + aDatabaseFileThatDoesNotExistYet("saving-streak-movable-clock"),
                        "--spring.profiles.active=dev",
                        "--server.port=0");
        movableHttp = new TestRestTemplate();
        movableHttp.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        seeded = new SeededAccounts(movableHttp);
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    /**
     * The move is reported back in the terms it was asked in, and the clock is standing where the
     * report says.
     */
    @Test
    void the_clock_moves_forward_by_the_days_it_was_asked_to_move() {
        long standingAt = whereTheClockIs().movedForwardByDays();

        Instant realMomentBefore = Instant.now();
        ResponseEntity<ClockView> response = advanceBy(30);
        Instant realMomentAfter = Instant.now();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().movedForwardByDays()).isEqualTo(standingAt + 30);
        // And it really is a clock that far on, not only a figure reported about one.
        assertThat(response.getBody().now())
                .isAfterOrEqualTo(earliestReadingOf(realMomentBefore, realMomentAfter, standingAt + 30))
                .isBeforeOrEqualTo(latestReadingOf(realMomentBefore, realMomentAfter, standingAt + 30));
    }

    /**
     * What the whole slice exists for. The deposit is dated off the moved clock, so the twelve-month
     * rules a participant writes against a deposit's age are reachable in a minute.
     */
    @Test
    void a_deposit_made_after_a_move_records_a_moment_that_far_ahead() {
        advanceBy(A_YEAR_AND_A_BIT);
        long movedForwardByDays = whereTheClockIs().movedForwardByDays();

        Instant realMomentBefore = Instant.now();
        DepositView made = deposit(seeded.savingsAccountOf(ANKE), "5.00");
        Instant realMomentAfter = Instant.now();

        assertThat(made.depositedAt())
                // A moment is kept to the millisecond, so it can sit up to a millisecond below the
                // real moment this test read just before making it.
                .isAfterOrEqualTo(earliestReadingOf(realMomentBefore, realMomentAfter, movedForwardByDays)
                        .minusMillis(1))
                .isBeforeOrEqualTo(latestReadingOf(realMomentBefore, realMomentAfter, movedForwardByDays));
        // Which is a moment no machine in this run is at: the point of moving the clock is that the
        // records land somewhere the calendar has not reached. A day short of the move and counted
        // as a fixed span, so that this bound holds whichever side of a clock change the calendar
        // move came out on — the assertion above is the one that says where the clock is precisely.
        assertThat(made.depositedAt())
                .isAfter(realMomentAfter.plus(Duration.ofDays(A_YEAR_AND_A_BIT - 1)));
    }

    /**
     * Two moves are one journey. Somebody who advanced a year and then wants another month asks for
     * the month, not for thirteen months.
     */
    @Test
    void moves_add_up_and_can_be_read_back() {
        long standingAt = whereTheClockIs().movedForwardByDays();

        advanceBy(10);
        advanceBy(5);

        assertThat(whereTheClockIs().movedForwardByDays()).isEqualTo(standingAt + 15);
    }

    /**
     * Asking where the clock is does not move it. It is the question somebody halfway through an
     * exercise asks precisely because they are unsure, and an answer that changed the thing being
     * asked about would be the worst possible one.
     */
    @Test
    void asking_where_the_clock_is_leaves_it_where_it_is() {
        ClockView asked = whereTheClockIs();

        ClockView askedAgain = whereTheClockIs();

        assertThat(askedAgain.movedForwardByDays()).isEqualTo(asked.movedForwardByDays());
        // Still running at the speed of the real clock, rather than stopped where it was moved to.
        assertThat(askedAgain.now()).isAfterOrEqualTo(asked.now());
    }

    /**
     * Backwards is refused rather than allowed. Records already written carry moments off this clock,
     * and winding it back would date the next ones before them.
     */
    @Test
    void the_clock_cannot_be_moved_backwards() {
        long standingAt = whereTheClockIs().movedForwardByDays();

        ResponseEntity<JsonNode> response = tryToAdvanceBy(-30);

        assertRefusedWithAReason(response);
        assertTheClockIsStillAt(standingAt);
    }

    /** Nought days is not a move, and answering as though it were would say nothing happened twice. */
    @Test
    void the_clock_cannot_be_moved_by_no_days_at_all() {
        long standingAt = whereTheClockIs().movedForwardByDays();

        ResponseEntity<JsonNode> response = tryToAdvanceBy(0);

        assertRefusedWithAReason(response);
        assertTheClockIsStillAt(standingAt);
    }

    /**
     * Further than the clock goes. A figure like this is a typo rather than a demonstration, and the
     * refusal says where the clock actually is so the person can see what they did.
     */
    @Test
    void the_clock_cannot_be_moved_absurdly_far() {
        long standingAt = whereTheClockIs().movedForwardByDays();

        ResponseEntity<JsonNode> response = tryToAdvanceBy(Long.MAX_VALUE);

        assertRefusedWithAReason(response);
        assertTheClockIsStillAt(standingAt);
    }

    /** A request that names no number of days at all is refused rather than read as some default. */
    @Test
    void a_move_that_names_no_days_is_refused() {
        long standingAt = whereTheClockIs().movedForwardByDays();

        ResponseEntity<JsonNode> response = movableHttp.postForEntity(
                "/api/dev/clock/advance", Map.of(), JsonNode.class);

        assertRefusedWithAReason(response);
        assertTheClockIsStillAt(standingAt);
    }

    /**
     * Every refusal answers the way every other refusal in this application does: a problem document
     * with the reason in "detail", which is the field the frontend renders unchanged.
     */
    private static void assertRefusedWithAReason(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + response.getBody())
                .isTrue();
        assertThat(response.getBody().get("detail").asText()).isNotBlank();
    }

    private static void assertTheClockIsStillAt(long movedForwardByDays) {
        assertThat(whereTheClockIs().movedForwardByDays())
                .as("where the clock is standing after a refused move")
                .isEqualTo(movedForwardByDays);
    }

    private static ClockView whereTheClockIs() {
        ResponseEntity<ClockView> response = movableHttp.getForEntity("/api/dev/clock", ClockView.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static ResponseEntity<ClockView> advanceBy(long days) {
        ResponseEntity<ClockView> response = movableHttp.postForEntity(
                "/api/dev/clock/advance", Map.of("days", days), ClockView.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private static ResponseEntity<JsonNode> tryToAdvanceBy(long days) {
        return movableHttp.postForEntity(
                "/api/dev/clock/advance", Map.of("days", days), JsonNode.class);
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
