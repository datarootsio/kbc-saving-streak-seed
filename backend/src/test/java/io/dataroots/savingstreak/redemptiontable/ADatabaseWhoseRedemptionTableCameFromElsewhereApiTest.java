package io.dataroots.savingstreak.redemptiontable;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
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
 * Opening a database whose {@code redemption} table was not written by this application.
 *
 * <p>The name is an ordinary one and this is not the only application that has used it. A file that
 * has been through something else — an earlier take on the same exercise, a colleague's branch —
 * arrives with a {@code redemption} table already in it, and schema generation
 * ({@code ddl-auto=update}) treats that as a table to add the missing columns to rather than a
 * table to replace. What it cannot do is take away the columns the other application declared
 * {@code not null}: {@code created_at}, {@code reward_id} and {@code reward_title} are still there,
 * still required, and a claim has no value for any of them.
 *
 * <p>Every claim is then refused by the database on the way in. The transaction rolls back, so no
 * points are lost and no voucher is issued — the customer simply gets a 500 and no reward, for as
 * long as the file lives. That is the failure this class is here to catch, and it is worth catching
 * in a test because it cannot happen in one that starts from an empty file: a table this application
 * created has none of those columns, so every other test in the suite passes with the sweep taken
 * out.
 *
 * <p>Its own application and its own database, like the other migration tests: the shared file this
 * run uses is one the current code wrote, and there is no foreign table in it to open.
 */
class ADatabaseWhoseRedemptionTableCameFromElsewhereApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-redemption-from-elsewhere");

    /** Enough saving to afford the cheapest thing in the catalogue twice over. */
    private static final String SAVED = "40.00";
    private static final String CLAIMED = "CHARITY_DONATION";
    private static final long IT_COST = 10;

    private static ConfigurableApplicationContext application;

    /** Bound to the application that reopened the foreign file, not to the shared one. */
    private static TestRestTemplate reopenedHttp;

    @BeforeAll
    static void writeAFileWithSomebodyElsesRedemptionTableAndOpenItWithThis() {
        try (ConfigurableApplicationContext ourOwn = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = boundTo(ourOwn);
            SeededAccounts seeded = new SeededAccounts(http);
            // Points to spend, so that a refused claim below is the table's doing and not the
            // customer's balance. Made before the table is swapped, because a deposit is the one
            // thing the foreign table has no opinion about.
            deposit(http, seeded.savingsAccountOf(ANKE), seeded.currentAccountOf(ANKE), SAVED);
        }
        putSomebodyElsesRedemptionTableInTheFile();
        application = startAnApplicationAgainstTheFile();
        reopenedHttp = boundTo(application);
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    /**
     * The whole point of the sweep. Without it this comes back 500: the insert names six columns and
     * the table demands nine, and SQLite refuses it on {@code created_at} before anything else is
     * looked at.
     */
    @Test
    void a_claim_can_still_be_made_against_the_reopened_file() {
        SeededAccounts seeded = new SeededAccounts(reopenedHttp);
        long before = seeded.pointsBalanceOf(ANKE);

        ClaimedRewardView claimed = claim(reopenedHttp, seeded.customerIdOf(ANKE), CLAIMED);

        assertThat(claimed.pointsSpent()).isEqualTo(IT_COST);
        assertThat(claimed.voucherCode()).isNotBlank();
        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(before - IT_COST);
    }

    /** And it is on their page afterwards, which is the half of a claim the customer keeps. */
    @Test
    void the_claim_is_listed_among_what_the_customer_has_claimed() {
        SeededAccounts seeded = new SeededAccounts(reopenedHttp);
        long customerId = seeded.customerIdOf(ANKE);
        ClaimedRewardView claimed = claim(reopenedHttp, customerId, CLAIMED);

        assertThat(claimsBy(reopenedHttp, customerId))
                .extracting(ClaimedRewardView::voucherCode)
                .contains(claimed.voucherCode());
    }

    /**
     * The file is left in the shape this application generates. Read out of the database itself,
     * because a schema is the one thing no endpoint reports.
     *
     * <p>Both halves matter. The foreign columns have to be gone or nothing can be written, and the
     * columns a claim fills have to still be there — a sweep that took {@code points_spent} with
     * them would leave a table that accepts claims and forgets what they cost.
     */
    @Test
    void the_columns_no_claim_can_fill_are_gone_and_the_rest_are_not() {
        withTheDatabase(statement -> assertThat(columnsOf(statement, "redemption"))
                .doesNotContain("created_at", "reward_id", "reward_title")
                .contains("id", "customer_id", "reward", "points_spent", "voucher_code", "claimed_at"));
    }

    /**
     * Puts the other application's {@code redemption} table into the file, exactly as it was found
     * in the wild: its three required columns, and this application's own added beside them the way
     * schema generation would have added them on the first start against such a file.
     *
     * <p>Rebuilt rather than added to with {@code alter table}, because the constraints are the
     * point — SQLite will not add a {@code not null} column without a default at all, and a nullable
     * stand-in would let every test above pass with the sweep taken out, which is the one thing this
     * class is here to notice.
     */
    private static void putSomebodyElsesRedemptionTableInTheFile() {
        withTheDatabase(statement -> {
            statement.executeUpdate("drop table redemption");
            statement.executeUpdate("create table redemption (id integer, "
                    + "created_at timestamp not null, "
                    + "points_spent integer not null, "
                    + "reward_id bigint not null, "
                    + "reward_title varchar(255) not null, "
                    + "voucher_code varchar(255) not null unique, "
                    + "claimed_at timestamp, customer_id bigint, "
                    + "reward varchar(255) check (reward in ('CHARITY_DONATION','SNACK_VOUCHER',"
                    + "'CINEMA_TICKET','FAMILY_CINEMA_PACK')), primary key (id))");

            assertThat(columnsOf(statement, "redemption"))
                    .as("the foreign table this test claims to have written")
                    .contains("created_at", "reward_id", "reward_title");
        });
    }

    private static List<String> columnsOf(Statement statement, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet found = statement.executeQuery("pragma table_info(" + table + ")")) {
            while (found.next()) {
                columns.add(found.getString("name"));
            }
        }
        return columns;
    }

    /**
     * Opens the file directly, with no application in the way — the only way to put a table there
     * that this application would never have written, and the only way to read a schema back.
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

    private static DepositView deposit(TestRestTemplate http, long savingsAccountId,
                                       long currentAccountId, String amount) {
        ResponseEntity<DepositView> made = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                DepositView.class,
                savingsAccountId);
        assertThat(made.getStatusCode())
                .describedAs("a deposit this test needs in order to have points at all")
                .isEqualTo(HttpStatus.CREATED);
        return made.getBody();
    }

    private static ClaimedRewardView claim(TestRestTemplate http, long customerId, String reward) {
        ResponseEntity<ClaimedRewardView> claimed = http.postForEntity(
                "/api/customers/{id}/redemptions",
                Map.of("reward", reward),
                ClaimedRewardView.class,
                customerId);
        assertThat(claimed.getStatusCode())
                .describedAs("the claim this test is about")
                .isEqualTo(HttpStatus.CREATED);
        return claimed.getBody();
    }

    private static ClaimedRewardView[] claimsBy(TestRestTemplate http, long customerId) {
        return http.getForObject(
                "/api/customers/{id}/redemptions", ClaimedRewardView[].class, customerId);
    }
}
