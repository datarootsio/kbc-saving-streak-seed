package io.dataroots.savingstreak.abundlehandedoverasonevoucher;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BundleMemberView;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.SeededAccounts;
import io.dataroots.savingstreak.support.VoucherAtTheCounterView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * An offer made out of other offers: composed rather than duplicated, priced on its own, claimed
 * once, and handed over as a single voucher that says what is in it.
 *
 * <p><strong>Every assertion here is about what the API says after something happened.</strong>
 * A member's stock is drawn down by a claim that names the bundle and nothing else — there is no
 * row anywhere saying that two cinema tickets went — so the only honest way to test it is to
 * read what is left afterwards, from the same address a customer reads it from. A test that went
 * looking for a decrement would be passing against the design this ticket refuses.
 *
 * <p><strong>The card and the refusal are asserted together wherever both exist</strong>, which
 * is the property the whole wave is built on: a customer told on the card that a hamper cannot
 * be made up must be refused in exactly those words if they press anyway.
 *
 * <p><strong>Every test earns what it is about to spend and asserts on deltas.</strong> One
 * database serves the run, so nothing here leans on what another test left in an account, and
 * every offer written is withdrawn afterwards so that the test pinning the customer's catalogue
 * to four entries at four prices goes on passing.
 *
 * <p>Anke is the customer, because she is the one the claiming tests already spend from.
 */
class ABundleHandedOverAsOneVoucherApiTest extends ApiIntegrationTest {

    /** Nothing seeded or opened by another test package is called this. */
    private static final AtomicInteger OPENED = new AtomicInteger();

    private SeededAccounts seeded;

    private BundlesThisTestWrites offers;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
        offers = new BundlesThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * A bundle names two offers with quantities, carries its own price, and says on the card
     * what is in it.
     *
     * <p>The price is the assertion that matters most. Ten and four are the members; the bundle
     * is nine, which is neither their sum nor any percentage of it — it is a figure somebody
     * chose, and the whole argument for pricing a bundle rather than summing one is that the
     * saving is a decision.
     */
    @Test
    void a_bundle_is_two_offers_with_quantities_at_a_price_of_its_own_and_says_what_is_in_it() {
        String popcorn = offers.aCodeNobodyHasUsed();
        String ticket = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(popcorn, "A bag of popcorn", 4, null);
        offers.anItemOnSale(ticket, "One cinema seat", 10, null);
        Map<String, Integer> members = new LinkedHashMap<>();
        members.put(ticket, 2);
        members.put(popcorn, 1);

        OfferView composed = offers.aBundleOnSale(bundle, "A night in", 9, null, members);

        assertThat(composed.costInPoints())
                .as("the bundle's own price, which is neither the sum of its members nor a "
                        + "percentage of it")
                .isEqualTo(9);
        assertThat(composed.contents())
                .extracting(BundleMemberView::code, BundleMemberView::quantity)
                .containsExactly(tuple(ticket, 2), tuple(popcorn, 1));
        RewardForACustomerView card = readingOf(bundle);
        assertThat(card.contents())
                .as("and a customer is told what they are getting for the one price")
                .extracting(BundleMemberView::title, BundleMemberView::quantity)
                .containsExactly(tuple("One cinema seat", 2), tuple("A bag of popcorn", 1));
        assertThat(card.costInPoints()).isEqualTo(9);
    }

    /**
     * Claiming a bundle spends its own price once and draws every member's stock down by the
     * quantity that is in it.
     *
     * <p>The load-bearing test of the ticket. One claim, one price out of the balance, and three
     * things gone off three shelves — two of one of them — none of which is named by the claim
     * that took them. Asserted through what is left of each member afterwards, which is the only
     * place the draw-down is visible and the only place it needs to be.
     */
    @Test
    void claiming_a_bundle_spends_its_own_price_once_and_draws_every_member_down_by_its_quantity() {
        String popcorn = offers.aCodeNobodyHasUsed();
        String ticket = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(popcorn, "Popcorn by the bag", 4, 5);
        offers.anItemOnSale(ticket, "A seat at the pictures", 10, 6);
        offers.aBundleOnSale(bundle, "Two seats and a bag", 9, null, Map.of(ticket, 2,
                popcorn, 1));
        earn(20);
        long before = pointsBalance();

        ClaimedRewardView claimed = claim(bundle);

        assertThat(claimed.code()).isEqualTo(bundle);
        assertThat(pointsBalance())
                .as("the bundle's own price, once, and not the members' prices as well")
                .isEqualTo(before - 9);
        assertThat(readingOf(ticket).whatIsLeft())
                .as("six existed and a bundle containing two of them went out")
                .isEqualTo(4);
        assertThat(readingOf(popcorn).whatIsLeft())
                .as("five existed and the same bundle contained one")
                .isEqualTo(4);
        assertThat(claimsOf().stream().filter(claim -> bundle.equals(claim.code())).count())
                .as("one claim, naming the bundle, and no claim naming either member")
                .isEqualTo(1);
        assertThat(claimsOf().stream().filter(claim -> ticket.equals(claim.code())).count())
                .isZero();
    }

    /**
     * A member's own claims and the bundles that contain it draw down the same stock.
     *
     * <p>The other half of the arithmetic, and the half that a bundle implemented as a second
     * catalogue would get wrong. The cinema seats are a thing somebody can buy on their own
     * <em>and</em> a thing inside a hamper, and there are only so many seats: three exist, one
     * goes on its own and a bundle takes two, and there are none.
     */
    @Test
    void a_member_is_drawn_down_by_its_own_claims_and_by_the_bundles_that_contain_it_alike() {
        String ticket = offers.aCodeNobodyHasUsed();
        String popcorn = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(ticket, "Three seats in all", 5, 3);
        offers.anItemOnSale(popcorn, "Popcorn aplenty", 2, null);
        offers.aBundleOnSale(bundle, "A seat each and a bag", 8, null, Map.of(ticket, 2,
                popcorn, 1));
        earn(20);

        claim(ticket);
        assertThat(readingOf(ticket).whatIsLeft()).isEqualTo(2);

        claim(bundle);

        assertThat(readingOf(ticket).whatIsLeft())
                .as("one went on its own and two went inside the bundle, out of three")
                .isZero();
        assertThat(readingOf(ticket).claimable()).isFalse();
        assertThat(readingOf(bundle).claimable())
                .as("and the bundle cannot be made up either, because its seats have gone")
                .isFalse();
    }

    /**
     * How many of a bundle are left is the least of its own stock and what its scarcest member
     * allows.
     *
     * <p>Six hampers in the cupboard and enough popcorn for two is two hampers, and a card that
     * said six would be inviting four people to be refused at the last step. The figure is the
     * honest answer to "how many of these can you actually hand over", which is the only
     * question a customer is asking when they read it.
     */
    @Test
    void what_is_left_of_a_bundle_is_the_least_of_its_own_stock_and_what_its_members_allow() {
        String popcorn = offers.aCodeNobodyHasUsed();
        String ticket = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(popcorn, "Two bags only", 4, 2);
        offers.anItemOnSale(ticket, "Plenty of seats", 10, 40);
        offers.aBundleOnSale(bundle, "Six of these exist", 9, 6, Map.of(ticket, 2, popcorn, 1));

        assertThat(readingOf(bundle).whatIsLeft())
                .as("six are in the cupboard and there is popcorn for two")
                .isEqualTo(2);
        assertThat(offers.theOfferAsItStands(bundle).stock())
                .as("and the back office still reads back the six somebody typed")
                .isEqualTo(6);
    }

    /**
     * A bundle nobody gave a stock figure to is still as limited as the things inside it.
     *
     * <p>The fencepost that a naive implementation gets wrong. An empty stock box means "never
     * runs out" everywhere else in this catalogue, and reading it that way for a bundle would
     * sell hampers out of an empty cupboard — the members are the stock, whether or not anybody
     * typed a second figure.
     */
    @Test
    void a_bundle_with_no_stock_of_its_own_is_still_limited_by_its_members() {
        String popcorn = offers.aCodeNobodyHasUsed();
        String ticket = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(popcorn, "One bag in the world", 4, 1);
        offers.anItemOnSale(ticket, "Seats without number", 10, null);
        offers.aBundleOnSale(bundle, "No figure of its own", 9, null, Map.of(ticket, 1,
                popcorn, 1));

        assertThat(readingOf(bundle).whatIsLeft())
                .as("no stock figure of its own, and one bag of popcorn in the world")
                .isEqualTo(1);
        assertThat(offers.theOfferAsItStands(bundle).stock()).isNull();
    }

    /**
     * A bundle whose member cannot supply the quantity it needs is refused as nothing left, with
     * nothing spent and nothing moved.
     *
     * <p>"So that I am not handed half of something" is the user's side of this; the balance is
     * the reviewer's. The stock question is asked before the points for exactly this case, and
     * the assertion that the balance did not move is what proves the order rather than assumes
     * it. The other member is asserted untouched too: a claim refused half way through would
     * have taken the seats and not the popcorn.
     */
    @Test
    void a_bundle_whose_member_has_run_out_is_refused_with_nothing_spent_and_no_stock_moved() {
        String popcorn = offers.aCodeNobodyHasUsed();
        String ticket = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(popcorn, "The very last bag", 4, 1);
        offers.anItemOnSale(ticket, "Seats to spare", 10, 20);
        offers.aBundleOnSale(bundle, "Needs two bags", 9, null, Map.of(ticket, 1, popcorn, 2));
        earn(20);
        long before = pointsBalance();

        RewardForACustomerView card = readingOf(bundle);
        ResponseEntity<JsonNode> refused = tryToClaim(bundle);

        assertThat(refused.getStatusCode())
                .as("the offer is real and the form is right; what says no is that there is not "
                        + "enough of something to make it up, which is what a conflict means")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(pointsBalance())
                .as("a refusal takes nothing, and the members are asked before the points "
                        + "precisely so that this cannot go the other way")
                .isEqualTo(before);
        assertThat(readingOf(ticket).whatIsLeft())
                .as("and nothing was drawn down from the member that could have supplied")
                .isEqualTo(20);
        assertThat(readingOf(popcorn).whatIsLeft()).isEqualTo(1);
        assertThat(card.claimable()).isFalse();
        assertThat(card.lockedBecause()).isEqualTo("NOTHING_LEFT");
        assertThat(card.whatIsLeft())
                .as("nought of the bundle can be made up, although one bag exists")
                .isZero();
        assertThat(card.whyItIsLocked())
                .as("the sentence names the part that is missing, because that is the part "
                        + "somebody is waiting for")
                .contains("The very last bag");
        assertThat(reasonGivenBy(refused))
                .as("the card and the refusal are the same fact said at two moments, so they "
                        + "are the same sentence")
                .isEqualTo(card.whyItIsLocked());
    }

    /**
     * A bundle issues one voucher, and the counter reading names what is inside it.
     *
     * <p>One voucher rather than one per member is the spec's decision and this is the assertion
     * of it: a claim of a hamper leaves exactly one row in the customer's own list and exactly
     * one code that works at a till. What stops that being a worse deal for the person at the
     * counter is the list of contents on the reading, which is what they put on the counter.
     */
    @Test
    void a_bundle_issues_one_voucher_and_the_counter_reads_what_is_in_it() {
        String popcorn = offers.aCodeNobodyHasUsed();
        String ticket = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(popcorn, "A bag for the film", 4, null);
        offers.anItemOnSale(ticket, "A seat for the film", 10, null);
        Map<String, Integer> members = new LinkedHashMap<>();
        members.put(ticket, 2);
        members.put(popcorn, 1);
        offers.aBundleOnSale(bundle, "The whole evening", 9, null, members);
        earn(20);
        int claimsBefore = claimsOf().size();

        ClaimedRewardView claimed = claim(bundle);

        assertThat(claimsOf()).hasSize(claimsBefore + 1);
        VoucherAtTheCounterView atTheTill = voucherAtACounter(claimed.voucherCode());
        assertThat(atTheTill.title()).isEqualTo("The whole evening");
        assertThat(atTheTill.good()).isTrue();
        assertThat(atTheTill.contents())
                .as("one voucher, and the till is told the three things to hand over")
                .extracting(BundleMemberView::title, BundleMemberView::quantity)
                .containsExactly(tuple("A seat for the film", 2), tuple("A bag for the film", 1));
        VoucherAtTheCounterView handedOver = handOverTheVoucher(claimed.voucherCode());
        assertThat(handedOver.state()).isEqualTo("USED");
        assertThat(handedOver.contents())
                .as("and it still says what it was, on the screen that confirms it went")
                .hasSize(2);
    }

    /**
     * A bundle's own window, limit and stock apply exactly as they do to anything else, and its
     * members' windows do not.
     *
     * <p>The rule that keeps a bundle an offer rather than a second kind of thing. A member that
     * has closed is still a thing the scheme holds and can hand over inside a hamper; what
     * decides whether the hamper can be claimed is the hamper's own window. Asserted with a
     * member whose last day is long past, which would lock the member's own card and must leave
     * the bundle alone.
     */
    @Test
    void a_members_own_window_does_not_close_the_bundle_and_the_bundles_own_limit_does() {
        String popcorn = offers.aCodeNobodyHasUsed();
        String ticket = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        Map<String, Object> closed = new HashMap<>();
        closed.put("code", popcorn);
        closed.put("title", "Popcorn whose season ended");
        closed.put("description", "Something to spend points on.");
        closed.put("costInPoints", 4);
        closed.put("voucherPrefix", "BUN");
        closed.put("closesOn", "2020-01-01");
        offers.writes(closed);
        offers.publishes(popcorn);
        offers.anItemOnSale(ticket, "A seat with no season", 10, null);
        Map<String, Object> theBundle = new HashMap<>();
        theBundle.put("code", bundle);
        theBundle.put("title", "One each per customer");
        theBundle.put("description", "Several things, handed over at once.");
        theBundle.put("costInPoints", 9);
        theBundle.put("voucherPrefix", "BND");
        theBundle.put("maxPerCustomer", 1);
        theBundle.put("members", BundlesThisTestWrites.asLines(Map.of(ticket, 1, popcorn, 1)));
        offers.writes(theBundle);
        offers.publishes(bundle);
        earn(30);

        assertThat(readingOf(popcorn).lockedBecause())
                .as("the member's own card is shut, because its own season ended")
                .isEqualTo("CLOSED");
        assertThat(readingOf(bundle).claimable())
                .as("and the bundle is not, because a bundle is claimed against its own window")
                .isTrue();

        claim(bundle);

        RewardForACustomerView afterTheirOne = readingOf(bundle);
        assertThat(afterTheirOne.claimable())
                .as("the bundle's own cap applies as it would to anything else")
                .isFalse();
        assertThat(afterTheirOne.lockedBecause()).isEqualTo("YOU_HAVE_HAD_YOUR_LIMIT");
    }

    /**
     * The catalogue with nobody in it gains no field when a bundle is published into it.
     *
     * <p>{@code /api/rewards} is the shape this whole feature is built not to move: four fields,
     * a price and no derivation. A bundle is published into it like any other offer — there is
     * no customer and no verdict at that address — and what must stay true is that the entries
     * gain nothing for a page to start depending on.
     */
    @Test
    void the_catalogue_with_nobody_in_it_gains_no_field_when_an_offer_is_a_bundle() {
        String popcorn = offers.aCodeNobodyHasUsed();
        String ticket = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(popcorn, "A bag", 4, null);
        offers.anItemOnSale(ticket, "A seat", 10, null);
        offers.aBundleOnSale(bundle, "Both at once", 9, null, Map.of(ticket, 1, popcorn, 1));

        assertThat(offers.theCatalogueWithNobodyInIt())
                .extracting(io.dataroots.savingstreak.support.RewardView::code)
                .contains(bundle);
        assertThat(theCatalogueAsItIsSent())
                .as("no contents and no kind leaks into the catalogue that has always been four "
                        + "fields wide")
                .doesNotContain("contents")
                .doesNotContain("quantity");
    }

    /**
     * The four offers that have always been there are still items, say nothing about contents,
     * and are claimable as they always were.
     *
     * <p>The safety argument of the ticket, asserted rather than asserted-to.
     * {@code FAMILY_CINEMA_PACK} is conspicuously a bundle in everything but structure and is
     * deliberately still a plain offer at a hundred and eighty, because the one promise this
     * feature makes is that nothing a customer already knew has moved.
     */
    @Test
    void the_four_offers_that_have_always_been_there_are_still_plain_offers() {
        long customerId = seeded.customerIdOf(ANKE);

        assertThat(offers.theCatalogueAsReadBy(customerId))
                .filteredOn(entry -> theFourThatHaveAlwaysBeenThere().contains(entry.code()))
                .hasSize(4)
                .allSatisfy(entry -> assertThat(entry.contents())
                        .as("nothing seeded is made out of anything else")
                        .isEmpty());
        OfferView familyPack = offers.theOfferAsItStands("FAMILY_CINEMA_PACK");
        assertThat(familyPack.contents()).isEmpty();
        assertThat(familyPack.costInPoints()).isEqualTo(180);
    }

    /**
     * A member somebody else is holding is not available to a bundle either, so a bundle whose
     * last seat is held reads as sold out and is refused.
     *
     * <p><strong>This is the hole the merge left, written down as a test.</strong> Holds and
     * bundles were built in parallel from the same commit and neither could see the other. One
     * taught the catalogue that a claim can draw down something it does not name; the other that
     * stock can be spoken for without being claimed. Between them they left a member's
     * arithmetic counting claims and bundle draws but not holds — so the very last cinema seat
     * could be set aside for one customer and handed over inside a hamper to another in the same
     * afternoon, with the seat's own card and the hamper's card each insisting they were right.
     *
     * <p>Two customers, because that is the whole of it: the hold has to belong to somebody
     * other than the person reading the bundle, or the reading would be answering a different
     * question. The sentence is asserted as well as the lock, because it is the part that sends
     * the customer to the right place — the hamper is not coming back until the seat does.
     */
    @Test
    void a_bundle_whose_member_is_held_by_somebody_else_reads_as_sold_out_and_is_refused() {
        String seat = offers.aCodeNobodyHasUsed();
        String popcorn = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(seat, "The very last seat", 10, 1);
        offers.anItemOnSale(popcorn, "Popcorn without end", 4, null);
        Map<String, Integer> members = new LinkedHashMap<>();
        members.put(seat, 1);
        members.put(popcorn, 1);
        offers.aBundleOnSale(bundle, "A seat and a bag", 9, null, members);
        earn(20);
        long before = pointsBalance();

        holdAsideFor(SeededAccounts.BRAM, seat);

        RewardForACustomerView card = readingOf(bundle);
        ResponseEntity<JsonNode> refused = tryToClaim(bundle);

        assertThat(readingOf(seat).whatIsLeft())
                .as("one existed and somebody else is holding it")
                .isZero();
        assertThat(card.whatIsLeft())
                .as("so no hamper can be made up, although nobody has claimed anything")
                .isZero();
        assertThat(card.claimable()).isFalse();
        assertThat(card.lockedBecause()).isEqualTo("NOTHING_LEFT");
        assertThat(card.whyItIsLocked())
                .as("and the sentence names the part that is spoken for")
                .contains("The very last seat");
        assertThat(refused.getStatusCode())
                .as("a bundle whose member is held cannot be handed over, and the refusal is "
                        + "the same conflict a sold-out one gets")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).isEqualTo(card.whyItIsLocked());
        assertThat(pointsBalance())
                .as("and nothing was spent on it")
                .isEqualTo(before);
    }

    /**
     * Cancelling a bundle's voucher returns every member's stock as well as the bundle's own.
     *
     * <p><strong>The criterion this ticket deferred, and it fell out of the merge with no
     * production code at all.</strong> A member's consumption is its containing bundle's
     * uncancelled claim count multiplied by the quantity on the line; the cancellation slice
     * made that count exclude cancelled claims; so a cancelled hamper stops being counted and
     * every one of its members stops being charged for it in the same breath. There is nothing
     * to point at, which is exactly why there is a test.
     */
    @Test
    void cancelling_a_bundles_voucher_returns_every_members_stock_as_well_as_its_own() {
        String seat = offers.aCodeNobodyHasUsed();
        String popcorn = offers.aCodeNobodyHasUsed();
        String bundle = offers.aCodeNobodyHasUsed();
        offers.anItemOnSale(seat, "One seat in the house", 10, 2);
        offers.anItemOnSale(popcorn, "One bag on the shelf", 4, 1);
        offers.aBundleOnSale(bundle, "The last night in", 9, 1, Map.of(seat, 2, popcorn, 1));
        // A customer of this test's own rather than the one the rest of this class spends from,
        // for the reason the cancellation package opens one for every test it has: a refund is
        // a fresh batch of points that no deposit explains, and two tests elsewhere assert that
        // what a customer earned less what they spent is exactly what they hold. Those are
        // rails about the seeded customers, and a cancellation is the one event in this
        // application that makes them false.
        String theirs = aCustomerOfItsOwn();
        earnFor(theirs, 20);

        ClaimedRewardView claimed = claimAs(theirs, bundle);

        assertThat(readingOf(seat).whatIsLeft()).isZero();
        assertThat(readingOf(popcorn).whatIsLeft()).isZero();
        assertThat(readingOf(bundle).whatIsLeft()).isZero();

        VoucherAtTheCounterView cancelled =
                cancelTheVoucher(claimed.voucherCode(), "Handed over the wrong hamper.");

        assertThat(cancelled.state()).isEqualTo("CANCELLED");
        assertThat(readingOf(seat).whatIsLeft())
                .as("the two seats the hamper took are back, because the claim that took them "
                        + "is no longer counted")
                .isEqualTo(2);
        assertThat(readingOf(popcorn).whatIsLeft())
                .as("and so is the bag")
                .isEqualTo(1);
        assertThat(readingOf(bundle).whatIsLeft())
                .as("and the hamper itself, which is the half of this that was never in doubt")
                .isEqualTo(1);
        assertThat(readingOf(bundle).claimable())
                .as("so it can be claimed again")
                .isTrue();
    }

    /**
     * A customer nobody else's assertions are about, opened for the one test here that cancels
     * something.
     *
     * <p>Copied from the cancellation package rather than shared with it, in the same spirit as
     * the fourth copy of the catalogue helper beside this class: those tests are a rail this
     * ticket may not edit, and lifting a helper out of one would mean changing a rail to save
     * writing twelve lines.
     */
    private String aCustomerOfItsOwn() {
        long distinct = OPENED.incrementAndGet();
        String name = "cancelled bundle " + distinct;
        ResponseEntity<JsonNode> opened = http.postForEntity("/api/customers",
                Map.of("name", name, "contactDetails",
                        "cancelled.bundle." + distinct + "@example.be"),
                JsonNode.class);
        assertThat(opened.getStatusCode())
                .describedAs("opening a customer of this test's own: " + opened.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return name;
    }

    /** Earns for somebody other than the customer the rest of this class spends from. */
    private void earnFor(String customerName, long points) {
        ResponseEntity<DepositView> deposit = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", points + ".00", "fromCurrentAccountId",
                        seeded.currentAccountOf(customerName)),
                DepositView.class, seeded.savingsAccountOf(customerName));
        assertThat(deposit.getStatusCode())
                .describedAs("a deposit " + customerName + " needs in order to have points")
                .isEqualTo(HttpStatus.CREATED);
    }

    /** And claims as them, for the same one test. */
    private ClaimedRewardView claimAs(String customerName, String code) {
        ResponseEntity<ClaimedRewardView> claimed = http.postForEntity(
                "/api/customers/{id}/redemptions", Map.of("reward", code),
                ClaimedRewardView.class, seeded.customerIdOf(customerName));
        assertThat(claimed.getStatusCode())
                .describedAs("claiming \"" + code + "\" as " + customerName + ": "
                        + claimed.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return claimed.getBody();
    }

    /** Puts something aside for a customer who is not the one this class claims with. */
    private void holdAsideFor(String customerName, String code) {
        ResponseEntity<JsonNode> taken = http.postForEntity("/api/customers/{id}/holds",
                Map.of("reward", code), JsonNode.class, seeded.customerIdOf(customerName));
        assertThat(taken.getStatusCode())
                .describedAs("putting \"" + code + "\" aside for " + customerName + ": "
                        + taken.getBody())
                .isEqualTo(HttpStatus.CREATED);
    }

    /** Revokes a voucher the way whoever runs the scheme would, and insists it took. */
    private VoucherAtTheCounterView cancelTheVoucher(String voucherCode, String why) {
        ResponseEntity<VoucherAtTheCounterView> cancelled = http.postForEntity(
                "/api/admin/vouchers/{code}/cancel", Map.of("reason", why),
                VoucherAtTheCounterView.class, voucherCode);
        assertThat(cancelled.getStatusCode())
                .describedAs("cancelling \"" + voucherCode + "\": " + cancelled.getBody())
                .isEqualTo(HttpStatus.OK);
        return cancelled.getBody();
    }

    /** The four the application has always offered, none of which is made out of the others. */
    private static List<String> theFourThatHaveAlwaysBeenThere() {
        return List.of("CHARITY_DONATION", "SNACK_VOUCHER", "CINEMA_TICKET",
                "FAMILY_CINEMA_PACK");
    }

    private RewardForACustomerView readingOf(String code) {
        return offers.asReadBy(seeded.customerIdOf(ANKE), code);
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

    /** The same claim read as unshaped JSON, for the ones this ticket refuses. */
    private ResponseEntity<JsonNode> tryToClaim(String code) {
        return http.postForEntity("/api/customers/{id}/redemptions", Map.of("reward", code),
                JsonNode.class, seeded.customerIdOf(ANKE));
    }

    /** What this customer has claimed, which is how a test counts what actually went out. */
    private List<ClaimedRewardView> claimsOf() {
        ResponseEntity<ClaimedRewardView[]> read = http.getForEntity(
                "/api/customers/{id}/redemptions", ClaimedRewardView[].class,
                seeded.customerIdOf(ANKE));
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** A voucher as somebody at a till reads it. */
    private VoucherAtTheCounterView voucherAtACounter(String voucherCode) {
        ResponseEntity<VoucherAtTheCounterView> read = http.getForEntity(
                "/api/staff/vouchers/{code}", VoucherAtTheCounterView.class, voucherCode);
        assertThat(read.getStatusCode()).describedAs("looking up \"" + voucherCode + "\"")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** And the one press that hands it over. */
    private VoucherAtTheCounterView handOverTheVoucher(String voucherCode) {
        ResponseEntity<VoucherAtTheCounterView> used = http.postForEntity(
                "/api/staff/vouchers/{code}/use", Map.of("counter", "Leuven, till 1"),
                VoucherAtTheCounterView.class, voucherCode);
        assertThat(used.getStatusCode()).describedAs("handing over \"" + voucherCode + "\"")
                .isEqualTo(HttpStatus.OK);
        return used.getBody();
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

    /** What the customer has to spend, for the tests that say a refusal took nothing. */
    private long pointsBalance() {
        return seeded.pointsBalanceOf(ANKE);
    }

    /** The plain catalogue exactly as it goes over the wire, for saying what is not in it. */
    private String theCatalogueAsItIsSent() {
        ResponseEntity<String> read = http.getForEntity("/api/rewards", String.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }
}
