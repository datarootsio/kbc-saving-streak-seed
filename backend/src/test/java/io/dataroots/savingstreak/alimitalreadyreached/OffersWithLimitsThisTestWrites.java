package io.dataroots.savingstreak.alimitalreadyreached;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offers with caps on them, written over the administration API, and the tidying up afterwards.
 *
 * <p><strong>The tidying up is why this class exists, and the argument is the one
 * {@code runningthecatalogue.OffersThisTestWrites} already makes at length.</strong> One database
 * file serves the whole run, and {@code ClaimingRewardsApiTest} asserts that the customer's
 * catalogue is <em>exactly</em> the four seeded offers at their four prices — the single
 * strongest rail on this feature and a test nobody may edit. An offer left published here would
 * break a test in a package this one never mentions. So every offer written through this helper
 * is written down and {@link #putTheCatalogueBack} withdraws the lot.
 *
 * <p>It follows that class rather than sharing it, exactly as
 * {@code anofferthatisnotopenyet.OffersWithAWindowThisTestWrites} does and for the reason that
 * one gives: the original is package-private to a package whose tests are a rail this ticket may
 * not touch, and lifting it into {@code support} would mean editing a file the rail is made of
 * in order to save writing thirty lines. The codes it hands out carry this package's own word,
 * so no two of the three can ever reach for the same one.
 *
 * <p>Every call goes over HTTP, like every other test here. Nothing reaches for the service, the
 * repository or the row — which matters more for this slice than for most, because how many
 * somebody has had is a count the module makes and a test that counted for itself would be
 * asserting its own arithmetic.
 */
class OffersWithLimitsThisTestWrites {

    /** Nothing seeded, demonstrated or written by another test package is called this. */
    private static final AtomicInteger HANDED_OUT = new AtomicInteger();

    private final TestRestTemplate http;

    private final List<String> written = new ArrayList<>();

    OffersWithLimitsThisTestWrites(TestRestTemplate http) {
        this.http = http;
    }

    /** A code no offer in this run has, and none will have again. */
    String aCodeNobodyHasUsed() {
        return "A_LIMIT_" + HANDED_OUT.incrementAndGet();
    }

    /**
     * Writes a draft with whatever caps this test needs, publishes it, and insists on both —
     * which is the state every test here starts from, because an offer nobody published is
     * refused for being a draft long before anybody counts what its claimant has had.
     *
     * <p>Either cap may be absent, and absent is the interesting case at least as often as a
     * number is: an offer with neither cap has to behave exactly as everything this application
     * shipped before today.
     */
    OfferView onSale(String code, String title, long costInPoints, Integer maxPerCustomer,
                     Integer maxPerCustomerPerWeek) {
        writes(code, title, costInPoints, maxPerCustomer, maxPerCustomerPerWeek);
        return publishes(code);
    }

    /** Writes a draft and insists it took, handing back the offer as the catalogue now holds it. */
    OfferView writes(String code, String title, long costInPoints, Integer maxPerCustomer,
                     Integer maxPerCustomerPerWeek) {
        // A HashMap rather than Map.of, because either cap may be absent and Map.of will not
        // hold a null: "no cap at all" is half of what this package is about and it has to be
        // sendable in the same shape a form sends it.
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Something to spend points on, but not endlessly.");
        offer.put("costInPoints", costInPoints);
        offer.put("voucherPrefix", "LIM");
        offer.put("maxPerCustomer", maxPerCustomer);
        offer.put("maxPerCustomerPerWeek", maxPerCustomerPerWeek);
        ResponseEntity<OfferView> created =
                http.postForEntity("/api/admin/rewards", offer, OfferView.class);
        assertThat(created.getStatusCode())
                .describedAs("writing the offer \"" + code + "\" this test needs")
                .isEqualTo(HttpStatus.CREATED);
        written.add(code);
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

    /** One offer as whoever runs the catalogue reads it, whatever state it is in. */
    OfferView theOffer(String code) {
        ResponseEntity<OfferView> read =
                http.getForEntity("/api/admin/rewards/{code}", OfferView.class, code);
        assertThat(read.getStatusCode()).describedAs("reading \"" + code + "\"")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
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
     * One entry of that reading, by its code — and an offer missing from the list is itself the
     * failure worth reporting in words, because an offer somebody has had their limit of is
     * meant to be shown locked rather than quietly taken off the page.
     */
    RewardForACustomerView asReadBy(long customerId, String code) {
        return theCatalogueAsReadBy(customerId).stream()
                .filter(entry -> code.equals(entry.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("customer " + customerId + " cannot see \""
                        + code + "\" at all; an offer somebody has had their limit of is meant "
                        + "to be shown locked rather than hidden, so this is the failure and not "
                        + "a missing set-up step"));
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
