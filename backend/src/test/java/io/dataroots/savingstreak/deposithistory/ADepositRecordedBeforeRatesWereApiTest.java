package io.dataroots.savingstreak.deposithistory;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
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
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Looking back at deposits made before there were rates to record.
 *
 * <p>A deposit made last spring earned one point per whole euro and nothing else, because that was
 * the whole scheme at the time. Its row says nothing about a rate, and the history has to explain it
 * anyway: the ordinary rate, an uplift of nothing, and the same total the customer was told then.
 * Reporting no rate at all would leave a page with a blank where every other row has a figure, and
 * reporting today's rate would be a claim about a week that had not happened yet.
 *
 * <p>The older database is made rather than checked in, the way the remaining-amounts test makes
 * one: an application is started, deposited into, stopped, and then the column this scheme added is
 * taken away again, which leaves exactly the file the previous release wrote — deposits with no rate
 * against them and no uplift in the points ledger, because there was no uplift to credit.
 *
 * <p>Its own application and its own database: the shared file this run uses was written by the
 * current code, and there is no older database in it to open.
 */
class ADepositRecordedBeforeRatesWereApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-before-rates-were-recorded");

    /** What the previous release had recorded before the file was handed to this one. */
    private static final List<String> AMOUNTS_ALREADY_DEPOSITED = List.of("12.50", "7.25", "0.99");

    private static ConfigurableApplicationContext application;

    /** Bound to the application that reopened the older file, not to the shared one. */
    private static TestRestTemplate reopenedHttp;

    private static long savingsAccount;
    private static List<DepositView> depositsAsTheyWere;

    @BeforeAll
    static void writeADatabaseTheOldWayAndOpenItWithThis() {
        try (ConfigurableApplicationContext previousRelease = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = boundTo(previousRelease);
            SeededAccounts seeded = new SeededAccounts(http);
            savingsAccount = seeded.savingsAccountOf(ANKE);
            long currentAccount = seeded.currentAccountOf(ANKE);
            AMOUNTS_ALREADY_DEPOSITED.forEach(
                    amount -> deposit(http, savingsAccount, currentAccount, amount));
            depositsAsTheyWere = List.of(depositsInto(http, savingsAccount));
        }
        takeAwayTheColumnThisSchemeAdded();
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
     * The figures a customer knew before the scheme existed, still the figures they are shown: one
     * point per whole euro, nothing on top, and the ordinary rate — which is what those deposits
     * were in fact paid at, not a stand-in for a rate nobody recorded.
     */
    @Test
    void a_deposit_made_before_the_scheme_reports_its_base_points_no_bonus_and_the_ordinary_rate() {
        DepositView[] history = depositsInto(reopenedHttp, savingsAccount);

        assertThat(history).hasSize(AMOUNTS_ALREADY_DEPOSITED.size());
        assertThat(history).allSatisfy(entry -> {
            assertThat(entry.multiplierApplied())
                    .as("the rate a deposit made before there were rates was paid at")
                    .isNotNull()
                    .isEqualByComparingTo("1.00");
            assertThat(entry.streakBonusPoints())
                    .as("no run of weeks could have paid an uplift on it")
                    .isZero();
            assertThat(entry.basePoints())
                    .as("one point per whole euro, the cents floored away")
                    .isEqualTo(entry.amount().setScale(0, RoundingMode.FLOOR).longValue());
            assertThat(entry.pointsEarned())
                    .as("the total is the base, because there is nothing else in it")
                    .isEqualTo(entry.basePoints());
        });
    }

    /**
     * And the entries are otherwise the ones the older application reported, in the order it
     * reported them: opening the file is not allowed to move a figure or reshuffle a history.
     */
    @Test
    void the_history_is_the_one_the_older_application_reported() {
        assertThat(depositsInto(reopenedHttp, savingsAccount))
                // Amounts and rates by value rather than by scale: a figure that has been out to
                // SQLite and back comes home carrying whatever scale a float kept, and 12.50 and
                // 12.5 are the same amount of money.
                .usingRecursiveFieldByFieldElementComparator(RecursiveComparisonConfiguration.builder()
                        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                        .build())
                .containsExactlyElementsOf(depositsAsTheyWere);
    }

    /** And the reopened file is a working database, not merely a readable one. */
    @Test
    void a_deposit_made_after_the_reopening_records_the_rate_it_was_paid_at() {
        SeededAccounts seeded = new SeededAccounts(reopenedHttp);
        // Bram's, so that this test's own deposit is not in the history the others compare.
        long bramsSavings = seeded.savingsAccountOf(BRAM);

        DepositView made = deposit(reopenedHttp, bramsSavings, seeded.currentAccountOf(BRAM), "20.00");

        assertThat(made.basePoints()).isEqualTo(20);
        assertThat(depositsInto(reopenedHttp, bramsSavings)).anySatisfy(entry -> {
            assertThat(entry.id()).isEqualTo(made.id());
            assertThat(entry.multiplierApplied()).isEqualByComparingTo(made.multiplierApplied());
            assertThat(entry.basePoints()).isEqualTo(made.basePoints());
            assertThat(entry.streakBonusPoints()).isEqualTo(made.streakBonusPoints());
            assertThat(entry.pointsEarned()).isEqualTo(made.pointsEarned());
        });
    }

    /**
     * Removes the column this scheme added, leaving the file as the previous release wrote it: the
     * deposits are still there, and nothing in them says what rate they were paid at.
     */
    private static void takeAwayTheColumnThisSchemeAdded() {
        withTheDatabase(statement -> {
            statement.executeUpdate("alter table deposit drop column multiplier_applied");
            assertThat(columnsOfTheDepositTable(statement))
                    .as("the older database this test claims to have written")
                    .doesNotContain("multiplier_applied")
                    .contains("amount");
        });
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
     * written before this scheme looked like.
     *
     * <p>The one statement that writes runs while no application is up. SQLite serialises writers,
     * and a second connection writing to a live database is how a test earns an intermittent
     * SQLITE_BUSY.
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
