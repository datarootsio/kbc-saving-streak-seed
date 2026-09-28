package io.dataroots.savingstreak.pointsexpiry;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A batch of points carries the day it goes, and that day is what happens to it.
 *
 * <p>The sweep used to work the day out again every night, twelve months from the moment the batch
 * was earned, against a constant. So the morning anybody shortened that constant every batch older
 * than the new period would die that night — including the batches whose owners had been promised a
 * year. This is the test that says the promise is now written down: the moment a batch expires is
 * stamped on it as it is earned, the sweep acts on the stamp, the figure the customer is shown comes
 * off the stamp, and a batch written before any of that existed was given the twelve months it
 * already had.
 *
 * <p><strong>Three starts against one file, with the database edited by hand in between.</strong>
 * The same shape {@code TheSchemeIsSeededOnceAtTodaysFiguresAndNeverResetApiTest} uses, and for the
 * same reason: what is under test is a start-up pass and a stamp, and "the pass ran once and nothing
 * argued with what it found" is a statement about two starts rather than about one. Nobody can
 * publish a second version of the scheme yet — that is a later ticket — so the only way to put a
 * lifetime other than twelve months in front of this application is to change a stamp underneath it,
 * which is exactly what a shortened scheme would do to the batches earned after it. A row changed by
 * hand is a row changed by hand however it got that way.
 *
 * <p>The hand edits are deliberately written without a single date literal in them: one nulls a
 * column and the other copies one row's stamp onto another. What a moment looks like in SQLite is
 * Hibernate's business, and a test that spelled one out would be asserting the storage rather than
 * the behaviour.
 *
 * <p>Here rather than in {@code savingspolicy}, which is the other package this feature is growing
 * tests in. Nothing observable about the scheme changes in this ticket — no version, no figure, no
 * page — and everything observable that does change is what happens to a batch of points on its
 * anniversary. The six tests already in this package are the ones a reader compares this against.
 */
class PointsKeepTheLifetimeTheyWerePromisedApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-points-keep-their-lifetime");

    /** A fortnight past a year, so the first batch is comfortably the far side of its own stamp. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    private static long batchesOnTheFirstStart;
    private static long batchesCarryingTheirExpiryOnTheFirstStart;
    private static long batchesLeftLookingLikeTheyPredateTheRelease;
    private static long batchesStillWithoutAnExpiryAfterTheBackfill;

    private static LocalDate theDayTheFirstBatchWasPromised;
    private static LocalDate theDayTheBackfilledBatchWasPromised;
    private static LocalDate theDayTheMovedStampPromises;

    private static long pointsAfterTheBackfill;
    private static long pointsExpiringNextAfterTheStampWasMoved;
    private static long pointsAfterASweepPastTheOldTwelveMonths;
    private static long pointsAfterASecondSweep;

    @BeforeAll
    static void earnSomePointsThenTakeTheirStampsAwayAndStartAgain() {
        // One start, one batch, and what the customer is told about it.
        try (AnApplicationWithAClockToMove app = new AnApplicationWithAClockToMove(DATABASE)) {
            long savingsAccount = app.savingsAccountOf(ANKE);
            DepositView paidIn = app.deposit(savingsAccount, ANKE, "40.00");
            assertThat(paidIn.pointsEarned()).isEqualTo(40);
            theDayTheFirstBatchWasPromised = app.pointsExpiringNextOnOf(ANKE);
        }

        // Every batch carries its expiry the moment it is earned, and then none of them does: the
        // column is emptied, which is the state every row written before this release is in.
        withTheDatabase(statement -> {
            batchesOnTheFirstStart = countOf(statement, "select count(*) from points_credit");
            batchesCarryingTheirExpiryOnTheFirstStart = countOf(statement,
                    "select count(*) from points_credit where expires_at is not null");
            batchesLeftLookingLikeTheyPredateTheRelease =
                    statement.executeUpdate("update points_credit set expires_at = null");
        });

        // The start that stamps them, and a second batch far enough on to have a stamp of its own.
        try (AnApplicationWithAClockToMove app = new AnApplicationWithAClockToMove(DATABASE)) {
            theDayTheBackfilledBatchWasPromised = app.pointsExpiringNextOnOf(ANKE);
            pointsAfterTheBackfill = app.pointsBalanceOf(ANKE);
            app.daysPass(DAYS_WELL_PAST_A_YEAR);
            DepositView thisWeek = app.deposit(app.savingsAccountOf(ANKE), ANKE, "25.00");
            assertThat(thisWeek.pointsEarned()).isEqualTo(25);
        }
        withTheDatabase(statement -> batchesStillWithoutAnExpiryAfterTheBackfill = countOf(statement,
                "select count(*) from points_credit where expires_at is null"));

        giveTheOldBatchTheYoungOnesExpiryByHand();

        // And the start that has to honour a stamp nothing would ever have computed.
        try (AnApplicationWithAClockToMove app = new AnApplicationWithAClockToMove(DATABASE)) {
            theDayTheMovedStampPromises = app.pointsExpiringNextOnOf(ANKE);
            pointsExpiringNextAfterTheStampWasMoved = app.pointsExpiringNextOf(ANKE);
            app.runJob("expireOldPoints");
            pointsAfterASweepPastTheOldTwelveMonths = app.pointsBalanceOf(ANKE);
            app.runJob("expireOldPoints");
            pointsAfterASecondSweep = app.pointsBalanceOf(ANKE);
        }
    }

    /**
     * A batch carries the moment it expires from the moment it is earned, rather than being told
     * each night how long it has left.
     *
     * <p>Every batch and not merely the one this test made, because a column filled for some ways of
     * earning and empty for others would be a sweep that skipped whichever way nobody thought about.
     */
    @Test
    void a_batch_carries_the_moment_it_expires_as_soon_as_it_is_earned() {
        assertThat(batchesOnTheFirstStart)
                .as("the deposit this test made left a batch behind to look at")
                .isPositive();
        assertThat(batchesCarryingTheirExpiryOnTheFirstStart)
                .as("and every batch in the ledger was stamped as it was credited")
                .isEqualTo(batchesOnTheFirstStart);
    }

    /**
     * Every batch written before this release is stamped on start-up with the twelve months it
     * already had — so nothing moves, and the customer is told the same day afterwards as before.
     *
     * <p>Asserted as the day rather than as the column, because the day is the promise. A backfill
     * that stamped the right number of rows with the wrong moment would pass a count and fail a
     * customer.
     */
    @Test
    void every_batch_written_before_this_release_is_stamped_with_the_twelve_months_it_already_had() {
        assertThat(batchesLeftLookingLikeTheyPredateTheRelease)
                .as("the rows this test emptied, so that the next start had something to stamp")
                .isEqualTo(batchesOnTheFirstStart);
        assertThat(batchesStillWithoutAnExpiryAfterTheBackfill)
                .as("and the start after it left none of them unstamped")
                .isZero();
        assertThat(theDayTheBackfilledBatchWasPromised)
                .as("stamped with exactly what those rows computed before there was a stamp, so the "
                        + "day the customer is told is the day they were always told")
                .isEqualTo(theDayTheFirstBatchWasPromised);
        assertThat(pointsAfterTheBackfill)
                .as("and nothing expired early on the way through")
                .isEqualTo(40);
    }

    /**
     * What the customer is told expires next is read off the stamp, so the date shown is the date
     * that will happen.
     *
     * <p>Proved by moving a stamp to a day no arithmetic in this application would ever have
     * produced from the moment that batch was earned. A figure still derived from the earning would
     * be unmoved by the edit and would go on promising a day the sweep no longer intends to act on.
     */
    @Test
    void what_the_customer_is_told_goes_next_is_read_from_the_stamp() {
        assertThat(theDayTheMovedStampPromises)
                .as("the stamp somebody moved is what the customer is now told")
                .isAfter(theDayTheFirstBatchWasPromised);
        assertThat(pointsExpiringNextAfterTheStampWasMoved)
                .as("both batches now share one stamp, so both are in the figure that day costs")
                .isEqualTo(65);
    }

    /**
     * And the sweep decides from the stamp too: a batch well past twelve months old, whose stamp says
     * otherwise, is left exactly where it is.
     *
     * <p>This is the whole ticket in one assertion. The batch was earned three hundred and
     * seventy-nine days before the sweep ran, so the sweep's query hands it over and the old rule
     * would have taken every point in it. The stamp says it is not due for another year, and the
     * stamp is what the sweep now reads — which is the same protection a batch will need the day the
     * bank publishes a shorter lifetime and a year-old batch has to survive it.
     *
     * <p>And twice, because a nightly job runs nightly.
     */
    @Test
    void the_sweep_takes_what_the_stamp_says_rather_than_recomputing_twelve_months() {
        assertThat(pointsAfterASweepPastTheOldTwelveMonths)
                .as("the year-old batch survives its own twelve months because its stamp says so")
                .isEqualTo(65);
        assertThat(pointsAfterASecondSweep)
                .as("and a second sweep over the same batches takes nothing, as it always did")
                .isEqualTo(65);
    }

    /**
     * Gives the oldest batch the stamp of the youngest, which is a promise no version of this
     * application would ever have written for it.
     *
     * <p>Copied from another row rather than typed as a moment, so that this test never has to know
     * how Hibernate spells an instant in SQLite — and so that the value is one the application
     * itself wrote, rather than one a test invented and the mapping might refuse to read back.
     */
    private static void giveTheOldBatchTheYoungOnesExpiryByHand() {
        withTheDatabase(statement -> {
            int moved = statement.executeUpdate("update points_credit set expires_at = "
                    + "(select expires_at from points_credit "
                    + "where id = (select max(id) from points_credit)) "
                    + "where id = (select min(id) from points_credit)");
            assertThat(moved).as("the stamp this test claims to have moved").isEqualTo(1);
        });
    }

    private static long countOf(Statement statement, String sql) throws SQLException {
        try (ResultSet counted = statement.executeQuery(sql)) {
            assertThat(counted.next()).as("a count always has a row").isTrue();
            return counted.getLong(1);
        }
    }

    /**
     * Opens the file directly, with no application in the way — the only way to put a batch in the
     * state every batch written before this release is in, and the only way to hand the sweep a
     * promise nothing would have computed.
     *
     * <p>Always while no application is up. SQLite serialises writers, and a second connection
     * writing to a live database is how a test earns an intermittent SQLITE_BUSY.
     */
    private static void withTheDatabase(SqlWork work) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE);
             Statement statement = connection.createStatement()) {
            work.doIt(statement);
        } catch (SQLException e) {
            throw new AssertionError("could not work on the database this test wrote at " + DATABASE, e);
        }
    }

    private interface SqlWork {

        void doIt(Statement statement) throws SQLException;
    }
}
