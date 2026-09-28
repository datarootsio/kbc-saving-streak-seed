package io.dataroots.savingstreak.anofferthatisnotopenyet;

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
 * Offers with windows on them, written over the administration API, and the tidying up afterwards.
 *
 * <p><strong>The tidying up is why this class exists, and the argument is the one
 * {@code runningthecatalogue.OffersThisTestWrites} already makes at length.</strong> One database
 * file serves the whole run, and {@code ClaimingRewardsApiTest} asserts that the customer's
 * catalogue is <em>exactly</em> the four seeded offers at their four prices — the single strongest
 * rail on this feature and a test nobody may edit. An offer left published here would break a
 * test in a package this one never mentions. So every offer written through this helper is
 * written down and {@link #putTheCatalogueBack} withdraws the lot.
 *
 * <p>It follows that class rather than sharing it, which is a deliberate duplication of about
 * thirty lines. That one is package-private to the package whose tests are a rail this ticket may
 * not touch, and lifting it into {@code support} would be editing a file the rail is made of in
 * order to save writing it out. The codes it hands out carry this package's name, so the two can
 * never reach for the same one.
 *
 * <p>Every call goes over HTTP, like every other test here. Nothing reaches for the service, the
 * repository or the row.
 */
class OffersWithAWindowThisTestWrites {

    /** Nothing seeded, demonstrated or written by another test package is called this. */
    private static final AtomicInteger HANDED_OUT = new AtomicInteger();

    private final TestRestTemplate http;

    private final List<String> written = new ArrayList<>();

    OffersWithAWindowThisTestWrites(TestRestTemplate http) {
        this.http = http;
    }

    /** A code no offer in this run has, and none will have again. */
    String aCodeNobodyHasUsed() {
        return "A_WINDOW_" + HANDED_OUT.incrementAndGet();
    }

    /**
     * Writes a draft with whatever window this test needs, publishes it, and insists on both —
     * which is the state every test here starts from, because an offer nobody published is
     * refused for being a draft long before anybody looks at its dates.
     *
     * <p>The days go up as the text a form would send, which is what the contract carries, and
     * null means the box was left empty.
     */
    OfferView onSale(String code, String title, long costInPoints, LocalDate opensOn,
                     LocalDate closesOn) {
        writes(code, title, costInPoints, opensOn, closesOn);
        return publishes(code);
    }

    /** Writes a draft and insists it took, handing back the offer as the catalogue now holds it. */
    OfferView writes(String code, String title, long costInPoints, LocalDate opensOn,
                     LocalDate closesOn) {
        // A HashMap rather than Map.of, because either day may be absent and Map.of will not hold
        // a null: "no opening day" is half of what this package is about and it has to be sendable.
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Something to spend points on.");
        offer.put("costInPoints", costInPoints);
        offer.put("voucherPrefix", "WIN");
        offer.put("opensOn", opensOn == null ? null : opensOn.toString());
        offer.put("closesOn", closesOn == null ? null : closesOn.toString());
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

    /** The catalogue with nobody in it, which must go on saying exactly what it always said. */
    List<RewardView> theCatalogueWithNobodyInIt() {
        return List.of(http.getForObject("/api/rewards", RewardView[].class));
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
     * One entry of that reading, by its code, because every assertion here is about a named offer
     * and picking it out of the list by hand is the same three lines every time.
     */
    RewardForACustomerView asReadBy(long customerId, String code) {
        return theCatalogueAsReadBy(customerId).stream()
                .filter(entry -> code.equals(entry.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("customer " + customerId + " cannot see \""
                        + code + "\" at all; a locked offer is meant to be shown rather than "
                        + "hidden, so this is the failure and not a missing set-up step"));
    }

    /**
     * Takes every offer this test wrote back out of the catalogue, and leaves the seeded four
     * exactly as they were found.
     *
     * <p>Withdrawn rather than deleted, because nothing in this application deletes an offer and a
     * test that needed a back door into the database would be testing something other than the
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
