package io.dataroots.savingstreak.runningthecatalogue;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A change to the catalogue this application will not make, refused with a reason the person who
 * typed it can act on — and nothing written down.
 *
 * <p>Both halves are asserted every time. A refusal that wrote the row anyway would leave an
 * administrator looking at an error message and a customer looking at the offer it was about,
 * which is worse than either.
 *
 * <p>The statuses are asserted as well as the sentences, because they are the part a page acts on
 * and the part nobody notices going wrong. A code already in use and a price of nought are not the
 * same sort of mistake: one is a conflict with something that exists and the other is a form to
 * correct, and a screen that treated them alike would tell somebody to fix a field that is fine.
 */
class RunningTheCatalogueIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private OffersThisTestWrites offers;

    @BeforeEach
    void readyToWriteOffers() {
        offers = new OffersThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * A code another offer already has. A conflict rather than a bad request: nothing typed is
     * malformed, and what the administrator does next is edit the offer that is in the way or
     * choose a different code — so the sentence names it back.
     */
    @Test
    void a_code_the_catalogue_already_has_is_refused_as_a_conflict() {
        ResponseEntity<JsonNode> refused = offers.triesToWrite(
                anOfferLike(Map.of("code", "CINEMA_TICKET")));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("CINEMA_TICKET");
        // And the offer that was already there is untouched, at the price it has always been.
        assertThat(offers.theOffer("CINEMA_TICKET").costInPoints()).isEqualTo(100);
    }

    /**
     * A price below a single point. An offer at nought is not a reward, it is a button, and it
     * would make the one figure this whole application asks people to work towards meaningless.
     */
    @Test
    void a_price_below_one_point_is_refused() {
        String code = offers.aCodeNobodyHasUsed();

        ResponseEntity<JsonNode> refused = offers.triesToWrite(
                anOfferLike(Map.of("code", code, "costInPoints", 0)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).isNotBlank();
        assertThat(offers.triesToRead(code).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** A title of spaces is a blank title to everybody who has to read the card. */
    @Test
    void an_empty_title_is_refused() {
        String code = offers.aCodeNobodyHasUsed();

        ResponseEntity<JsonNode> refused = offers.triesToWrite(
                anOfferLike(Map.of("code", code, "title", "   ")));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).isNotBlank();
        assertThat(offers.triesToRead(code).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * An offer with no code at all, and one with no voucher prefix. Both are fields the row cannot
     * be written without, and both are refused in a sentence about the field rather than as a
     * request that could not be read.
     */
    @Test
    void an_offer_missing_something_it_cannot_be_written_without_is_refused() {
        Map<String, Object> withoutACode = anOfferLike(Map.of());
        withoutACode.remove("code");
        assertThat(offers.triesToWrite(withoutACode).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> withoutAPrefix = anOfferLike(Map.of("code",
                offers.aCodeNobodyHasUsed()));
        withoutAPrefix.remove("voucherPrefix");
        ResponseEntity<JsonNode> refused = offers.triesToWrite(withoutAPrefix);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).isNotBlank();
    }

    /** A request with no body at all is answered in this application's words rather than Spring's. */
    @Test
    void an_offer_with_no_body_at_all_is_refused_in_words() {
        ResponseEntity<JsonNode> refused = http.postForEntity("/api/admin/rewards", null,
                JsonNode.class);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).isNotBlank();
    }

    /**
     * The rule the whole of the rest of this feature leans on: an offer's code is fixed from the
     * moment it exists, because every claim already made names it.
     *
     * <p>Refused rather than ignored, and that is the decision worth the test. A PATCH that
     * quietly dropped the field would answer 200 and show an administrator a rename that never
     * happened — and the code they would go on addressing it by would be the old one.
     */
    @Test
    void changing_an_offers_code_is_refused_and_the_code_does_not_move() {
        String code = offers.aCodeNobodyHasUsed();
        String wanted = offers.aCodeNobodyHasUsed();
        offers.writes(code, "A name it will keep", 25, "KEP");

        ResponseEntity<JsonNode> refused = offers.triesToChange(code, Map.of("code", wanted));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains(code);
        assertThat(offers.theOffer(code).code()).isEqualTo(code);
        assertThat(offers.triesToRead(wanted).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Sending the offer's own code back is not a rename, and is let through with the rest. */
    @Test
    void a_change_that_sends_the_code_it_already_has_is_not_a_rename() {
        String code = offers.aCodeNobodyHasUsed();
        offers.writes(code, "Before", 25, "BEF");

        OfferView changed = offers.changes(code, Map.of("code", code, "title", "After"));

        assertThat(changed.code()).isEqualTo(code);
        assertThat(changed.title()).isEqualTo("After");
    }

    /**
     * A change that asks for nothing. Answering it 200 with the offer unchanged would tell a page
     * its edit went through — the same argument the bills make about their own empty change.
     */
    @Test
    void a_change_that_asks_for_nothing_is_refused() {
        String code = offers.aCodeNobodyHasUsed();
        offers.writes(code, "Unchanged", 25, "UNC");

        assertThat(offers.triesToChange(code, Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(offers.theOffer(code).title()).isEqualTo("Unchanged");
    }

    /**
     * A withdrawn offer is a record rather than something still being run: it cannot be edited and
     * it cannot be put back on sale.
     *
     * <p>Refused rather than allowed, because "taken down for good" has to mean what it says.
     * Somebody who wants to sell the thing again creates an offer for it, which is honest about it
     * being a new one — and leaves the vouchers already out there pointing at what they were
     * actually bought from.
     */
    @Test
    void a_withdrawn_offer_can_be_neither_edited_nor_put_back_on_sale() {
        String code = offers.aCodeNobodyHasUsed();
        offers.writes(code, "Gone for good", 25, "GON");
        offers.publishes(code);
        offers.withdraws(code);

        ResponseEntity<JsonNode> edit = offers.triesToChange(code, Map.of("title", "Back again"));
        ResponseEntity<JsonNode> republish = offers.triesToPublish(code);

        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(republish.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(republish)).contains(code);
        assertThat(offers.theOffer(code).title()).isEqualTo("Gone for good");
        assertThat(offers.theOffer(code).state()).isEqualTo("WITHDRAWN");
        assertThat(offers.theCatalogueACustomerReads()).extracting(RewardView::code)
                .doesNotContain(code);
    }

    /** Withdrawing twice changes nothing, because the second press asks for what is already so. */
    @Test
    void withdrawing_something_already_withdrawn_changes_nothing() {
        String code = offers.aCodeNobodyHasUsed();
        offers.writes(code, "Already gone", 25, "AGN");
        OfferView first = offers.withdraws(code);

        assertThat(offers.withdraws(code)).isEqualTo(first);
    }

    /**
     * An address naming an offer nobody has heard of is a page that is not there, on every one of
     * the four paths that name one — which is not the answer a customer's claim gets for the same
     * code, on purpose: a claim is a form with a wrong word in it.
     */
    @Test
    void every_administration_path_naming_an_offer_nobody_has_heard_of_is_not_found() {
        String nothing = offers.aCodeNobodyHasUsed();

        assertThat(offers.triesToRead(nothing).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(offers.triesToChange(nothing, Map.of("title", "Anything")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(offers.triesToPublish(nothing).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(offers.triesToWithdraw(nothing).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(offers.triesToRead(nothing))).contains(nothing);
    }

    /**
     * An offer as a form would send it, with whatever this test wants said differently written
     * over the top. A mutable map, because the refusals worth testing include fields left out
     * altogether and {@code Map.of} has no way to say that.
     */
    private Map<String, Object> anOfferLike(Map<String, Object> differences) {
        Map<String, Object> offer = new HashMap<>(Map.of(
                "code", "AN_OFFER_THIS_TEST_NEVER_EXPECTS_TO_WRITE",
                "title", "A perfectly good title",
                "description", "Words a card could carry.",
                "costInPoints", 40,
                "voucherPrefix", "REF"));
        offer.putAll(differences);
        return offer;
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }
}
