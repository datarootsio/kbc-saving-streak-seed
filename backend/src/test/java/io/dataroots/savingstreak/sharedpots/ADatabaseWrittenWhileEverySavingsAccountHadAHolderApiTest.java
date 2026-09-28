package io.dataroots.savingstreak.sharedpots;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opening a database that was written while every savings account had to have a holder.
 *
 * <p>A pot holds its own savings account and no customer holds it, which is what keeps a pot's money
 * out of everybody's personal screens. Every database written before this release was generated with
 * the holder required, and schema generation adds columns but never relaxes one — so without a step
 * at start-up the first pot opened against such a file would not be opened at all: the account
 * underneath it would be refused by the database, in a constraint nobody can read, and what the
 * customer would see is a failure rather than a pot.
 *
 * <p>That is what {@link #a_pot_opened_after_the_reopening_gets_an_account_of_its_own} is here to
 * catch, and it is why the table below is rebuilt with the constraint the previous release generated
 * rather than merely described in a comment. Take the relaxation out of {@code AccountsOnStartUp}
 * and that test fails; nothing else in the suite would.
 *
 * <p>The older database is made rather than checked in, as the other before-and-after tests in this
 * application explain: an application is started against a fresh file, stopped, and then its savings
 * accounts are put back into the shape the previous release wrote. A checked-in binary would say
 * less and would have to be rebuilt by hand every time the schema moved.
 *
 * <p>Its own application and its own database: the shared file this run uses is one the current code
 * wrote, and there is no older database in it to open.
 */
class ADatabaseWrittenWhileEverySavingsAccountHadAHolderApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-before-an-account-could-be-nobodys");

    private static AnApplicationWithAClockToMove reopened;

    /** What the previous release had recorded: the accounts the seeded households were opened with. */
    private static List<Long> savingsAccountsAsTheyWere;

    @BeforeAll
    static void writeADatabaseTheOldWayAndOpenItWithThis() {
        try (AnApplicationWithAClockToMove previousRelease = new AnApplicationWithAClockToMove(DATABASE)) {
            savingsAccountsAsTheyWere = previousRelease.savingsAccountsOf(ANKE);
        }
        putTheHolderBackTheWayThePreviousReleaseRequiredIt();
        reopened = new AnApplicationWithAClockToMove(DATABASE);
    }

    @AfterAll
    static void stopTheApplication() {
        if (reopened != null) {
            reopened.close();
        }
    }

    /**
     * The test the relaxation exists for. A pot's savings account names no customer, and on a file
     * written before that was allowed the insert is refused by the database — so this comes back a
     * server error instead of a pot.
     */
    @Test
    void a_pot_opened_after_the_reopening_gets_an_account_of_its_own() {
        SharedPotView pot = reopened.openAPot(ANKE, "Kitchen");

        assertThat(pot.savingsAccountId()).isNotNull();
        assertThat(pot.savingsAccountId())
                .as("and it is the pot's rather than hers")
                .isNotIn(reopened.savingsAccountsOf(ANKE));
        assertThat(reopened.potWith(pot.id()).name()).isEqualTo("Kitchen");
    }

    /**
     * And nothing that was in the file before is any different for having gone through the rebuild.
     * The accounts are the same accounts, with the same identifiers, held by the same customer —
     * which is the whole of what a relaxation is allowed to change about existing rows: nothing.
     */
    @Test
    void every_account_that_was_there_is_still_there_and_still_hers() {
        assertThat(reopened.savingsAccountsOf(ANKE))
                .as("her accounts came through the rebuild unchanged")
                .isEqualTo(savingsAccountsAsTheyWere);
    }

    /**
     * The file is left in the shape this release generates, rather than the one it was opened in.
     * Read out of the database itself, because a column's constraint is the one thing no endpoint
     * reports.
     */
    @Test
    void the_holder_is_no_longer_required_of_a_savings_account() {
        withTheDatabase(statement -> assertThat(theHolderIsRequiredIn(statement))
                .as("the holder is optional in the file this application reopened")
                .isFalse());
    }

    /**
     * Puts the savings accounts back into the shape the previous release wrote: a holder every
     * account had to have.
     *
     * <p>Rebuilt rather than altered, because the constraint is the point and SQLite has no
     * statement for adding one. The {@code create table} below is the previous release's own, as
     * Hibernate generated it from the entity model while the holder was {@code optional = false}.
     */
    private static void putTheHolderBackTheWayThePreviousReleaseRequiredIt() {
        withTheDatabase(statement -> {
            statement.executeUpdate("alter table savings_account rename to savings_account_before");
            statement.executeUpdate("create table savings_account "
                    + "(id integer, customer_id bigint not null, primary key (id))");
            statement.executeUpdate("insert into savings_account (id, customer_id) "
                    + "select id, customer_id from savings_account_before");
            statement.executeUpdate("drop table savings_account_before");

            assertThat(theHolderIsRequiredIn(statement))
                    .as("the older database this test claims to have written")
                    .isTrue();
        });
    }

    /** Whether a savings account in this file is still required to name a customer. */
    private static boolean theHolderIsRequiredIn(Statement statement) throws SQLException {
        try (ResultSet columns = statement.executeQuery("pragma table_info(savings_account)")) {
            while (columns.next()) {
                if ("customer_id".equals(columns.getString("name"))) {
                    return columns.getInt("notnull") == 1;
                }
            }
        }
        throw new AssertionError("no savings account in this database names a customer at all");
    }

    /**
     * Opens the file directly, with no application in the way — the only way to say what a database
     * written before this change looked like, and the only way to read a constraint nothing reports.
     *
     * <p>The statements that write run while no application is up. SQLite serialises writers, and a
     * second connection writing to a live database is how a test earns an intermittent SQLITE_BUSY.
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
