package io.dataroots.savingstreak.movingtheclock;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opening a database whose record of the clock says something the clock would never have written.
 *
 * <p>Only the development endpoint writes that figure, and it refuses anything but a move forward
 * within range — so a database saying the clock was moved backwards is one somebody edited by hand,
 * which on a training laptop is a thing that happens. Read back without asking, it would start the
 * application behind the real moment, and every rule about the age of a record would be quietly
 * wrong for the rest of the session. It is refused instead, and the application comes up at the real
 * moment.
 *
 * <p>The bad record is made rather than checked in: an application is started, the clock is moved,
 * the application is stopped, and the figure it wrote is then overwritten in the file directly. Its
 * own database, like every test in this package, because the file the rest of the run shares is not
 * one to leave wound forward.
 *
 * <p>Nothing here moves the clock. Both tests below say where this application is standing in
 * absolute terms, which is only an answer if no test in the class has moved it since it came up —
 * that the clock still moves after a refused record is asserted where every other move is, in
 * {@link MovingTheClockForwardApiTest}.
 */
class AClockRecordNobodyWroteApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-clock-edited");

    /** Backwards, which is the direction the endpoint exists to refuse. */
    private static final long WHAT_THE_EDITED_RECORD_SAYS = -400;

    private static ConfigurableApplicationContext application;

    /** Bound to the application that reopened the edited file, not to the shared one. */
    private static TestRestTemplate reopenedHttp;

    @BeforeAll
    static void moveTheClockThenEditWhatItWroteAndStartAgain() {
        try (ConfigurableApplicationContext beforeTheEdit = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = boundTo(beforeTheEdit);
            ResponseEntity<ClockView> moved = http.postForEntity(
                    "/api/dev/clock/advance", Map.of("days", 10), ClockView.class);
            assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        overwriteWhatTheClockWroteWith(WHAT_THE_EDITED_RECORD_SAYS);
        application = startAnApplicationAgainstTheFile();
        reopenedHttp = boundTo(application);
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    /** The application says where it really is, rather than where the edited record claims. */
    @Test
    void a_record_that_says_the_clock_went_backwards_is_not_put_back() {
        assertThat(whereTheClockIs().movedForwardByDays()).isZero();
    }

    /** And it is the clock that stayed put, not only the figure reported about it. */
    @Test
    void a_deposit_made_afterwards_is_dated_at_the_real_moment() {
        SeededAccounts seeded = new SeededAccounts(reopenedHttp);

        Instant realMomentBefore = Instant.now();
        DepositView made = deposit(seeded.savingsAccountOf(ANKE), seeded.currentAccountOf(ANKE), "5.00");
        Instant realMomentAfter = Instant.now();

        assertThat(made.depositedAt())
                // A moment is kept to the millisecond, so it can sit up to a millisecond below the
                // real moment this test read just before making it.
                .isAfterOrEqualTo(realMomentBefore.minusMillis(1))
                .isBeforeOrEqualTo(realMomentAfter);
    }

    /**
     * Writes a figure into the clock's record that the application would never have written there.
     *
     * <p>Below the API on purpose, and the only thing here that goes there: the whole question is
     * what happens when the file says something no endpoint could have put in it. Written while no
     * application is up, because SQLite serialises writers and a second connection writing to a live
     * database is how a test earns an intermittent SQLITE_BUSY.
     */
    private static void overwriteWhatTheClockWroteWith(long movedForwardByDays) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE);
             Statement statement = connection.createStatement()) {
            int rowsEdited = statement.executeUpdate(
                    "update clock_offset set moved_forward_by_days = " + movedForwardByDays);
            assertThat(rowsEdited)
                    .as("the record of where the clock was left, which this test claims to have edited")
                    .isEqualTo(1);
        } catch (SQLException e) {
            throw new AssertionError("could not edit the database this test wrote at " + DATABASE, e);
        }
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

    private static ClockView whereTheClockIs() {
        ResponseEntity<ClockView> response = reopenedHttp.getForEntity("/api/dev/clock", ClockView.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static DepositView deposit(long savingsAccountId, long fromCurrentAccountId, String amount) {
        ResponseEntity<DepositView> response = reopenedHttp.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", fromCurrentAccountId),
                DepositView.class,
                savingsAccountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
