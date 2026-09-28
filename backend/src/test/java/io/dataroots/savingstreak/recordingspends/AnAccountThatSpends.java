package io.dataroots.savingstreak.recordingspends;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.SpendView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import io.dataroots.savingstreak.support.TheCurrentAccountView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A current account with money in it and nobody else's spending against it, driven over HTTP the way
 * the frontend drives it.
 *
 * <p><strong>Its own customer, every time</strong>, for the reason {@code AnAccountWithCategories}
 * gives and one more of its own: the whole run shares one database, and a spend takes real money out
 * of a real balance — a test that spent out of the seeded Anke's account would leave every later
 * test reading her balance to account for it. A customer costs one request and is opened with a
 * balance of its own to spend.
 *
 * <p>Every call is available twice: once as a method that insists on success and hands back what was
 * recorded, and once as a {@code try...} method that hands back the whole response. A test asserting
 * that something was refused needs the status and the words; a test recording a spend in order to
 * read it back needs neither, and should fail on the spot if the recording did not take.
 *
 * <p>The balance is read off the account's own resource rather than tracked here, because that is
 * where it lives: when a test needs to know a spend was taken it checks that the balance fell, and a
 * figure this class remembered would be this class's arithmetic rather than the application's.
 */
class AnAccountThatSpends {

    /**
     * Makes each class's contact details different from every other class's without anybody having
     * to keep a list. An address used twice is the duplicate the application refuses, and the second
     * class to run would fail on the first one's customer.
     */
    private static final AtomicLong DISTINCT = new AtomicLong();

    private static final ParameterizedTypeReference<List<SpendView>> A_LIST_OF_SPENDS =
            new ParameterizedTypeReference<>() {
            };

    private final TestRestTemplate http;

    private final long currentAccountId;

    AnAccountThatSpends(TestRestTemplate http, String purpose) {
        this.http = http;
        long customerId = aFreshCustomerFor(http, purpose);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class,
                customerId);
        JsonNode currentAccounts = accounts.get("currentAccounts");
        assertThat(currentAccounts.size())
                .describedAs("a customer is opened with a current account for their spending to "
                        + "come out of")
                .isPositive();
        this.currentAccountId = currentAccounts.get(0).get("id").asLong();
    }

    long id() {
        return currentAccountId;
    }

    /** What the account holds, read off its own resource — the one place that figure comes from. */
    BigDecimal balance() {
        TheCurrentAccountView account = http.getForObject("/api/current-accounts/{id}",
                TheCurrentAccountView.class, currentAccountId);
        return account.balance();
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
    void ends(long categoryId) {
        ResponseEntity<SpendingCategoryView> ended = http.exchange(
                "/api/current-accounts/{account}/categories/{category}", HttpMethod.DELETE, null,
                SpendingCategoryView.class, currentAccountId, categoryId);
        assertThat(ended.getStatusCode())
                .describedAs("ending category " + categoryId + ": " + ended.getBody())
                .isEqualTo(HttpStatus.OK);
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

    /** A spend exactly as somebody typed it, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToSpend(String name, String amount, Part... parts) {
        return tryToSpendOn(currentAccountId, name, amount, parts);
    }

    /** The same request against any current account, named by identifier. */
    ResponseEntity<JsonNode> tryToSpendOn(long anyCurrentAccountId, String name, String amount,
                                          Part... parts) {
        return http.postForEntity("/api/current-accounts/{id}/spends", bodyFor(name, amount, parts),
                JsonNode.class, anyCurrentAccountId);
    }

    /** A request with no body at all, which is a fact about the request rather than about a spend. */
    ResponseEntity<JsonNode> tryToSpendNothingAtAll() {
        return http.postForEntity("/api/current-accounts/{id}/spends", null, JsonNode.class,
                currentAccountId);
    }

    /**
     * Replaces the whole split of a spend already recorded, and insists it was accepted.
     *
     * <p>The whole split rather than the part that changed: a correction is the customer saying what
     * the spend was for, and a request that named one part would leave the other parts to be guessed
     * at. What cannot be sent is the amount or the name — the money moved, and there is no field
     * here for either.
     */
    SpendView corrects(long spendId, Part... parts) {
        ResponseEntity<SpendView> corrected = http.exchange(
                "/api/current-accounts/{account}/spends/{spend}/split", HttpMethod.PUT,
                new HttpEntity<>(splitOf(parts)), SpendView.class, currentAccountId, spendId);
        assertThat(corrected.getStatusCode())
                .describedAs("correcting spend " + spendId + ": " + corrected.getBody())
                .isEqualTo(HttpStatus.OK);
        return corrected.getBody();
    }

    /** A correction exactly as somebody typed it, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToCorrect(long spendId, Part... parts) {
        return tryToCorrectOn(currentAccountId, spendId, parts);
    }

    /** The same request against any current account, named by identifier. */
    ResponseEntity<JsonNode> tryToCorrectOn(long anyCurrentAccountId, long spendId, Part... parts) {
        return http.exchange("/api/current-accounts/{account}/spends/{spend}/split", HttpMethod.PUT,
                new HttpEntity<>(splitOf(parts)), JsonNode.class, anyCurrentAccountId, spendId);
    }

    /** A correction with no body at all, which is a fact about the request rather than a split. */
    ResponseEntity<JsonNode> tryToCorrectWithNothingAtAll(long spendId) {
        return http.exchange("/api/current-accounts/{account}/spends/{spend}/split", HttpMethod.PUT,
                null, JsonNode.class, currentAccountId, spendId);
    }

    /**
     * Whatever the application answers to a request to unmake a spend, so that a test can assert
     * there is no such thing rather than assert that a method it never wrote is absent.
     */
    ResponseEntity<JsonNode> tryTo(HttpMethod method, long spendId) {
        return http.exchange("/api/current-accounts/{account}/spends/{spend}", method,
                new HttpEntity<>(Map.of("name", "A different name", "amount", "1.00")),
                JsonNode.class, currentAccountId, spendId);
    }

    /** An identifier no spend has on this account, for a correction naming one that is not there. */
    long anIdNoSpendHas() {
        long highest = 0;
        for (SpendView spend : recentSpends()) {
            highest = Math.max(highest, spend.spendId());
        }
        return highest + 1_000;
    }

    /** The recent spends on the account, newest first. */
    List<SpendView> recentSpends() {
        ResponseEntity<List<SpendView>> read = http.exchange(
                "/api/current-accounts/{id}/spends", HttpMethod.GET, null, A_LIST_OF_SPENDS,
                currentAccountId);
        assertThat(read.getStatusCode())
                .describedAs("a read of the spends on current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** The list read for any account at all, for the one that has never been heard of. */
    ResponseEntity<JsonNode> tryToReadTheSpendsOf(long anyCurrentAccountId) {
        return http.getForEntity("/api/current-accounts/{id}/spends", JsonNode.class,
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

    /** An identifier no category has on this account, for a part naming one that is not there. */
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
     * One part of a split as the page sends it: an amount, and a category or none at all.
     *
     * <p>A record here rather than a map at every call site, so that a test reads as the split it is
     * describing. The amount is text, because it is text that travels.
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
     * The body as the page sends it: a name, an amount as text, and a list of parts — any of which a
     * test about a missing field may leave out. HashMaps rather than Map.of, because Map.of will not
     * hold a null.
     */
    private static Map<String, Object> bodyFor(String name, String amount, Part... parts) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("amount", amount);
        if (parts != null) {
            body.put("parts", partsOf(parts));
        }
        return body;
    }

    /**
     * The body of a correction: the whole split and nothing else. There is no name in it and no
     * amount, because there is no endpoint that would take either — the money moved, and the split
     * is the only thing about a spend that was ever an opinion.
     */
    private static Map<String, Object> splitOf(Part... parts) {
        Map<String, Object> body = new HashMap<>();
        if (parts != null) {
            body.put("parts", partsOf(parts));
        }
        return body;
    }

    /** The parts as the page sends them. HashMaps, because Map.of will not hold a null category. */
    private static List<Map<String, Object>> partsOf(Part... parts) {
        List<Map<String, Object>> split = new ArrayList<>();
        for (Part part : Arrays.asList(parts)) {
            Map<String, Object> one = new HashMap<>();
            one.put("categoryId", part.categoryId());
            one.put("amount", part.amount());
            split.add(one);
        }
        return split;
    }

    /**
     * A customer of this test class's own, named after what the class is for, so that two classes
     * never spend out of one account. The name is made distinct as well as the address because tests
     * elsewhere in this run look customers up by name, and a dozen customers wearing one name would
     * hide eleven accounts from that walk.
     */
    private static long aFreshCustomerFor(TestRestTemplate http, String purpose) {
        long distinct = DISTINCT.incrementAndGet();
        String address = "spends-" + purpose + "-" + distinct + "@example.be";
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Shopper " + distinct + " for " + purpose, "contactDetails", address),
                JsonNode.class);
        assertThat(added.getStatusCode())
                .describedAs("opening the customer whose spending this test records")
                .isEqualTo(HttpStatus.CREATED);
        return added.getBody().get("id").asLong();
    }
}
