package io.dataroots.savingstreak.thelastoneheldforyou;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.HoldView;
import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scarce offers to put aside, written over the administration API, the holds taken on them, and
 * the tidying up afterwards.
 *
 * <p><strong>The tidying up is why this class exists, and the argument is the one
 * {@code runningthecatalogue.OffersThisTestWrites} makes at length and which
 * {@code anofferthatcanrunout.OffersWithStockThisTestWrites} and
 * {@code apricethatislowerthisweek.OffersOnPromotionThisTestWrites} have each made again
 * since.</strong> One database file serves the whole run, and {@code ClaimingRewardsApiTest}
 * asserts that the catalogue is <em>exactly</em> the four seeded offers at their four prices —
 * the strongest rail on this feature and a test nobody may edit. An offer left published here
 * would break a test in a package this one never mentions. So every offer written through this
 * helper is written down and {@link #putTheCatalogueBack} withdraws the lot.
 *
 * <p>It is a fourth copy of about fifty lines rather than a shared class, which is deliberate and
 * is the same deliberate duplication the three before it made: every one of those lives inside a
 * package whose tests are rails this ticket may not touch, so lifting one of them into
 * {@code support} would mean editing a file a rail is made of in order to save writing this one
 * out. The codes handed out here carry this package's own word, so no two of the four can ever
 * reach for the same one.
 *
 * <p>The hold calls live here too rather than in the test, because there are four of them and
 * every test in the package makes at least one. They are the plain HTTP the frontend makes:
 * nothing in this package reaches for a service, a repository or a row.
 */
class OffersToHoldThisTestWrites {

    /** Nothing seeded, demonstrated or written by another test package is called this. */
    private static final AtomicInteger HANDED_OUT = new AtomicInteger();

    private final TestRestTemplate http;

    private final List<String> written = new ArrayList<>();

    OffersToHoldThisTestWrites(TestRestTemplate http) {
        this.http = http;
    }

    /** A code no offer in this run has, and none will have again. */
    String aCodeNobodyHasUsed() {
        return "HELD_FOR_YOU_" + HANDED_OUT.incrementAndGet();
    }

    /**
     * Writes a draft with however many of it there are and whatever caps it carries, publishes
     * it, and insists on both — the state every test here starts from, because a draft is
     * refused for being a draft long before anybody asks to hold one.
     */
    OfferView onSale(String code, String title, long costInPoints, Integer stock,
                     Integer maxPerCustomer) {
        // A HashMap rather than Map.of, because both of the last two may be absent and Map.of
        // will not hold a null: an offer that never runs out and an offer nobody capped are
        // both things this package writes.
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Something worth putting aside.");
        offer.put("costInPoints", costInPoints);
        offer.put("voucherPrefix", "HLD");
        offer.put("stock", stock);
        offer.put("maxPerCustomer", maxPerCustomer);
        ResponseEntity<OfferView> created =
                http.postForEntity("/api/admin/rewards", offer, OfferView.class);
        assertThat(created.getStatusCode())
                .describedAs("writing the offer \"" + code + "\" this test needs")
                .isEqualTo(HttpStatus.CREATED);
        written.add(code);
        return publishes(code);
    }

    /** The commonest shape here: one of something, and no cap on who may have it. */
    OfferView theLastOneOnSale(String code, String title, long costInPoints) {
        return onSale(code, title, costInPoints, 1, null);
    }

    /** Publishes an offer and insists it took. */
    OfferView publishes(String code) {
        ResponseEntity<OfferView> published = http.postForEntity(
                "/api/admin/rewards/{code}/publish", null, OfferView.class, code);
        assertThat(published.getStatusCode())
                .describedAs("publishing \"" + code + "\": " + published.getBody())
                .isEqualTo(HttpStatus.OK);
        return published.getBody();
    }

    /** Changes an offer and insists it took, handing back the offer as it now reads. */
    OfferView changes(String code, Map<String, Object> change) {
        ResponseEntity<OfferView> changed = http.exchange("/api/admin/rewards/{code}",
                HttpMethod.PATCH, new org.springframework.http.HttpEntity<>(change),
                OfferView.class, code);
        assertThat(changed.getStatusCode())
                .describedAs("changing \"" + code + "\" the way this test needs: "
                        + changed.getBody())
                .isEqualTo(HttpStatus.OK);
        return changed.getBody();
    }

    /** The same change read as unshaped JSON, for the ones this feature refuses. */
    ResponseEntity<JsonNode> triesToChange(String code, Map<String, Object> change) {
        return http.exchange("/api/admin/rewards/{code}", HttpMethod.PATCH,
                new org.springframework.http.HttpEntity<>(change), JsonNode.class, code);
    }

    ResponseEntity<JsonNode> triesToWithdraw(String code) {
        return http.postForEntity("/api/admin/rewards/{code}/withdraw", null, JsonNode.class, code);
    }

    /** The catalogue as one customer reads it: the same offers, each saying where they stand. */
    List<RewardForACustomerView> theCatalogueAsReadBy(long customerId) {
        ResponseEntity<RewardForACustomerView[]> read = http.getForEntity(
                "/api/customers/{id}/rewards", RewardForACustomerView[].class, customerId);
        assertThat(read.getStatusCode())
                .describedAs("reading the catalogue as customer " + customerId + " sees it")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * One entry of that reading, by its code, because every assertion here is about a named
     * offer — and because an offer missing from the list is itself the failure worth reporting
     * in words.
     */
    RewardForACustomerView asReadBy(long customerId, String code) {
        return theCatalogueAsReadBy(customerId).stream()
                .filter(entry -> code.equals(entry.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("customer " + customerId + " cannot see \""
                        + code + "\" at all; an offer whose last one is held is meant to be shown "
                        + "rather than hidden, so this is the failure and not a missing step"));
    }

    /** Puts the last one aside for a customer, and insists it took. */
    HoldView heldBy(long customerId, String code) {
        ResponseEntity<HoldView> taken = http.postForEntity("/api/customers/{id}/holds",
                Map.of("reward", code), HoldView.class, customerId);
        assertThat(taken.getStatusCode())
                .describedAs("putting \"" + code + "\" aside for customer " + customerId + ": "
                        + taken.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return taken.getBody();
    }

    /** The same request read as a problem document, for the ones this feature refuses. */
    ResponseEntity<JsonNode> triesToHold(long customerId, String code) {
        return http.postForEntity("/api/customers/{id}/holds", Map.of("reward", code),
                JsonNode.class, customerId);
    }

    /** Turns a hold into a claim, read as unshaped JSON so that a refusal reads as one too. */
    ResponseEntity<JsonNode> triesToConvert(long customerId, String code) {
        return http.postForEntity("/api/customers/{id}/holds/{code}/claim", null, JsonNode.class,
                customerId, code);
    }

    /** Gives a hold up, read the same way and for the same reason. */
    ResponseEntity<JsonNode> triesToGiveUp(long customerId, String code) {
        return http.exchange("/api/customers/{id}/holds/{code}", HttpMethod.DELETE, null,
                JsonNode.class, customerId, code);
    }

    /**
     * Takes every offer this test wrote back out of the catalogue, and leaves the seeded four
     * exactly as they were found.
     *
     * <p>Withdrawn rather than deleted, because nothing in this application deletes an offer and
     * a test that needed a back door into the database would be testing something other than the
     * API. A hold left live on a withdrawn offer harms nothing and is deliberately not tidied
     * up: it holds stock of an offer no customer can see, it lapses on its own within three
     * days, and reaching into the database to remove it would be this helper doing the one thing
     * the whole test package refuses to do.
     */
    void putTheCatalogueBack() {
        written.forEach(code -> assertThat(triesToWithdraw(code).getStatusCode())
                .describedAs("putting \"" + code + "\" back out of the catalogue, so that the "
                        + "rest of this run reads the four offers it is entitled to")
                .isEqualTo(HttpStatus.OK));
        written.clear();
    }
}
