package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.NoticeView;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.TheNoticeOnAnAccountView;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole application of its own, on a database nothing has ever been written to, in which one
 * customer holds a savings account that asks for thirty-two days' notice — and whose clock the test
 * that started it may wind through that notice period.
 *
 * <p><strong>Its own application because it winds months.</strong> Every test here moves the clock
 * forward by weeks to watch a refusal turn into an allowed withdrawal, which nothing sharing a
 * clock or a database could survive — the same reason the streak, expiry and loyalty tests each run
 * an application of their own, and the same reason the administration tests do.
 *
 * <p><strong>How the account comes to be a notice account is the one thing here worth
 * arguing.</strong> Nothing in this application can yet open a savings account on a product of the
 * customer's choosing; that is the slice after the migration and it has not landed. What exists
 * already is the administration door that publishes a new version of a product's terms, and the
 * rule that an account opened today is opened on whatever version is being sold today. So this
 * harness publishes a version of free savings that asks for thirty-two days' notice and then opens
 * a customer, who is written onto it — using two doors this API already has and inventing nothing.
 *
 * <p>That is deliberately the long way round, and it buys something: the account under test is on
 * the <em>instant-access</em> product, so every assertion here is about the notice days its version
 * of the terms asks for and never about a product code or a kind. A withdrawal is refused because
 * the agreement says thirty-two days, not because somebody wrote {@code NOTICE32} into a rule —
 * which is the difference between the condition living in the terms and the condition living in the
 * code, and it is the whole of what this feature is for. When opening on a named product arrives,
 * this class changes to open on {@code NOTICE32} and not one assertion in the tests moves.
 *
 * <p>Driven entirely over HTTP, endpoints and all, so a test using it knows nothing about the
 * database or the columns underneath. The clock is moved through the endpoint a trainer would use,
 * which is how "come back in eleven days" becomes something a test can watch happen.
 */
final class ANoticeAccountSomebodyHolds implements AutoCloseable {

    /** The zone this application counts its days in, which is the zone a notice is dated in. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /** What the version this harness publishes asks for, and what every sentence here quotes. */
    static final int THE_DAYS_IT_ASKS_FOR = 32;

    /** The product whose terms are republished with a notice period, for the reason above. */
    private static final String FREE_SAVINGS = "INSTANT";

    private final ConfigurableApplicationContext application;
    private final TestRestTemplate http;
    private final long savingsAccountId;
    private final long currentAccountId;

    ANoticeAccountSomebodyHolds(Path databaseFile, String whoHoldsIt) {
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
        putThirtyTwoDaysNoticeOnWhatIsBeingSoldToday();
        // Opened after the version is published and never before, because an account is written
        // onto the version being sold the moment it is opened. A customer opened first would be on
        // the old terms for good, which is the promise the whole feature rests on and is exactly
        // why the order matters here.
        long[] theirs = openACustomer(whoHoldsIt);
        this.savingsAccountId = theirs[0];
        this.currentAccountId = theirs[1];
    }

    /** The savings account under test, which asks for thirty-two days' notice. */
    long savingsAccount() {
        return savingsAccountId;
    }

    /** What the account holds, read off its own page rather than summed here. */
    BigDecimal balance() {
        return theAccount().moneyBalance();
    }

    /** The agreement the account is living under, for the test that checks it is a notice one. */
    BalancesView theAccount() {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class,
                savingsAccountId);
    }

    /** Puts money in, with the refusal ruled out: a deposit that failed would leave nothing to take. */
    void payIn(String amount) {
        ResponseEntity<JsonNode> made = http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId), JsonNode.class,
                savingsAccountId);
        assertThat(made.getStatusCode()).as("paying in EUR %s", amount)
                .isEqualTo(HttpStatus.CREATED);
    }

    /** Gives notice on an amount, refusal ruled out, and answers with the notice. */
    NoticeView giveNoticeOn(String amount) {
        ResponseEntity<NoticeView> given = http.postForEntity(
                "/api/savings-accounts/{id}/notices", Map.of("amount", amount), NoticeView.class,
                savingsAccountId);
        assertThat(given.getStatusCode()).as("giving notice on EUR %s", amount)
                .isEqualTo(HttpStatus.CREATED);
        return given.getBody();
    }

    /** The same request, with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToGiveNoticeOn(String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/notices", Map.of("amount", amount),
                JsonNode.class, savingsAccountId);
    }

    /** What this account's notice says today: the days, the two totals and what is still standing. */
    TheNoticeOnAnAccountView theNotice() {
        return http.getForObject("/api/savings-accounts/{id}/notices",
                TheNoticeOnAnAccountView.class, savingsAccountId);
    }

    /** Cancels a notice, refusal ruled out. */
    NoticeView cancel(long noticeId) {
        ResponseEntity<NoticeView> cancelled = http.postForEntity(
                "/api/savings-accounts/{id}/notices/{noticeId}/cancel", null, NoticeView.class,
                savingsAccountId, noticeId);
        assertThat(cancelled.getStatusCode()).as("cancelling notice %d", noticeId)
                .isEqualTo(HttpStatus.OK);
        return cancelled.getBody();
    }

    /** The same request, with whatever came back. */
    ResponseEntity<JsonNode> tryToCancel(long noticeId) {
        return http.postForEntity("/api/savings-accounts/{id}/notices/{noticeId}/cancel", null,
                JsonNode.class, savingsAccountId, noticeId);
    }

    /** Takes money out, with the refusal ruled out: this is the half of the rule that allows. */
    void takeOut(String amount) {
        ResponseEntity<JsonNode> made = tryToTakeOut(amount);
        assertThat(made.getStatusCode()).as("taking out EUR %s: %s", amount,
                        made.getBody() == null ? "" : made.getBody().path("detail").asText())
                .isEqualTo(HttpStatus.CREATED);
    }

    /** The same request, with whatever came back, for the half of the rule that refuses. */
    ResponseEntity<JsonNode> tryToTakeOut(String amount) {
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

    /**
     * Republishes free savings with a notice period, so that anybody opening an account from this
     * moment on is opened onto terms that ask for thirty-two days.
     *
     * <p>Every figure of the version before it is carried over unchanged and one is moved, built
     * from a version that was actually served rather than typed here — the same form the
     * administration screen sends, and the same helper the administration tests use, so that this
     * harness cannot quietly assert against a shape the API does not have.
     */
    private void putThirtyTwoDaysNoticeOnWhatIsBeingSoldToday() {
        TermsVersionView beingSoldToday = http.getForObject("/api/savings-products/{code}",
                SavingsProductView.class, FREE_SAVINGS).currentTerms();
        Map<String, Object> withNotice = ACatalogueSomebodyAdministers.theSameTermsAgain(
                beingSoldToday, theDateTheClockReads(),
                "Money now leaves this account " + THE_DAYS_IT_ASKS_FOR + " days after notice is "
                        + "given on it. Nothing else changed, and accounts opened before today "
                        + "carry on with nothing to give notice of.");
        withNotice.put("noticeDays", String.valueOf(THE_DAYS_IT_ASKS_FOR));
        ResponseEntity<JsonNode> published = http.postForEntity(
                "/api/admin/savings-products/{code}/versions", withNotice, JsonNode.class,
                FREE_SAVINGS);
        assertThat(published.getStatusCode())
                .as("publishing terms with a notice period: %s", withNotice)
                .isEqualTo(HttpStatus.CREATED);
    }

    /** A customer of this application's own, and the two accounts they are opened with. */
    private long[] openACustomer(String name) {
        ResponseEntity<CustomerView> added = http.postForEntity("/api/customers",
                Map.of("name", name, "contactDetails", name.replace(' ', '.') + "@example.be"),
                CustomerView.class);
        assertThat(added.getStatusCode()).as("opening %s", name).isEqualTo(HttpStatus.CREATED);
        OverviewView theirs = http.getForObject("/api/customers/{id}/accounts", OverviewView.class,
                added.getBody().id());
        return new long[] {
                theirs.savingsAccounts().get(0).id(),
                theirs.currentAccounts().get(0).id() };
    }

    @Override
    public void close() {
        application.close();
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
