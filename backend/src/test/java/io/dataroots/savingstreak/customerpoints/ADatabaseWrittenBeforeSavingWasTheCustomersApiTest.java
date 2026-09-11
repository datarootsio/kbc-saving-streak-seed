package io.dataroots.savingstreak.customerpoints;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opening a database that was written while saving belonged to a savings account.
 *
 * <p>The points, the claims and the weeks are the customer's now, and the previous release recorded
 * all three against the savings account the money went into. Summing a customer's points would skip
 * every batch of them: somebody who saved for a cinema ticket last week would open the application
 * on the morning of the upgrade and be told they had nothing. Every claim they had made would be
 * missing from their page, and the week they were part-way through and the run of weeks behind it
 * would both read as nothing saved. So all three are handed to the customer who holds the account
 * they were recorded against, before anything is served.
 *
 * <p>And the old column has to go, which is what makes this more than filling a column in. The
 * previous release wrote it {@code not null}, so a batch credited from now on — which names a
 * customer and no account — could not be inserted beside it: without the drop, the first deposit
 * after the upgrade is refused by the database rather than credited. That is what
 * {@link #a_deposit_made_after_the_reopening_still_earns_its_points} is here to catch, and it is why
 * the table below is rebuilt with the constraint the previous release generated rather than merely
 * given its column back.
 *
 * <p>The older database is made rather than checked in, as the remaining-amounts test explains: an
 * application is started, deposited into and claimed out of, stopped, and then its two tables are
 * put back into the shape the previous release wrote. A checked-in binary would say less and would
 * have to be rebuilt by hand every time the schema moved.
 *
 * <p>Its own application and its own database: the shared file this run uses is one the current code
 * wrote, and there is no older database in it to open.
 */
class ADatabaseWrittenBeforeSavingWasTheCustomersApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-before-customer-saving");

    /** What the previous release had recorded: two goals paid into, and a reward already taken. */
    private static final String INTO_ONE_GOAL = "25.00";
    private static final String INTO_ANOTHER_GOAL = "12.00";
    private static final String ALREADY_CLAIMED = "CHARITY_DONATION";
    private static final long IT_COST = 10;

    private static ConfigurableApplicationContext application;

    /** Bound to the application that reopened the older file, not to the shared one. */
    private static TestRestTemplate reopenedHttp;

    private static long savingsAccount;
    private static long otherSavingsAccount;
    private static long pointsAsTheyWere;
    private static BalancesView savingAsItWas;
    private static List<ClaimedRewardView> claimsAsTheyWere;

    /**
     * How many batches were still without a customer at the moment the reopened application's web
     * server began accepting connections. Written by {@link WatchingTheServerComeUp} from inside that
     * application's start-up.
     */
    private static volatile Integer stillOwnerlessWhenTheServerCameUp;

    /** The same question asked of the deposits, which is what a week and a run are counted from. */
    private static volatile Integer depositsStillOwnerlessWhenTheServerCameUp;

    @BeforeAll
    static void writeADatabaseTheOldWayAndOpenItWithThis() {
        try (ConfigurableApplicationContext previousRelease = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = boundTo(previousRelease);
            SeededAccounts seeded = new SeededAccounts(http);
            savingsAccount = seeded.savingsAccountOf(ANKE);
            otherSavingsAccount = seeded.otherSavingsAccountOf(ANKE);
            long currentAccount = seeded.currentAccountOf(ANKE);
            // Two accounts, so that the reopened file is asked for a pot filled from two places
            // rather than one — the whole question the change is about.
            deposit(http, savingsAccount, currentAccount, INTO_ONE_GOAL);
            deposit(http, otherSavingsAccount, currentAccount, INTO_ANOTHER_GOAL);
            claim(http, seeded.customerIdOf(ANKE), ALREADY_CLAIMED);
            pointsAsTheyWere = seeded.pointsBalanceOf(ANKE);
            savingAsItWas = http.getForObject(
                    "/api/savings-accounts/{id}", BalancesView.class, savingsAccount);
            claimsAsTheyWere = List.of(claimsBy(http, seeded.customerIdOf(ANKE)));
        }
        putTheSavingBackTheWayThePreviousReleaseRecordedIt();
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
     * Filled in before the application could be asked anything, rather than shortly afterwards.
     *
     * <p>Asked from inside the application at the moment its port opens, for the reason the
     * remaining-amounts test gives: the window this is about closes microseconds after the port
     * opens, and no request a test could send would reliably land inside it. Get this wrong — fill
     * the batches in from a {@code CommandLineRunner}, which Spring Boot runs once the application is
     * already serving — and the first person to look at their points on the morning of an upgrade is
     * told they have none.
     */
    @Test
    void the_saving_finds_its_customer_before_the_application_serves_a_single_request() {
        assertThat(stillOwnerlessWhenTheServerCameUp)
                .as("batches of points with no customer when the web server began accepting requests")
                .isNotNull()
                .isZero();
        assertThat(depositsStillOwnerlessWhenTheServerCameUp)
                .as("deposits with no customer when the web server began accepting requests")
                .isNotNull()
                .isZero();
    }

    /**
     * The figure the customer knows, and the one thing this change must not move. Earned in two
     * accounts, so a balance that had been handed to only one of them would be short.
     */
    @Test
    void the_customer_still_has_the_points_they_had_before() {
        assertThat(new SeededAccounts(reopenedHttp).pointsBalanceOf(ANKE)).isEqualTo(pointsAsTheyWere);
        // And the arithmetic it is made of, said out loud: everything the two deposits earned, less
        // what the claim took.
        assertThat(pointsAsTheyWere).isEqualTo(25 + 12 - IT_COST);
    }

    /** The same figure beside either account, because it is the customer's and not either one's. */
    @Test
    void the_balance_reads_the_same_beside_both_of_the_accounts_that_earned_it() {
        assertThat(pointsBesideTheAccount(savingsAccount)).isEqualTo(pointsAsTheyWere);
        assertThat(pointsBesideTheAccount(otherSavingsAccount)).isEqualTo(pointsAsTheyWere);
    }

    /**
     * The week they are part-way through is the week they were part-way through, and the run behind
     * it is the run they had. Both are counted from the deposits, so a deposit that came back from
     * the upgrade saying nothing about whose saving it was would leave a customer who paid in EUR 37
     * this week looking at a week with nothing in it — and, next Monday, at a lost streak.
     */
    @Test
    void the_week_and_the_run_of_weeks_are_the_ones_the_older_application_reported() {
        BalancesView now = balancesOf(savingsAccount);

        assertThat(now.newSavingsThisWeek()).isEqualByComparingTo(savingAsItWas.newSavingsThisWeek());
        assertThat(now.stillNeededThisWeek())
                .isEqualByComparingTo(savingAsItWas.stillNeededThisWeek());
        assertThat(now.currentStreakWeeks()).isEqualTo(savingAsItWas.currentStreakWeeks());
        assertThat(now.bestStreakWeeks()).isEqualTo(savingAsItWas.bestStreakWeeks());
        // The figure the deposits add up to, said out loud: both of them, because the week is the
        // customer's and counts what went into either goal.
        assertThat(now.newSavingsThisWeek()).isEqualByComparingTo("37.00");
    }

    /**
     * A voucher somebody is holding is still on their page. A claim left behind by the migration
     * would be a reward the customer paid for and can no longer see.
     */
    @Test
    void the_claims_are_the_ones_the_older_application_reported() {
        assertThat(claimsBy(reopenedHttp, new SeededAccounts(reopenedHttp).customerIdOf(ANKE)))
                .containsExactlyElementsOf(claimsAsTheyWere);
    }

    /**
     * And the reopened file is a working ledger, not merely a readable one. This is the test the
     * dropped column exists for: while it is there, a batch that names a customer and no account
     * cannot be inserted, and this deposit comes back a server error instead of some points.
     */
    @Test
    void a_deposit_made_after_the_reopening_still_earns_its_points() {
        SeededAccounts seeded = new SeededAccounts(reopenedHttp);
        // Bram's, so that this test's own deposit is not in the balance the others compare.
        long bramsSavings = seeded.savingsAccountOf(BRAM);
        long before = seeded.pointsBalanceOf(BRAM);

        DepositView made = deposit(reopenedHttp, bramsSavings, seeded.currentAccountOf(BRAM), "20.00");

        assertThat(made.pointsEarned()).isEqualTo(20);
        assertThat(seeded.pointsBalanceOf(BRAM)).isEqualTo(before + 20);
    }

    /**
     * And a claim can still be made, which is the same question asked of the other table.
     *
     * <p>Bram's, and his own deposit pays for it, for the reason the deposit above gives: Anke's
     * balance and Anke's claims are what the tests above compare against, and a claim of theirs
     * spending her points would leave those tests asserting against whatever order this class
     * happened to run in.
     */
    @Test
    void a_claim_made_after_the_reopening_still_spends_them() {
        SeededAccounts seeded = new SeededAccounts(reopenedHttp);
        long bramsSavings = seeded.savingsAccountOf(BRAM);
        deposit(reopenedHttp, bramsSavings, seeded.currentAccountOf(BRAM), "10.00");
        long before = seeded.pointsBalanceOf(BRAM);

        ClaimedRewardView claimed = claim(reopenedHttp, seeded.customerIdOf(BRAM), ALREADY_CLAIMED);

        assertThat(claimed.pointsSpent()).isEqualTo(IT_COST);
        assertThat(seeded.pointsBalanceOf(BRAM)).isEqualTo(before - IT_COST);
    }

    /**
     * The file is left in the shape this release generates, rather than carrying a column nothing
     * writes. Read out of the database itself, because a schema is the one thing no endpoint reports.
     */
    @Test
    void neither_points_nor_claims_name_a_savings_account_any_more() {
        withTheDatabase(statement -> {
            assertThat(columnsOf(statement, "points_credit"))
                    .doesNotContain("savings_account_id")
                    .contains("customer_id");
            assertThat(columnsOf(statement, "redemption"))
                    .doesNotContain("savings_account_id")
                    .contains("customer_id");
        });
    }

    /**
     * Puts the three tables back into the shape the previous release wrote: a column naming the
     * savings account, and nothing saying whose saving this was.
     *
     * <p>Rebuilt rather than given the column back with {@code alter table}, because the constraint
     * is the point. SQLite will not add a {@code not null} column to a table that has rows in it, and
     * a nullable stand-in would let every test below pass with the migration's drop taken out — which
     * is the one thing this class is here to notice.
     *
     * <p>The two {@code create table} statements are the previous release's own, as Hibernate
     * generated them from the entity model before points belonged to a customer.
     */
    private static void putTheSavingBackTheWayThePreviousReleaseRecordedIt() {
        withTheDatabase(statement -> {
            // A deposit said nothing about whose saving it was, because a week was an account's.
            // Dropped rather than nulled: the previous release had no such column at all, and
            // schema generation adds it back nullable on the way in.
            statement.executeUpdate("alter table deposit drop column customer_id");
            statement.executeUpdate("alter table points_credit rename to points_credit_before");
            statement.executeUpdate("create table points_credit (id integer, earned_at timestamp, "
                    + "points bigint not null, "
                    + "reason varchar(255) check (reason in ('BASE_ACCRUAL','STREAK_BONUS')), "
                    + "remaining_points bigint not null, savings_account_id bigint not null, "
                    + "source_reference_id bigint not null, primary key (id))");
            // Which account earned a batch is where its deposit went, which is what the previous
            // release wrote in that column.
            statement.executeUpdate("insert into points_credit (id, earned_at, points, reason, "
                    + "remaining_points, savings_account_id, source_reference_id) "
                    + "select before.id, before.earned_at, before.points, before.reason, "
                    + "before.remaining_points, "
                    + "(select deposit.savings_account_id from deposit "
                    + "where deposit.id = before.source_reference_id), "
                    + "before.source_reference_id from points_credit_before before");
            statement.executeUpdate("drop table points_credit_before");

            statement.executeUpdate("alter table redemption rename to redemption_before");
            statement.executeUpdate("create table redemption (id integer, claimed_at timestamp, "
                    + "points_spent bigint not null, "
                    + "reward varchar(255) check (reward in ('CHARITY_DONATION','SNACK_VOUCHER',"
                    + "'CINEMA_TICKET','FAMILY_CINEMA_PACK')), "
                    + "savings_account_id bigint not null, "
                    + "voucher_code varchar(255) not null unique, primary key (id))");
            // A claim in that world was made from one savings account, and this is the one it was
            // made from: the file was written by claiming while the first goal held the points.
            statement.executeUpdate("insert into redemption (id, claimed_at, points_spent, reward, "
                    + "savings_account_id, voucher_code) "
                    + "select before.id, before.claimed_at, before.points_spent, before.reward, "
                    + savingsAccount + ", before.voucher_code from redemption_before before");
            statement.executeUpdate("drop table redemption_before");

            assertThat(columnsOf(statement, "points_credit"))
                    .as("the older database this test claims to have written")
                    .contains("savings_account_id")
                    .doesNotContain("customer_id");
            assertThat(columnsOf(statement, "redemption"))
                    .as("the older database this test claims to have written")
                    .contains("savings_account_id")
                    .doesNotContain("customer_id");
            assertThat(columnsOf(statement, "deposit"))
                    .as("the older database this test claims to have written")
                    .contains("savings_account_id")
                    .doesNotContain("customer_id");
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
     * Opens the file directly, with no application in the way — the only way to say what a database
     * written before this change looked like, and the only way to read a schema nothing reports.
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
        return new SpringApplicationBuilder(SavingStreakApplication.class, WatchingTheServerComeUp.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + DATABASE,
                        "--spring.profiles.active=dev",
                        "--server.port=0");
    }

    /**
     * Asks the database, from inside the application being started, how many batches of points still
     * have no customer — at the moment its web server started accepting requests.
     *
     * <p>Handed to the builder by name and carrying no stereotype annotation, for the reason the
     * fixed-clock test gives: a {@code @Configuration} in a package the application scans would
     * attach itself to every other application these tests start.
     */
    static class WatchingTheServerComeUp {

        @Bean
        ApplicationListener<WebServerInitializedEvent> countWhatIsStillOwnerless(DataSource dataSource) {
            return event -> {
                try (Connection connection = dataSource.getConnection();
                     Statement statement = connection.createStatement()) {
                    stillOwnerlessWhenTheServerCameUp =
                            countedBy(statement, "points_credit");
                    depositsStillOwnerlessWhenTheServerCameUp =
                            countedBy(statement, "deposit");
                } catch (SQLException e) {
                    throw new IllegalStateException("could not count the rows without a customer as "
                            + "the server came up", e);
                }
            };
        }

        /** The rows of one table that still say nothing about whose saving they were. */
        private static int countedBy(Statement statement, String table) throws SQLException {
            try (ResultSet counted = statement.executeQuery(
                    "select count(*) from " + table + " where customer_id is null")) {
                counted.next();
                return counted.getInt(1);
            }
        }
    }

    private static TestRestTemplate boundTo(ConfigurableApplicationContext application) {
        TestRestTemplate http = new TestRestTemplate();
        http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        return http;
    }

    private static long pointsBesideTheAccount(long savingsAccountId) {
        return balancesOf(savingsAccountId).pointsBalance();
    }

    private static BalancesView balancesOf(long savingsAccountId) {
        return reopenedHttp.getForObject(
                "/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    private static DepositView deposit(TestRestTemplate http, long savingsAccountId, long currentAccountId,
                                       String amount) {
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
                .describedAs("a claim this test needs in order to have spent anything")
                .isEqualTo(HttpStatus.CREATED);
        return claimed.getBody();
    }

    private static ClaimedRewardView[] claimsBy(TestRestTemplate http, long customerId) {
        return http.getForObject(
                "/api/customers/{id}/redemptions", ClaimedRewardView[].class, customerId);
    }
}
