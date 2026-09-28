package io.dataroots.savingstreak.spendingcategories;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A current account nobody else's categories are declared against, and the category endpoints
 * driven over HTTP the way the frontend drives them.
 *
 * <p><strong>Its own customer, every time</strong>, for the reason {@code AnAccountWithBills} gives:
 * the whole run shares one database, and twenty categories left standing on the seeded Anke's
 * account would be twenty categories any later test reading her account has to account for. Adding
 * a customer costs one request and buys an account whose whole list of categories is this test's.
 *
 * <p>Every call is available twice: once as a method that insists on success and hands back the
 * category, and once as a {@code try...} method that hands back the whole response. A test asserting
 * that something was refused needs the status and the words; a test declaring a category in order to
 * rename it needs neither, and should fail on the spot if the first declaration did not take.
 */
class AnAccountWithCategories {

    /**
     * Makes each class's contact details different from every other class's without anybody having
     * to keep a list. An address used twice is the duplicate the application refuses, and the second
     * class to run would fail on the first one's customer.
     */
    private static final AtomicLong DISTINCT = new AtomicLong();

    private static final ParameterizedTypeReference<List<SpendingCategoryView>> A_LIST_OF_CATEGORIES =
            new ParameterizedTypeReference<>() {
            };

    private final TestRestTemplate http;

    private final long currentAccountId;

    AnAccountWithCategories(TestRestTemplate http, String purpose) {
        this.http = http;
        long customerId = aFreshCustomerFor(http, purpose);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class,
                customerId);
        JsonNode currentAccounts = accounts.get("currentAccounts");
        assertThat(currentAccounts.size())
                .describedAs("a customer is opened with a current account for their spending to be "
                        + "described against")
                .isPositive();
        this.currentAccountId = currentAccounts.get(0).get("id").asLong();
    }

    long id() {
        return currentAccountId;
    }

    /** The categories standing on the account, in the order their holder named them. */
    List<SpendingCategoryView> standingCategories() {
        return listAt("/api/current-accounts/{id}/categories");
    }

    /** The categories no longer standing on it. */
    List<SpendingCategoryView> endedCategories() {
        return listAt("/api/current-accounts/{id}/categories/ended");
    }

    /** Declares a category and insists it was accepted, handing back the category that now stands. */
    SpendingCategoryView declares(String name) {
        ResponseEntity<SpendingCategoryView> declared = http.postForEntity(
                "/api/current-accounts/{id}/categories", bodyNaming(name),
                SpendingCategoryView.class, currentAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring \"" + name + "\": " + declared.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return declared.getBody();
    }

    /** A declaration exactly as somebody typed it, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToDeclare(String name) {
        return tryToDeclareOn(currentAccountId, name);
    }

    /** The same request against any current account, named by identifier. */
    ResponseEntity<JsonNode> tryToDeclareOn(long anyCurrentAccountId, String name) {
        return http.postForEntity("/api/current-accounts/{id}/categories", bodyNaming(name),
                JsonNode.class, anyCurrentAccountId);
    }

    /** Renames a category and insists it took, handing back the category as it now reads. */
    SpendingCategoryView renames(long categoryId, String name) {
        ResponseEntity<SpendingCategoryView> renamed = http.exchange(
                "/api/current-accounts/{account}/categories/{category}", HttpMethod.PATCH,
                new HttpEntity<>(bodyNaming(name)), SpendingCategoryView.class, currentAccountId,
                categoryId);
        assertThat(renamed.getStatusCode())
                .describedAs("renaming category " + categoryId + " to \"" + name + "\": "
                        + renamed.getBody())
                .isEqualTo(HttpStatus.OK);
        return renamed.getBody();
    }

    /** The same rename on any account and any category, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToRename(long anyCurrentAccountId, long categoryId, String name) {
        return http.exchange("/api/current-accounts/{account}/categories/{category}",
                HttpMethod.PATCH, new HttpEntity<>(bodyNaming(name)), JsonNode.class,
                anyCurrentAccountId, categoryId);
    }

    /** Ends a category and insists it was accepted, handing back the category as it now reads. */
    SpendingCategoryView ends(long categoryId) {
        ResponseEntity<SpendingCategoryView> ended = http.exchange(
                "/api/current-accounts/{account}/categories/{category}", HttpMethod.DELETE, null,
                SpendingCategoryView.class, currentAccountId, categoryId);
        assertThat(ended.getStatusCode())
                .describedAs("ending category " + categoryId + ": " + ended.getBody())
                .isEqualTo(HttpStatus.OK);
        return ended.getBody();
    }

    /** The same ending on any account and any category, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToEnd(long anyCurrentAccountId, long categoryId) {
        return http.exchange("/api/current-accounts/{account}/categories/{category}",
                HttpMethod.DELETE, null, JsonNode.class, anyCurrentAccountId, categoryId);
    }

    /** The list read for any account at all, for the one that has never been heard of. */
    ResponseEntity<JsonNode> tryToReadTheCategoriesOf(long anyCurrentAccountId) {
        return http.getForEntity("/api/current-accounts/{id}/categories", JsonNode.class,
                anyCurrentAccountId);
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

    private List<SpendingCategoryView> listAt(String path) {
        ResponseEntity<List<SpendingCategoryView>> read = http.exchange(path, HttpMethod.GET, null,
                A_LIST_OF_CATEGORIES, currentAccountId);
        assertThat(read.getStatusCode())
                .describedAs("a read of " + path + " for current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * The body as the page sends it: one field of text, which a test about a missing name may leave
     * out. A HashMap rather than Map.of, because Map.of will not hold a null.
     */
    private static Map<String, Object> bodyNaming(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        return body;
    }

    /**
     * A customer of this test class's own, named after what the class is for, so that two classes
     * never declare a category against one account. The name is made distinct as well as the
     * address for the reason {@code AnAccountWithBills} gives: tests elsewhere in this run look
     * customers up by name, and a dozen customers wearing one name would hide eleven accounts from
     * that walk.
     */
    private static long aFreshCustomerFor(TestRestTemplate http, String purpose) {
        long distinct = DISTINCT.incrementAndGet();
        String address = "categories-" + purpose + "-" + distinct + "@example.be";
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Spender " + distinct + " for " + purpose, "contactDetails", address),
                JsonNode.class);
        assertThat(added.getStatusCode())
                .describedAs("opening the customer whose spending this test describes")
                .isEqualTo(HttpStatus.CREATED);
        return added.getBody().get("id").asLong();
    }
}
