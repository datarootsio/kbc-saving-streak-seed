package io.dataroots.savingstreak.apricethatislowerthisweek;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.RewardView;
import io.dataroots.savingstreak.support.SeededAccounts;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A second, lower price with a window of its own: what an administrator may set, what a customer
 * is shown, and what a claim actually takes.
 *
 * <p><strong>Both figures, every time.</strong> A promotion that only charged less would be half
 * the feature: the thing a customer is being sold is the saving, and a saving they cannot see is
 * a price cut nobody notices. So every reading below asserts the effective price <em>and</em> the
 * struck-through one together — and the pair is asserted in the other direction too, because an
 * offer at its ordinary price must strike nothing through rather than strike its own price
 * through for a saving of nothing.
 *
 * <p><strong>The page performs no arithmetic and neither does this test.</strong> Nothing here
 * subtracts one price from the other and nothing asserts a percentage. What a card can draw is
 * what the backend sent it, which is why "the ordinary price is null" is an assertion worth
 * making: it is the whole of how a page knows to draw one figure rather than two.
 *
 * <p><strong>Days are taken from the application's own clock, never from this machine's.</strong>
 * The development clock is one a trainer winds, and a test that computed "tomorrow" from
 * {@code LocalDate.now()} would be asserting against a different day from the one the application
 * prices by the moment anybody has wound it. Winding it is the other test in this package, which
 * has an application of its own to wind.
 *
 * <p><strong>Every test asserts on what its own requests changed, and puts the catalogue
 * back.</strong> The run shares one database and the catalogue is the most shared thing in it.
 * Nothing here counts the offers or leans on a balance it did not earn, and
 * {@link OffersOnPromotionThisTestWrites#putTheCatalogueBack} withdraws every offer it wrote so
 * that the test pinning the customer's catalogue to four entries at four prices goes on passing.
 *
 * <p>Anke is the customer, because she is the one the claiming tests already spend from; what she
 * spends here, she earns here.
 */
class APriceThatIsLowerThisWeekApiTest extends ApiIntegrationTest {

    /** Cheap, and earned inside the test, because what these tests spend is not the point of them. */
    private static final int THE_ORDINARY_PRICE = 9;

    private static final int THE_SALE_PRICE = 4;

    /**
     * Far more points than anybody in this run has, for the tests about being told the price.
     *
     * <p>Out of reach on purpose and by a wide margin: a refusal is what this asserts, and a
     * figure that the seeded balances could ever creep up to would make the test pass or fail on
     * what the rest of the run happened to deposit.
     */
    private static final long MORE_THAN_ANYBODY_HAS = 9_000_000L;

    private SeededAccounts seeded;

    private OffersOnPromotionThisTestWrites offers;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
        offers = new OffersOnPromotionThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * The card a customer sees while the sale is on: the lower figure to pay and the ordinary one
     * to strike through.
     *
     * <p>The two asserted together, because either alone would pass against a bug. A reading that
     * only carried the lower price would be a repricing with no saving on the screen, and one
     * that only carried the ordinary price beside an unchanged figure would be a card advertising
     * a discount it does not give.
     */
    @Test
    void an_offer_on_promotion_is_read_at_the_lower_price_with_the_ordinary_one_beside_it() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSaleAt(code, "Half a hamper", THE_ORDINARY_PRICE, THE_SALE_PRICE,
                today.minusDays(1), today.plusDays(1));

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.costInPoints())
                .as("what it costs is what they would be charged, which today is the sale price")
                .isEqualTo(THE_SALE_PRICE);
        assertThat(reading.ordinaryCostInPoints())
                .as("and the figure to strike through, so that the saving is on the card without "
                        + "the page working it out")
                .isEqualTo(THE_ORDINARY_PRICE);
        assertThat(reading.discountClosesOn())
                .as("the day the price goes back up, which is a different promise from the day "
                        + "the offer goes away")
                .isEqualTo(today.plusDays(1));
        assertThat(reading.claimable())
                .as("a sale is not a lock: a discount changes what an offer costs and never "
                        + "whether it can be had")
                .isTrue();
        assertThat(reading.lockedBecause()).isNull();
    }

    /**
     * An offer nobody has put on sale is one price and nothing struck through.
     *
     * <p>The null is the assertion. It is how a page knows to draw one figure, and a reading that
     * repeated the price into both fields would have every card in the catalogue advertising a
     * saving of nothing.
     */
    @Test
    void an_offer_at_its_ordinary_price_has_nothing_struck_through() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Just a hamper", THE_ORDINARY_PRICE);

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.costInPoints()).isEqualTo(THE_ORDINARY_PRICE);
        assertThat(reading.ordinaryCostInPoints()).isNull();
        assertThat(reading.discountClosesOn()).isNull();
    }

    /**
     * The four the application has always offered read exactly as they always have.
     *
     * <p>The safety argument of the ticket, asserted rather than asserted-to. They carry no
     * promotion, so they are at their own prices with nothing struck through — and a page drawing
     * them today draws exactly what it drew before this ticket existed. Not by counting them: the
     * run shares a database and another class's offer may be on sale while this one looks.
     */
    @Test
    void the_four_offers_that_have_always_been_there_are_at_their_own_prices_with_no_sale_on() {
        assertThat(offers.theCatalogueAsReadBy(seeded.customerIdOf(ANKE)))
                .filteredOn(entry -> theFourThatHaveAlwaysBeenThere().contains(entry.code()))
                .hasSize(4)
                .allSatisfy(entry -> {
                    assertThat(entry.ordinaryCostInPoints()).isNull();
                    assertThat(entry.discountClosesOn()).isNull();
                    assertThat(entry.claimable()).isTrue();
                });
    }

    /**
     * A sale that starts next week does nothing this week.
     *
     * <p>The other half of "the discount stops on the day it stops", said from the near end: a
     * promotion written in advance is the ordinary way to set one up, and an application that
     * applied it the moment it was saved would charge the sale price for however long the
     * administrator was early.
     */
    @Test
    void a_promotion_that_has_not_started_leaves_the_offer_at_its_ordinary_price() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSaleAt(code, "Next week's sale", THE_ORDINARY_PRICE, THE_SALE_PRICE,
                today.plusDays(1), today.plusDays(8));

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.costInPoints()).isEqualTo(THE_ORDINARY_PRICE);
        assertThat(reading.ordinaryCostInPoints())
                .as("nothing is struck through before a sale starts, because there is no saving "
                        + "to advertise yet")
                .isNull();
        assertThat(reading.discountClosesOn()).isNull();
    }

    /** And a sale that ended yesterday is over: the price is back up and the card says one figure. */
    @Test
    void a_promotion_that_has_ended_leaves_the_offer_at_its_ordinary_price() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSaleAt(code, "Last week's sale", THE_ORDINARY_PRICE, THE_SALE_PRICE,
                today.minusDays(8), today.minusDays(1));

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.costInPoints()).isEqualTo(THE_ORDINARY_PRICE);
        assertThat(reading.ordinaryCostInPoints()).isNull();
        assertThat(reading.discountClosesOn()).isNull();
    }

    /**
     * Both ends of the sale's window are days the sale applies on.
     *
     * <p>Two fenceposts, not one, and in one test because they are one rule. A window read with
     * the wrong comparison at either end is invisible until somebody's sale is a day short, and a
     * test standing only on the far side of each boundary would pass against both mistakes. The
     * closing day is inclusive for the reason the offer's own is, and it matters more here: a
     * customer who read "half price until the eighth" and came back on the eighth afternoon would
     * otherwise be charged full price by an application that told them otherwise that morning.
     */
    @Test
    void the_first_and_the_last_day_of_a_sale_are_days_it_applies_on() {
        LocalDate today = theDayTheApplicationThinksItIs();
        long customerId = seeded.customerIdOf(ANKE);
        String startsToday = offers.aCodeNobodyHasUsed();
        offers.onSaleAt(startsToday, "Starts today", THE_ORDINARY_PRICE, THE_SALE_PRICE, today,
                today.plusDays(6));
        String endsToday = offers.aCodeNobodyHasUsed();
        offers.onSaleAt(endsToday, "Ends today", THE_ORDINARY_PRICE, THE_SALE_PRICE,
                today.minusDays(6), today);

        assertThat(offers.asReadBy(customerId, startsToday).costInPoints())
                .as("a sale starting on the third is on all through the third")
                .isEqualTo(THE_SALE_PRICE);
        assertThat(offers.asReadBy(customerId, endsToday).costInPoints())
                .as("and a sale ending on the eighth is on all through the eighth")
                .isEqualTo(THE_SALE_PRICE);
    }

    /**
     * A one-day sale is a sale, which is what the comparison being strictly before is for.
     *
     * <p>"Half price today" is a thing a scheme says, and a rule that refused a window whose ends
     * are equal would refuse the promotion promotions are most obviously for.
     */
    @Test
    void a_sale_that_starts_and_ends_on_the_same_day_is_a_day_it_is_cheaper_on() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSaleAt(code, "Today only", THE_ORDINARY_PRICE, THE_SALE_PRICE, today, today);

        assertThat(offers.asReadBy(seeded.customerIdOf(ANKE), code).costInPoints())
                .isEqualTo(THE_SALE_PRICE);
    }

    /**
     * The points actually taken are the sale price, and the claim keeps that figure.
     *
     * <p>The balance and the claim asserted together, because the two are different promises. The
     * balance is what the customer was charged this minute; {@code pointsSpent} on the claim is
     * what their history will say they paid, and it is the snapshot that makes a promotion ending
     * never rewrite what anybody did. A delta rather than an absolute, because one database
     * serves the whole run.
     */
    @Test
    void claiming_while_the_sale_is_on_takes_the_sale_price_and_the_claim_keeps_it() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSaleAt(code, "Half a hamper", THE_ORDINARY_PRICE, THE_SALE_PRICE, today, today);
        earn(THE_ORDINARY_PRICE);
        long before = pointsBalance();

        ClaimedRewardView claimed = claim(code);

        assertThat(claimed.pointsSpent())
                .as("what the claim says it cost is the sale price, because that is what was paid")
                .isEqualTo(THE_SALE_PRICE);
        assertThat(pointsBalance())
                .as("and the ledger agrees: the ordinary price was never taken")
                .isEqualTo(before - THE_SALE_PRICE);
    }

    /**
     * "You are N points short" quotes the sale price while the sale is on.
     *
     * <p>The sentence is the whole of the assertion. A refusal that named the ordinary price
     * would send somebody away to save for a figure they do not need, on a card that had just
     * told them a smaller one — which is the two-prices-in-one-application bug this ticket exists
     * to make impossible.
     */
    @Test
    void being_short_is_said_against_the_sale_price_and_not_the_ordinary_one() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSaleAt(code, "Out of reach", MORE_THAN_ANYBODY_HAS, MORE_THAN_ANYBODY_HAS - 1,
                today, today);
        long before = pointsBalance();

        ResponseEntity<JsonNode> refused = tryToClaim(code);

        assertThat(refused.getStatusCode())
                .as("being short of points is answered the way it has always been answered; what "
                        + "this ticket changes is the figure in the sentence and not the status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .as("the figure quoted back is the one they would actually be charged")
                .contains(String.valueOf(MORE_THAN_ANYBODY_HAS - 1))
                .doesNotContain(String.valueOf(MORE_THAN_ANYBODY_HAS));
        assertThat(pointsBalance())
                .as("a refusal takes nothing, at either price")
                .isEqualTo(before);
    }

    /**
     * The catalogue with nobody in it quotes the ordinary price and gains no field for the sale.
     *
     * <p>Two assertions that both have to hold, and the first of them is a decision this wave
     * settled against the first draft of this slice. {@code /api/rewards} is the catalogue with
     * nobody in it: the window slice established that it is customer-free <em>and</em>
     * clock-free — it deliberately does not filter by an offer's window either — so an address
     * that read the clock for the price while ignoring it for availability would be
     * inconsistent with itself. It therefore quotes the ordinary price, and every "today" a
     * promotion implies belongs on {@code /api/customers/{id}/rewards}, which has a day, a
     * person, and room to say both figures.
     *
     * <p>And its shape must not move: the four fields it has always had are the shape a test
     * nobody may edit pins it to, so nothing about the promotion may leak into it.
     */
    @Test
    void the_catalogue_with_nobody_in_it_quotes_the_ordinary_price_and_gains_no_field() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSaleAt(code, "Half a hamper", THE_ORDINARY_PRICE, THE_SALE_PRICE, today, today);

        assertThat(offers.theCatalogueWithNobodyInIt())
                .filteredOn(entry -> code.equals(entry.code()))
                .singleElement()
                .extracting(RewardView::costInPoints)
                .isEqualTo((long) THE_ORDINARY_PRICE);
        assertThat(theCatalogueAsItIsSent())
                .as("no second price and no sale window leaks into the catalogue that has always "
                        + "been four fields wide")
                .doesNotContain("discountedCostInPoints")
                .doesNotContain("ordinaryCostInPoints")
                .doesNotContain("discountOpensOn")
                .doesNotContain("discountClosesOn");
    }

    /**
     * A sale is set from the administration screen, read back there, moved, and called off.
     *
     * <p>The calling off is the half worth insisting on, and it is the one place this ticket
     * makes a choice somebody could have made differently. A promotion is one fact in three
     * parts, so emptying both of its days takes the price with them: there is no such thing as
     * keeping a price that applies on no day, and refusing an administrator who emptied both
     * boxes would leave them with a sale they could move for ever but never end.
     */
    @Test
    void a_sale_is_set_read_back_moved_and_then_called_off() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();

        OfferView written = offers.writes(OffersOnPromotionThisTestWrites.anOffer(code,
                "A sale", THE_ORDINARY_PRICE, (long) THE_SALE_PRICE, today, today.plusDays(6)));
        offers.publishes(code);
        assertThat(written.discountedCostInPoints()).isEqualTo(THE_SALE_PRICE);
        assertThat(written.discountOpensOn()).isEqualTo(today);
        assertThat(written.discountClosesOn()).isEqualTo(today.plusDays(6));

        OfferView extended = offers.changes(code,
                Map.of("discountClosesOn", today.plusDays(20).toString()));
        assertThat(extended.discountOpensOn())
                .as("a change names one part of the sale and leaves the rest exactly as it was")
                .isEqualTo(today);
        assertThat(extended.discountedCostInPoints()).isEqualTo(THE_SALE_PRICE);
        assertThat(extended.discountClosesOn()).isEqualTo(today.plusDays(20));

        OfferView cheaper = offers.changes(code, Map.of("discountedCostInPoints", 2));
        assertThat(cheaper.discountedCostInPoints()).isEqualTo(2);
        assertThat(cheaper.discountClosesOn()).isEqualTo(today.plusDays(20));

        Map<String, Object> calledOff = new HashMap<>();
        calledOff.put("discountOpensOn", "");
        calledOff.put("discountClosesOn", "");
        OfferView over = offers.changes(code, calledOff);
        assertThat(over.discountedCostInPoints())
                .as("emptying both days is how a sale is called off, and the price goes with them")
                .isNull();
        assertThat(over.discountOpensOn()).isNull();
        assertThat(over.discountClosesOn()).isNull();
        assertThat(over.costInPoints())
                .as("and the ordinary price is exactly where it was left")
                .isEqualTo(THE_ORDINARY_PRICE);
        assertThat(offers.asReadBy(seeded.customerIdOf(ANKE), code).ordinaryCostInPoints())
                .as("with nothing struck through on the card afterwards")
                .isNull();
    }

    /**
     * A change that empties both days and names a price at the same time is refused.
     *
     * <p>The one shape of that form nobody can read: it starts a sale and ends one in the same
     * sentence. Guessing which half was meant would be the application deciding, and whichever it
     * decided would be wrong half the time and silent every time.
     */
    @Test
    void a_change_that_both_starts_and_ends_a_sale_is_refused() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSaleAt(code, "A sale", THE_ORDINARY_PRICE, THE_SALE_PRICE, today, today);
        Map<String, Object> contradictory = new HashMap<>();
        contradictory.put("discountedCostInPoints", 3);
        contradictory.put("discountOpensOn", "");
        contradictory.put("discountClosesOn", "");

        ResponseEntity<JsonNode> refused = offers.triesToChange(code, contradictory);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("a price and two days");
        assertThat(offers.asReadBy(seeded.customerIdOf(ANKE), code).costInPoints())
                .as("and nothing moved: the sale is exactly as it was")
                .isEqualTo(THE_SALE_PRICE);
    }

    /**
     * A discounted price at or above the ordinary one is refused, at both of those boundaries.
     *
     * <p>Not a badly judged promotion but a card advertising a saving that does not exist. Equal
     * is refused alongside above, because a saving of nothing is still a claim of a saving, and a
     * customer who worked out that the struck-through figure matched the one beside it would have
     * been told something untrue by an application that could have known.
     */
    @Test
    void a_discounted_price_at_or_above_the_ordinary_one_is_refused() {
        LocalDate today = theDayTheApplicationThinksItIs();

        ResponseEntity<JsonNode> theSame = offers.triesToWrite(
                OffersOnPromotionThisTestWrites.anOffer(offers.aCodeNobodyHasUsed(), "No saving",
                        THE_ORDINARY_PRICE, (long) THE_ORDINARY_PRICE, today, today));
        ResponseEntity<JsonNode> dearer = offers.triesToWrite(
                OffersOnPromotionThisTestWrites.anOffer(offers.aCodeNobodyHasUsed(), "Dearer",
                        THE_ORDINARY_PRICE, THE_ORDINARY_PRICE + 1L, today, today));

        assertThat(theSame.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(theSame))
                .contains(String.valueOf(THE_ORDINARY_PRICE))
                .contains("below");
        assertThat(dearer.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(dearer)).contains(String.valueOf(THE_ORDINARY_PRICE + 1));
    }

    /**
     * A discounted price below a single point is refused, exactly as the ordinary price is.
     *
     * <p>The same argument with the same words: a reward at nought points is not a reward, it is
     * a button, and it would make the figure this whole application asks people to work towards
     * mean nothing for one week in the year.
     */
    @Test
    void a_discounted_price_below_a_point_is_refused() {
        LocalDate today = theDayTheApplicationThinksItIs();

        ResponseEntity<JsonNode> refused = offers.triesToWrite(
                OffersOnPromotionThisTestWrites.anOffer(offers.aCodeNobodyHasUsed(), "Free",
                        THE_ORDINARY_PRICE, 0L, today, today));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("at least one point");
    }

    /**
     * Half a promotion is refused, in a sentence naming the half that is missing — whichever half
     * it is.
     *
     * <p>A price with no window would strike the ordinary figure through for ever, which makes
     * "usually nine" a sentence about a price nobody was ever charged. A window with no price is
     * two dates that do nothing, and nobody reading the row a month later could tell it from a
     * sale that silently failed to apply. Both are rows that can only be a mistake, and a row
     * that can only be a mistake is answered at the form.
     */
    @Test
    void a_promotion_missing_its_price_or_missing_a_day_is_refused() {
        LocalDate today = theDayTheApplicationThinksItIs();

        ResponseEntity<JsonNode> noWindow = offers.triesToWrite(
                OffersOnPromotionThisTestWrites.anOffer(offers.aCodeNobodyHasUsed(),
                        "A price and no days", THE_ORDINARY_PRICE, (long) THE_SALE_PRICE, null,
                        null));
        ResponseEntity<JsonNode> noPrice = offers.triesToWrite(
                OffersOnPromotionThisTestWrites.anOffer(offers.aCodeNobodyHasUsed(),
                        "Days and no price", THE_ORDINARY_PRICE, null, today, today.plusDays(6)));
        ResponseEntity<JsonNode> oneDayOnly = offers.triesToWrite(
                OffersOnPromotionThisTestWrites.anOffer(offers.aCodeNobodyHasUsed(),
                        "A sale that never ends", THE_ORDINARY_PRICE, (long) THE_SALE_PRICE, today,
                        null));

        assertThat(noWindow.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(noWindow))
                .contains("a price and two days")
                .contains("a day it starts on")
                .contains("a day it ends on");
        assertThat(noPrice.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(noPrice)).contains("a discounted price");
        assertThat(oneDayOnly.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(oneDayOnly)).contains("a day it ends on");
    }

    /** A sale whose window runs backwards is refused where it is written, in a sentence. */
    @Test
    void a_sale_whose_window_runs_backwards_is_refused() {
        LocalDate today = theDayTheApplicationThinksItIs();

        ResponseEntity<JsonNode> refused = offers.triesToWrite(
                OffersOnPromotionThisTestWrites.anOffer(offers.aCodeNobodyHasUsed(), "Backwards",
                        THE_ORDINARY_PRICE, (long) THE_SALE_PRICE, today.plusDays(10),
                        today.plusDays(2)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains(today.plusDays(2).toString())
                .contains(today.plusDays(10).toString());
    }

    /**
     * A sale's window need not sit inside the offer's own, and this test records the decision.
     *
     * <p>Chosen deliberately and argued in the module: an offer with no window at all can have a
     * sale, so a containment rule would have to exempt the commonest case or forbid it. A sale
     * positioned before an offer opens is not a mistake either — it is a price that applies on
     * the days the offer is claimable and does nothing on the days it is not, which is exactly
     * what a trimmed one would do. And an administrator moving a season and its sale makes two
     * edits in some order; a containment rule would refuse whichever came first and make the
     * order of two correct changes matter.
     *
     * <p>What the card says while the offer is shut is the second half of it: locked for the
     * window, and priced at today's price, because a customer deciding whether to come back for
     * something is deciding against a figure.
     */
    @Test
    void a_sale_may_run_outside_the_offers_own_window() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        Map<String, Object> offer = OffersOnPromotionThisTestWrites.anOffer(code,
                "A sale before the season", THE_ORDINARY_PRICE, (long) THE_SALE_PRICE, today,
                today.plusDays(2));
        offer.put("opensOn", today.plusDays(5).toString());
        offer.put("closesOn", today.plusDays(10).toString());
        offers.writes(offer);
        offers.publishes(code);

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.lockedBecause())
                .as("the offer has not opened yet, which is the window's business and not the "
                        + "sale's")
                .isEqualTo("NOT_OPEN_YET");
        assertThat(reading.costInPoints())
                .as("and it is priced at today's price even so, because that is the figure "
                        + "somebody is deciding against")
                .isEqualTo(THE_SALE_PRICE);
        assertThat(reading.ordinaryCostInPoints()).isEqualTo(THE_ORDINARY_PRICE);
    }

    /**
     * What day the application thinks it is, in the zone it counts its calendars in.
     *
     * <p>Read off the development clock rather than off this machine, because those are two
     * different days the moment anybody winds one of them — and a test that positioned a sale
     * against the wrong one would assert a price the application had no reason to charge.
     */
    private LocalDate theDayTheApplicationThinksItIs() {
        ClockView clock = http.getForObject("/api/dev/clock", ClockView.class);
        return clock.now().atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }

    /** The four the application has always offered, which no sale is ever put on. */
    private static List<String> theFourThatHaveAlwaysBeenThere() {
        return List.of("CHARITY_DONATION", "SNACK_VOUCHER", "CINEMA_TICKET",
                "FAMILY_CINEMA_PACK");
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

    /** What the customer has to spend, for the tests that say what a claim took. */
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
