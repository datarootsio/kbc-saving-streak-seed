package io.dataroots.savingstreak.avouchercancelledandthepointscomeback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.VoucherAtTheCounterView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The offers this package claims from, the cancellations it makes, and the tidying up afterwards.
 *
 * <p><strong>A copy of a helper three other packages already have, and the duplication is
 * deliberate — the same deliberate duplication tickets 04, 05, 06, 07 and 11 each documented in
 * turn.</strong> One database file serves the whole run, and {@code ClaimingRewardsApiTest}
 * asserts that the customer's catalogue is <em>exactly</em> the four seeded offers at their four
 * prices: the single strongest rail on this feature and a test nobody may edit. An offer left
 * published here would break a test in a package this one never mentions, so everything written
 * through this helper is written down and {@link #putTheCatalogueBack} withdraws the lot.
 *
 * <p>Those other helpers all live inside packages whose own tests are rails this ticket may not
 * touch, and every one of them is package-private. Lifting one into {@code support} would mean
 * editing a file a rail is made of in order to save writing one out, which is a worse trade than
 * forty duplicated lines. The codes handed out here carry this package's own word, so no two of
 * the helpers can ever reach for the same one.
 *
 * <p><strong>The cancelling lives here rather than in {@code support} for a smaller reason:</strong>
 * the shared application has no clock to move and no helper of its own for this surface, and a
 * test that cancels is a test that first wrote an offer to claim from. The two belong together.
 *
 * <p>Every call goes over HTTP, like every other test here. Nothing reaches for the service, the
 * repository or the row.
 */
class OffersToCancelClaimsFromThisTestWrites {

    /** Nothing seeded, demonstrated or written by another test package is called this. */
    private static final AtomicInteger HANDED_OUT = new AtomicInteger();

    private final TestRestTemplate http;

    private final List<String> written = new ArrayList<>();

    OffersToCancelClaimsFromThisTestWrites(TestRestTemplate http) {
        this.http = http;
    }

    /** A code no offer in this run has, and none will have again. */
    String aCodeNobodyHasUsed() {
        return "CANCELLED_CLAIM_" + HANDED_OUT.incrementAndGet();
    }

    /**
     * Writes a draft with however many of it there are, publishes it, and insists on both — the
     * state every test here starts from, because a draft is refused for being a draft long before
     * anybody claims from it or counts what is left.
     *
     * <p>A null stock is sendable and is half of what this package needs: an offer that never runs
     * out is what all four seeded entries are, and a cancellation has to be harmless to one.
     */
    OfferView onSale(String code, String title, long costInPoints, Integer stock) {
        // A HashMap rather than Map.of, because the stock may be absent and Map.of will not hold
        // a null: "however many you like" is an empty box on the form and has to be sendable as
        // one.
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Something to spend points on.");
        offer.put("costInPoints", costInPoints);
        offer.put("voucherPrefix", "CNL");
        offer.put("stock", stock);
        ResponseEntity<OfferView> created =
                http.postForEntity("/api/admin/rewards", offer, OfferView.class);
        assertThat(created.getStatusCode())
                .describedAs("writing the offer \"" + code + "\" this test needs")
                .isEqualTo(HttpStatus.CREATED);
        written.add(code);
        ResponseEntity<OfferView> published = http.postForEntity(
                "/api/admin/rewards/{code}/publish", null, OfferView.class, code);
        assertThat(published.getStatusCode())
                .describedAs("publishing \"" + code + "\": " + published.getBody())
                .isEqualTo(HttpStatus.OK);
        return published.getBody();
    }

    /** Changes an offer and insists it took — what a restock or a lowered stock goes through. */
    OfferView changes(String code, Map<String, Object> change) {
        ResponseEntity<OfferView> changed = http.exchange("/api/admin/rewards/{code}",
                org.springframework.http.HttpMethod.PATCH,
                new org.springframework.http.HttpEntity<>(change), OfferView.class, code);
        assertThat(changed.getStatusCode())
                .describedAs("changing \"" + code + "\": " + changed.getBody())
                .isEqualTo(HttpStatus.OK);
        return changed.getBody();
    }

    /** The same change read as unshaped JSON, for the ones this feature refuses. */
    ResponseEntity<JsonNode> triesToChange(String code, Map<String, Object> change) {
        return http.exchange("/api/admin/rewards/{code}", org.springframework.http.HttpMethod.PATCH,
                new org.springframework.http.HttpEntity<>(change), JsonNode.class, code);
    }

    /**
     * Revokes a voucher with a reason, the way whoever runs the scheme would, and insists it took:
     * a cancellation that was refused would leave a test asserting about a balance that never
     * moved and failing somewhere further down, about a field rather than about a refusal.
     */
    VoucherAtTheCounterView cancels(String voucherCode, String reason) {
        ResponseEntity<VoucherAtTheCounterView> cancelled = http.postForEntity(
                "/api/admin/vouchers/{code}/cancel", Map.of("reason", reason),
                VoucherAtTheCounterView.class, voucherCode);
        assertThat(cancelled.getStatusCode())
                .describedAs("cancelling \"" + voucherCode + "\": " + cancelled.getBody())
                .isEqualTo(HttpStatus.OK);
        return cancelled.getBody();
    }

    /** The same cancellation read as unshaped JSON, for the ones this feature refuses. */
    ResponseEntity<JsonNode> triesToCancel(String voucherCode, Object body) {
        return http.postForEntity("/api/admin/vouchers/{code}/cancel", body, JsonNode.class,
                voucherCode);
    }

    /** One voucher as a counter reads it, insisted on: a refused lookup would arrive as nulls. */
    VoucherAtTheCounterView atACounter(String voucherCode) {
        ResponseEntity<VoucherAtTheCounterView> read = http.getForEntity(
                "/api/staff/vouchers/{code}", VoucherAtTheCounterView.class, voucherCode);
        assertThat(read.getStatusCode()).describedAs("looking up " + voucherCode)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** An attempt to hand a voucher over, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> triesToHandOver(String voucherCode, String counter) {
        return http.postForEntity("/api/staff/vouchers/{code}/use", Map.of("counter", counter),
                JsonNode.class, voucherCode);
    }

    /** One offer as whoever runs the catalogue reads it, whatever state it is in. */
    OfferView theOfferAsItStands(String code) {
        ResponseEntity<OfferView> read = http.getForEntity("/api/admin/rewards/{code}",
                OfferView.class, code);
        assertThat(read.getStatusCode()).describedAs("reading \"" + code + "\"")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * One entry of the customer's reading, by its code, because every assertion here is about a
     * named offer — and an offer missing from the list is itself the failure worth reporting in
     * words rather than as an empty optional somewhere else.
     */
    RewardForACustomerView asReadBy(long customerId, String code) {
        ResponseEntity<RewardForACustomerView[]> read = http.getForEntity(
                "/api/customers/{id}/rewards", RewardForACustomerView[].class, customerId);
        assertThat(read.getStatusCode())
                .describedAs("reading the catalogue as customer " + customerId + " sees it")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody()).stream()
                .filter(entry -> code.equals(entry.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("customer " + customerId + " cannot see \""
                        + code + "\" at all; a sold-out or limited offer is meant to be shown "
                        + "rather than hidden, so this is the failure and not a missing set-up "
                        + "step"));
    }

    /**
     * Takes every offer this test wrote back out of the catalogue, and leaves the seeded four
     * exactly as they were found.
     *
     * <p>Withdrawn rather than deleted, because nothing in this application deletes an offer and a
     * test that needed a back door into the database would be testing something other than the
     * API. The claims made against them stay exactly where they are, which is the whole reason
     * withdrawing exists — and it is why every test here counts against <em>its own</em> offer
     * rather than against anything it found.
     */
    void putTheCatalogueBack() {
        written.forEach(code -> assertThat(
                http.postForEntity("/api/admin/rewards/{code}/withdraw", null, JsonNode.class,
                        code).getStatusCode())
                .describedAs("putting \"" + code + "\" back out of the catalogue, so that the "
                        + "rest of this run reads the four offers it is entitled to")
                .isEqualTo(HttpStatus.OK));
        written.clear();
    }
}
