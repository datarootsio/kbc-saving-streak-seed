package io.dataroots.savingstreak.thelastoneheldforyou;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.HoldView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.SeededAccounts;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The last one, held for whoever asked first: what a hold takes, what it keeps from everybody
 * else, and the three ways it ends.
 *
 * <p><strong>A hold takes stock and no points, and that is the property nearly every test here
 * checks in passing.</strong> The balance before and the balance after is one line in most of
 * them, and it is not padding: the entire design rests on points staying in the ledger running
 * their own twelve-month clocks, and a hold that quietly took some would break the one thing
 * this feature promises the points module.
 *
 * <p><strong>Nothing here winds the clock.</strong> Seventy-two hours cannot be reached on the
 * shared database — the development clock is the whole run's and only moves forward — so
 * everything about lapsing lives in {@code AHoldThatRanOutApiTest} beside this, which starts an
 * application of its own for exactly that reason. What is left here is everything that happens
 * inside the three days, which is most of what a hold is.
 *
 * <p><strong>Every test writes its own offer and withdraws it afterwards</strong>, because the
 * catalogue a customer reads is asserted elsewhere to be exactly the four seeded entries at
 * their four prices, and because a hold is about a scarce thing and none of the four is scarce.
 * Every test earns the points it is about to spend and asserts on the delta it caused; one
 * database serves the run.
 *
 * <p>Anke holds and Bram is everybody else, which is the pairing the rest of the suite already
 * uses for "somebody else got there first".
 */
class TheLastOneHeldForYouApiTest extends ApiIntegrationTest {

    /** The spec's seventy-two hours, as the thing a customer is promised. */
    private static final Duration THREE_DAYS = Duration.ofHours(72);

    private SeededAccounts seeded;

    private OffersToHoldThisTestWrites offers;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
        offers = new OffersToHoldThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * A customer can hold something they could otherwise have claimed, and the hold says when it
     * stops being theirs.
     *
     * <p>The moment is the load-bearing part. A deadline the customer never saw is the one thing
     * the spec says a hold must never be, so it is in the answer to the very request that
     * created it rather than somewhere they would have to go and look.
     *
     * <p>Bracketed against the real clock either side rather than asserted exactly, because the
     * moment is seventy-two hours after whenever the application read its clock and no test can
     * know that instant. What is asserted is that it is three days away and not three hours or
     * thirty.
     */
    @Test
    void a_customer_can_hold_an_offer_and_the_hold_names_the_moment_it_lapses() {
        String code = offers.aCodeNobodyHasUsed();
        offers.theLastOneOnSale(code, "The last hamper", 5);
        earn(10);

        Instant before = theMomentTheApplicationThinksItIs();
        HoldView held = offers.heldBy(anke(), code);
        Instant after = theMomentTheApplicationThinksItIs();

        assertThat(held.offerCode()).isEqualTo(code);
        assertThat(held.title()).isEqualTo("The last hamper");
        assertThat(held.state()).isEqualTo("HELD");
        assertThat(held.endedAt())
                .as("a live hold has not ended, whatever it is due to do")
                .isNull();
        assertThat(held.lapsesAt())
                .as("seventy-two hours after the moment the application took it")
                .isBetween(before.plus(THREE_DAYS), after.plus(THREE_DAYS));
        assertThat(Duration.between(held.takenAt(), held.lapsesAt()))
                .as("and the two moments on the hold agree about how long it is")
                .isEqualTo(THREE_DAYS);
    }

    /**
     * <strong>A hold takes stock and no points.</strong> The balance is untouched and so is
     * everything the ledger says about when the points go, which is the whole reason a hold can
     * be given up for nothing and the whole reason one can fail to convert.
     *
     * <p>The expiry figures are asserted beside the balance deliberately. A hold implemented by
     * moving points into some held state would very likely leave the balance looking right and
     * the expiry arithmetic wrong, and "my points vanish next Tuesday" is the fact a customer
     * would notice.
     */
    @Test
    void a_hold_takes_no_points_and_leaves_every_batch_where_it_was() {
        String code = offers.aCodeNobodyHasUsed();
        offers.theLastOneOnSale(code, "Costs nothing to hold", 5);
        earn(10);
        long balanceBefore = seeded.pointsBalanceOf(ANKE);
        Long expiringBefore = seeded.pointsExpiringNextOf(ANKE);
        LocalDate expiringOnBefore = seeded.pointsExpiringNextOnOf(ANKE);

        offers.heldBy(anke(), code);

        assertThat(seeded.pointsBalanceOf(ANKE))
                .as("a hold costs nothing until it is claimed")
                .isEqualTo(balanceBefore);
        assertThat(seeded.pointsExpiringNextOf(ANKE))
                .as("and no batch has been touched, so the next lot still goes when it did")
                .isEqualTo(expiringBefore);
        assertThat(seeded.pointsExpiringNextOnOf(ANKE)).isEqualTo(expiringOnBefore);
    }

    /**
     * <strong>Held stock is not available to anybody else, and an offer whose last one is held
     * reads as sold out.</strong>
     *
     * <p>The card and the refusal together, because the whole point of scarcity being a lock is
     * that somebody is told before they commit. Bram's balance is asserted afterwards for the
     * reason every refusal in this suite is: a refusal that had already spent the points would
     * pass an assertion about the status and fail the customer.
     */
    @Test
    void an_offer_whose_last_one_is_held_reads_as_sold_out_to_everybody_else() {
        String code = offers.aCodeNobodyHasUsed();
        offers.theLastOneOnSale(code, "The very last one", 5);
        earn(10);
        earnFor(BRAM, 10);
        long bramsPoints = seeded.pointsBalanceOf(BRAM);

        offers.heldBy(anke(), code);

        RewardForACustomerView asBramReadsIt = offers.asReadBy(bram(), code);
        assertThat(asBramReadsIt.whatIsLeft())
                .as("the one that exists is spoken for, so there are none")
                .isEqualTo(0);
        assertThat(asBramReadsIt.claimable()).isFalse();
        assertThat(asBramReadsIt.lockedBecause()).isEqualTo("NOTHING_LEFT");
        assertThat(asBramReadsIt.yourHoldLapsesAt())
                .as("somebody else's hold is not a countdown on his card")
                .isNull();

        ResponseEntity<JsonNode> refused = tryToClaim(bram(), code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("sold out");
        assertThat(seeded.pointsBalanceOf(BRAM))
                .as("a refusal takes nothing")
                .isEqualTo(bramsPoints);
    }

    /**
     * And nobody else can hold it either, which is the same rule one step earlier: a hold is a
     * claim on the stock, and the stock is gone.
     */
    @Test
    void nobody_else_can_hold_the_one_that_is_already_held() {
        String code = offers.aCodeNobodyHasUsed();
        offers.theLastOneOnSale(code, "Only one of these", 5);
        earn(10);
        earnFor(BRAM, 10);
        offers.heldBy(anke(), code);

        ResponseEntity<JsonNode> refused = offers.triesToHold(bram(), code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("sold out");
    }

    /**
     * The holder's own card is an affordance rather than a lock, which is the decision this test
     * exists to pin.
     *
     * <p>Their hold is one of the ones subtracted from what is left, so a card drawn without
     * this rule would tell the one person the thing is being kept for that it had sold out. What
     * they get instead is a claimable card with the moment on it, which is what the page draws
     * the countdown and the two buttons from.
     */
    @Test
    void the_customer_holding_one_reads_it_as_theirs_rather_than_as_sold_out() {
        String code = offers.aCodeNobodyHasUsed();
        offers.theLastOneOnSale(code, "Yours for three days", 5);
        earn(10);

        HoldView held = offers.heldBy(anke(), code);

        RewardForACustomerView asSheReadsIt = offers.asReadBy(anke(), code);
        assertThat(asSheReadsIt.yourHoldLapsesAt())
                .as("the countdown the page draws, and the same moment the hold came back with")
                .isEqualTo(held.lapsesAt());
        assertThat(asSheReadsIt.claimable())
                .as("a hold is the scheme having said yes")
                .isTrue();
        assertThat(asSheReadsIt.lockedBecause()).isNull();
        assertThat(asSheReadsIt.whyItIsLocked()).isNull();
        assertThat(asSheReadsIt.whatIsLeft())
                .as("and the window really is empty — hers is not in it")
                .isEqualTo(0);
    }

    /**
     * One live hold per offer per customer. Pressing again cannot give somebody another, because
     * the whole of what a hold does for them is already done.
     */
    @Test
    void a_second_hold_on_the_same_offer_is_refused() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Two of these", 5, 2, null);
        earn(20);
        HoldView first = offers.heldBy(anke(), code);

        ResponseEntity<JsonNode> refused = offers.triesToHold(anke(), code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("and the sentence says how long the one they have has left")
                .contains("already holding")
                .contains(first.lapsesAt().toString());
        assertThat(offers.asReadBy(anke(), code).whatIsLeft())
                .as("the refusal reserved nothing: one of the two is still in the window")
                .isEqualTo(1);
    }

    /**
     * <strong>A live hold counts against a purchase limit</strong>, so a customer cannot hold one
     * and claim another past their cap.
     *
     * <p>Two in stock and a cap of one is the arrangement that tells the two readings apart:
     * there is plainly one left, so a refusal here can only be the cap, and a scheme that
     * counted holds only once converted would hand this customer two of something one person may
     * have one of.
     */
    @Test
    void a_live_hold_counts_against_a_purchase_limit() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "One each", 5, 2, 1);
        earn(20);
        offers.heldBy(anke(), code);

        ResponseEntity<JsonNode> refused = tryToClaim(anke(), code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("the cap, said with the hold that reached it")
                .contains("holding another")
                .contains("may have 1 in all");
    }

    /**
     * <strong>Converting a hold spends the points at that moment and issues the voucher.</strong>
     *
     * <p>The cap of one is on the offer deliberately: the hold counts against a cap for anything
     * else, and must not count against its own conversion. An offer refusing to hand over the
     * thing it had set aside would be the most embarrassing bug this slice could have.
     */
    @Test
    void converting_a_hold_spends_the_points_and_issues_the_voucher() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Converted at last", 5, 1, 1);
        earn(10);
        long before = seeded.pointsBalanceOf(ANKE);
        offers.heldBy(anke(), code);

        ResponseEntity<JsonNode> converted = offers.triesToConvert(anke(), code);

        assertThat(converted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(converted.getBody().path("pointsSpent").asLong()).isEqualTo(5);
        assertThat(converted.getBody().path("voucherCode").asText()).startsWith("SS-HLD-");
        assertThat(converted.getBody().path("state").asText()).isEqualTo("ISSUED");
        assertThat(seeded.pointsBalanceOf(ANKE))
                .as("the points go at the moment of conversion and not before")
                .isEqualTo(before - 5);
        assertThat(offers.asReadBy(anke(), code).yourHoldLapsesAt())
                .as("the hold is spent, so there is no countdown left on the card")
                .isNull();
    }

    /**
     * <strong>Converting spends the points at the price in force then</strong>, so a hold taken
     * before a promotion and converted during one pays the sale price.
     *
     * <p>The promotion is opened after the hold is taken, which is the whole shape of the
     * argument: nothing about the hold changed, and the price did. A price frozen onto the hold
     * would charge this customer the old figure for up to three days while the card beside it
     * advertised the new one.
     */
    @Test
    void converting_during_a_promotion_pays_the_promoted_price() {
        String code = offers.aCodeNobodyHasUsed();
        offers.theLastOneOnSale(code, "Cheaper by the time she claimed", 20);
        earn(30);
        offers.heldBy(anke(), code);
        long before = seeded.pointsBalanceOf(ANKE);
        LocalDate today = theDayTheApplicationThinksItIs();
        offers.changes(code, Map.of(
                "discountedCostInPoints", 5,
                "discountOpensOn", today.minusDays(1).toString(),
                "discountClosesOn", today.plusDays(1).toString()));

        ResponseEntity<JsonNode> converted = offers.triesToConvert(anke(), code);

        assertThat(converted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(converted.getBody().path("pointsSpent").asLong())
                .as("the price in force at the moment of conversion, not the one on the day of "
                        + "the hold")
                .isEqualTo(5);
        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(before - 5);
    }

    /**
     * <strong>A hold whose points expired underneath it cannot be converted, and the hold is
     * unaffected.</strong>
     *
     * <p>Points cannot actually be made to expire without winding the clock a year, which this
     * class cannot do — so what is asserted is the same outcome arrived at the only other way a
     * customer can be short: by not having enough. It is the same code path and the same refusal,
     * and the property that matters is the second half of the sentence rather than why they were
     * short. A conversion that consumed the hold on its way to refusing would leave somebody with
     * neither the thing nor the reservation.
     */
    @Test
    void converting_without_the_points_is_refused_and_the_hold_is_untouched() {
        String code = offers.aCodeNobodyHasUsed();
        offers.theLastOneOnSale(code, "More than she has", 5);
        earn(10);
        HoldView held = offers.heldBy(anke(), code);
        // Repriced above what she has, after the hold was taken, which is how a hold outlives
        // the balance that would have paid for it.
        offers.changes(code, Map.of("costInPoints", seeded.pointsBalanceOf(ANKE) + 1000));
        long before = seeded.pointsBalanceOf(ANKE);

        ResponseEntity<JsonNode> refused = offers.triesToConvert(anke(), code);

        assertThat(refused.getStatusCode())
                .as("short of points is a bad request here exactly as it is for a claim")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("points, and you have");
        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(before);
        assertThat(offers.asReadBy(anke(), code).yourHoldLapsesAt())
                .as("the hold is exactly where it was, which is the whole reason a hold takes "
                        + "stock rather than points")
                .isEqualTo(held.lapsesAt());
    }

    /**
     * A customer can give a hold up, and the thing is back in the window at once — no job, no
     * night, no waiting.
     */
    @Test
    void giving_a_hold_up_returns_the_stock_at_once() {
        String code = offers.aCodeNobodyHasUsed();
        offers.theLastOneOnSale(code, "Changed her mind", 5);
        earn(10);
        earnFor(BRAM, 10);
        offers.heldBy(anke(), code);
        assertThat(offers.asReadBy(bram(), code).whatIsLeft()).isEqualTo(0);

        ResponseEntity<JsonNode> given = offers.triesToGiveUp(anke(), code);

        assertThat(given.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(given.getBody().path("state").asText()).isEqualTo("GIVEN_UP");
        assertThat(given.getBody().path("endedAt").asText())
                .as("a hold that ended says when, and it is not the moment it was due to")
                .isNotBlank();
        assertThat(offers.asReadBy(bram(), code).whatIsLeft())
                .as("back in the window immediately, with nothing having run")
                .isEqualTo(1);
        assertThat(offers.asReadBy(bram(), code).claimable()).isTrue();
        assertThat(offers.asReadBy(anke(), code).yourHoldLapsesAt())
                .as("and it is no longer hers")
                .isNull();
    }

    /** Giving one up costs nothing, because holding one cost nothing. */
    @Test
    void giving_a_hold_up_refunds_nothing_because_nothing_was_taken() {
        String code = offers.aCodeNobodyHasUsed();
        offers.theLastOneOnSale(code, "Nothing to refund", 5);
        earn(10);
        long before = seeded.pointsBalanceOf(ANKE);
        offers.heldBy(anke(), code);

        offers.triesToGiveUp(anke(), code);

        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(before);
    }

    /**
     * Giving up a hold that is not there is refused, and the sentence says which absence it is.
     *
     * <p>Three of the four absences are reachable without a clock and all three are here, because
     * the kind is one and the sentence is the whole of what tells them apart — which is exactly
     * the sort of thing that rots if nothing asserts it.
     */
    @Test
    void acting_on_a_hold_that_is_not_there_says_which_absence_it_is() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Nothing set aside", 5, 3, null);
        earn(20);

        ResponseEntity<JsonNode> neverHadOne = offers.triesToConvert(anke(), code);
        assertThat(neverHadOne.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(neverHadOne)).contains("not holding one");

        offers.heldBy(anke(), code);
        offers.triesToGiveUp(anke(), code);
        ResponseEntity<JsonNode> gaveItUp = offers.triesToConvert(anke(), code);
        assertThat(gaveItUp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(gaveItUp)).contains("gave up your hold");

        offers.heldBy(anke(), code);
        assertThat(offers.triesToConvert(anke(), code).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<JsonNode> alreadyClaimed = offers.triesToGiveUp(anke(), code);
        assertThat(alreadyClaimed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(alreadyClaimed)).contains("already claimed");
    }

    /**
     * Taking a hold runs the same gauntlet a claim runs, minus the points.
     *
     * <p>An offer nobody may claim is not one anybody may reserve: a customer allowed to set
     * aside something they could never convert would be holding stock back from everybody else
     * for nothing. The withdrawn offer is the cheapest of the gauntlet's refusals to arrange and
     * is the one that would actually happen — somebody takes a reward down while a page is open.
     */
    @Test
    void an_offer_that_is_not_on_sale_cannot_be_held() {
        String code = offers.aCodeNobodyHasUsed();
        offers.theLastOneOnSale(code, "Taken down this morning", 5);
        earn(10);
        offers.triesToWithdraw(code);

        ResponseEntity<JsonNode> refused = offers.triesToHold(anke(), code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("not on sale");
    }

    /** And a code the catalogue has never heard of is answered about the catalogue. */
    @Test
    void a_hold_on_something_that_does_not_exist_is_refused() {
        ResponseEntity<JsonNode> refused = offers.triesToHold(anke(), "NO_SUCH_THING_AT_ALL");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("NO_SUCH_THING_AT_ALL");
    }

    /**
     * <strong>An administrator cannot set the stock below what is claimed and held together.</strong>
     *
     * <p>A hold is a promise this scheme made to a named person for the next three days. A stock
     * figure that ignored it would be true until that person converted and one in the hole
     * afterwards, with nobody having done anything wrong — which is precisely the lie the
     * refusal exists to prevent.
     */
    @Test
    void the_stock_cannot_be_lowered_below_what_is_claimed_and_held() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Two, one of them spoken for", 5, 2, null);
        earn(20);
        offers.heldBy(anke(), code);

        ResponseEntity<JsonNode> refused = offers.triesToChange(code, Map.of("stock", 0));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("the held one is counted and said out loud")
                .contains("1 more are being held")
                .contains("lowest it can be is 1");
        // And the figure it names is one somebody can actually set.
        assertThat(offers.changes(code, Map.of("stock", 1)).stock()).isEqualTo(1);
    }

    /**
     * <strong>An offer nobody is holding behaves exactly as it did before this slice.</strong>
     *
     * <p>The rail, said in this package as well as in the ones that own it: the four the
     * application has always had set no stock, nobody can hold what has no scarcity to reserve
     * in the first place, and every card in the catalogue goes on saying nothing about a hold.
     */
    @Test
    void the_four_offers_that_have_always_been_there_say_nothing_about_a_hold() {
        assertThat(offers.theCatalogueAsReadBy(anke()))
                .filteredOn(entry -> entry.code().startsWith("HELD_FOR_YOU_") == false)
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.yourHoldLapsesAt())
                        .as("nobody is holding any of these, so no card counts anything down")
                        .isNull());
    }

    /**
     * An offer that never runs out can still be held, and the answer is deliberately yes.
     *
     * <p>It was worth deciding rather than falling into. Holding one of something unlimited buys
     * the customer nothing they did not already have — there is always another — so a scheme
     * could reasonably refuse it as a pointless request. It is allowed because refusing would
     * mean a rule whose only effect is to make the page's button appear and disappear depending
     * on a figure the customer cannot see, and because the day an administrator sets a stock
     * figure on an offer somebody is already holding, the hold has to mean what every other hold
     * means. What matters is the half that <em>is</em> load-bearing: an offer nobody holds is
     * untouched, and that is the test above.
     */
    @Test
    void an_offer_with_no_stock_set_can_still_be_held() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "As many as you like", 5, null, null);
        earn(10);

        HoldView held = offers.heldBy(anke(), code);

        assertThat(held.state()).isEqualTo("HELD");
        assertThat(offers.asReadBy(anke(), code).whatIsLeft())
                .as("and it still says nothing about stock, because it has none")
                .isNull();
        assertThat(offers.asReadBy(bram(), code).claimable())
                .as("nor does holding one keep anybody from something there is no shortage of")
                .isTrue();
    }

    private long anke() {
        return seeded.customerIdOf(ANKE);
    }

    private long bram() {
        return seeded.customerIdOf(BRAM);
    }

    /** The ordinary claim, read as unshaped JSON, because half of these are refusals. */
    private ResponseEntity<JsonNode> tryToClaim(long customerId, String code) {
        return http.postForEntity("/api/customers/{id}/redemptions", Map.of("reward", code),
                JsonNode.class, customerId);
    }

    private void earn(long points) {
        earnFor(ANKE, points);
    }

    /**
     * Earns at least the points this test is about to spend, at a point per euro. A test that
     * needs points is the test that earns them — leaning on what an earlier test left in the
     * account is how a failure lands in a class that never mentioned this one.
     */
    private void earnFor(String customerName, long points) {
        ResponseEntity<DepositView> deposit = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", points + ".00", "fromCurrentAccountId",
                        seeded.currentAccountOf(customerName)),
                DepositView.class, seeded.savingsAccountOf(customerName));
        assertThat(deposit.getStatusCode())
                .describedAs("a deposit this test needs in order to have points to spend")
                .isEqualTo(HttpStatus.CREATED);
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private static String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        assertThat(refusal.getBody()).as("a problem document with a reason in it").isNotNull();
        return refusal.getBody().path("detail").asText();
    }

    /**
     * The day the <em>application</em> thinks it is, off the development clock rather than off
     * this machine's.
     *
     * <p>Copied from the promotion package for the reason it gives: one database and one
     * application serve the whole run, another test class may have wound the clock on, and a
     * window written against {@code LocalDate.now()} would be a window the application had
     * already gone past.
     */
    private Instant theMomentTheApplicationThinksItIs() {
        return http.getForObject("/api/dev/clock", ClockView.class).now();
    }

    private LocalDate theDayTheApplicationThinksItIs() {
        ClockView clock = http.getForObject("/api/dev/clock", ClockView.class);
        return clock.now().atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }
}
