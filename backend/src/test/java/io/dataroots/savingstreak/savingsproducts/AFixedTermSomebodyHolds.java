package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ATermBrokenView;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.InterestPostingView;
import io.dataroots.savingstreak.support.MoneyMovementView;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.TermsVersionView;
import io.dataroots.savingstreak.support.TheTermOnAnAccountView;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole application of its own, on a database nothing has ever been written to, in which one
 * customer holds twelve-month fixed terms — and whose clock the test that started it may wind past
 * the day they mature.
 *
 * <p><strong>Its own application because it winds a year.</strong> Every test here moves the clock
 * forward by months to watch a refusal turn into an allowed withdrawal, which nothing sharing a
 * clock or a database could survive — the same reason the streak, expiry, loyalty and notice tests
 * each run an application of their own.
 *
 * <p><strong>The account is opened on the product by name, which is the short way round.</strong>
 * The notice harness beside this one publishes a version of free savings carrying a notice period,
 * because when it was written nothing could open an account on a chosen product; that door exists
 * now, so this opens on {@code FIXED12} directly and the terms under test are the ones the bank
 * actually seeds. Every assertion is still about what the <em>agreement</em> says — the months, the
 * maturity date, the penalty days — and never about the product code being {@code FIXED12}, which
 * is the difference between the condition living in the terms and the condition living in the code.
 *
 * <p><strong>Two accounts, and both are needed.</strong> One is broken early, which moves it off
 * its term for good and makes it useless for asking what a locked account does afterwards; the
 * other is left alone so that the clock can be wound past its maturity and the same refusal watched
 * turning into an allowed withdrawal. One account could not be both, because breaking is one-way
 * and the clock only moves forward.
 *
 * <p>Driven entirely over HTTP, endpoints and all, so a test using it knows nothing about the
 * database or the columns underneath. The clock is moved through the endpoint a trainer would use,
 * which is how "come back in three hundred and sixty-five days" becomes something a test can watch
 * happen.
 */
final class AFixedTermSomebodyHolds implements AutoCloseable {

    /** The zone this application counts its days in, which is the zone a term matures in. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /** The twelve-month fixed term the catalogue seeds, which is what these tests are about. */
    static final String THE_FIXED_TERM = "FIXED12";

    /** What the account is moved onto when its term is broken. */
    static final String FREE_SAVINGS = "INSTANT";

    private final ConfigurableApplicationContext application;
    private final TestRestTemplate http;
    private final long customerId;
    private final long currentAccountId;

    AFixedTermSomebodyHolds(Path databaseFile, String whoHoldsIt) {
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
                Map.of("name", whoHoldsIt, "contactDetails",
                        whoHoldsIt.replace(' ', '.') + "@example.be"),
                CustomerView.class);
        assertThat(added.getStatusCode()).as("opening %s", whoHoldsIt)
                .isEqualTo(HttpStatus.CREATED);
        this.customerId = added.getBody().id();
        this.currentAccountId = overview().currentAccounts().get(0).id();
    }

    /** Opens another savings account on the twelve-month fixed term, with a refusal ruled out. */
    long openAFixedTerm() {
        ResponseEntity<AnAccountView> opened = http.postForEntity(
                "/api/customers/{id}/savings-accounts", Map.of("product", THE_FIXED_TERM),
                AnAccountView.class, customerId);
        assertThat(opened.getStatusCode()).as("opening a savings account on %s", THE_FIXED_TERM)
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

    /** What this account's term says today: the months, the maturity date and the price of ending it. */
    TheTermOnAnAccountView theTermOn(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}/term", TheTermOnAnAccountView.class,
                savingsAccountId);
    }

    /** Breaks the term, with the refusal ruled out: this is the half of the rule that allows. */
    ATermBrokenView breakTheTerm(long savingsAccountId) {
        ResponseEntity<ATermBrokenView> broken = http.postForEntity(
                "/api/savings-accounts/{id}/term/break", null, ATermBrokenView.class,
                savingsAccountId);
        assertThat(broken.getStatusCode()).as("breaking the term on savings account %d",
                savingsAccountId).isEqualTo(HttpStatus.OK);
        return broken.getBody();
    }

    /** The same press, with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToBreakTheTerm(long savingsAccountId) {
        return http.postForEntity("/api/savings-accounts/{id}/term/break", null, JsonNode.class,
                savingsAccountId);
    }

    /** Puts money in, with the refusal ruled out: a deposit that failed would leave nothing to take. */
    void payIn(long savingsAccountId, String amount) {
        ResponseEntity<JsonNode> made = http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId), JsonNode.class,
                savingsAccountId);
        assertThat(made.getStatusCode()).as("paying EUR %s into savings account %d", amount,
                savingsAccountId).isEqualTo(HttpStatus.CREATED);
    }

    /** Takes money out, with the refusal ruled out: this is the half of the rule that allows. */
    void takeOut(long savingsAccountId, String amount) {
        ResponseEntity<JsonNode> made = tryToTakeOut(savingsAccountId, amount);
        assertThat(made.getStatusCode()).as("taking EUR %s out of savings account %d: %s", amount,
                        savingsAccountId,
                        made.getBody() == null ? "" : made.getBody().path("detail").asText())
                .isEqualTo(HttpStatus.CREATED);
    }

    /** The same request, with whatever came back, for the half of the rule that refuses. */
    ResponseEntity<JsonNode> tryToTakeOut(long savingsAccountId, String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", currentAccountId), JsonNode.class,
                savingsAccountId);
    }

    /**
     * Every movement in or out of this customer's savings accounts, newest first — the record of
     * money that moved, which is where a charge has to be visible.
     */
    List<MoneyMovementView> theMoneyThatMoved() {
        return http.exchange("/api/customers/{id}/money-movements", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<MoneyMovementView>>() { }, customerId)
                .getBody();
    }

    /** What this account's holder has taken back out of it, which is not the same list. */
    List<AWithdrawalView> theWithdrawalsFrom(long savingsAccountId) {
        return http.exchange("/api/savings-accounts/{id}/withdrawals", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<AWithdrawalView>>() { }, savingsAccountId)
                .getBody();
    }

    /** Which version that product is selling today, asked of the catalogue rather than written down. */
    int whatIsBeingSoldToday(String product) {
        return http.getForObject("/api/savings-products/{code}", SavingsProductView.class, product)
                .currentTerms().version();
    }

    /**
     * One published version of a product's terms, by the number an account names it with.
     *
     * <p>The rate a test checks interest against is read from here rather than written down,
     * because an account is paid at the rate <em>its own</em> version names and free savings has
     * published two of them at two different rates. An account that broke its term is on whichever
     * of those was being sold on the day it broke, and a test that hard-coded either would be
     * asserting what the seed happens to say this month.
     */
    TermsVersionView theVersionOf(String product, int version) {
        TermsVersionView[] published = http.getForObject(
                "/api/savings-products/{code}/versions", TermsVersionView[].class, product);
        return List.of(published).stream()
                .filter(terms -> terms.version() == version)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        product + " has never published a version " + version));
    }

    /**
     * Runs a named job now and insists that it ran: a job refused for want of a name, or one that
     * threw, would leave a test asserting that nothing had happened — and passing.
     */
    void runJob(String name) {
        ResponseEntity<JsonNode> ran = http.postForEntity("/api/dev/jobs/{name}/run", null,
                JsonNode.class, name);
        assertThat(ran.getStatusCode()).as("running the %s job", name).isEqualTo(HttpStatus.OK);
    }

    /** Every month of interest this account has been paid, off the account's own page. */
    List<InterestPostingView> interestPaidInto(long savingsAccountId) {
        ResponseEntity<InterestPostingView[]> read = http.getForEntity(
                "/api/savings-accounts/{id}/interest", InterestPostingView[].class,
                savingsAccountId);
        assertThat(read.getStatusCode())
                .as("reading the interest paid into savings account %d", savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
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

    /**
     * Only the amount, because that is all the one test reading this list asserts: that the charge
     * the bank took is not in the customer's own record of what they took out.
     */
    record AWithdrawalView(Long id, BigDecimal amount) {
    }
}
