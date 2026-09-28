package io.dataroots.savingstreak.runningthecatalogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The administration API as somebody running the scheme drives it, and the tidying up afterwards.
 *
 * <p><strong>The tidying up is the reason this class exists.</strong> One database file serves the
 * whole run, and {@code ClaimingRewardsApiTest} asserts that the customer's catalogue is
 * <em>exactly</em> the four seeded offers at their four prices — which is the single strongest
 * rail on this feature and a test nobody may edit. A test here that published a fifth offer and
 * left it there would break that test from another package it never mentions, which is precisely
 * the failure the base class warns about. So every offer written through this helper is written
 * down, and {@link #putTheCatalogueBack} withdraws the lot: a withdrawn offer is out of the
 * customer's catalogue and cannot be claimed, so the run is left with the four it started with.
 *
 * <p>Codes are handed out rather than chosen, for the same reason. A code is unique for the life
 * of the database and two tests reaching for the same obvious word would refuse each other on
 * whichever ran second.
 *
 * <p>Every call goes over HTTP, like every other test here. Nothing reaches for the service, the
 * repository or the row.
 */
class OffersThisTestWrites {

    /** Nothing seeded or demonstrated is called this, and no two calls answer the same. */
    private static final AtomicInteger HANDED_OUT = new AtomicInteger();

    private final TestRestTemplate http;

    private final List<String> written = new ArrayList<>();

    OffersThisTestWrites(TestRestTemplate http) {
        this.http = http;
    }

    /** A code no offer in this run has, and none will have again. */
    String aCodeNobodyHasUsed() {
        return "RUN_THE_CATALOGUE_" + HANDED_OUT.incrementAndGet();
    }

    /** Writes a draft and insists it took, handing back the offer as the catalogue now holds it. */
    OfferView writes(String code, String title, long costInPoints, String voucherPrefix) {
        ResponseEntity<OfferView> created = http.postForEntity("/api/admin/rewards",
                Map.of("code", code, "title", title, "description", "Something to spend points on.",
                        "costInPoints", costInPoints, "voucherPrefix", voucherPrefix),
                OfferView.class);
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

    ResponseEntity<JsonNode> triesToPublish(String code) {
        return http.postForEntity("/api/admin/rewards/{code}/publish", null, JsonNode.class, code);
    }

    /** Withdraws an offer and insists it took. */
    OfferView withdraws(String code) {
        ResponseEntity<OfferView> withdrawn = http.postForEntity(
                "/api/admin/rewards/{code}/withdraw", null, OfferView.class, code);
        assertThat(withdrawn.getStatusCode())
                .describedAs("withdrawing \"" + code + "\": " + withdrawn.getBody())
                .isEqualTo(HttpStatus.OK);
        return withdrawn.getBody();
    }

    ResponseEntity<JsonNode> triesToWithdraw(String code) {
        return http.postForEntity("/api/admin/rewards/{code}/withdraw", null, JsonNode.class, code);
    }

    /** One offer as whoever runs the catalogue reads it, whatever state it is in. */
    OfferView theOffer(String code) {
        ResponseEntity<OfferView> read = http.getForEntity("/api/admin/rewards/{code}",
                OfferView.class, code);
        assertThat(read.getStatusCode()).describedAs("reading \"" + code + "\"")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    ResponseEntity<JsonNode> triesToRead(String code) {
        return http.getForEntity("/api/admin/rewards/{code}", JsonNode.class, code);
    }

    /** Every offer the catalogue holds, in every state. */
    List<OfferView> everyOffer() {
        return List.of(http.getForObject("/api/admin/rewards", OfferView[].class));
    }

    /** The catalogue a customer reads, which is the other half of every assertion here. */
    List<RewardView> theCatalogueACustomerReads() {
        return List.of(http.getForObject("/api/rewards", RewardView[].class));
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
