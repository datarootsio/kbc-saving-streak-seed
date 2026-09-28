package io.dataroots.savingstreak.abundlehandedoverasonevoucher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.RewardView;
import io.dataroots.savingstreak.support.TheRowsATestLeftBehind;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offers and bundles, written over the administration API, and the tidying up afterwards.
 *
 * <p><strong>The tidying up is why this class exists, and the argument is the one
 * {@code runningthecatalogue.OffersThisTestWrites} makes at length.</strong> One database file
 * serves the whole run, and {@code ClaimingRewardsApiTest} asserts that the customer's catalogue
 * is <em>exactly</em> the four seeded offers at their four prices — the strongest rail on this
 * feature and a test nobody may edit. An offer left published here would break a test in a
 * package this one never mentions. So everything written through this helper is written down and
 * {@link #putTheCatalogueBack} withdraws the lot, members first-written-last-withdrawn order
 * being immaterial because withdrawing is not a deletion — and then takes out the two kinds of
 * row a withdrawal leaves behind, which are a bundle's member lines and any hold standing on one
 * of these offers. Both cost every later test in the run something, and neither has an address
 * that removes it; the argument is on {@code support.TheRowsATestLeftBehind}.
 *
 * <p>It is a fourth copy of about sixty lines, after the ones in {@code runningthecatalogue},
 * {@code anofferthatcanrunout} and {@code apricethatislowerthisweek}, and the duplication is
 * deliberate for the fourth time in a row. Every one of those lives in a package whose tests are
 * rails this ticket may not touch, and lifting one into {@code support} would mean editing a
 * file a rail is made of in order to save writing one out. The codes handed out here carry this
 * package's own word, so no two of the four can ever reach for the same one.
 *
 * <p><strong>What this one adds is a bundle.</strong> A bundle is composed out of offers that
 * already exist, so almost every test here writes two or three items first and then something
 * that contains them — which means the members are written through this helper too, and go back
 * out through the same list at the end.
 *
 * <p>Every call goes over HTTP, like every other test here. Nothing reaches for the service, the
 * repository or the row.
 */
class BundlesThisTestWrites {

    /** Nothing seeded, demonstrated or written by another test package is called this. */
    private static final AtomicInteger HANDED_OUT = new AtomicInteger();

    private final TestRestTemplate http;

    private final List<String> written = new ArrayList<>();

    BundlesThisTestWrites(TestRestTemplate http) {
        this.http = http;
    }

    /** A code no offer in this run has, and none will have again. */
    String aCodeNobodyHasUsed() {
        return "HANDED_OVER_AS_ONE_" + HANDED_OUT.incrementAndGet();
    }

    /**
     * Writes a plain item with however many of it there are and publishes it — the thing a bundle
     * is composed out of.
     *
     * <p>A null stock is sendable and is the ordinary case: an offer that never runs out, which
     * is what all four seeded entries are and what a bundle's members usually will be.
     */
    OfferView anItemOnSale(String code, String title, long costInPoints, Integer stock) {
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Something to spend points on.");
        offer.put("costInPoints", costInPoints);
        offer.put("voucherPrefix", "BUN");
        offer.put("stock", stock);
        writes(offer);
        return publishes(code);
    }

    /**
     * Writes a bundle of the members handed in, prices it itself, and publishes it.
     *
     * <p>The price is the bundle's own and is not the members' added up, which is the whole
     * point of the feature: the saving is a decision somebody made rather than a subtraction
     * this application performed.
     */
    OfferView aBundleOnSale(String code, String title, long costInPoints, Integer stock,
                            Map<String, Integer> members) {
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Several things, handed over at once.");
        offer.put("costInPoints", costInPoints);
        offer.put("voucherPrefix", "BND");
        offer.put("stock", stock);
        offer.put("members", asLines(members));
        writes(offer);
        return publishes(code);
    }

    /**
     * The lines of a bundle as the form sends them: a code and how many of it go in.
     *
     * <p>In whatever order the map iterates, so a test that asserts the order a bundle reads
     * back in hands in a {@link java.util.LinkedHashMap}. Most do not care.
     */
    static List<Map<String, Object>> asLines(Map<String, Integer> members) {
        List<Map<String, Object>> lines = new ArrayList<>();
        members.forEach((code, quantity) -> {
            Map<String, Object> line = new HashMap<>();
            line.put("code", code);
            line.put("quantity", quantity);
            lines.add(line);
        });
        return lines;
    }

    /** Writes a draft and insists it took, handing back the offer as the catalogue now holds it. */
    OfferView writes(Map<String, Object> offer) {
        ResponseEntity<OfferView> created =
                http.postForEntity("/api/admin/rewards", offer, OfferView.class);
        assertThat(created.getStatusCode())
                .describedAs("writing the offer \"" + offer.get("code") + "\" this test needs: "
                        + created.getBody())
                .isEqualTo(HttpStatus.CREATED);
        written.add(String.valueOf(offer.get("code")));
        return created.getBody();
    }

    /** The same write read as unshaped JSON, for the ones this feature refuses. */
    ResponseEntity<JsonNode> triesToWrite(Map<String, Object> body) {
        return http.postForEntity("/api/admin/rewards", body, JsonNode.class);
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

    /** Withdraws an offer and insists it took, for the tests that need a withdrawn one. */
    OfferView withdraws(String code) {
        ResponseEntity<OfferView> withdrawn = http.postForEntity(
                "/api/admin/rewards/{code}/withdraw", null, OfferView.class, code);
        assertThat(withdrawn.getStatusCode())
                .describedAs("withdrawing \"" + code + "\": " + withdrawn.getBody())
                .isEqualTo(HttpStatus.OK);
        return withdrawn.getBody();
    }

    /** One offer as whoever runs the catalogue reads it, whatever state it is in. */
    OfferView theOfferAsItStands(String code) {
        ResponseEntity<OfferView> read = http.getForEntity("/api/admin/rewards/{code}",
                OfferView.class, code);
        assertThat(read.getStatusCode()).describedAs("reading \"" + code + "\"")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** The same read as unshaped JSON, for saying that an offer was never written at all. */
    ResponseEntity<JsonNode> triesToRead(String code) {
        return http.getForEntity("/api/admin/rewards/{code}", JsonNode.class, code);
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

    /** One entry of that reading, by its code, because every assertion here is about a named one. */
    RewardForACustomerView asReadBy(long customerId, String code) {
        return theCatalogueAsReadBy(customerId).stream()
                .filter(entry -> code.equals(entry.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("customer " + customerId + " cannot see \""
                        + code + "\" at all; a bundle nobody can make up is meant to be shown "
                        + "locked rather than hidden, so this is the failure and not a missing "
                        + "set-up step"));
    }

    /**
     * Takes every offer this test wrote back out of the catalogue, and leaves the seeded four
     * exactly as they were found.
     *
     * <p>Withdrawn rather than deleted, because nothing in this application deletes an offer and
     * a test that needed a back door into the database in order to <em>arrange</em> something
     * would be testing something other than the API. Idempotent, so a test that withdrew its own
     * offer as the point of the test is not a failure here — and so is a test whose bundle was
     * refused and therefore never written.
     *
     * <p><strong>Withdrawing is not enough on its own, and that is what the second half of this
     * method is for.</strong> Two kinds of row survive a withdrawal and there is no address that
     * removes either. A bundle's member lines stay in the table: the catalogue's stock
     * arithmetic short-circuits on that table being <em>entirely</em> empty, which it is in a
     * shipped application and was in this run until the first test here wrote a bundle, so
     * leaving them behind makes every claim in every later package pay for a member-line query
     * and a grouped hold query for the rest of the run. And a hold taken on one of these offers
     * by somebody other than the customer a test claims with stays live for seventy-two hours,
     * because a hold is given up by the person holding it and this helper does not know who that
     * was. Both are deleted by code, so nothing that was not written here can be touched; the
     * argument for reaching past the API to do it — and the reason claims are deliberately left
     * alone — is written out on {@link TheRowsATestLeftBehind}.
     */
    void putTheCatalogueBack() {
        written.forEach(code -> {
            HttpStatus status = (HttpStatus) triesToWithdraw(code).getStatusCode();
            assertThat(status == HttpStatus.OK || status == HttpStatus.NOT_FOUND)
                    .describedAs("putting \"" + code + "\" back out of the catalogue, so that "
                            + "the rest of this run reads the four offers it is entitled to")
                    .isTrue();
        });
        TheRowsATestLeftBehind.forTheOffers(written);
        written.clear();
    }
}
