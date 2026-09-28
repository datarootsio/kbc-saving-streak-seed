package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.MonthlyIncomeView;
import io.dataroots.savingstreak.support.TheCurrentAccountView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A current account nobody else's income is declared against, and the income endpoints driven over
 * HTTP the way the frontend drives them.
 *
 * <p><strong>Its own customer, every time</strong>, for the reason {@code AnAccountWithGoals} gives:
 * the whole run shares one database, and an income left standing on the seeded Anke's account would
 * be a salary any later test that ran the nightly job would pay her. Adding a customer costs one
 * request and buys an account whose whole declaration is this test's.
 *
 * <p>Every call is available twice: once as a method that insists on success and hands back the
 * income, and once as a {@code try...} method that hands back the whole response. A test asserting
 * that something was refused needs the status and the words; a test declaring an income in order to
 * change it needs neither, and should fail on the spot if the first declaration did not take.
 */
class AnAccountWithAnIncome {

    /**
     * Makes each class's contact details different from every other class's without anybody having
     * to keep a list. An address used twice is the duplicate the application refuses, and the second
     * class to run would fail on the first one's customer.
     */
    private static final AtomicLong DISTINCT = new AtomicLong();

    private final TestRestTemplate http;

    /** Whose the account is, for reading its balance back off the directory. */
    private final long customerId;

    private final long currentAccountId;

    AnAccountWithAnIncome(TestRestTemplate http, String purpose) {
        this.http = http;
        this.customerId = aFreshCustomerFor(http, purpose);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class, customerId);
        JsonNode currentAccounts = accounts.get("currentAccounts");
        assertThat(currentAccounts.size())
                .describedAs("a customer is opened with a current account to be paid into")
                .isPositive();
        this.currentAccountId = currentAccounts.get(0).get("id").asLong();
    }

    long id() {
        return currentAccountId;
    }

    /**
     * The account's own resource: what is in it, who holds it and what it says about its income,
     * insisting the read succeeded. The read the current account's page makes.
     */
    TheCurrentAccountView theAccount() {
        ResponseEntity<TheCurrentAccountView> read = http.getForEntity(
                "/api/current-accounts/{id}", TheCurrentAccountView.class, currentAccountId);
        assertThat(read.getStatusCode())
                .describedAs("a read of current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** The same read against any identifier, for the test about an account nobody has heard of. */
    ResponseEntity<JsonNode> tryToRead(long anyCurrentAccountId) {
        return http.getForEntity("/api/current-accounts/{id}", JsonNode.class, anyCurrentAccountId);
    }

    /** What the account says about the income declared against it, insisting the read succeeded. */
    MonthlyIncomeView income() {
        ResponseEntity<MonthlyIncomeView> read = http.getForEntity(
                "/api/current-accounts/{id}/monthly-income", MonthlyIncomeView.class, currentAccountId);
        assertThat(read.getStatusCode())
                .describedAs("a read of the income on current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** Declares an income and insists it was accepted, handing back what the account then reports. */
    MonthlyIncomeView declares(String dayOfMonth, String amount) {
        ResponseEntity<MonthlyIncomeView> declared = http.exchange(
                "/api/current-accounts/{id}/monthly-income", HttpMethod.PUT,
                new HttpEntity<>(Map.of("dayOfMonth", dayOfMonth, "amount", amount)),
                MonthlyIncomeView.class, currentAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring an income of " + amount + " on day " + dayOfMonth
                        + ": " + declared.getBody())
                .isEqualTo(HttpStatus.OK);
        return declared.getBody();
    }

    /** A declaration exactly as somebody typed it, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToDeclare(String dayOfMonth, String amount) {
        return tryToDeclareOn(currentAccountId, dayOfMonth, amount);
    }

    /**
     * The same request against any current account, named by identifier — for the test about being
     * told that the account somebody named is not one this application has heard of.
     */
    ResponseEntity<JsonNode> tryToDeclareOn(long anyCurrentAccountId, String dayOfMonth,
                                            String amount) {
        // A HashMap rather than Map.of, because a test about a missing field sends a null and
        // Map.of will not hold one.
        Map<String, Object> body = new HashMap<>();
        body.put("dayOfMonth", dayOfMonth);
        body.put("amount", amount);
        return http.exchange("/api/current-accounts/{id}/monthly-income", HttpMethod.PUT,
                new HttpEntity<>(body), JsonNode.class, anyCurrentAccountId);
    }

    /** Withdraws the declaration and insists it was accepted, handing back what is left. */
    MonthlyIncomeView withdrawsTheDeclaration() {
        ResponseEntity<MonthlyIncomeView> withdrawn = http.exchange(
                "/api/current-accounts/{id}/monthly-income", HttpMethod.DELETE, null,
                MonthlyIncomeView.class, currentAccountId);
        assertThat(withdrawn.getStatusCode())
                .describedAs("withdrawing the income on current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
        return withdrawn.getBody();
    }

    /** What is in the account right now, read off the customer's own directory of accounts. */
    BigDecimal balance() {
        for (JsonNode account : accountsOfTheHolder().get("currentAccounts")) {
            if (account.get("id").asLong() == currentAccountId) {
                return account.get("balance").decimalValue();
            }
        }
        throw new AssertionError("this test's own current account " + currentAccountId + " is gone");
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

    private JsonNode accountsOfTheHolder() {
        return http.getForObject("/api/customers/{id}/accounts", JsonNode.class, customerId);
    }

    /**
     * A customer of this test class's own, named after what the class is for, so that two classes
     * never declare an income against one account. The name is made distinct as well as the address
     * for the reason {@code AnAccountWithGoals} gives: tests elsewhere in this run look customers up
     * by name, and a dozen customers wearing one name would hide eleven accounts from that walk.
     */
    private static long aFreshCustomerFor(TestRestTemplate http, String purpose) {
        long distinct = DISTINCT.incrementAndGet();
        String address = "income-" + purpose + "-" + distinct + "@example.be";
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Earner " + distinct + " for " + purpose, "contactDetails", address),
                JsonNode.class);
        assertThat(added.getStatusCode())
                .describedAs("opening the customer whose account this test's income goes on")
                .isEqualTo(HttpStatus.CREATED);
        return added.getBody().get("id").asLong();
    }
}
