package io.dataroots.savingstreak.accounts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.TheCurrentAccountView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A current account nobody else's bills are declared against, and the bill endpoints driven over
 * HTTP the way the frontend drives them.
 *
 * <p><strong>Its own customer, every time</strong>, for the reason {@link AnAccountWithAnIncome}
 * gives: the whole run shares one database, and a rent left standing on the seeded Anke's account
 * would be a debit any later test that ran a nightly job would take from her. Adding a customer
 * costs one request and buys an account whose whole list of bills is this test's.
 *
 * <p>Every call is available twice: once as a method that insists on success and hands back the
 * bill, and once as a {@code try...} method that hands back the whole response. A test asserting
 * that something was refused needs the status and the words; a test declaring a bill in order to
 * change it needs neither, and should fail on the spot if the first declaration did not take.
 */
class AnAccountWithBills {

    /**
     * Makes each class's contact details different from every other class's without anybody having
     * to keep a list. An address used twice is the duplicate the application refuses, and the second
     * class to run would fail on the first one's customer.
     */
    private static final AtomicLong DISTINCT = new AtomicLong();

    private static final ParameterizedTypeReference<List<RecurringBillView>> A_LIST_OF_BILLS =
            new ParameterizedTypeReference<>() {
            };

    private final TestRestTemplate http;

    private final long currentAccountId;

    AnAccountWithBills(TestRestTemplate http, String purpose) {
        this.http = http;
        long customerId = aFreshCustomerFor(http, purpose);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class,
                customerId);
        JsonNode currentAccounts = accounts.get("currentAccounts");
        assertThat(currentAccounts.size())
                .describedAs("a customer is opened with a current account for their bills to leave")
                .isPositive();
        this.currentAccountId = currentAccounts.get(0).get("id").asLong();
    }

    long id() {
        return currentAccountId;
    }

    /** The account's own read — the one the page makes — insisting it succeeded. */
    TheCurrentAccountView theAccount() {
        ResponseEntity<TheCurrentAccountView> read = http.getForEntity(
                "/api/current-accounts/{id}", TheCurrentAccountView.class, currentAccountId);
        assertThat(read.getStatusCode())
                .describedAs("a read of current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** The bills standing against the account, asked for by themselves. */
    List<RecurringBillView> standingBills() {
        return listAt("/api/current-accounts/{id}/bills");
    }

    /** The bills no longer standing against it. */
    List<RecurringBillView> endedBills() {
        return listAt("/api/current-accounts/{id}/bills/ended");
    }

    /** Declares a bill and insists it was accepted, handing back the bill that now stands. */
    RecurringBillView declares(String name, String dayOfMonth, String amount) {
        ResponseEntity<RecurringBillView> declared = http.postForEntity(
                "/api/current-accounts/{id}/bills", bodyOf(name, dayOfMonth, amount),
                RecurringBillView.class, currentAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring \"" + name + "\" of " + amount + " on day " + dayOfMonth
                        + ": " + declared.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return declared.getBody();
    }

    /** A declaration exactly as somebody typed it, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToDeclare(String name, String dayOfMonth, String amount) {
        return tryToDeclareOn(currentAccountId, name, dayOfMonth, amount);
    }

    /** The same request against any current account, named by identifier. */
    ResponseEntity<JsonNode> tryToDeclareOn(long anyCurrentAccountId, String name,
                                            String dayOfMonth, String amount) {
        return http.postForEntity("/api/current-accounts/{id}/bills",
                bodyOf(name, dayOfMonth, amount), JsonNode.class, anyCurrentAccountId);
    }

    /** Changes whichever fields are given — a null is "leave it alone" — and insists it took. */
    RecurringBillView changes(long billId, String name, String dayOfMonth, String amount) {
        ResponseEntity<RecurringBillView> changed = http.exchange(
                "/api/current-accounts/{account}/bills/{bill}", HttpMethod.PATCH,
                new HttpEntity<>(bodyOf(name, dayOfMonth, amount)), RecurringBillView.class,
                currentAccountId, billId);
        assertThat(changed.getStatusCode())
                .describedAs("changing bill " + billId + ": " + changed.getBody())
                .isEqualTo(HttpStatus.OK);
        return changed.getBody();
    }

    /** The same change on any account and any bill, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToChange(long anyCurrentAccountId, long billId, String name,
                                         String dayOfMonth, String amount) {
        return http.exchange("/api/current-accounts/{account}/bills/{bill}", HttpMethod.PATCH,
                new HttpEntity<>(bodyOf(name, dayOfMonth, amount)), JsonNode.class,
                anyCurrentAccountId, billId);
    }

    /** Ends a bill and insists it was accepted, handing back the bill as it now reads. */
    RecurringBillView ends(long billId) {
        ResponseEntity<RecurringBillView> ended = http.exchange(
                "/api/current-accounts/{account}/bills/{bill}", HttpMethod.DELETE, null,
                RecurringBillView.class, currentAccountId, billId);
        assertThat(ended.getStatusCode())
                .describedAs("ending bill " + billId + ": " + ended.getBody())
                .isEqualTo(HttpStatus.OK);
        return ended.getBody();
    }

    /** The same ending on any account and any bill, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToEnd(long anyCurrentAccountId, long billId) {
        return http.exchange("/api/current-accounts/{account}/bills/{bill}", HttpMethod.DELETE,
                null, JsonNode.class, anyCurrentAccountId, billId);
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

    private List<RecurringBillView> listAt(String path) {
        ResponseEntity<List<RecurringBillView>> read = http.exchange(path, HttpMethod.GET, null,
                A_LIST_OF_BILLS, currentAccountId);
        assertThat(read.getStatusCode())
                .describedAs("a read of " + path + " for current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * The body as the page sends it: three fields of text, any of which a test may leave out. A
     * HashMap rather than Map.of, because a test about a missing field sends a null and Map.of will
     * not hold one.
     */
    private static Map<String, Object> bodyOf(String name, String dayOfMonth, String amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("dayOfMonth", dayOfMonth);
        body.put("amount", amount);
        return body;
    }

    /**
     * A customer of this test class's own, named after what the class is for, so that two classes
     * never declare a bill against one account. The name is made distinct as well as the address
     * for the reason {@link AnAccountWithAnIncome} gives: tests elsewhere in this run look customers
     * up by name, and a dozen customers wearing one name would hide eleven accounts from that walk.
     */
    private static long aFreshCustomerFor(TestRestTemplate http, String purpose) {
        long distinct = DISTINCT.incrementAndGet();
        String address = "bills-" + purpose + "-" + distinct + "@example.be";
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Payer " + distinct + " for " + purpose, "contactDetails", address),
                JsonNode.class);
        assertThat(added.getStatusCode())
                .describedAs("opening the customer whose account this test's bills leave")
                .isEqualTo(HttpStatus.CREATED);
        return added.getBody().get("id").asLong();
    }
}
