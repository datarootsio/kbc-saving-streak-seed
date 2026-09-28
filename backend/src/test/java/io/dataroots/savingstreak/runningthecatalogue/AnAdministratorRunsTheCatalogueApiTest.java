package io.dataroots.savingstreak.runningthecatalogue;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Somebody runs the catalogue: they write an offer, read it back, correct it, put it on sale and
 * take it down again — and a customer sees exactly the half of that they are meant to.
 *
 * <p><strong>Every test here asserts on what its own requests changed.</strong> The run shares one
 * database and the catalogue is the most shared thing in it, so nothing below counts the offers or
 * names the four that were already there. What it asserts is that the offer this test wrote
 * arrived, or did not.
 *
 * <p><strong>And every test puts the catalogue back.</strong> An offer left published would show
 * up in the customer's catalogue for the rest of the run, and the test that pins that catalogue to
 * four entries at four prices is the one rail this whole feature is built not to break. The
 * tidying is in {@link OffersThisTestWrites#putTheCatalogueBack}, called after every test rather
 * than at the end of the class, so a failure in the middle does not leave the next class with a
 * fifth reward.
 *
 * <p>Anke is the customer who claims, because she is the customer the claiming tests already spend
 * from; what she spends here, she earns here.
 */
class AnAdministratorRunsTheCatalogueApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    private OffersThisTestWrites offers;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
        offers = new OffersThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * The first half of the whole ticket: an offer can be written without a release, and writing
     * one does not put it in front of anybody.
     *
     * <p>Both halves in one test on purpose. "It was created" and "the customer cannot see it" are
     * the same fact about a draft read from two ends, and asserting the first without the second
     * would pass just as well against a catalogue that published everything immediately.
     */
    @Test
    void an_offer_is_written_as_a_draft_and_no_customer_can_see_it() {
        String code = offers.aCodeNobodyHasUsed();

        OfferView written = offers.writes(code, "Winter hamper", 250, "WIN");

        assertThat(written.code()).isEqualTo(code);
        assertThat(written.title()).isEqualTo("Winter hamper");
        assertThat(written.costInPoints()).isEqualTo(250);
        assertThat(written.voucherPrefix()).isEqualTo("WIN");
        assertThat(written.state()).isEqualTo("DRAFT");
        assertThat(offers.theCatalogueACustomerReads()).extracting(RewardView::code)
                .doesNotContain(code);
    }

    /**
     * Whoever runs the catalogue sees everything in it, which is the difference between their list
     * and the customer's. A draft that could not be listed could not be found again in order to be
     * published.
     */
    @Test
    void every_offer_in_every_state_is_listed_and_each_one_can_be_read_back() {
        String draft = offers.aCodeNobodyHasUsed();
        String live = offers.aCodeNobodyHasUsed();
        offers.writes(draft, "Still being written", 30, "DRF");
        offers.writes(live, "On the shelf", 30, "LIV");
        offers.publishes(live);

        assertThat(offers.everyOffer()).extracting(OfferView::code, OfferView::state)
                .contains(tuple(draft, "DRAFT"),
                        tuple(live, "PUBLISHED"));
        // The four the application has always had are in the same list, published, because a back
        // office that could not see the catalogue it inherited would be a second catalogue.
        assertThat(offers.everyOffer()).extracting(OfferView::code).contains("CHARITY_DONATION");
        assertThat(offers.theOffer(draft).title()).isEqualTo("Still being written");
    }

    /**
     * A draft is not claimable, and the refusal says the true thing rather than the convenient
     * one. "There is nothing called that" would have been less code and would have sent somebody
     * off to check their spelling for a code that is perfectly real.
     *
     * <p>Asserted through the claim endpoint rather than by reading the catalogue, because the
     * catalogue is what an honest page draws itself from and this is the request a page that has
     * been open too long actually sends.
     */
    @Test
    void a_draft_can_never_be_claimed_and_is_refused_as_not_on_sale() {
        String code = offers.aCodeNobodyHasUsed();
        offers.writes(code, "Not ready yet", 5, "NRY");
        long pointsBefore = seeded.pointsBalanceOf(ANKE);

        ResponseEntity<JsonNode> refused = tryToClaim(code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("Not ready yet");
        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(pointsBefore);
    }

    /**
     * Publishing is the moment the thing exists for a customer: it appears in the catalogue at the
     * price it was written at, and it can actually be had.
     *
     * <p>Claimed as well as listed, because a reward that is on the page and refuses at the last
     * step is worse than one that was never there — the same reason the existing claiming test
     * walks every seeded reward rather than trusting the list.
     */
    @Test
    void publishing_puts_the_offer_in_the_catalogue_and_it_can_be_claimed() {
        String code = offers.aCodeNobodyHasUsed();
        offers.writes(code, "A pot of jam", 5, "JAM");

        OfferView published = offers.publishes(code);

        assertThat(published.state()).isEqualTo("PUBLISHED");
        assertThat(offers.theCatalogueACustomerReads())
                .extracting(RewardView::code, RewardView::costInPoints)
                .contains(tuple(code, 5L));
        earn(5);
        long pointsBefore = seeded.pointsBalanceOf(ANKE);
        ClaimedRewardView claimed = claim(code);
        assertThat(claimed.pointsSpent()).isEqualTo(5);
        assertThat(claimed.voucherCode()).matches("SS-JAM-[0-9A-Z]{6}");
        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(pointsBefore - 5);
    }

    /**
     * Withdrawing takes it out of the catalogue and refuses the next claim, and the refusal is the
     * same true sentence a draft gets: the offer is real, and it is not on sale.
     */
    @Test
    void withdrawing_takes_the_offer_out_of_the_catalogue_and_stops_it_being_claimed() {
        String code = offers.aCodeNobodyHasUsed();
        offers.writes(code, "Last season's mug", 5, "MUG");
        offers.publishes(code);

        OfferView withdrawn = offers.withdraws(code);

        assertThat(withdrawn.state()).isEqualTo("WITHDRAWN");
        assertThat(offers.theCatalogueACustomerReads()).extracting(RewardView::code)
                .doesNotContain(code);
        // Still there for whoever runs it, because a withdrawn offer is a record rather than a
        // deletion — which is the whole reason the vouchers below still mean something.
        assertThat(offers.theOffer(code).state()).isEqualTo("WITHDRAWN");
        assertThat(tryToClaim(code).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * The promise that makes withdrawing safe: a voucher issued before the offer came down reads
     * exactly as it did, afterwards and for ever.
     *
     * <p>Asserted field by field against the claim as it was answered, because "still reads
     * correctly" is not a status — it is the title, the price paid and the code on the voucher,
     * and a catalogue read at display time would have lost all three the moment the row left it.
     */
    @Test
    void a_voucher_issued_before_the_offer_was_withdrawn_reads_exactly_as_it_did() {
        String code = offers.aCodeNobodyHasUsed();
        offers.writes(code, "A pot of honey", 5, "HNY");
        offers.publishes(code);
        earn(5);
        ClaimedRewardView issued = claim(code);

        offers.withdraws(code);

        ClaimedRewardView[] afterwards = http.getForObject("/api/customers/{id}/redemptions",
                ClaimedRewardView[].class, seeded.customerIdOf(ANKE));
        assertThat(afterwards).filteredOn(one -> one.id().equals(issued.id())).singleElement()
                .satisfies(still -> {
                    assertThat(still.code()).isEqualTo(code);
                    assertThat(still.title()).isEqualTo("A pot of honey");
                    assertThat(still.pointsSpent()).isEqualTo(5);
                    assertThat(still.voucherCode()).isEqualTo(issued.voucherCode());
                });
    }

    /**
     * Everything but the code can be changed, one field at a time, and on an offer that is already
     * live — because a typo on a published reward is a correction rather than an incident, and
     * taking the reward off the screen to fix a word would be the incident.
     *
     * <p>The customer's catalogue is read at the end rather than the administration list, because
     * the point of an edit is the page somebody is looking at.
     */
    @Test
    void everything_but_the_code_is_changed_on_a_published_offer_and_the_customer_sees_it() {
        String code = offers.aCodeNobodyHasUsed();
        offers.writes(code, "Cinama ticket", 90, "CNA");
        offers.publishes(code);

        offers.changes(code, Map.of("title", "Cinema ticket"));
        offers.changes(code, Map.of("costInPoints", 120));
        offers.changes(code, Map.of("description", "One seat, one film, any afternoon."));
        OfferView corrected = offers.changes(code, Map.of("voucherPrefix", "CNM"));

        assertThat(corrected.code()).isEqualTo(code);
        assertThat(corrected.state()).isEqualTo("PUBLISHED");
        assertThat(corrected.voucherPrefix()).isEqualTo("CNM");
        assertThat(offers.theCatalogueACustomerReads())
                .filteredOn(reward -> reward.code().equals(code)).singleElement()
                .satisfies(reward -> {
                    assertThat(reward.title()).isEqualTo("Cinema ticket");
                    assertThat(reward.costInPoints()).isEqualTo(120);
                    assertThat(reward.description()).isEqualTo("One seat, one film, any afternoon.");
                });
    }

    /**
     * A field left out of a change is left alone, which is what makes correcting one word one
     * field rather than the whole form retyped.
     */
    @Test
    void a_change_leaves_every_field_it_does_not_name_exactly_as_it_was() {
        String code = offers.aCodeNobodyHasUsed();
        OfferView written = offers.writes(code, "A bag of coffee", 60, "COF");

        OfferView changed = offers.changes(code, Map.of("costInPoints", 75));

        assertThat(changed.costInPoints()).isEqualTo(75);
        assertThat(changed.title()).isEqualTo(written.title());
        assertThat(changed.description()).isEqualTo(written.description());
        assertThat(changed.voucherPrefix()).isEqualTo(written.voucherPrefix());
        assertThat(changed.state()).isEqualTo("DRAFT");
    }

    /**
     * Pressing publish on something already published changes nothing and is not an error, the
     * same reading pausing an already-paused saving rule is given: whoever pressed it wants the
     * offer on sale, and it is.
     */
    @Test
    void publishing_something_already_published_changes_nothing() {
        String code = offers.aCodeNobodyHasUsed();
        offers.writes(code, "A second look", 20, "SEC");
        OfferView first = offers.publishes(code);

        OfferView again = offers.publishes(code);

        assertThat(again).isEqualTo(first);
        assertThat(offers.theCatalogueACustomerReads())
                .filteredOn(reward -> reward.code().equals(code)).hasSize(1);
    }

    /** Claimed by the customer, out of the one pot of points every account of theirs earns into. */
    private ClaimedRewardView claim(String code) {
        ResponseEntity<ClaimedRewardView> claimed = http.postForEntity(
                "/api/customers/{id}/redemptions", Map.of("reward", code),
                ClaimedRewardView.class, seeded.customerIdOf(ANKE));
        assertThat(claimed.getStatusCode()).describedAs("claiming \"" + code + "\"")
                .isEqualTo(HttpStatus.CREATED);
        return claimed.getBody();
    }

    /** The same claim read as unshaped JSON, for the ones this feature refuses. */
    private ResponseEntity<JsonNode> tryToClaim(String code) {
        return http.postForEntity("/api/customers/{id}/redemptions", Map.of("reward", code),
                JsonNode.class, seeded.customerIdOf(ANKE));
    }

    /**
     * Earns at least the points this test is about to spend, at a point per euro. A test that
     * needs points is the test that earns them — leaning on what an earlier test left in the
     * account is how a failure lands in a class that never mentioned this one.
     */
    private void earn(long points) {
        ResponseEntity<DepositView> deposit = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", points + ".00", "fromCurrentAccountId",
                        seeded.currentAccountOf(ANKE)),
                DepositView.class, seeded.savingsAccountOf(ANKE));
        assertThat(deposit.getStatusCode())
                .describedAs("a deposit this test needs in order to have points to spend")
                .isEqualTo(HttpStatus.CREATED);
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }
}
