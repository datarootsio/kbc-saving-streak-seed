package io.dataroots.savingstreak.savingsproducts;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole application of its own, on a database nothing has ever been written to, whose savings
 * catalogue the test that started it may publish to.
 *
 * <p><strong>Its own application because publishing cannot be undone.</strong> That is the promise
 * of the whole ticket — a version that has been published is never edited and never deleted — so a
 * test that published a third version of free savings into the run's shared database would leave it
 * there for every test afterwards, and the two that pin the catalogue to four cards at four rates
 * and free savings to two versions are the rail this feature is built not to break. There is no
 * tidying-up that could put it back, by design. The only honest answer is a file of this test's own,
 * the same one {@code TheCatalogueOfProductsIsSeededOnceAndNeverResetApiTest} reaches for and for a
 * neighbouring reason.
 *
 * <p><strong>Driven entirely over HTTP</strong>, endpoints and all, so a test using it knows nothing
 * about the database or the columns underneath. The clock is moved through the endpoint a trainer
 * would use, which is how "a version dated for next month is not on offer yet, and is on offer next
 * month" becomes something a test can watch happen.
 *
 * <p>Shared between the three administration tests rather than copied into each, for the reason the
 * shared views give: three copies of "boot an application and publish a version" are three chances
 * to disagree about what publishing one is.
 */
final class ACatalogueSomebodyAdministers implements AutoCloseable {

    /** The zone this application counts its days in, which is the zone the seed anchors to. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    private final ConfigurableApplicationContext application;
    private final TestRestTemplate http;

    ACatalogueSomebodyAdministers(Path databaseFile) {
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
    }

    /** The whole shelf as a customer reads it, closed products and all. */
    List<SavingsProductView> shelf() {
        return List.of(http.getForObject("/api/savings-products", SavingsProductView[].class));
    }

    /** One product and the terms it is offering today. */
    SavingsProductView product(String code) {
        return http.getForObject("/api/savings-products/{code}", SavingsProductView.class, code);
    }

    /** Every version that product has published, oldest first. */
    List<TermsVersionView> versionsOf(String code) {
        return List.of(http.getForObject("/api/savings-products/{code}/versions",
                TermsVersionView[].class, code));
    }

    /**
     * Publishes a version, with the refusal ruled out rather than assumed: a refused publish would
     * leave a test asserting that nothing changed — and passing.
     */
    TermsVersionView publish(String code, Map<String, Object> form) {
        ResponseEntity<TermsVersionView> published = http.postForEntity(
                "/api/admin/savings-products/{code}/versions", form, TermsVersionView.class, code);
        assertThat(published.getStatusCode())
                .as("publishing a version of %s: %s", code, form)
                .isEqualTo(HttpStatus.CREATED);
        return published.getBody();
    }

    /** The same request, with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToPublish(String code, Object form) {
        return http.postForEntity("/api/admin/savings-products/{code}/versions", form,
                JsonNode.class, code);
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
    ResponseEntity<JsonNode> tryToPublishNothingAtAll(String code) {
        HttpHeaders asJson = new HttpHeaders();
        asJson.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("/api/admin/savings-products/{code}/versions",
                new HttpEntity<>("null", asJson), JsonNode.class, code);
    }

    /** Stops anybody opening a new account on it, refusal ruled out. */
    SavingsProductView close(String code) {
        return oneOfTheTwoDoors(code, "close");
    }

    /** Puts it back on sale, refusal ruled out. */
    SavingsProductView reopen(String code) {
        return oneOfTheTwoDoors(code, "reopen");
    }

    private SavingsProductView oneOfTheTwoDoors(String code, String door) {
        ResponseEntity<SavingsProductView> answered = http.postForEntity(
                "/api/admin/savings-products/{code}/" + door, null, SavingsProductView.class, code);
        assertThat(answered.getStatusCode()).as("%s %s", door, code).isEqualTo(HttpStatus.OK);
        return answered.getBody();
    }

    /** Whatever the application says to a request nobody built a door for. */
    ResponseEntity<JsonNode> tryTo(HttpMethod verb, String path, Object body) {
        return http.exchange(path, verb, body == null ? null : new HttpEntity<>(body),
                JsonNode.class);
    }

    /** The day this application thinks it is, read off its own clock rather than the machine's. */
    LocalDate theDateTheClockReads() {
        return LocalDate.ofInstant(
                http.getForObject("/api/dev/clock", ClockView.class).now(), BRUSSELS);
    }

    /** Moves the clock, through the endpoint a trainer would use. */
    void daysPass(long days) {
        ResponseEntity<ClockView> moved = http.postForEntity(
                "/api/dev/clock/advance", Map.of("days", days), ClockView.class);
        assertThat(moved.getStatusCode()).as("moving the clock %d days on", days)
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * A filled-in publishing form saying exactly what a version already says, ready for a test to
     * change the one figure it is about.
     *
     * <p><strong>Every figure, every time, because that is the contract.</strong> A version carries
     * nothing over from the version before it — the module refuses an absent figure by name — so
     * this is what the administration screen's pre-filled form sends, and building it here from a
     * version that was actually served is what keeps the tests from quietly asserting against a
     * shape the API does not have.
     *
     * <p>A {@link LinkedHashMap} rather than {@code Map.of} so that a test can put one figure back
     * over another, and so that the form logged beside a failure reads in the order the screen
     * draws it.
     */
    static Map<String, Object> theSameTermsAgain(TermsVersionView as, LocalDate effectiveFrom,
                                                 String whatChanged) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("effectiveFrom", effectiveFrom.toString());
        form.put("annualRatePercent", as.annualRatePercent().toPlainString());
        form.put("bonusRatePercent", as.bonusRatePercent().toPlainString());
        form.put("noticeDays", String.valueOf(as.noticeDays()));
        form.put("termMonths", String.valueOf(as.termMonths()));
        form.put("minimumBalance", as.minimumBalance().toPlainString());
        form.put("earlyExitPenaltyDays", String.valueOf(as.earlyExitPenaltyDays()));
        form.put("pointsMultiplier", as.pointsMultiplier().toPlainString());
        form.put("anniversaryRatePercent", as.anniversaryRatePercent().toPlainString());
        form.put("maturityAction", as.maturityAction());
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
