package io.dataroots.savingstreak.redemptiontable;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import io.dataroots.savingstreak.support.VoucherAtTheCounterView;
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
 * Opening a database the release before this one wrote, with a voucher already in it.
 *
 * <p>That file is not hypothetical — it is the one in {@code data/} on every machine this
 * application has ever run on, and it carries something no test could otherwise see. While the
 * reward on a claim was mapped {@code @Enumerated(STRING)}, schema generation wrote the catalogue
 * into the schema as a constraint: {@code check (reward in ('CHARITY_DONATION', 'SNACK_VOUCHER',
 * 'CINEMA_TICKET', 'FAMILY_CINEMA_PACK'))}. Dropping the annotation does not drop the check,
 * {@code ddl-auto=update} only ever adds, and so the first claim of a fifth reward on such a file
 * is refused by SQLite — on the very feature that exists to let somebody add a fifth reward. A
 * throwaway test file gets a fresh, check-free table, which is exactly why the suite was green and
 * every real database was broken.
 *
 * <p>So this writes the previous release's table by hand, with the check and a claim in it, opens
 * it with this one, and asks for both halves: that the voucher already there reads back exactly as
 * it did, and that a reward code the constraint would have rejected can now be claimed.
 *
 * <p>That hand-written claim earns this class a third job now that a voucher has a life. The row
 * it writes has no state column at all, because the release that wrote it had none, so the file
 * exercises the start-up backfill for free: the voucher in it has to read as {@code ISSUED} at the
 * counter and be handed over there like any other, or every voucher a customer is carrying on the
 * morning of the upgrade is one nobody can spend.
 *
 * <p>Its own application and its own database, like the other migration tests. The shared file this
 * run uses was written by the current code and has no old constraint left in it to take off.
 */
class ADatabaseFromTheReleaseBeforeApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-release-before");

    /** Enough saving to afford the offer added below several times over. */
    private static final String SAVED = "40.00";

    /** The voucher the previous release issued, written into the file exactly as it stored one. */
    private static final String THE_OLD_VOUCHER = "SS-DON-QQ7X4M";
    private static final String THE_OLD_CLAIM_WAS_FOR = "CHARITY_DONATION";
    private static final String IT_WAS_CALLED = "Charity donation";
    private static final long IT_COST = 10;
    private static final Instant IT_WAS_CLAIMED_AT = Instant.parse("2026-01-15T10:00:00Z");

    /** Whoever is standing at the till in the test that hands the old voucher over. */
    private static final String A_COUNTER = "Gent Zuid, till 1";

    /**
     * A fifth reward, put into the catalogue the way the administration screen will: a row. Its
     * code is not one of the four the old check allows, which is the whole point of it.
     */
    private static final String THE_FIFTH_REWARD = "MYSTERY_BOX";
    private static final long THE_FIFTH_REWARD_COSTS = 5;

    private static ConfigurableApplicationContext application;

    /** Bound to the application that reopened the old file, not to the shared one. */
    private static TestRestTemplate reopenedHttp;

    private static long anke;

    @BeforeAll
    static void writeThePreviousReleasesDatabaseAndOpenItWithThis() {
        try (ConfigurableApplicationContext ourOwn = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = boundTo(ourOwn);
            SeededAccounts seeded = new SeededAccounts(http);
            anke = seeded.customerIdOf(ANKE);
            // Points to spend, so that a claim below is about the table and not about her balance.
            deposit(http, seeded.savingsAccountOf(ANKE), seeded.currentAccountOf(ANKE), SAVED);
        }
        putThePreviousReleasesRedemptionTableInTheFile();
        putAFifthRewardInTheCatalogue();
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
     * The claim the previous release wrote is still the customer's, and still says what it said:
     * the same reward, the same price, the same voucher, the same moment.
     *
     * <p>The title is the one thing it never stored, and it reads correctly all the same, because
     * the start-up backfill wrote the catalogue's answer onto it — which is the answer the page was
     * showing for it yesterday. A customer's own history is not allowed to change on the morning of
     * an upgrade.
     */
    @Test
    void a_voucher_from_the_previous_release_reads_back_exactly_as_it_did() {
        assertThat(claimsBy(reopenedHttp, anke))
                .filteredOn(claim -> claim.voucherCode().equals(THE_OLD_VOUCHER))
                .singleElement()
                .satisfies(claim -> {
                    assertThat(claim.code()).isEqualTo(THE_OLD_CLAIM_WAS_FOR);
                    assertThat(claim.title()).isEqualTo(IT_WAS_CALLED);
                    assertThat(claim.pointsSpent()).isEqualTo(IT_COST);
                    assertThat(claim.claimedAt()).isEqualTo(IT_WAS_CLAIMED_AT);
                });
    }

    /**
     * The whole point of taking the check off. Without it this comes back 500: SQLite refuses the
     * insert because {@code MYSTERY_BOX} is not one of the four codes the old catalogue had, and a
     * catalogue somebody runs would be a catalogue nobody can add to on any file that has ever been
     * used.
     */
    @Test
    void a_reward_the_old_check_never_heard_of_can_be_claimed() {
        SeededAccounts seeded = new SeededAccounts(reopenedHttp);
        long before = seeded.pointsBalanceOf(ANKE);

        ClaimedRewardView claimed = claim(reopenedHttp, anke, THE_FIFTH_REWARD);

        assertThat(claimed.code()).isEqualTo(THE_FIFTH_REWARD);
        assertThat(claimed.pointsSpent()).isEqualTo(THE_FIFTH_REWARD_COSTS);
        assertThat(claimed.voucherCode()).startsWith("SS-MYS-");
        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(before - THE_FIFTH_REWARD_COSTS);
    }

    /**
     * A voucher issued before vouchers had a state reads as issued at a counter, and can then be
     * handed over there like any other.
     *
     * <p>Both halves in one test on purpose, because using the voucher is a one-way door and two
     * tests sharing the only previous-release voucher in the file would pass or fail on whichever
     * of them the runner happened to start with. Read first, then spent, which is also the order
     * the person at the till does it in.
     *
     * <p>{@code ISSUED} is the only thing this row can truthfully be: the release that wrote it
     * could not use, expire or cancel a voucher. And it has to be spendable, not merely readable —
     * a backfill that made the screen look right and left the voucher dead would move the failure
     * from a page nobody was watching to a counter with somebody standing at it.
     */
    @Test
    void a_voucher_from_the_previous_release_is_issued_and_can_be_handed_over() {
        VoucherAtTheCounterView voucher = reopenedHttp.getForObject(
                "/api/staff/vouchers/{code}", VoucherAtTheCounterView.class, THE_OLD_VOUCHER);

        assertThat(voucher.state()).isEqualTo("ISSUED");
        assertThat(voucher.good()).isTrue();
        assertThat(voucher.code()).isEqualTo(THE_OLD_CLAIM_WAS_FOR);
        assertThat(voucher.customerId()).isEqualTo(anke);

        ResponseEntity<VoucherAtTheCounterView> used = reopenedHttp.postForEntity(
                "/api/staff/vouchers/{code}/use",
                Map.of("counter", A_COUNTER),
                VoucherAtTheCounterView.class,
                THE_OLD_VOUCHER);

        assertThat(used.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(used.getBody().state()).isEqualTo("USED");
        assertThat(used.getBody().usedByCounter()).isEqualTo(A_COUNTER);
        assertThat(used.getBody().usedAt()).isNotNull();
    }

    /**
     * The file is left in the shape this application generates, and nothing but the check has gone.
     * Read out of the database itself, because a schema is the one thing no endpoint reports.
     *
     * <p>All three halves matter. The check has to be gone or no new reward can ever be claimed;
     * the columns a claim fills have to still be there, including the title this release adds; and
     * the voucher code has to still be unique, because rebuilding a table is exactly how a
     * uniqueness guarantee gets quietly dropped and two customers end up holding one code.
     */
    @Test
    void the_check_is_gone_and_the_shape_around_it_is_not() {
        withTheDatabase(statement -> {
            assertThat(howTheTableIsDeclared(statement)).doesNotContainIgnoringCase("check");
            assertThat(columnsOf(statement, "redemption"))
                    .contains("id", "customer_id", "reward", "title", "points_spent",
                            "voucher_code", "claimed_at", "voucher_state", "used_at",
                            "used_by_counter");
            assertThat(theColumnsKeptUnique(statement)).contains(List.of("voucher_code"));
        });
    }

    /**
     * Puts the previous release's {@code redemption} table into the file, exactly as it is found in
     * {@code data/saving-streak.db}: the columns that release wrote, the check its enum mapping
     * generated, and one claim already in it.
     *
     * <p>Rebuilt rather than altered, because the constraint is the point and SQLite cannot add one
     * to a table that has none. The claim's moment goes in as epoch milliseconds, which is how that
     * release stored an instant and therefore the only faithful way to write one.
     */
    private static void putThePreviousReleasesRedemptionTableInTheFile() {
        withTheDatabase(statement -> {
            statement.executeUpdate("drop table redemption");
            statement.executeUpdate("create table redemption (id integer, claimed_at timestamp, "
                    + "customer_id bigint, points_spent bigint not null, "
                    + "reward varchar(255) check (reward in ('CHARITY_DONATION','SNACK_VOUCHER',"
                    + "'CINEMA_TICKET','FAMILY_CINEMA_PACK')), "
                    + "voucher_code varchar(255) not null unique, primary key (id))");
            statement.executeUpdate("insert into redemption "
                    + "(id, claimed_at, customer_id, points_spent, reward, voucher_code) values "
                    + "(1, " + IT_WAS_CLAIMED_AT.toEpochMilli() + ", " + anke + ", " + IT_COST
                    + ", '" + THE_OLD_CLAIM_WAS_FOR + "', '" + THE_OLD_VOUCHER + "')");

            assertThat(howTheTableIsDeclared(statement))
                    .as("the previous release's table this test claims to have written")
                    .containsIgnoringCase("check");
        });
    }

    /**
     * Adds the fifth reward as a row, which is what the administration screen will do and what the
     * old check makes unclaimable.
     *
     * <p>By hand for the same reason the table above is: there is no endpoint that writes an offer
     * yet, and this test is about what the start-up does to a file rather than about how the row
     * got into it.
     */
    private static void putAFifthRewardInTheCatalogue() {
        withTheDatabase(statement -> {
            int written = statement.executeUpdate("insert into reward_offer "
                    + "(code, title, words, cost_in_points, voucher_prefix, state, kind) values "
                    + "('" + THE_FIFTH_REWARD + "', 'Mystery box', 'Nobody knows, and that is the "
                    + "offer.', " + THE_FIFTH_REWARD_COSTS + ", 'MYS', 'PUBLISHED', 'ITEM')");
            assertThat(written).as("the fifth reward this test claims to have added").isEqualTo(1);
        });
    }

    private static String howTheTableIsDeclared(Statement statement) throws SQLException {
        try (ResultSet found = statement.executeQuery(
                "select sql from sqlite_master where type = 'table' and name = 'redemption'")) {
            assertThat(found.next()).as("a redemption table to read the declaration of").isTrue();
            return found.getString(1);
        }
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

    /** Every set of columns the table still refuses to hold two of the same values in. */
    private static List<List<String>> theColumnsKeptUnique(Statement statement) throws SQLException {
        List<String> indexes = new ArrayList<>();
        try (ResultSet found = statement.executeQuery("pragma index_list(redemption)")) {
            while (found.next()) {
                if (found.getInt("unique") == 1) {
                    indexes.add(found.getString("name"));
                }
            }
        }
        List<List<String>> unique = new ArrayList<>();
        for (String index : indexes) {
            List<String> columns = new ArrayList<>();
            try (ResultSet found = statement.executeQuery("pragma index_info(" + index + ")")) {
                while (found.next()) {
                    columns.add(found.getString("name"));
                }
            }
            unique.add(columns);
        }
        return unique;
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
