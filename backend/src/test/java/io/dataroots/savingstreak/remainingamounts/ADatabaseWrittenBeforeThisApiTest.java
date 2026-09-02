package io.dataroots.savingstreak.remainingamounts;

import java.math.BigDecimal;
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
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opening a database that was written before deposits carried what remains of them.
 *
 * <p>The money balance is now summed from what remains of each deposit, and a deposit written before
 * that column existed has nothing in it. Somebody who deposited last week and starts the application
 * today must see the balance they saw last week, so those deposits are given back the whole of their
 * own amount at start-up — which is what remains of a deposit that nothing could yet take money out
 * of.
 *
 * <p>The older database is made rather than checked in: an application is started, deposited into,
 * stopped, and then the column this change added is taken away again, which leaves exactly the file
 * the previous release wrote. A checked-in binary would say less and would have to be rebuilt by
 * hand every time the schema moved.
 *
 * <p>Its own application and its own database, like the fixed-clock test: the shared file this run
 * uses is one the current code wrote, and there is no older database in it to open.
 */
class ADatabaseWrittenBeforeThisApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-before-remaining-amounts");

    /** What the previous release had recorded before the file was handed to this one. */
    private static final List<String> AMOUNTS_ALREADY_DEPOSITED = List.of("12.50", "7.25", "0.99");

    private static ConfigurableApplicationContext application;

    /** Bound to the application that reopened the older file, not to the shared one. */
    private static TestRestTemplate reopenedHttp;

    private static long savingsAccount;
    private static long otherSavingsAccount;
    private static BalancesView savingsAccountAsItWas;
    private static BalancesView otherSavingsAccountAsItWas;
    private static BigDecimal currentAccountBalanceAsItWas;
    private static List<DepositView> depositsAsTheyWere;

    /**
     * How many deposits were still without a remaining amount at the moment the reopened
     * application's web server began accepting connections. Written by {@link WatchingTheServerComeUp}
     * from inside that application's start-up.
     */
    private static volatile Integer stillMissingWhenTheServerCameUp;

    @BeforeAll
    static void writeADatabaseTheOldWayAndOpenItWithThis() {
        try (ConfigurableApplicationContext previousRelease = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = boundTo(previousRelease);
            SeededAccounts seeded = new SeededAccounts(http);
            savingsAccount = seeded.savingsAccountOf(ANKE);
            otherSavingsAccount = seeded.otherSavingsAccountOf(ANKE);
            long currentAccount = seeded.currentAccountOf(ANKE);
            AMOUNTS_ALREADY_DEPOSITED.forEach(
                    amount -> deposit(http, savingsAccount, currentAccount, amount));
            // Two accounts, so that the restart is asked about an account with deposits in it and an
            // account with none.
            savingsAccountAsItWas = balancesOf(http, savingsAccount);
            otherSavingsAccountAsItWas = balancesOf(http, otherSavingsAccount);
            currentAccountBalanceAsItWas = seeded.currentAccountBalanceOf(ANKE);
            depositsAsTheyWere = List.of(depositsInto(http, savingsAccount));
        }
        takeAwayTheColumnThisChangeAdded();
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
     * <p>Below the API for the reason the walking skeleton gives about its connection pool: the
     * window this is about closes microseconds after the port opens, and no request a test could
     * send would reliably land inside it. So the question is asked from inside the application at
     * the moment the port opens, which is the only moment that settles it. Get this wrong — fill the
     * deposits in from a {@code CommandLineRunner}, which Spring Boot runs once the application is
     * already serving — and the first balance asked for on the morning of an upgrade is a 500.
     */
    @Test
    void what_remains_is_filled_in_before_the_application_serves_a_single_request() {
        assertThat(stillMissingWhenTheServerCameUp)
                .as("deposits with no remaining amount when the web server began accepting requests")
                .isNotNull()
                .isZero();
    }

    /** The figure the customer knows. Nothing this change did is allowed to move it. */
    @Test
    void the_savings_account_reports_the_balances_it_reported_before() {
        assertThat(balancesOf(reopenedHttp, savingsAccount).moneyBalance())
                .isEqualByComparingTo(savingsAccountAsItWas.moneyBalance());
        assertThat(balancesOf(reopenedHttp, savingsAccount).pointsBalance())
                .isEqualTo(savingsAccountAsItWas.pointsBalance());
    }

    /** An account with no deposits in it still answers zero rather than nothing. */
    @Test
    void a_savings_account_that_was_never_deposited_into_reports_what_it_did_before() {
        assertThat(balancesOf(reopenedHttp, otherSavingsAccount).moneyBalance())
                .isEqualByComparingTo(otherSavingsAccountAsItWas.moneyBalance());
    }

    /** The other half of every deposit ever made into the file, untouched by the reopening. */
    @Test
    void the_current_account_reports_the_balance_it_reported_before() {
        assertThat(new SeededAccounts(reopenedHttp).currentAccountBalanceOf(ANKE))
                .isEqualByComparingTo(currentAccountBalanceAsItWas);
    }

    /** Each deposit is still the deposit it was, down to the moment it was made. */
    @Test
    void the_deposit_history_is_the_one_the_older_application_reported() {
        assertThat(depositsInto(reopenedHttp, savingsAccount))
                // Amounts by value rather than by scale: a figure that has been out to SQLite and
                // back comes home carrying whatever scale a float kept, and 12.50 and 12.5 are the
                // same amount of money.
                .usingRecursiveFieldByFieldElementComparator(RecursiveComparisonConfiguration.builder()
                        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                        .build())
                .containsExactlyElementsOf(depositsAsTheyWere);
    }

    /**
     * What remains of each deposit already recorded, read out of the database itself.
     *
     * <p>Below the API on purpose, and the only test here that goes there. A deposit's remaining
     * amount has no observable behaviour of its own yet — it is deliberately equal to the amount
     * until a withdrawal can reduce it, and it is reported nowhere — so the balances above are the
     * whole of what HTTP can say about it. What the rows actually hold is the thing this criterion
     * is about, and the alternative to reading it is asserting nothing at all.
     */
    @Test
    void every_deposit_already_recorded_carries_its_own_amount_as_what_remains() {
        List<DepositRow> recorded = whatEachDepositHoldsIn(savingsAccount);

        assertThat(recorded).hasSize(AMOUNTS_ALREADY_DEPOSITED.size());
        assertThat(recorded).allSatisfy(deposit -> {
            assertThat(deposit.remainingAmount())
                    .as("what remains of a deposit written before this change")
                    .isNotNull()
                    .isEqualByComparingTo(deposit.amount());
        });
        // And those are the figures the balance above is made of, rather than three values that
        // merely happen to agree with themselves.
        assertThat(recorded.stream().map(DepositRow::remainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(savingsAccountAsItWas.moneyBalance());
    }

    /** And the reopened file is a working database, not merely a readable one. */
    @Test
    void a_deposit_made_after_the_reopening_still_earns_its_points_and_raises_the_balance() {
        SeededAccounts seeded = new SeededAccounts(reopenedHttp);
        // Bram's, so that this test's own deposit is not in the balances the others compare.
        long bramsSavings = seeded.savingsAccountOf(BRAM);
        BigDecimal before = balancesOf(reopenedHttp, bramsSavings).moneyBalance();

        DepositView made = deposit(reopenedHttp, bramsSavings, seeded.currentAccountOf(BRAM), "20.00");

        assertThat(made.pointsEarned()).isEqualTo(20);
        assertThat(balancesOf(reopenedHttp, bramsSavings).moneyBalance())
                .isEqualByComparingTo(before.add(new BigDecimal("20.00")));
    }

    /**
     * Removes the column this change added, leaving the file as the previous release wrote it: the
     * deposits are still there, and nothing in them says how much of each is left.
     */
    private static void takeAwayTheColumnThisChangeAdded() {
        withTheDatabase(statement -> {
            statement.executeUpdate("alter table deposit drop column remaining_amount");
            assertThat(columnsOfTheDepositTable(statement))
                    .as("the older database this test claims to have written")
                    .doesNotContain("remaining_amount")
                    .contains("amount");
        });
    }

    /**
     * Every deposit into one savings account, as what was put in and what is recorded as remaining
     * of it. One account rather than the whole table, because other tests in this class deposit into
     * other accounts and the order they run in is not this test's to decide.
     */
    private static List<DepositRow> whatEachDepositHoldsIn(long savingsAccountId) {
        List<DepositRow> rows = new ArrayList<>();
        withTheDatabase(statement -> {
            try (ResultSet found = statement.executeQuery(
                    "select amount, remaining_amount from deposit "
                            + "where savings_account_id = " + savingsAccountId + " order by id")) {
                while (found.next()) {
                    rows.add(new DepositRow(found.getBigDecimal("amount"),
                            found.getBigDecimal("remaining_amount")));
                }
            }
        });
        return rows;
    }

    /** One deposit as the database holds it, for the one test that looks in there. */
    private record DepositRow(BigDecimal amount, BigDecimal remainingAmount) {
    }

    private static List<String> columnsOfTheDepositTable(Statement statement) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet found = statement.executeQuery("pragma table_info(deposit)")) {
            while (found.next()) {
                columns.add(found.getString("name"));
            }
        }
        return columns;
    }

    /**
     * Opens the file directly, with no application in the way — the only way to say what a database
     * written before this change looked like, and the only way to read a column nothing reports.
     *
     * <p>The one statement that writes, the column being taken away, runs while no application is
     * up. SQLite serialises writers, and a second connection writing to a live database is how a
     * test earns an intermittent SQLITE_BUSY.
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
        return new SpringApplicationBuilder(SavingStreakApplication.class, WatchingTheServerComeUp.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + DATABASE,
                        "--spring.profiles.active=dev",
                        "--server.port=0");
    }

    /**
     * Asks the database, from inside the application being started, how many deposits still have
     * nothing recorded as remaining — at the moment its web server started accepting requests.
     *
     * <p>Handed to the builder by name and carrying no stereotype annotation, for the reason the
     * fixed-clock test gives: a {@code @Configuration} in a package the application scans would
     * attach itself to every other application these tests start.
     */
    static class WatchingTheServerComeUp {

        @Bean
        ApplicationListener<WebServerInitializedEvent> countWhatIsStillMissing(DataSource dataSource) {
            return event -> {
                try (Connection connection = dataSource.getConnection();
                     Statement statement = connection.createStatement();
                     ResultSet counted = statement.executeQuery(
                             "select count(*) from deposit where remaining_amount is null")) {
                    counted.next();
                    stillMissingWhenTheServerCameUp = counted.getInt(1);
                } catch (SQLException e) {
                    throw new IllegalStateException("could not count the deposits missing what "
                            + "remains of them as the server came up", e);
                }
            };
        }
    }

    private static TestRestTemplate boundTo(ConfigurableApplicationContext application) {
        TestRestTemplate http = new TestRestTemplate();
        http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        return http;
    }

    private static BalancesView balancesOf(TestRestTemplate http, long savingsAccountId) {
        ResponseEntity<BalancesView> response = http.getForEntity(
                "/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static DepositView[] depositsInto(TestRestTemplate http, long savingsAccountId) {
        return http.getForObject(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
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
