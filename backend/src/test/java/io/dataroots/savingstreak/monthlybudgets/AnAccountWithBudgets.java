package io.dataroots.savingstreak.monthlybudgets;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.MonthOfSpendingView;
import io.dataroots.savingstreak.support.MonthlyBudgetView;
import io.dataroots.savingstreak.support.SpendView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import io.dataroots.savingstreak.support.TheCurrentAccountView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A current account with categories of its own, figures on them, and money going out against them,
 * driven over HTTP the way the frontend drives it.
 *
 * <p><strong>Its own customer, every time</strong>, for the reason {@code AnAccountThatSpends} gives
 * and one more of its own: the whole run shares one database, and a month read is a sum over
 * everything that happened on an account — a test that budgeted the seeded Anke's groceries would
 * change what every other test reading her account sees.
 *
 * <p>It drives four things at once: the categories, the figures on them, the spends against them and
 * the month that reports all three. That is what lets a test assert the thing this slice is actually
 * about, which is that a budget you can declare but cannot read is not a budget.
 *
 * <p>Every call that can be refused is available twice: once as a method that insists on success and
 * hands back what came of it, and once as a {@code try...} method that hands back the whole
 * response. A test asserting that something was refused needs the status and the words; a test
 * naming a figure in order to read a month back needs neither, and should fail on the spot if the
 * first request did not take.
 *
 * <p><strong>There is nothing here about bills.</strong> The committed half of a month comes from a
 * bill occurrence, which only exists once a nightly run has presented one — so it belongs to a test
 * with a clock of its own, and the fixture for that is {@code AnApplicationWithAClockToMove}.
 */
class AnAccountWithBudgets {

    /**
     * Makes each class's contact details different from every other class's without anybody having
     * to keep a list. An address used twice is the duplicate the application refuses, and the second
     * class to run would fail on the first one's customer.
     */
    private static final AtomicLong DISTINCT = new AtomicLong();

    private final TestRestTemplate http;

    private final long currentAccountId;

    AnAccountWithBudgets(TestRestTemplate http, String purpose) {
        this.http = http;
        long customerId = aFreshCustomerFor(http, purpose);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class,
                customerId);
        JsonNode currentAccounts = accounts.get("currentAccounts");
        assertThat(currentAccounts.size())
                .describedAs("a customer is opened with a current account for their budget to be "
                        + "about")
                .isPositive();
        this.currentAccountId = currentAccounts.get(0).get("id").asLong();
    }

    long id() {
        return currentAccountId;
    }

    /** What the account holds, read off its own resource — the one place that figure comes from. */
    BigDecimal balance() {
        return http.getForObject("/api/current-accounts/{id}", TheCurrentAccountView.class,
                currentAccountId).balance();
    }

    /** Names something the money goes on, and insists it was accepted. */
    SpendingCategoryView declares(String name) {
        ResponseEntity<SpendingCategoryView> declared = http.postForEntity(
                "/api/current-accounts/{id}/categories", Map.of("name", name),
                SpendingCategoryView.class, currentAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring \"" + name + "\": " + declared.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return declared.getBody();
    }

    /** Ends a category for good, and insists it was accepted. */
    SpendingCategoryView ends(long categoryId) {
        ResponseEntity<SpendingCategoryView> ended = http.exchange(
                "/api/current-accounts/{account}/categories/{category}", HttpMethod.DELETE, null,
                SpendingCategoryView.class, currentAccountId, categoryId);
        assertThat(ended.getStatusCode())
                .describedAs("ending category " + categoryId + ": " + ended.getBody())
                .isEqualTo(HttpStatus.OK);
        return ended.getBody();
    }

    /** Says what a category is allowed to cost each month, and insists it was accepted. */
    MonthlyBudgetView budgets(long categoryId, String amount) {
        return budgets(categoryId, amount, null);
    }

    /**
     * The same, saying what should become of the difference when a month it governs ends.
     *
     * <p>Two methods rather than a null at every call site: most tests here have no opinion about
     * rollover and sending none is what a customer who has never thought about it does, which is a
     * request worth making on purpose rather than by accident.
     */
    MonthlyBudgetView budgets(long categoryId, String amount, String rollover) {
        ResponseEntity<MonthlyBudgetView> declared = http.exchange(
                "/api/current-accounts/{account}/categories/{category}/budget", HttpMethod.PUT,
                new HttpEntity<>(bodyFor(amount, rollover)), MonthlyBudgetView.class,
                currentAccountId, categoryId);
        assertThat(declared.getStatusCode())
                .describedAs("budgeting EUR " + amount + " on category " + categoryId
                        + " with rollover " + rollover + ": " + declared.getBody())
                .isEqualTo(HttpStatus.OK);
        return declared.getBody();
    }

    /** The same request on any account, any category and any figure, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToBudget(long anyCurrentAccountId, long categoryId, String amount) {
        return tryToBudget(anyCurrentAccountId, categoryId, amount, null);
    }

    /** The same, with a rollover rule as it was typed, for the words this feature does not know. */
    ResponseEntity<JsonNode> tryToBudget(long anyCurrentAccountId, long categoryId, String amount,
                                         String rollover) {
        return http.exchange("/api/current-accounts/{account}/categories/{category}/budget",
                HttpMethod.PUT, new HttpEntity<>(bodyFor(amount, rollover)), JsonNode.class,
                anyCurrentAccountId, categoryId);
    }

    /** A request with no body at all, which is a fact about the request rather than about a budget. */
    ResponseEntity<JsonNode> tryToBudgetNothingAtAll(long categoryId) {
        return http.exchange("/api/current-accounts/{account}/categories/{category}/budget",
                HttpMethod.PUT, HttpEntity.EMPTY, JsonNode.class, currentAccountId, categoryId);
    }

    /** Stops budgeting a category without ending it, and insists it was accepted. */
    MonthlyBudgetView stopsBudgeting(long categoryId) {
        ResponseEntity<MonthlyBudgetView> stopped = http.exchange(
                "/api/current-accounts/{account}/categories/{category}/budget", HttpMethod.DELETE,
                null, MonthlyBudgetView.class, currentAccountId, categoryId);
        assertThat(stopped.getStatusCode())
                .describedAs("stopping the budget on category " + categoryId + ": "
                        + stopped.getBody())
                .isEqualTo(HttpStatus.OK);
        return stopped.getBody();
    }

    /** The same on any account and any category, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToStopBudgeting(long anyCurrentAccountId, long categoryId) {
        return http.exchange("/api/current-accounts/{account}/categories/{category}/budget",
                HttpMethod.DELETE, null, JsonNode.class, anyCurrentAccountId, categoryId);
    }

    /** Records a spend and insists it was accepted, handing back the spend as it now reads. */
    SpendView spends(String name, String amount, Part... parts) {
        ResponseEntity<SpendView> recorded = http.postForEntity(
                "/api/current-accounts/{id}/spends", bodyFor(name, amount, parts), SpendView.class,
                currentAccountId);
        assertThat(recorded.getStatusCode())
                .describedAs("recording \"" + name + "\" of EUR " + amount + ": "
                        + recorded.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return recorded.getBody();
    }

    /** How this month is going: the account's totals, and a row per category under them. */
    MonthOfSpendingView thisMonth() {
        ResponseEntity<MonthOfSpendingView> read = http.getForEntity(
                "/api/current-accounts/{id}/spending", MonthOfSpendingView.class, currentAccountId);
        assertThat(read.getStatusCode())
                .describedAs("a read of this month on current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** The same answer about a month somebody names, written as the year and the month. */
    MonthOfSpendingView month(String yearMonth) {
        ResponseEntity<MonthOfSpendingView> read = http.getForEntity(
                "/api/current-accounts/{id}/spending/{month}", MonthOfSpendingView.class,
                currentAccountId, yearMonth);
        assertThat(read.getStatusCode())
                .describedAs("a read of " + yearMonth + " on current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** The month read for any account and any month at all, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToReadTheMonthOf(long anyCurrentAccountId, String yearMonth) {
        return yearMonth == null
                ? http.getForEntity("/api/current-accounts/{id}/spending", JsonNode.class,
                        anyCurrentAccountId)
                : http.getForEntity("/api/current-accounts/{id}/spending/{month}", JsonNode.class,
                        anyCurrentAccountId, yearMonth);
    }

    /**
     * An identifier no current account has: one past the highest any customer holds. Walked over
     * every customer rather than the seeded two, because a customer added by a test holds accounts
     * numbered above theirs and asking only the seeded pair would hand back one that exists.
     */
    long anIdNoCurrentAccountHas() {
        long highest = 0;
        for (JsonNode customer : http.getForObject("/api/customers", JsonNode.class)) {
            JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class,
                    customer.get("id").asLong());
            for (JsonNode account : accounts.get("currentAccounts")) {
                highest = Math.max(highest, account.get("id").asLong());
            }
        }
        return highest + 1;
    }

    /** An identifier no category has on this account, for a budget put on one that is not there. */
    long anIdNoCategoryHas() {
        long highest = 0;
        for (SpendingCategoryView category : http.getForObject(
                "/api/current-accounts/{id}/categories", SpendingCategoryView[].class,
                currentAccountId)) {
            highest = Math.max(highest, category.categoryId());
        }
        return highest + 1_000;
    }

    /**
     * One part of a split as the page sends it: an amount, and a category or none at all. The amount
     * is text, because it is text that travels.
     */
    record Part(Long categoryId, String amount) {

        /** A part filed under a category. */
        static Part of(long categoryId, String amount) {
            return new Part(categoryId, amount);
        }

        /** A part the customer has not decided about, which is a state rather than a gap. */
        static Part unfiled(String amount) {
            return new Part(null, amount);
        }
    }

    /**
     * The budget body as the page sends it: one figure and one rule, both as text, either of which a
     * test about a missing field may leave out. A HashMap rather than Map.of, because Map.of will
     * not hold a null — and a null here is exactly the case worth sending, since saying nothing
     * about rollover is the ordinary thing a customer does.
     */
    private static Map<String, Object> bodyFor(String amount, String rollover) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("rollover", rollover);
        return body;
    }

    /** The spend body as the page sends it, in the shape {@code AnAccountThatSpends} already uses. */
    private static Map<String, Object> bodyFor(String name, String amount, Part... parts) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("amount", amount);
        List<Map<String, Object>> split = new ArrayList<>();
        for (Part part : Arrays.asList(parts)) {
            Map<String, Object> one = new HashMap<>();
            one.put("categoryId", part.categoryId());
            one.put("amount", part.amount());
            split.add(one);
        }
        body.put("parts", split);
        return body;
    }

    /**
     * A customer of this test class's own, named after what the class is for, so that two classes
     * never budget one account. The name is made distinct as well as the address because tests
     * elsewhere in this run look customers up by name, and a dozen customers wearing one name would
     * hide eleven accounts from that walk.
     */
    private static long aFreshCustomerFor(TestRestTemplate http, String purpose) {
        long distinct = DISTINCT.incrementAndGet();
        String address = "budgets-" + purpose + "-" + distinct + "@example.be";
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Budgeter " + distinct + " for " + purpose, "contactDetails", address),
                JsonNode.class);
        assertThat(added.getStatusCode())
                .describedAs("opening the customer whose budget this test is about")
                .isEqualTo(HttpStatus.CREATED);
        return added.getBody().get("id").asLong();
    }
}
