package io.dataroots.savingstreak.abundlehandedoverasonevoucher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The five things a bundle may not be made of, each refused where it is written and each in a
 * sentence somebody can act on.
 *
 * <p><strong>Refused at composition rather than at claim, which is the decision this class
 * exists to pin.</strong> A bundle's contents are fixed when it is composed — there is no way to
 * edit them afterwards — so this is the one moment the application gets to say no, and a draft
 * that named something impossible would be a half-written reward nobody could either publish or
 * correct.
 *
 * <p><strong>Every sentence is asserted, not only every status.</strong> A refusal is an
 * instruction: the status says what sort of mistake it was and the sentence says which line of
 * the form it was on, and a test that checked only the first would pass against an application
 * that said "something is wrong" five times.
 *
 * <p>Nothing here claims anything and nothing here needs points. Every offer written is
 * withdrawn afterwards, including the members written as set-up for a bundle that was then
 * refused, so the test pinning the customer's catalogue to four entries goes on passing.
 */
class ComposingABundleIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private BundlesThisTestWrites offers;

    @BeforeEach
    void readyTheCatalogue() {
        offers = new BundlesThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * A bundle naming itself is refused as something that cannot be, and not as something that
     * is missing.
     *
     * <p>The sentence is the whole of this test. At the moment it is checked the code genuinely
     * does not exist — the row has not been written — so "there is no such offer" would be
     * technically true and completely useless, and would send somebody looking for a typo in a
     * code they got right.
     */
    @Test
    void a_bundle_that_names_itself_is_refused_for_containing_itself() {
        String member = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(member, "Something real", 4, null);

        ResponseEntity<JsonNode> refused = offers.triesToWrite(
                aBundle(bundle, Map.of(bundle, 1, member, 1)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .as("a bundle cannot contain itself, and saying so is the only useful sentence")
                .contains("cannot contain itself")
                .contains(bundle);
    }

    /**
     * A bundle naming an offer the catalogue has never heard of is refused, naming the code that
     * could not be found.
     *
     * <p>The code has to be in the sentence, because a hamper with four lines and one typo is
     * otherwise a refusal somebody has to bisect by hand. A bad request rather than a 404: the
     * address is real and what arrived is a form with a wrong word in it, which is the same
     * reading a customer claiming an unknown code already gets.
     */
    @Test
    void a_bundle_naming_an_offer_that_does_not_exist_is_refused_naming_the_code() {
        String member = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(member, "Something real", 4, null);

        ResponseEntity<JsonNode> refused = offers.triesToWrite(
                aBundle(bundle, Map.of(member, 1, "NOTHING_IS_CALLED_THIS", 1)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("NOTHING_IS_CALLED_THIS");
        assertThat(offers.triesToRead(bundle).getStatusCode())
                .as("and the bundle was never written, not even as a draft")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * A bundle naming a withdrawn offer is refused, because a withdrawn offer is a record rather
     * than something the scheme still hands over.
     *
     * <p>A conflict rather than a bad request, which is the reading every withdrawn offer
     * already gets: nothing about the form is malformed, and what says no is that the thing they
     * named is over.
     */
    @Test
    void a_bundle_naming_a_withdrawn_offer_is_refused_because_it_is_over() {
        String withdrawn = offers.aCodeNobodyHasUsed();
        String member = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(withdrawn, "No longer offered", 4, null);
        offers.anItemOnSale(member, "Still offered", 4, null);
        offers.withdraws(withdrawn);

        ResponseEntity<JsonNode> refused = offers.triesToWrite(
                aBundle(bundle, Map.of(member, 1, withdrawn, 1)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains(withdrawn).contains("withdrawn");
    }

    /**
     * A bundle of one is refused, because one thing in a wrapper is that thing sold twice.
     *
     * <p>Priced separately, stocked separately and claimable under two codes, which is not a
     * hamper and is not what anybody composing one means. The sentence quotes the rule and what
     * they sent, because the difference between them is the whole of the correction.
     */
    @Test
    void a_bundle_of_fewer_than_two_offers_is_refused() {
        String member = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(member, "The only member", 4, null);

        ResponseEntity<JsonNode> refused = offers.triesToWrite(
                aBundle(bundle, Map.of(member, 2)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .as("two of one thing is still one thing")
                .contains("at least 2");
    }

    /**
     * A quantity below one is refused, because nought of something is not something that is in a
     * bundle.
     *
     * <p>A fact about the form, which is why it is a bad request. Left unchecked it would be a
     * line that took no stock, read as "nothing" on the card, and divided by nought the first
     * time anybody worked out how many of the bundle could be made up.
     */
    @Test
    void a_quantity_below_one_is_refused_where_it_is_written() {
        String first = offers.aCodeNobodyHasUsed();
        String second = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(first, "One of these", 4, null);
        offers.anItemOnSale(second, "None of these", 4, null);

        ResponseEntity<JsonNode> refused = offers.triesToWrite(
                aBundle(bundle, Map.of(first, 1, second, 0)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains(second).contains("at least 1");
    }

    /**
     * A bundle inside a bundle is refused, and the sentence says what to do instead.
     *
     * <p>One level deep is a rule that can be checked in a line and proved by reading it. A
     * bundle of bundles has no natural bottom: what is left of the outer one becomes a walk of
     * unknown depth on every read of every card, and two of them naming each other is a cycle.
     */
    @Test
    void a_bundle_inside_a_bundle_is_refused() {
        String first = offers.aCodeNobodyHasUsed();
        String second = offers.aCodeNobodyHasUsed();
        String inner = offers.aCodeNobodyHasUsed();
        String outer = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(first, "A thing", 4, null);
        offers.anItemOnSale(second, "Another thing", 4, null);
        offers.aBundleOnSale(inner, "Both things", 7, null, Map.of(first, 1, second, 1));

        ResponseEntity<JsonNode> refused = offers.triesToWrite(
                aBundle(outer, Map.of(inner, 1, first, 1)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains(inner).contains("bundle cannot contain");
    }

    /**
     * One offer named twice is refused, and is told where to say how many instead.
     *
     * <p>Two lines naming the same thing would add up correctly and read back as a hamper
     * containing popcorn and popcorn. Somebody who meant two of it has a box to say so in, and
     * refusing keeps "two or more members" meaning two or more <em>things</em>.
     */
    @Test
    void the_same_offer_named_twice_is_refused_and_pointed_at_the_quantity() {
        String member = offers.aCodeNobodyHasUsed();
        String other = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(member, "Named twice", 4, null);
        offers.anItemOnSale(other, "Named once", 4, null);
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(Map.of("code", member, "quantity", 1));
        lines.add(Map.of("code", other, "quantity", 1));
        lines.add(Map.of("code", member, "quantity", 1));
        Map<String, Object> asked = aBundle(bundle, Map.of());
        asked.put("members", lines);

        ResponseEntity<JsonNode> refused = offers.triesToWrite(asked);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains(member).contains("twice");
    }

    /** A bundle as the form sends one, with whatever this test wants inside it. */
    private static Map<String, Object> aBundle(String code, Map<String, Integer> members) {
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", "Something composed");
        offer.put("description", "Several things, handed over at once.");
        offer.put("costInPoints", 9);
        offer.put("voucherPrefix", "BND");
        offer.put("members", BundlesThisTestWrites.asLines(members));
        return offer;
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }
}
