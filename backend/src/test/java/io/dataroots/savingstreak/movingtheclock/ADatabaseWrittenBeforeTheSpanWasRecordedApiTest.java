package io.dataroots.savingstreak.movingtheclock;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 * Opening a database whose record of the clock was written before it said what the days came to.
 *
 * <p>The record now carries two figures — the whole days that were asked for and the span through
 * the calendar they came to — because the days alone are not a position: seven of them are 169 hours
 * in the week the clocks go back. A database written by the release before that has the second column
 * added empty by the schema update, and says how many days but not what they came to.
 *
 * <p>That row is filled in rather than refused, and this is the test of why. The build that wrote it
 * added a fixed 86 400 seconds a day on every reading, so those days come to exactly that many
 * seconds and putting the position back is putting that application's own position back. Refusing the
 * row would take the days with it, and the next advance counts from where the clock says it is
 * standing: a trainer whose record said a year, restarting on this release and then asking for seven
 * days, would be moved to seven days rather than 372 and would stamp the next deposits nearly a year
 * before ones already on the ledger.
 *
 * <p>The older database is made rather than checked in, the way
 * {@code ADatabaseWrittenBeforeThisApiTest} makes its own: an application is started, the clock is
 * moved, the application is stopped, and the column this change added is then taken away, which
 * leaves exactly the file the previous release wrote. Its own database, like every test in this
 * package, because the file the rest of the run shares is not one to leave wound forward.
 *
 * <p>Everything the tests below assert is read in {@link #openTheOlderDatabaseAndSeeWhereItStands()},
 * because the last of them advances the clock and each of the others says where the clock is standing
 * in absolute terms — which is only an answer if nothing has moved it since.
 */
class ADatabaseWrittenBeforeTheSpanWasRecordedApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-clock-before-the-span");

    /** What the older release recorded: ten days, and nothing about what they came to. */
    private static final long DAYS_THE_OLDER_RELEASE_MOVED = 10;

    /**
     * What those days came to for it: a fixed 86 400 seconds each, which is what its clock added to
     * the real moment on every reading. The position this release has to come back up at.
     */
    private static final Duration WHAT_THAT_RELEASE_WAS_ADDING =
            Duration.ofDays(DAYS_THE_OLDER_RELEASE_MOVED);

    /** A week more, asked for the way a trainer picking the exercise back up asks for it. */
    private static final long A_WEEK = 7;

    private static ConfigurableApplicationContext application;

    /** Bound to the application that reopened the older file, not to the shared one. */
    private static TestRestTemplate reopenedHttp;

    private static ClockView asItStoodBeforeTheColumnWasTakenAway;
    private static ClockView asItStandsOnTheOlderDatabase;

    private static Instant realMomentBeforeTheDeposit;
    private static Instant realMomentAfterTheDeposit;
    private static DepositView depositAfterTheReopening;

    private static List<Long> whatTheRowSaysTheDaysCameTo;

    private static ClockView afterAskingForAWeekMore;

    @BeforeAll
    static void openTheOlderDatabaseAndSeeWhereItStands() {
        try (ConfigurableApplicationContext previousRelease = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = boundTo(previousRelease);
            asItStoodBeforeTheColumnWasTakenAway =
                    advanceBy(http, DAYS_THE_OLDER_RELEASE_MOVED);
        }
        takeAwayTheColumnThisChangeAdded();

        application = startAnApplicationAgainstTheFile();
        reopenedHttp = boundTo(application);
        asItStandsOnTheOlderDatabase = whereTheClockIs(reopenedHttp);

        SeededAccounts seeded = new SeededAccounts(reopenedHttp);
        realMomentBeforeTheDeposit = Instant.now();
        depositAfterTheReopening = deposit(reopenedHttp, seeded.savingsAccountOf(ANKE),
                seeded.currentAccountOf(ANKE), "5.00");
        realMomentAfterTheDeposit = Instant.now();

        whatTheRowSaysTheDaysCameTo = whatTheRecordOfTheClockNowHoldsAsItsSpan();

        // Last, because it moves the clock the assertions above are about.
        afterAskingForAWeekMore = advanceBy(reopenedHttp, A_WEEK);
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    /** The figure the participant was told by the older release is the figure they are told now. */
    @Test
    void the_days_the_older_record_says_are_put_back_rather_than_thrown_away() {
        assertThat(asItStoodBeforeTheColumnWasTakenAway.movedForwardByDays())
                .as("what the release before this one reported having moved the clock")
                .isEqualTo(DAYS_THE_OLDER_RELEASE_MOVED);
        assertThat(asItStandsOnTheOlderDatabase.movedForwardByDays())
                .isEqualTo(DAYS_THE_OLDER_RELEASE_MOVED);
    }

    /**
     * And it is the clock that came back, not only the figure reported about it: a deposit is dated
     * the same span on that the older release was adding, to the second.
     */
    @Test
    void a_deposit_made_afterwards_is_dated_the_span_the_older_release_was_adding() {
        assertThat(depositAfterTheReopening.depositedAt())
                // A moment is kept to the millisecond, so it can sit up to a millisecond below the
                // real moment this test read just before making the deposit.
                .isAfterOrEqualTo(realMomentBeforeTheDeposit.plus(WHAT_THAT_RELEASE_WAS_ADDING)
                        .minusMillis(1))
                .isBeforeOrEqualTo(realMomentAfterTheDeposit.plus(WHAT_THAT_RELEASE_WAS_ADDING));
    }

    /**
     * The row is complete afterwards, so a start after this one has nothing left to work out.
     *
     * <p>Below the API, and the only thing here that goes there: what the row holds is the whole of
     * this criterion, and nothing the application reports says whether the column was filled in or
     * merely stood in for.
     */
    @Test
    void the_record_is_left_saying_what_those_days_came_to() {
        assertThat(whatTheRowSaysTheDaysCameTo)
                .as("the span in the clock's record, once the older database has been opened")
                .containsExactly(WHAT_THAT_RELEASE_WAS_ADDING.toSeconds());
    }

    /**
     * The move that follows counts from the position that was put back, which is the damage refusing
     * the row would have done: an advance counted from zero instead would leave the clock ten days
     * behind records this database already holds.
     */
    @Test
    void the_next_advance_counts_from_the_position_that_was_put_back() {
        assertThat(afterAskingForAWeekMore.movedForwardByDays())
                .isEqualTo(DAYS_THE_OLDER_RELEASE_MOVED + A_WEEK);
        assertThat(afterAskingForAWeekMore.now())
                .as("where the clock reads after the advance, which may not be before where it read "
                        + "before it")
                .isAfter(asItStandsOnTheOlderDatabase.now())
                .isAfter(depositAfterTheReopening.depositedAt());
    }

    /**
     * Removes the column this change added, leaving the record as the previous release wrote it: the
     * days are still there, and nothing beside them says what they came to.
     *
     * <p>Written while no application is up, because SQLite serialises writers and a second
     * connection writing to a live database is how a test earns an intermittent SQLITE_BUSY.
     */
    private static void takeAwayTheColumnThisChangeAdded() {
        withTheDatabase(statement -> {
            statement.executeUpdate("alter table clock_offset drop column moved_forward_by_seconds");
            assertThat(columnsOfTheClockOffsetTable(statement))
                    .as("the older record this test claims to have written")
                    .doesNotContain("moved_forward_by_seconds")
                    .contains("moved_forward_by_days");
        });
    }

    /** What the clock's record holds as its span, one figure per row, nulls included. */
    private static List<Long> whatTheRecordOfTheClockNowHoldsAsItsSpan() {
        List<Long> spans = new ArrayList<>();
        withTheDatabase(statement -> {
            try (ResultSet found = statement.executeQuery(
                    "select moved_forward_by_seconds from clock_offset order by id")) {
                while (found.next()) {
                    long span = found.getLong("moved_forward_by_seconds");
                    spans.add(found.wasNull() ? null : span);
                }
            }
        });
        return spans;
    }

    private static List<String> columnsOfTheClockOffsetTable(Statement statement) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet found = statement.executeQuery("pragma table_info(clock_offset)")) {
            while (found.next()) {
                columns.add(found.getString("name"));
            }
        }
        return columns;
    }

    /**
     * Opens the file directly, with no application in the way — the only way to say what a database
     * written before this change looked like, and the only way to read a column nothing reports.
     */
    private static void withTheDatabase(SqlWork work) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE);
             Statement statement = connection.createStatement()) {
            work.doIt(statement);
        } catch (SQLException e) {
            throw new AssertionError("could not read the database this test wrote at " + DATABASE, e);
        }
    }

    private interface SqlWork {

        void doIt(Statement statement) throws SQLException;
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
