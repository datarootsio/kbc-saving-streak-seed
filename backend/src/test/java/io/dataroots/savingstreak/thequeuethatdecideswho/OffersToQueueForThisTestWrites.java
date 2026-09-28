package io.dataroots.savingstreak.thequeuethatdecideswho;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.PlaceInTheQueueView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.WaitingListEntryView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sold-out offers to queue for, written over the administration API, the places taken in their
 * queues, and the tidying up afterwards.
 *
 * <p><strong>The tidying up is why this class exists, and the argument is the one
 * {@code runningthecatalogue.OffersThisTestWrites} makes at length and which four packages have
 * each made again since.</strong> One database file serves the whole run, and
 * {@code ClaimingRewardsApiTest} asserts that the catalogue is <em>exactly</em> the four seeded
 * offers at their four prices — the strongest rail on this feature and a test nobody may edit.
 * An offer left published here would break a test in a package this one never mentions. So
 * every offer written through this helper is written down and {@link #putTheCatalogueBack}
 * withdraws the lot.
 *
 * <p>It is a fifth copy of about fifty lines rather than a shared class, which is deliberate and
 * is the same deliberate duplication the four before it made: every one of those lives inside a
 * package whose tests are rails this ticket may not touch, so lifting one of them into
 * {@code support} would mean editing a file a rail is made of in order to save writing this one
 * out. The codes handed out here carry this package's own word, so no two of the five can ever
 * reach for the same one.
 *
 * <p><strong>The offers here are written with no stock at all rather than with one somebody
 * else has taken.</strong> A queue may only be joined for something that has genuinely run out,
 * and there are two ways to arrange that: put one of it on the shelf and have somebody hold or
 * claim it, or put nought of it on the shelf. The second is what an administrator writing next
 * season's hamper before the hampers arrive actually does, it is the state a restock is the
 * answer to, and it needs no second customer and no points — so the tests that are about the
 * queue are about the queue rather than about arranging one.
 *
 * <p>The queue calls live here too rather than in the tests, because there are four of them and
 * every test in the package makes at least one. They are the plain HTTP the frontend makes:
 * nothing in this package reaches for a service, a repository or a row.
 */
class OffersToQueueForThisTestWrites {

    /** Nothing seeded, demonstrated or written by another test package is called this. */
    private static final AtomicInteger HANDED_OUT = new AtomicInteger();

    private final TestRestTemplate http;

    private final List<String> written = new ArrayList<>();

    OffersToQueueForThisTestWrites(TestRestTemplate http) {
        this.http = http;
    }

    /** A code no offer in this run has, and none will have again. */
    String aCodeNobodyHasUsed() {
        return "IN_THE_QUEUE_" + HANDED_OUT.incrementAndGet();
    }

    /**
     * Writes a draft with however many of it there are, publishes it, and insists on both — the
     * state every test here starts from, because a draft is refused for being a draft long
     * before anybody asks to join a queue for it.
     */
    OfferView onSale(String code, String title, long costInPoints, Integer stock) {
        // A HashMap rather than Map.of, because the stock may be absent: an offer that never
        // runs out is one of the things this package refuses a queue for.
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Something worth waiting for.");
        offer.put("costInPoints", costInPoints);
        offer.put("voucherPrefix", "QUE");
        offer.put("stock", stock);
        ResponseEntity<OfferView> created =
                http.postForEntity("/api/admin/rewards", offer, OfferView.class);
        assertThat(created.getStatusCode())
                .describedAs("writing the offer \"" + code + "\" this test needs: "
                        + created.getBody())
                .isEqualTo(HttpStatus.CREATED);
        written.add(code);
        return publishes(code);
    }

    /** The commonest shape here: nought of something, which is a thing that has run out. */
    OfferView soldOut(String code, String title) {
        return onSale(code, title, 5, 0);
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
                HttpMethod.PATCH, new HttpEntity<>(change), OfferView.class, code);
        assertThat(changed.getStatusCode())
                .describedAs("changing \"" + code + "\" the way this test needs: "
                        + changed.getBody())
                .isEqualTo(HttpStatus.OK);
        return changed.getBody();
    }

    ResponseEntity<JsonNode> triesToWithdraw(String code) {
        return http.postForEntity("/api/admin/rewards/{code}/withdraw", null, JsonNode.class,
                code);
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
                        + code + "\" at all; a sold-out offer is meant to be shown rather than "
                        + "hidden, so this is the failure and not a missing step"));
    }

    /** Puts a customer in the queue for something, and insists it took. */
    PlaceInTheQueueView joins(long customerId, String code) {
        ResponseEntity<PlaceInTheQueueView> joined = http.postForEntity(
                "/api/customers/{id}/waiting-lists", Map.of("reward", code),
                PlaceInTheQueueView.class, customerId);
        assertThat(joined.getStatusCode())
                .describedAs("putting customer " + customerId + " in the queue for \"" + code
                        + "\": " + joined.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return joined.getBody();
    }

    /** The same request read as a problem document, for the ones this feature refuses. */
    ResponseEntity<JsonNode> triesToJoin(long customerId, String code) {
        return http.postForEntity("/api/customers/{id}/waiting-lists", Map.of("reward", code),
                JsonNode.class, customerId);
    }

    /** Takes a customer out of a queue, and insists it took. */
    PlaceInTheQueueView leaves(long customerId, String code) {
        ResponseEntity<PlaceInTheQueueView> left = http.exchange(
                "/api/customers/{id}/waiting-lists/{code}", HttpMethod.DELETE, null,
                PlaceInTheQueueView.class, customerId, code);
        assertThat(left.getStatusCode())
                .describedAs("taking customer " + customerId + " out of the queue for \"" + code
                        + "\": " + left.getBody())
                .isEqualTo(HttpStatus.OK);
        return left.getBody();
    }

    /** The same, read as a problem document, for a queue they are not in to leave. */
    ResponseEntity<JsonNode> triesToLeave(long customerId, String code) {
        return http.exchange("/api/customers/{id}/waiting-lists/{code}", HttpMethod.DELETE, null,
                JsonNode.class, customerId, code);
    }

    /** Who is queued for one offer, in order, as whoever runs the scheme reads it. */
    List<WaitingListEntryView> theWaitingListFor(String code) {
        ResponseEntity<WaitingListEntryView[]> read = http.getForEntity(
                "/api/admin/rewards/{code}/waiting-list", WaitingListEntryView[].class, code);
        assertThat(read.getStatusCode())
                .describedAs("reading the waiting list for \"" + code + "\"")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** The same read as unshaped JSON, for an offer the catalogue has never heard of. */
    ResponseEntity<JsonNode> triesToReadTheWaitingListFor(String code) {
        return http.getForEntity("/api/admin/rewards/{code}/waiting-list", JsonNode.class, code);
    }

    /**
     * Takes every offer this test wrote back out of the catalogue, and leaves the seeded four
     * exactly as they were found.
     *
     * <p>Withdrawn rather than deleted, because nothing in this application deletes an offer and
     * a test that needed a back door into the database would be testing something other than
     * the API. Places left in a queue on a withdrawn offer harm nothing and are deliberately not
     * tidied up: the offer is gone from every customer's catalogue, the nightly sweep passes a
     * withdrawn offer over without promoting anybody, and reaching into the database to remove a
     * row would be this helper doing the one thing the whole test package refuses to do.
     */
    void putTheCatalogueBack() {
        written.forEach(code -> assertThat(triesToWithdraw(code).getStatusCode())
                .describedAs("putting \"" + code + "\" back out of the catalogue, so that the "
                        + "rest of this run reads the four offers it is entitled to")
                .isEqualTo(HttpStatus.OK));
        written.clear();
    }
}
