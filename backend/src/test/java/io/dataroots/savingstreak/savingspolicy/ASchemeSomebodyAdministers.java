package io.dataroots.savingstreak.savingspolicy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SchemePreviewView;
import io.dataroots.savingstreak.support.SchemeView;
import io.dataroots.savingstreak.support.SchemeVersionPublishedView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole application of its own, on a database nothing has ever been written to, whose scheme the
 * test that started it may publish versions of.
 *
 * <p><strong>Its own application because publishing cannot be undone.</strong> That is the promise
 * of the whole module — a version that has been published is never edited and never deleted — so a
 * test that published version 2 of the scheme into the run's shared database would leave it there
 * for every test afterwards, and the tests that pin the scheme to one seeded version at the figures
 * this application has always run on are the rail this feature is built not to break. There is no
 * tidying-up that could put it back, by design. It is the same answer
 * {@code ACatalogueSomebodyAdministers} reaches for, for the same reason, one module along.
 *
 * <p><strong>Driven entirely over HTTP</strong>, endpoints and all, so a test using it knows nothing
 * about the database or the columns underneath. The clock is moved through the endpoint a trainer
 * would use, which is how "a version announced for next Monday is not in force until next Monday,
 * and is on the Monday" becomes something a test can watch happen rather than something it has to
 * take on trust.
 *
 * <p><strong>It saves and reads as well as publishing, which it did not need to until a week was
 * judged by the scheme in force on its Monday.</strong> The tests that ticket added are about what a
 * published version does to somebody's run of weeks, so they have to pay money in, let weeks go by
 * and read an account back — and they have to do all of that in the <em>same</em> application the
 * version was published into. Two applications would not do: a version published in one is not the
 * scheme the other runs on, and the whole claim under test is about one bank changing its mind about
 * one customer's weeks. So the deposits and the reads are here, driven over the same endpoints
 * {@code AnApplicationWithAClockToMove} drives them over and answering with the same shared views.
 *
 * <p>Not folded into {@code AnApplicationWithAClockToMove} itself, although the two fixtures now
 * overlap. That one is what every streak test in the run already boots, and giving it a publishing
 * door would put a door that cannot be undone within reach of thirty tests whose subject is a scheme
 * that never changes. Two fixtures that overlap is the cheaper mistake.
 *
 * <p>Shared between the publishing tests rather than copied into each, for the reason the shared
 * views give: three copies of "boot an application and publish a version of the scheme" are three
 * chances to disagree about what publishing one is.
 *
 * <p><strong>Public for the sake of {@link #theSameSchemeAgain} alone.</strong> The tests that watch
 * a changed figure reach a rule — a threshold that decides what the notification sweep says, a rate
 * a streak is paid at — are in the modules those rules live in, and they drive an application of
 * their own that can also deposit money and run a nightly job. What they must not do is write out
 * their own copy of the publishing form: a version carries nothing over from the version before it,
 * so the form is every figure every time, and a second copy of that list is the thing that quietly
 * stops matching the shape the API has. So the builder is shared and this class is visible; the
 * application it holds is still only for the tests whose subject is publishing itself.
 */
public final class ASchemeSomebodyAdministers implements AutoCloseable {

    /** The zone this application counts its days — and therefore its Mondays — in. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    private final ConfigurableApplicationContext application;
    private final TestRestTemplate http;
    /** The seeded households, found over the API, so that a test starts from an account to save into. */
    private final SeededAccounts seeded;

    ASchemeSomebodyAdministers(Path databaseFile) {
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
        this.seeded = new SeededAccounts(http);
    }

    /** The first savings account the customer holds, with no deposit ever made into it. */
    long savingsAccountOf(String customerName) {
        return seeded.savingsAccountOf(customerName);
    }

    /**
     * A deposit this test needed to land, with the refusal ruled out rather than assumed: a refused
     * deposit would leave a test asserting that a week nothing landed in secured nothing — and
     * passing.
     */
    DepositView deposit(long savingsAccountId, String customerName, String amount) {
        ResponseEntity<DepositView> made = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", seeded.currentAccountOf(customerName)),
                DepositView.class, savingsAccountId);
        assertThat(made.getStatusCode())
                .as("paying EUR %s into savings account %d", amount, savingsAccountId)
                .isEqualTo(HttpStatus.CREATED);
        return made.getBody();
    }

    /** Every deposit into the account, oldest first, each still quoting the rate it was paid at. */
    List<DepositView> depositsInto(long savingsAccountId) {
        return List.of(http.getForObject("/api/savings-accounts/{id}/deposits", DepositView[].class,
                savingsAccountId));
    }

    /**
     * The account as the API reports it: both balances, the week and what it asks for, the run of
     * weeks behind it, what that run pays, and the version of the scheme that decided the last two.
     */
    BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    /**
     * One of this application's own beans, by type, for the one question this module answers that no
     * endpoint asks.
     *
     * <p>The exception rather than the way in, in exactly the words
     * {@code AnApplicationWithAClockToMove} uses for the same escape hatch. There is precisely one
     * claim here that no sequence of requests can arrange: the development clock moves forward only,
     * on purpose, so a week behind the Monday the scheme was seeded on cannot be reached by winding
     * anything. The rule about such a week is real and load-bearing all the same — no week is ever
     * judged under nothing — so it is asked of the module that owns it, once, by a test that drove
     * everything else through the endpoints.
     */
    <T> T theApplicationsOwn(Class<T> type) {
        return application.getBean(type);
    }

    /** What the scheme says today, as any customer reads it. */
    SchemeView theSchemeInForce() {
        return http.getForObject("/api/scheme", SchemeView.class);
    }

    /** Every version the bank has published, newest first. */
    List<SchemeView> everyVersionPublished() {
        return List.of(http.getForObject("/api/scheme/versions", SchemeView[].class));
    }

    /**
     * Publishes a version, with the refusal ruled out rather than assumed: a refused publish would
     * leave a test asserting that nothing changed — and passing.
     */
    SchemeVersionPublishedView publish(Map<String, Object> form) {
        ResponseEntity<SchemeVersionPublishedView> published = http.postForEntity(
                "/api/admin/scheme/versions", form, SchemeVersionPublishedView.class);
        assertThat(published.getStatusCode())
                .as("publishing a version of the scheme: %s", form)
                .isEqualTo(HttpStatus.CREATED);
        return published.getBody();
    }

    /**
     * Asks what publishing this form would do, with the refusal ruled out rather than assumed: a
     * refused preview would leave a test asserting that nothing changes — and passing.
     */
    SchemePreviewView preview(Map<String, Object> form) {
        ResponseEntity<SchemePreviewView> previewed = http.postForEntity(
                "/api/admin/scheme/preview", form, SchemePreviewView.class);
        assertThat(previewed.getStatusCode())
                .as("previewing a candidate version of the scheme: %s", form)
                .isEqualTo(HttpStatus.OK);
        return previewed.getBody();
    }

    /** The same request, with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToPreview(Object form) {
        return http.postForEntity("/api/admin/scheme/preview", form, JsonNode.class);
    }

    /**
     * Everything this application will say about itself, as the characters it says it in.
     *
     * <p><strong>For one claim only: that a preview wrote nothing at all.</strong> Taken before and
     * after, and compared, it catches a written version row, a raised notification, an expired
     * batch, a credited point and a moved euro in one assertion — including the ones nobody thought
     * to name, which is the whole reason it is a sweep of the API rather than a list of six
     * readings. A preview that writes is a preview nobody can afford to run, so the test for it is
     * deliberately the blunt one.
     *
     * <p>Every reading is one this application already serves to somebody: the scheme, the versions,
     * the customers, each customer's accounts and notifications, and each savings account with its
     * deposits. Nothing reaches below the seam — a snapshot taken from the database would be
     * asserting about columns, and a column that did not move is not the promise. The promise is
     * that nobody can tell the preview happened.
     */
    String everythingTheApplicationSays() {
        StringBuilder said = new StringBuilder();
        readInto(said, "/api/scheme");
        readInto(said, "/api/scheme/versions");
        readInto(said, "/api/customers");
        for (JsonNode customer : http.getForObject("/api/customers", JsonNode.class)) {
            long customerId = customer.path("id").asLong();
            readInto(said, "/api/customers/" + customerId + "/accounts");
            readInto(said, "/api/customers/" + customerId + "/notifications");
            JsonNode accounts =
                    http.getForObject("/api/customers/" + customerId + "/accounts", JsonNode.class);
            for (JsonNode savingsAccount : accounts.path("savingsAccounts")) {
                long savingsAccountId = savingsAccount.path("id").asLong();
                readInto(said, "/api/savings-accounts/" + savingsAccountId);
                readInto(said, "/api/savings-accounts/" + savingsAccountId + "/deposits");
            }
        }
        return said.toString();
    }

    /** One reading, named beside its answer so that a difference says which door it came out of. */
    private void readInto(StringBuilder said, String path) {
        said.append(path).append(" -> ").append(http.getForObject(path, String.class)).append('\n');
    }

    /** The same request, with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToPublish(Object form) {
        return http.postForEntity("/api/admin/scheme/versions", form, JsonNode.class);
    }

    /**
     * A publish whose body is a well-formed JSON {@code null} rather than a form — which is what a
     * page with a broken submit handler actually sends.
     *
     * <p>Sent with the content type spelled out and the body written as characters, because that is
     * the only way to arrange it: a request object left out altogether never reaches the handler at
     * all, and the refusal under test is the one the handler gives when the body arrived and was
     * empty.
     */
    ResponseEntity<JsonNode> tryToPublishNothingAtAll() {
        HttpHeaders asJson = new HttpHeaders();
        asJson.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("/api/admin/scheme/versions", new HttpEntity<>("null", asJson),
                JsonNode.class);
    }

    /** Whatever the application says to a request nobody built a door for. */
    ResponseEntity<JsonNode> tryTo(org.springframework.http.HttpMethod verb, String path) {
        return http.exchange(path, verb, null, JsonNode.class);
    }

    /** The day this application thinks it is, read off its own clock rather than the machine's. */
    LocalDate theDateTheClockReads() {
        return LocalDate.ofInstant(
                http.getForObject("/api/dev/clock", ClockView.class).now(), BRUSSELS);
    }

    /**
     * The first Monday strictly after the day this application thinks it is, which is the earliest
     * day a version may take effect on.
     *
     * <p>Worked out from the application's clock rather than the machine's, because the rule it is
     * about is worked out from the application's clock — and a test that computed "next Monday"
     * from {@code LocalDate.now()} would pass or fail depending on how far a previous test had
     * wound the clock.
     */
    LocalDate theNextMondayStillToCome() {
        return theDateTheClockReads().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    /** Moves the clock, through the endpoint a trainer would use. */
    void daysPass(long days) {
        ResponseEntity<ClockView> moved = http.postForEntity(
                "/api/dev/clock/advance", Map.of("days", days), ClockView.class);
        assertThat(moved.getStatusCode()).as("moving the clock %d days on", days)
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Winds the clock forward until the application says it is that day, which is how a version
     * announced for a Monday is watched coming into force.
     *
     * <p>Forward only, and it says so by insisting: this application's clock can be wound back, and
     * a test that quietly went backwards to reach a date would be a test asserting about a morning
     * that had already happened.
     */
    void theClockReaches(LocalDate day) {
        LocalDate today = theDateTheClockReads();
        assertThat(day).as("a day this test can wind the clock forward to").isAfterOrEqualTo(today);
        daysPass(ChronoUnit.DAYS.between(today, day));
        assertThat(theDateTheClockReads()).isEqualTo(day);
    }

    /**
     * A filled-in publishing form saying exactly what a version already says, ready for a test to
     * change the one figure it is about.
     *
     * <p><strong>Every figure, every time, because that is the contract.</strong> A version of the
     * scheme carries nothing over from the version before it — the module refuses an absent figure
     * by name — so this is what the administration screen's pre-filled form sends, and building it
     * here from a version that was actually served is what keeps the tests from quietly asserting
     * against a shape the API does not have.
     *
     * <p>A {@link LinkedHashMap} rather than {@code Map.of} so that a test can put one figure back
     * over another, and so that the form logged beside a failure reads in the order the screen
     * draws it.
     */
    public static Map<String, Object> theSameSchemeAgain(SchemeView as, LocalDate effectiveFrom,
                                                         String whatChanged) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("effectiveFrom", effectiveFrom.toString());
        form.put("weeklyThreshold", as.weeklyThreshold().toPlainString());
        form.put("theOrdinaryRate", as.theOrdinaryRate().toPlainString());
        form.put("extraForEachFurtherWeek", as.extraForEachFurtherWeek().toPlainString());
        form.put("theMostAStreakPays", as.theMostAStreakPays().toPlainString());
        form.put("howLongABatchOfPointsLasts", String.valueOf(as.howLongABatchOfPointsLasts()));
        form.put("balanceRungs", as.balanceRungs().stream().map(BigDecimal::toPlainString).toList());
        form.put("whatShareOfABudgetIsRunningLow",
                as.whatShareOfABudgetIsRunningLow().toPlainString());
        form.put("howManyOutstandingIsASpiral", String.valueOf(as.howManyOutstandingIsASpiral()));
        form.put("daysBeforeAMaturityIsWorthSaying",
                String.valueOf(as.daysBeforeAMaturityIsWorthSaying()));
        form.put("daysBeforeAnAnniversaryIsWorthSaying",
                String.valueOf(as.daysBeforeAnAnniversaryIsWorthSaying()));
        form.put("whatChanged", whatChanged);
        return form;
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
}
