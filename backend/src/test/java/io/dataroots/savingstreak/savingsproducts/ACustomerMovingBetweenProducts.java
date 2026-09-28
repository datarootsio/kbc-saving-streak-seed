package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.AMoveView;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.NoticeView;
import io.dataroots.savingstreak.support.TheNoticeOnAnAccountView;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole application of its own, on a database nothing has ever been written to, in which one
 * customer holds savings accounts on several products at once — and whose clock the test that
 * started it may wind through a notice period.
 *
 * <p><strong>Its own application because it winds days.</strong> A move refused for a notice period
 * that has not run is only half the rule; the other half is watching the refusal turn into an
 * allowed move thirty-two days later, and then watching the notice it ran on be gone. Nothing
 * sharing a clock or a database with the rest of the run could survive that, which is the same
 * reason the notice, term, streak and loyalty tests each run one of these.
 *
 * <p><strong>Accounts are opened on the products by name</strong>, which is the short way round the
 * notice harness beside this one could not take when it was written. Every assertion in the tests
 * using this is still about what the <em>agreement</em> says — the days it asks for, the months it
 * locks for — and never about the product code, which is the difference between a condition living
 * in the terms and a condition living in the code.
 *
 * <p>Driven entirely over HTTP, endpoints and all, so a test using it knows nothing about the
 * database or the columns underneath. The clock is moved through the endpoint a trainer would use.
 */
final class ACustomerMovingBetweenProducts implements AutoCloseable {

    /** The zone this application counts its days in, which is the zone a notice is dated in. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /** What the thirty-two day notice product the catalogue seeds asks for. */
    static final int THE_DAYS_NOTICE_ASKS_FOR = 32;

    private final ConfigurableApplicationContext application;
    private final TestRestTemplate http;
    private final long customerId;
    private final long currentAccountId;
    private final long theAccountTheyWereOpenedWith;

    ACustomerMovingBetweenProducts(Path databaseFile, String whoHoldsThem) {
        // Command-line arguments rather than default properties, for the reason the walking
        // skeleton gives: defaults lose to application.properties, which would point this instance
        // at the real database.
        this.application = new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + databaseFile,
                        "--spring.profiles.active=dev",
                        "--server.port=0");
        this.http = new TestRestTemplate();
        this.http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        ResponseEntity<CustomerView> added = http.postForEntity("/api/customers",
                Map.of("name", whoHoldsThem, "contactDetails",
                        whoHoldsThem.replace(' ', '.') + "@example.be"),
                CustomerView.class);
        assertThat(added.getStatusCode()).as("opening %s", whoHoldsThem)
                .isEqualTo(HttpStatus.CREATED);
        this.customerId = added.getBody().id();
        OverviewView theirs = overview();
        this.currentAccountId = theirs.currentAccounts().get(0).id();
        this.theAccountTheyWereOpenedWith = theirs.savingsAccounts().get(0).id();
    }

    /** The savings account they were opened with, which is on instant access. */
    long theAccountTheyWereOpenedWith() {
        return theAccountTheyWereOpenedWith;
    }

    /** Opens another savings account on that product, with a refusal ruled out. */
    long open(String product) {
        ResponseEntity<AnAccountView> opened = http.postForEntity(
                "/api/customers/{id}/savings-accounts", Map.of("product", product),
                AnAccountView.class, customerId);
        assertThat(opened.getStatusCode()).as("opening a savings account on %s", product)
                .isEqualTo(HttpStatus.CREATED);
        return opened.getBody().id();
    }

    /** What one of their accounts holds and what it is living under, off its own page. */
    BalancesView theAccount(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class,
                savingsAccountId);
    }

    /** What the account holds, read off its own page rather than summed here. */
    BigDecimal balanceOf(long savingsAccountId) {
        return theAccount(savingsAccountId).moneyBalance();
    }

    /** Puts money in, refusal ruled out: a deposit that failed would leave nothing to move. */
    void payIn(long savingsAccountId, String amount) {
        ResponseEntity<JsonNode> made = http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId), JsonNode.class,
                savingsAccountId);
        assertThat(made.getStatusCode()).as("paying EUR %s into savings account %d", amount,
                savingsAccountId).isEqualTo(HttpStatus.CREATED);
    }

    /** Gives notice on an amount, refusal ruled out, and answers with the notice. */
    NoticeView giveNoticeOn(long savingsAccountId, String amount) {
        ResponseEntity<NoticeView> given = http.postForEntity(
                "/api/savings-accounts/{id}/notices", Map.of("amount", amount), NoticeView.class,
                savingsAccountId);
        assertThat(given.getStatusCode()).as("giving notice on EUR %s", amount)
                .isEqualTo(HttpStatus.CREATED);
        return given.getBody();
    }

    /** What this account's notice says today: the days, the two totals and what is still standing. */
    TheNoticeOnAnAccountView theNoticeOn(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}/notices",
                TheNoticeOnAnAccountView.class, savingsAccountId);
    }

    /** Moves money between two of their own savings accounts, with the refusal ruled out. */
    AMoveView move(long fromSavingsAccountId, long toSavingsAccountId, String amount) {
        ResponseEntity<AMoveView> moved = http.postForEntity("/api/savings-accounts/{id}/moves",
                Map.of("amount", amount, "toSavingsAccountId", toSavingsAccountId), AMoveView.class,
                fromSavingsAccountId);
        assertThat(moved.getStatusCode()).as("moving EUR %s from savings account %d to %d", amount,
                fromSavingsAccountId, toSavingsAccountId).isEqualTo(HttpStatus.CREATED);
        return moved.getBody();
    }

    /** The same press with whatever came back, for the half of the rule that refuses. */
    ResponseEntity<JsonNode> tryToMove(long fromSavingsAccountId, long toSavingsAccountId,
                                       String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/moves",
                Map.of("amount", amount, "toSavingsAccountId", toSavingsAccountId), JsonNode.class,
                fromSavingsAccountId);
    }

    /** Asking what moving would cost, with whatever came back — it refuses what the move refuses. */
    ResponseEntity<JsonNode> tryToAskWhatMovingWouldCost(long fromSavingsAccountId,
                                                         long toSavingsAccountId, String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/moves/preview",
                Map.of("amount", amount, "toSavingsAccountId", toSavingsAccountId), JsonNode.class,
                fromSavingsAccountId);
    }

    /** Taking money back out, with whatever came back — the sentence a move has to match. */
    ResponseEntity<JsonNode> tryToTakeOut(long savingsAccountId, String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", currentAccountId), JsonNode.class,
                savingsAccountId);
    }

    /** The day this application thinks it is, read off its own clock rather than the machine's. */
    LocalDate theDateTheClockReads() {
        return LocalDate.ofInstant(
                http.getForObject("/api/dev/clock", ClockView.class).now(), BRUSSELS);
    }

    /** Moves the clock, through the endpoint a trainer would use. */
    void daysPass(long days) {
        ResponseEntity<ClockView> moved = http.postForEntity("/api/dev/clock/advance",
                Map.of("days", days), ClockView.class);
        assertThat(moved.getStatusCode()).as("moving the clock %d days on", days)
                .isEqualTo(HttpStatus.OK);
    }

    /** The sentence the domain wrote, carried into the problem detail untouched. */
    static String reasonGivenBy(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).as("the problem detail").isNotNull();
        return response.getBody().path("detail").asText();
    }

    @Override
    public void close() {
        application.close();
    }

    private OverviewView overview() {
        return http.getForObject("/api/customers/{id}/accounts", OverviewView.class, customerId);
    }

    private record CustomerView(Long id, String name, String contactDetails) {
    }

    /**
     * Only the two lists, because that is all this harness reads off the overview. The figures
     * beside them belong to the customer and have their own tests; naming them here would be a
     * second copy of a shape that already has one.
     */
    private record OverviewView(List<AnAccountView> currentAccounts,
                                List<AnAccountView> savingsAccounts) {
    }

    private record AnAccountView(Long id) {
    }
}
