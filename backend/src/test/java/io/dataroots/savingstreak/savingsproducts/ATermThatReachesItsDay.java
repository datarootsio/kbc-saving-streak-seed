package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.InterestPostingView;
import io.dataroots.savingstreak.support.JobRunView;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.ScheduledJobView;
import io.dataroots.savingstreak.support.TermsVersionView;
import io.dataroots.savingstreak.support.TheTermOnAnAccountView;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole application of its own, on a database nothing has ever been written to, in which one
 * customer holds terms with each of the three endings — and whose clock the test that started it
 * may wind years past the day they mature.
 *
 * <p><strong>Its own application because it winds years and publishes versions.</strong> Every test
 * here moves the clock forward by whole terms to watch a maturity settle, and two of them publish
 * versions of seeded products to get the endings the catalogue does not sell — neither of which
 * anything sharing a clock or a database could survive. The same reason the term, notice, loyalty
 * and administration harnesses each run an application of their own, and the same one
 * {@link ACatalogueSomebodyAdministers} gives about publishing being a thing that cannot be undone.
 *
 * <p><strong>Three endings out of one catalogue, which seeds only one of them.</strong> The bank
 * sells exactly one product with a term and its terms say to roll over. The other two endings are
 * reached by publishing a version of a product that has no term today — the notice account and the
 * core saver — carrying twelve months and the ending under test. That is not a contrivance: it is
 * the administration door doing the thing it exists for, and it means the tests assert against terms
 * somebody actually published rather than against rows written behind the API's back. It also keeps
 * the twelve-month fixed term itself on version 1 for the whole run, which is what lets the rolling
 * account roll three times into the same terms.
 *
 * <p><strong>The sweep is run by name through the development jobs endpoint</strong>, which is the
 * door a trainer uses and the only way a test can make a nightly job happen on an afternoon. Running
 * it is also half of what several of these tests assert: a job that is not reachable by name is a
 * job nobody can demonstrate.
 *
 * <p>Driven entirely over HTTP, endpoints and all, so a test using it knows nothing about the
 * database or the columns underneath.
 */
final class ATermThatReachesItsDay implements AutoCloseable {

    /** The zone this application counts its days in, which is the zone a term matures in. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /** The twelve-month fixed term the catalogue seeds, whose terms say to roll over. */
    static final String THE_ROLLING_TERM = "FIXED12";

    /** The notice account, republished by these tests as a term that waits at the end. */
    static final String THE_ONE_REPUBLISHED_TO_WAIT = "NOTICE32";

    /** The core saver, republished by these tests as a term that comes free at the end. */
    static final String THE_ONE_REPUBLISHED_TO_COME_FREE = "CORE";

    /** What free savings is, which is where a term set to move to instant access lands. */
    static final String FREE_SAVINGS = "INSTANT";

    /** The name the maturity sweep answers to on the development jobs endpoint. */
    static final String THE_MATURITY_SWEEP = "settleMaturedTerms";

    /** The name the interest sweep answers to, which runs half an hour after it. */
    static final String THE_INTEREST_SWEEP = "postMonthlyInterest";

    /** How long every term these tests publish runs for, matching the one the bank seeds. */
    static final int TWELVE_MONTHS = 12;

    private final ConfigurableApplicationContext application;
    private final TestRestTemplate http;
    private final long customerId;
    private final long currentAccountId;

    ATermThatReachesItsDay(Path databaseFile, String whoHoldsThem) {
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
        this.currentAccountId = overview().currentAccounts().get(0).id();
    }

    /**
     * Publishes a version of a product that turns it into a twelve-month term with the ending named,
     * effective today, and answers with it.
     *
     * <p>Built out of the version the product is offering now, figure for figure, because a version
     * carries nothing over from the one before it — so this changes the three figures the test is
     * about and sends the rest back exactly as they were, which is what the administration screen's
     * pre-filled form does. {@link ACatalogueSomebodyAdministers#theSameTermsAgain} is borrowed
     * rather than copied, for the reason it gives about two copies of a shape.
     */
    TermsVersionView republishAsATermThat(String product, String ending, String penaltyDays) {
        return republishAsATermThat(product, ending, penaltyDays, theDateTheClockReads(), null);
    }

    /**
     * The same, taking the day it comes into effect and, optionally, a rate to change as well.
     *
     * <p>The day is an argument because one test needs a version that is on offer <em>today</em> and
     * was not on offer on the morning an account's term matured — which is the only arrangement in
     * which "pinned to the version current on its own day" says something different from "pinned to
     * the version current tonight". A version effective in the past is an ordinary thing for a bank
     * to publish, and the module accepts one.
     */
    TermsVersionView republishAsATermThat(String product, String ending, String penaltyDays,
                                          LocalDate effectiveFrom, String annualRatePercent) {
        List<TermsVersionView> versions = versionsOf(product);
        Map<String, Object> form = ACatalogueSomebodyAdministers.theSameTermsAgain(
                versions.get(versions.size() - 1), effectiveFrom,
                "Now a " + TWELVE_MONTHS + "-month term that " + ending + " at the end of it.");
        if (annualRatePercent != null) {
            form.put("annualRatePercent", annualRatePercent);
        }
        form.put("termMonths", String.valueOf(TWELVE_MONTHS));
        form.put("earlyExitPenaltyDays", penaltyDays);
        // A term account gives no notice: the two conditions would stack, and what these tests are
        // about is the ending rather than the way out.
        form.put("noticeDays", "0");
        form.put("maturityAction", ending);
        ResponseEntity<TermsVersionView> published = http.postForEntity(
                "/api/admin/savings-products/{code}/versions", form, TermsVersionView.class,
                product);
        assertThat(published.getStatusCode()).as("republishing %s as a term that %s: %s", product,
                ending, form).isEqualTo(HttpStatus.CREATED);
        return published.getBody();
    }

    /** Opens a savings account on that product, with a refusal ruled out. */
    long openAnAccountOn(String product) {
        ResponseEntity<AnAccountView> opened = http.postForEntity(
                "/api/customers/{id}/savings-accounts", Map.of("product", product),
                AnAccountView.class, customerId);
        assertThat(opened.getStatusCode()).as("opening a savings account on %s", product)
                .isEqualTo(HttpStatus.CREATED);
        return opened.getBody().id();
    }

    /** Every version that product has published, oldest first. */
    List<TermsVersionView> versionsOf(String product) {
        return List.of(http.getForObject("/api/savings-products/{code}/versions",
                TermsVersionView[].class, product));
    }

    /** What one of their accounts holds and what it is living under, off its own page. */
    BalancesView theAccount(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class,
                savingsAccountId);
    }

    /** What the account is living under, which is the row a maturity rewrites. */
    AnAgreementView theAgreementOf(long savingsAccountId) {
        return theAccount(savingsAccountId).agreement();
    }

    /** What the account holds, read off its own page rather than summed here. */
    BigDecimal balanceOf(long savingsAccountId) {
        return theAccount(savingsAccountId).moneyBalance();
    }

    /** What this account's term says today: the months, the maturity date and the ending. */
    TheTermOnAnAccountView theTermOn(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}/term", TheTermOnAnAccountView.class,
                savingsAccountId);
    }

    /** Every month this account has been judged for, oldest first, with the rate each was paid at. */
    List<InterestPostingView> theInterestPaidInto(long savingsAccountId) {
        return http.exchange("/api/savings-accounts/{id}/interest", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<InterestPostingView>>() { }, savingsAccountId)
                .getBody();
    }

    /** Puts money in, with the refusal ruled out: a deposit that failed would leave nothing to lock. */
    void payIn(long savingsAccountId, String amount) {
        ResponseEntity<JsonNode> made = http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId), JsonNode.class,
                savingsAccountId);
        assertThat(made.getStatusCode()).as("paying EUR %s into savings account %d: %s", amount,
                        savingsAccountId,
                        made.getBody() == null ? "" : made.getBody().path("detail").asText())
                .isEqualTo(HttpStatus.CREATED);
    }

    /** Takes money out, with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToTakeOut(long savingsAccountId, String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", currentAccountId), JsonNode.class,
                savingsAccountId);
    }

    /** The jobs this application says it has, which is where a sweep has to be reachable by name. */
    List<ScheduledJobView> theJobsThereAreToRun() {
        return List.of(http.getForObject("/api/dev/jobs", ScheduledJobView[].class));
    }

    /** Runs a job by name through the endpoint a trainer would use, with the refusal ruled out. */
    JobRunView run(String job) {
        ResponseEntity<JobRunView> ran = http.postForEntity("/api/dev/jobs/{name}/run", null,
                JobRunView.class, job);
        assertThat(ran.getStatusCode()).as("running the job %s", job).isEqualTo(HttpStatus.OK);
        return ran.getBody();
    }

    /** Which version that product is selling today, asked of the catalogue rather than written down. */
    int whatIsBeingSoldToday(String product) {
        return http.getForObject("/api/savings-products/{code}", SavingsProductView.class, product)
                .currentTerms().version();
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
