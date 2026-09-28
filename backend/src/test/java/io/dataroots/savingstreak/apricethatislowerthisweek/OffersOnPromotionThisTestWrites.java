package io.dataroots.savingstreak.apricethatislowerthisweek;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.RewardView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offers with a sale on them, written over the administration API, and the tidying up afterwards.
 *
 * <p><strong>The tidying up is why this class exists</strong>, and the argument is the one
 * {@code runningthecatalogue.OffersThisTestWrites} makes at length and
 * {@code anofferthatisnotopenyet.OffersWithAWindowThisTestWrites} repeats. One database file
 * serves the whole run, and {@code ClaimingRewardsApiTest} asserts that the catalogue is
 * <em>exactly</em> the four seeded offers at their four prices — the single strongest rail on
 * this feature and a test nobody may edit. An offer left published here would break a test in a
 * package this one never mentions. So every offer written through this helper is written down and
 * {@link #putTheCatalogueBack} withdraws the lot.
 *
 * <p>It follows those two rather than sharing either, which is a deliberate duplication of about
 * forty lines. Both of them are package-private to packages whose tests are rails this ticket may
 * not touch, and lifting one into {@code support} would mean editing a file a rail is made of in
 * order to save writing it out. The codes it hands out carry this package's own word, so no two
 * of the three can ever reach for the same one — which matters more than usual this week, because
 * four tickets are being written against this same catalogue at once.
 *
 * <p>Every call goes over HTTP, like every other test here. Nothing reaches for the service, the
 * repository or the row.
 */
class OffersOnPromotionThisTestWrites {

    /** Nothing seeded, demonstrated or written by another test package is called this. */
    private static final AtomicInteger HANDED_OUT = new AtomicInteger();

    private final TestRestTemplate http;

    private final List<String> written = new ArrayList<>();

    OffersOnPromotionThisTestWrites(TestRestTemplate http) {
        this.http = http;
    }

    /** A code no offer in this run has, and none will have again. */
    String aCodeNobodyHasUsed() {
        return "A_LOWER_PRICE_" + HANDED_OUT.incrementAndGet();
    }

    /**
     * Writes an offer with a sale on it and publishes it, insisting on both — which is the state
     * every test here starts from, because a draft is refused for being a draft long before
     * anybody looks at what it costs.
     */
    OfferView onSaleAt(String code, String title, long costInPoints, long discountedCostInPoints,
                       LocalDate discountOpensOn, LocalDate discountClosesOn) {
        writes(anOffer(code, title, costInPoints, discountedCostInPoints, discountOpensOn,
                discountClosesOn));
        return publishes(code);
    }

    /** The same, at the ordinary price, for the readings that have to say there is no sale on. */
    OfferView onSale(String code, String title, long costInPoints) {
        writes(anOffer(code, title, costInPoints, null, null, null));
        return publishes(code);
    }

    /**
     * An offer as an administration form would send one, with a sale on it or without.
     *
     * <p>A {@link HashMap} rather than {@code Map.of}, because every part of a promotion may be
     * absent and {@code Map.of} will not hold a null: an offer at its ordinary price is what the
     * seeded four are and it has to be sendable through the same door.
     *
     * <p>The days go up as the text a date box sends, which is what the contract carries.
     */
    static Map<String, Object> anOffer(String code, String title, long costInPoints,
                                       Long discountedCostInPoints, LocalDate discountOpensOn,
                                       LocalDate discountClosesOn) {
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Something to spend points on.");
        offer.put("costInPoints", costInPoints);
        offer.put("voucherPrefix", "LOW");
        offer.put("discountedCostInPoints", discountedCostInPoints);
        offer.put("discountOpensOn", discountOpensOn == null ? null : discountOpensOn.toString());
        offer.put("discountClosesOn", discountClosesOn == null ? null
                : discountClosesOn.toString());
        return offer;
    }

    /** Writes a draft and insists it took, handing back the offer as the catalogue now holds it. */
    OfferView writes(Map<String, Object> offer) {
        ResponseEntity<OfferView> created =
                http.postForEntity("/api/admin/rewards", offer, OfferView.class);
        assertThat(created.getStatusCode())
                .describedAs("writing the offer this test needs: " + offer)
                .isEqualTo(HttpStatus.CREATED);
        written.add((String) offer.get("code"));
        return created.getBody();
    }

    /** The same write with whatever a test wants to send, for the ones this feature refuses. */
    ResponseEntity<JsonNode> triesToWrite(Map<String, Object> body) {
        return http.postForEntity("/api/admin/rewards", body, JsonNode.class);
    }

    /** Changes an offer and insists it took, handing back the offer as it now reads. */
    OfferView changes(String code, Map<String, Object> change) {
        ResponseEntity<OfferView> changed = http.exchange("/api/admin/rewards/{code}",
                HttpMethod.PATCH, new HttpEntity<>(change), OfferView.class, code);
        assertThat(changed.getStatusCode())
                .describedAs("changing \"" + code + "\" the way this test needs: "
                        + changed.getBody())
                .isEqualTo(HttpStatus.OK);
        return changed.getBody();
    }

    /** The same change read as unshaped JSON, for the ones this feature refuses. */
    ResponseEntity<JsonNode> triesToChange(String code, Map<String, Object> change) {
        return http.exchange("/api/admin/rewards/{code}", HttpMethod.PATCH,
                new HttpEntity<>(change), JsonNode.class, code);
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

    ResponseEntity<JsonNode> triesToWithdraw(String code) {
        return http.postForEntity("/api/admin/rewards/{code}/withdraw", null, JsonNode.class, code);
    }

    /** The catalogue with nobody in it, which must go on saying exactly what it always said. */
    List<RewardView> theCatalogueWithNobodyInIt() {
        return List.of(http.getForObject("/api/rewards", RewardView[].class));
    }

    /** The catalogue as one customer reads it: the same offers, each at the price they would pay. */
    List<RewardForACustomerView> theCatalogueAsReadBy(long customerId) {
        ResponseEntity<RewardForACustomerView[]> read = http.getForEntity(
                "/api/customers/{id}/rewards", RewardForACustomerView[].class, customerId);
        assertThat(read.getStatusCode())
                .describedAs("reading the catalogue as customer " + customerId + " sees it")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * One entry of that reading, by its code, because every assertion here is about a named offer
     * — and because an offer missing from the list is itself the failure worth reporting in words.
     */
    RewardForACustomerView asReadBy(long customerId, String code) {
        return theCatalogueAsReadBy(customerId).stream()
                .filter(entry -> code.equals(entry.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("customer " + customerId + " cannot see \""
                        + code + "\" at all; an offer on sale is meant to be shown, so this is "
                        + "the failure and not a missing set-up step"));
    }

    /**
     * Takes every offer this test wrote back out of the catalogue, and leaves the seeded four
     * exactly as they were found.
     *
     * <p>Withdrawn rather than deleted, because nothing in this application deletes an offer and
     * a test that needed a back door into the database would be testing something other than the
     * API. Idempotent, so a test that withdrew its own offer as the point of the test is not a
     * failure here.
     */
    void putTheCatalogueBack() {
        written.forEach(code -> assertThat(triesToWithdraw(code).getStatusCode())
                .describedAs("putting \"" + code + "\" back out of the catalogue, so that the "
                        + "rest of this run reads the four offers it is entitled to")
                .isEqualTo(HttpStatus.OK));
        written.clear();
    }
}
