package io.dataroots.savingstreak.billsincategories;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.BillInACategoryView;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A current account with bills of its own and categories of its own, and the endpoints that put one
 * in the other driven over HTTP the way the frontend drives them.
 *
 * <p><strong>Its own customer, every time</strong>, for the reason {@code AnAccountWithBills} gives:
 * the whole run shares one database, and a rent left standing on the seeded Anke's account would be
 * a debit any later test that ran a nightly job would take from her. Adding a customer costs one
 * request and buys an account whose whole picture — bills, categories and the labels between them —
 * is this test's.
 *
 * <p>It drives three endpoints from two modules: the bills are Accounts', the categories are
 * Budgets', and the label between them is Budgets' row served under the bills' path. A fixture that
 * can reach all three is what lets a test assert the thing this slice is actually about, which is
 * that the two sit side by side without either knowing about the other.
 *
 * <p>Every call that can be refused is available twice: once as a method that insists on success and
 * hands back what came of it, and once as a {@code try...} method that hands back the whole
 * response. A test asserting that something was refused needs the status and the words; a test
 * filing a bill in order to move it needs neither, and should fail on the spot if the first request
 * did not take.
 */
class AnAccountWithBillsInCategories {

    /**
     * Makes each class's contact details different from every other class's without anybody having
     * to keep a list. An address used twice is the duplicate the application refuses, and the second
     * class to run would fail on the first one's customer.
     */
    private static final AtomicLong DISTINCT = new AtomicLong();

    private static final ParameterizedTypeReference<List<BillInACategoryView>> A_LIST_OF_LABELS =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<RecurringBillView>> A_LIST_OF_BILLS =
            new ParameterizedTypeReference<>() {
            };

    private final TestRestTemplate http;

    private final long currentAccountId;

    AnAccountWithBillsInCategories(TestRestTemplate http, String purpose) {
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

    /** Declares a bill and insists it was accepted, handing back the bill that now stands. */
    RecurringBillView declaresABill(String name, String dayOfMonth, String amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("dayOfMonth", dayOfMonth);
        body.put("amount", amount);
        ResponseEntity<RecurringBillView> declared = http.postForEntity(
                "/api/current-accounts/{id}/bills", body, RecurringBillView.class,
                currentAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring the bill \"" + name + "\": " + declared.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return declared.getBody();
    }

    /** Ends a bill and insists it took, handing back the bill as it now reads. */
    RecurringBillView endsTheBill(long billId) {
        ResponseEntity<RecurringBillView> ended = http.exchange(
                "/api/current-accounts/{account}/bills/{bill}", HttpMethod.DELETE, null,
                RecurringBillView.class, currentAccountId, billId);
        assertThat(ended.getStatusCode())
                .describedAs("ending bill " + billId + ": " + ended.getBody())
                .isEqualTo(HttpStatus.OK);
        return ended.getBody();
    }

    /** Renames a bill, for the test that insists a rename does not move its label. */
    RecurringBillView renamesTheBill(long billId, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        ResponseEntity<RecurringBillView> changed = http.exchange(
                "/api/current-accounts/{account}/bills/{bill}", HttpMethod.PATCH,
                new HttpEntity<>(body), RecurringBillView.class, currentAccountId, billId);
        assertThat(changed.getStatusCode())
                .describedAs("renaming bill " + billId + ": " + changed.getBody())
                .isEqualTo(HttpStatus.OK);
        return changed.getBody();
    }

    /** Declares a category and insists it was accepted, handing back the category that now stands. */
    SpendingCategoryView declaresACategory(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        ResponseEntity<SpendingCategoryView> declared = http.postForEntity(
                "/api/current-accounts/{id}/categories", body, SpendingCategoryView.class,
                currentAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring the category \"" + name + "\": " + declared.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return declared.getBody();
    }

    /** Renames a category and insists it took, handing back the category as it now reads. */
    SpendingCategoryView renamesTheCategory(long categoryId, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        ResponseEntity<SpendingCategoryView> renamed = http.exchange(
                "/api/current-accounts/{account}/categories/{category}", HttpMethod.PATCH,
                new HttpEntity<>(body), SpendingCategoryView.class, currentAccountId, categoryId);
        assertThat(renamed.getStatusCode())
                .describedAs("renaming category " + categoryId + ": " + renamed.getBody())
                .isEqualTo(HttpStatus.OK);
        return renamed.getBody();
    }

    /** Ends a category and insists it took, handing back the category as it now reads. */
    SpendingCategoryView endsTheCategory(long categoryId) {
        ResponseEntity<SpendingCategoryView> ended = http.exchange(
                "/api/current-accounts/{account}/categories/{category}", HttpMethod.DELETE, null,
                SpendingCategoryView.class, currentAccountId, categoryId);
        assertThat(ended.getStatusCode())
                .describedAs("ending category " + categoryId + ": " + ended.getBody())
                .isEqualTo(HttpStatus.OK);
        return ended.getBody();
    }

    /** The bills standing against the account, for the tests that insist filing one changed none. */
    List<RecurringBillView> standingBills() {
        ResponseEntity<List<RecurringBillView>> read = http.exchange(
                "/api/current-accounts/{id}/bills", HttpMethod.GET, null, A_LIST_OF_BILLS,
                currentAccountId);
        assertThat(read.getStatusCode())
                .describedAs("a read of the bills of current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** Where each of this account's bills is filed, as the page reads it. */
    List<BillInACategoryView> theCategoryEachBillIsIn() {
        ResponseEntity<List<BillInACategoryView>> read = http.exchange(
                "/api/current-accounts/{id}/bills/categories", HttpMethod.GET, null, A_LIST_OF_LABELS,
                currentAccountId);
        assertThat(read.getStatusCode())
                .describedAs("a read of the bill categories of current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** The same read for any account at all, for the one that has never been heard of. */
    ResponseEntity<JsonNode> tryToReadTheBillCategoriesOf(long anyCurrentAccountId) {
        return http.getForEntity("/api/current-accounts/{id}/bills/categories", JsonNode.class,
                anyCurrentAccountId);
    }

    /** Puts a bill in a category and insists it took, handing back where the bill now sits. */
    BillInACategoryView putsBillInCategory(long billId, long categoryId) {
        ResponseEntity<BillInACategoryView> filed = http.exchange(
                "/api/current-accounts/{account}/bills/{bill}/category", HttpMethod.PUT,
                new HttpEntity<>(bodyNaming(categoryId)), BillInACategoryView.class,
                currentAccountId, billId);
        assertThat(filed.getStatusCode())
                .describedAs("putting bill " + billId + " in category " + categoryId + ": "
                        + filed.getBody())
                .isEqualTo(HttpStatus.OK);
        return filed.getBody();
    }

    /** The same request on any account, any bill and any category, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToPutBillInCategory(long anyCurrentAccountId, long billId,
                                                    Long categoryId) {
        return http.exchange("/api/current-accounts/{account}/bills/{bill}/category", HttpMethod.PUT,
                new HttpEntity<>(bodyNaming(categoryId)), JsonNode.class, anyCurrentAccountId,
                billId);
    }

    /** Takes a bill out of every category and insists it took, handing back the bill in none. */
    BillInACategoryView takesBillOutOfEveryCategory(long billId) {
        ResponseEntity<BillInACategoryView> out = http.exchange(
                "/api/current-accounts/{account}/bills/{bill}/category", HttpMethod.DELETE, null,
                BillInACategoryView.class, currentAccountId, billId);
        assertThat(out.getStatusCode())
                .describedAs("taking bill " + billId + " out of every category: " + out.getBody())
                .isEqualTo(HttpStatus.OK);
        return out.getBody();
    }

    /** The same on any account and any bill, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToTakeBillOutOfEveryCategory(long anyCurrentAccountId, long billId) {
        return http.exchange("/api/current-accounts/{account}/bills/{bill}/category",
                HttpMethod.DELETE, null, JsonNode.class, anyCurrentAccountId, billId);
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

    /**
     * The body as the page sends it: one identifier, which a test about a request that names no
     * category may leave out. A HashMap rather than Map.of, because Map.of will not hold a null.
     */
    private static Map<String, Object> bodyNaming(Long categoryId) {
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", categoryId);
        return body;
    }

    /**
     * A customer of this test class's own, named after what the class is for, so that two classes
     * never file a bill against one account. The name is made distinct as well as the address for
     * the reason {@code AnAccountWithBills} gives: tests elsewhere in this run look customers up by
     * name, and a dozen customers wearing one name would hide eleven accounts from that walk.
     */
    private static long aFreshCustomerFor(TestRestTemplate http, String purpose) {
        long distinct = DISTINCT.incrementAndGet();
        String address = "bill-categories-" + purpose + "-" + distinct + "@example.be";
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Filer " + distinct + " for " + purpose, "contactDetails", address),
                JsonNode.class);
        assertThat(added.getStatusCode())
                .describedAs("opening the customer whose bills this test files")
                .isEqualTo(HttpStatus.CREATED);
        return added.getBody().get("id").asLong();
    }
}
