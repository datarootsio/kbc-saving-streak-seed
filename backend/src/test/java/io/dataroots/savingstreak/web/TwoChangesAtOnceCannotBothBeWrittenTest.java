package io.dataroots.savingstreak.web;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the application stops two changes to one balance at once from both being written.
 *
 * <p>Both halves of the guard are asserted below the API, and this class says so rather than leaving
 * a reader to wonder why: <em>the guard has no observable behaviour yet</em>. The connection pool is
 * one connection deep, so there is one transaction at a time and no two changes can interleave to be
 * caught. What is under test is therefore the arrangement itself — that the rows money moves through
 * carry a version, and that a clash over one is reported as a conflict rather than as a server
 * error — because the day this application runs on a database that serves more than one writer, the
 * arrangement is the only thing standing between it and money moved twice on one balance.
 *
 * <p>Which is the point of having it at all. The guarantee today is a property of one line of
 * configuration; these two facts are what make it a property of the model.
 */
class TwoChangesAtOnceCannotBothBeWrittenTest extends ApiIntegrationTest {

    @Autowired
    private DataSource database;

    /**
     * The two rows every movement of money in this application writes to: the current account whose
     * balance is read, decided against and written back, and the deposit a withdrawal draws down
     * after summing what the account's deposits still hold.
     *
     * <p>Read out of the database's own catalogue rather than off the entity, because the column has
     * to be in the file: an existing database opened by this release has to come out of the upgrade
     * with it, and a mapping that Hibernate never wrote would guard nothing.
     */
    @ParameterizedTest
    @ValueSource(strings = {"current_account", "deposit"})
    void the_rows_money_moves_through_carry_a_version(String table) throws SQLException {
        assertThat(columnsOf(table))
                .as("%s has a version column, so a second write against a row somebody else has "
                        + "changed fails instead of overwriting theirs", table)
                .contains("version");
    }

    /**
     * And a clash is an answer rather than a crash. Raised when the transaction commits — after the
     * service that made the change has returned and has nothing left to say — so this handler is the
     * only place it can be worded.
     *
     * <p>Called directly, because there is no way to provoke one through the API while the pool
     * holds a single connection. What is being checked is the status and the sentence: a 409 says
     * "your request was fine, somebody got there first, ask again", and a 500 would send a
     * participant looking for a fault that is not there.
     */
    @Test
    void a_clash_over_one_row_is_reported_as_a_conflict_and_not_as_a_fault() {
        ResponseEntity<ProblemDetail> answer = new RefusalsAsHttp()
                .twoChangesAtOnce(new OptimisticLockingFailureException("deposit 7 was changed"));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(answer.getBody().getDetail())
                .as("words a person can act on: nothing happened, and they may ask again")
                .contains("Nothing was moved")
                .contains("try again");
    }

    /** Every column name in that table, as SQLite's own catalogue reports them. */
    private java.util.List<String> columnsOf(String table) throws SQLException {
        try (Connection connection = database.getConnection();
             Statement query = connection.createStatement();
             ResultSet columns = query.executeQuery("select name from pragma_table_info('" + table + "')")) {
            java.util.List<String> named = new java.util.ArrayList<>();
            while (columns.next()) {
                named.add(columns.getString("name"));
            }
            return named;
        }
    }
}
