package io.dataroots.savingstreak.anofferthatisnotforyou;

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
 * Offers with rules about who they are for, written over the administration API, and the tidying
 * up afterwards.
 *
 * <p><strong>The tidying up is why this class exists, and the argument is the one
 * {@code runningthecatalogue.OffersThisTestWrites} makes at length and
 * {@code anofferthatisnotopenyet.OffersWithAWindowThisTestWrites} repeats.</strong> One database
 * file serves the whole run, and {@code ClaimingRewardsApiTest} asserts that the customer's
 * catalogue is <em>exactly</em> the four seeded offers at their four prices — the single
 * strongest rail on this feature and a test nobody may edit. An offer left published here would
 * break a test in a package this one never mentions. So every offer written through this helper
 * is written down and {@link #putTheCatalogueBack} withdraws the lot.
 *
 * <p>It follows those two rather than sharing either, which is a deliberate duplication of about
 * forty lines and the previous slice said why: both of them are package-private to packages
 * whose tests are rails this ticket may not touch, and lifting one into {@code support} would be
 * editing a file the rail is made of in order to save writing it out. The codes it hands out
 * carry this package's own name, so no two of the three can ever reach for the same one.
 *
 * <p>Every call goes over HTTP, like every other test here. Nothing reaches for the service, the
 * repository or the row — which is worth saying twice for this ticket, because the rule under
 * test is a pure function and the temptation to call it directly is real. That temptation is
 * answered by {@code rewards.WhoAnOfferIsForTest}, which calls it directly and knows nothing
 * about an application; what is asserted here is everything that test cannot say.
 */
class OffersWithRulesThisTestWrites {

    /** Nothing seeded, demonstrated or written by another test package is called this. */
    private static final AtomicInteger HANDED_OUT = new AtomicInteger();

    private final TestRestTemplate http;

    private final List<String> written = new ArrayList<>();

    OffersWithRulesThisTestWrites(TestRestTemplate http) {
        this.http = http;
    }

    /** A code no offer in this run has, and none will have again. */
    String aCodeNobodyHasUsed() {
        return "NOT_FOR_YOU_" + HANDED_OUT.incrementAndGet();
    }

    /**
     * Writes a draft with whatever rules this test needs, publishes it, and insists on both —
     * the state every test here starts from, because a draft is refused for being a draft long
     * before anybody looks at who it is for.
     *
     * <p>All three thresholds on one call, each of them nullable, because the question this
     * package is about is what happens when several are set at once and a helper taking one at a
     * time could not ask it.
     */
    OfferView onSale(String code, String title, long costInPoints, Integer minimumStreakWeeks,
                     String requiresBadge, Long minimumLifetimePointsEarned) {
        writes(code, title, costInPoints, minimumStreakWeeks, requiresBadge,
                minimumLifetimePointsEarned);
        return publishes(code);
    }

    /** Writes a draft and insists it took, handing back the offer as the catalogue now holds it. */
    OfferView writes(String code, String title, long costInPoints, Integer minimumStreakWeeks,
                     String requiresBadge, Long minimumLifetimePointsEarned) {
        // A HashMap rather than Map.of, because every rule may be absent and Map.of will not hold
        // a null: "no such rule" is the ordinary case and is half of what this package asserts.
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Something to spend points on.");
        offer.put("costInPoints", costInPoints);
        offer.put("voucherPrefix", "WHO");
        offer.put("minimumStreakWeeks", minimumStreakWeeks);
        offer.put("requiresBadge", requiresBadge);
        offer.put("minimumLifetimePointsEarned", minimumLifetimePointsEarned);
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

    /** One offer as whoever runs the catalogue reads it, rules and all. */
    OfferView theOffer(String code) {
        ResponseEntity<OfferView> read =
                http.getForEntity("/api/admin/rewards/{code}", OfferView.class, code);
        assertThat(read.getStatusCode()).describedAs("reading \"" + code + "\"")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
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
     * One entry of that reading, by its code — and the failure it raises when the entry is not
     * there at all is the one worth reporting in words, because "shown locked, never hidden" is
     * the whole customer-facing decision of this ticket.
     */
    RewardForACustomerView asReadBy(long customerId, String code) {
        return theCatalogueAsReadBy(customerId).stream()
                .filter(entry -> code.equals(entry.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("customer " + customerId + " cannot see \""
                        + code + "\" at all; an offer that is not for somebody is meant to be "
                        + "shown locked rather than hidden, so this is the failure and not a "
                        + "missing set-up step"));
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
