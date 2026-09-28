package io.dataroots.savingstreak.alimitalreadyreached;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * How often one person may have a thing: a cap on how many they may ever claim, a cap on how
 * many they may claim in a week, and the reading that tells each customer where they stand
 * inside both.
 *
 * <p><strong>The reading is the bigger half of this ticket, exactly as it was for the
 * window.</strong> A limit that only ever refused a claim would be a rule a customer meets at
 * the last step, after they have decided they want the thing and have gone and saved for it —
 * which is the failure the whole spec is written against. So every test below asserts the card
 * and the refusal together where both exist: what the customer is shown before they press, and
 * what they are told if they press anyway. They are the same fact at two moments and they say
 * the same sentence, word for word.
 *
 * <p><strong>A limit is the first rule in this feature whose answer depends on who is
 * asking.</strong> A window is the same for everybody and an offer is on sale to everybody or to
 * nobody; this one is a fact about one person's own history. So the test that matters most here
 * is not the one where somebody is locked — it is
 * {@link #one_customers_limit_leaves_another_customers_reading_alone}, because a count that
 * leaked between two people would take a reward away from somebody who had never claimed
 * anything.
 *
 * <p><strong>The week is not wound here.</strong> Everything below fixes the day and moves the
 * caps and the claims, which proves the counting;
 * {@code AWeeklyLimitResetsWhenTheWeekTurnsApiTest} fixes the caps and moves the clock, which
 * proves the thing an administrator is actually being sold. Winding a clock cannot be undone, so
 * that one has an application of its own.
 *
 * <p><strong>Every test asserts on what its own requests changed, and puts the catalogue
 * back.</strong> The run shares one database and the catalogue is the most shared thing in it.
 * Nothing here counts the offers; what it asserts is what happened to the one it wrote, against
 * a code nobody else in the run has, and
 * {@link OffersWithLimitsThisTestWrites#putTheCatalogueBack} withdraws it afterwards so that the
 * test pinning the customer's catalogue to four entries at four prices goes on passing.
 *
 * <p>Anke is the customer, because she is the one the claiming tests already spend from; what
 * she spends here, she earns here. Bram is the second person, and he is here only to prove that
 * nothing Anke does reaches him.
 */
class ALimitAlreadyReachedApiTest extends ApiIntegrationTest {

    /**
     * Cheap, and earned inside each test. What these offers cost is not what any of them is
     * about, and a price small enough to earn in one deposit keeps the arrangement out of the
     * way of the assertion.
     */
    private static final long WHAT_A_CAPPED_THING_COSTS = 2;

    private SeededAccounts seeded;

    private OffersWithLimitsThisTestWrites offers;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
        offers = new OffersWithLimitsThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * The administrator's half, which happens before any customer has had anything: two numbers
     * set on an offer and read back as they were typed.
     */
    @Test
    void an_offer_carries_the_two_caps_whoever_runs_the_catalogue_gave_it() {
        String code = offers.aCodeNobodyHasUsed();

        OfferView written = offers.onSale(code, "Two in all", WHAT_A_CAPPED_THING_COSTS, 2, 1);

        assertThat(written.maxPerCustomer()).isEqualTo(2);
        assertThat(written.maxPerCustomerPerWeek()).isEqualTo(1);
        assertThat(offers.theOffer(code).maxPerCustomer())
                .as("read back out of the catalogue rather than off the reply to the write")
                .isEqualTo(2);
        assertThat(offers.theOffer(code).maxPerCustomerPerWeek()).isEqualTo(1);
    }

    /**
     * <strong>The safety argument of the ticket.</strong> The four offers this application has
     * always had carry neither cap, and a customer reads them today exactly as they read them
     * before limits existed: no cap, nothing left over to count against, and claimable.
     */
    @Test
    void the_four_that_have_always_been_there_cap_nobody() {
        List<RewardForACustomerView> catalogue =
                offers.theCatalogueAsReadBy(seeded.customerIdOf(ANKE));

        assertThat(catalogue).extracting(RewardForACustomerView::code)
                .containsAll(theFourThatHaveAlwaysBeenThere());
        for (String code : theFourThatHaveAlwaysBeenThere()) {
            OfferView asItStands = offers.theOffer(code);
            assertThat(asItStands.maxPerCustomer())
                    .as(code + " has never had a lifetime cap and must not gain one")
                    .isNull();
            assertThat(asItStands.maxPerCustomerPerWeek()).as(code).isNull();
            RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);
            assertThat(reading.maxPerCustomer()).as(code).isNull();
            assertThat(reading.maxPerCustomerPerWeek()).as(code).isNull();
            assertThat(reading.howManyYouMayStillHave())
                    .as("no cap is not a large number of remaining claims, it is no answer at all")
                    .isNull();
            assertThat(reading.lockedBecause()).as(code).isNull();
        }
    }

    /**
     * And an uncapped offer can be claimed again and again, which is the same argument said with
     * claims rather than with nulls: whatever the counting does, it must do nothing at all to an
     * offer nobody limited.
     */
    @Test
    void an_offer_with_neither_cap_can_be_claimed_over_and_over() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "As often as you like", WHAT_A_CAPPED_THING_COSTS, null, null);
        earn(WHAT_A_CAPPED_THING_COSTS * 3);

        claim(code);
        claim(code);
        claim(code);

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);
        assertThat(reading.claimable())
                .as("three claims deep and nothing has been capped, because nothing was capped")
                .isTrue();
        assertThat(reading.lockedBecause()).isNull();
        assertThat(reading.howManyYouHaveHad())
                .as("counted all the same, because the count is what a cap would be measured "
                        + "against and it does not wait for one")
                .isEqualTo(3);
        assertThat(reading.howManyYouMayStillHave()).isNull();
    }

    /**
     * The half a customer notices first: somebody who has had their lifetime allowance sees the
     * offer on the page, greyed, with how many they have had in the sentence.
     *
     * <p>Shown and locked asserted together, on purpose. "It says I have had my limit" would
     * pass just as well against a catalogue that left the offer out entirely, and leaving it out
     * is precisely what this feature refuses to do: a card that vanishes once somebody has had
     * their two teaches them nothing about why.
     */
    @Test
    void a_customer_at_their_lifetime_limit_sees_it_locked_with_how_many_they_have_had() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "One in a lifetime", WHAT_A_CAPPED_THING_COSTS, 1, null);
        earn(WHAT_A_CAPPED_THING_COSTS);
        claim(code);

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.claimable()).isFalse();
        assertThat(reading.lockedBecause()).isEqualTo("YOU_HAVE_HAD_YOUR_LIMIT");
        assertThat(reading.whyItIsLocked())
                .as("the count and the cap are both in it, because a limit asserted without "
                        + "either is something nobody can check")
                .contains("One in a lifetime")
                .contains("1");
        assertThat(reading.howManyYouHaveHad()).isEqualTo(1);
        assertThat(reading.howManyYouMayStillHave()).isZero();
    }

    /**
     * And pressing it anyway is refused, in the same words, with nothing spent.
     *
     * <p>The balance is the load-bearing half. A refusal that had already taken the points would
     * be worse than no rule at all, and the order the module checks in — the limit before the
     * points, always — is what makes that impossible rather than unlikely.
     */
    @Test
    void claiming_past_a_lifetime_limit_is_refused_in_the_same_words_and_takes_nothing() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "One in a lifetime", WHAT_A_CAPPED_THING_COSTS, 1, null);
        earn(WHAT_A_CAPPED_THING_COSTS * 2);
        claim(code);
        RewardForACustomerView theCard = offers.asReadBy(seeded.customerIdOf(ANKE), code);
        long before = seeded.pointsBalanceOf(ANKE);
        int claimsBefore = claimsOf(ANKE).size();

        ResponseEntity<JsonNode> refused = tryToClaim(code);

        assertThat(refused.getStatusCode())
                .as("the offer is real, open and well named; what says no is this customer's own "
                        + "history, which is a state of the world and not a malformed form")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("the card and the refusal are the same fact said at two moments, so they are "
                        + "the same sentence")
                .isEqualTo(theCard.whyItIsLocked());
        assertThat(seeded.pointsBalanceOf(ANKE))
                .as("they could afford it twice over, and were charged for neither")
                .isEqualTo(before);
        assertThat(claimsOf(ANKE)).hasSize(claimsBefore);
    }

    /**
     * <strong>The test this ticket most needs to pass.</strong> A limit is one person's history,
     * and a count that leaked between two people would lock somebody out of a reward they had
     * never claimed once in their life.
     */
    @Test
    void one_customers_limit_leaves_another_customers_reading_alone() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "One each", WHAT_A_CAPPED_THING_COSTS, 1, null);
        earn(WHAT_A_CAPPED_THING_COSTS);
        claim(code);

        RewardForACustomerView hers = offers.asReadBy(seeded.customerIdOf(ANKE), code);
        RewardForACustomerView his = offers.asReadBy(seeded.customerIdOf(BRAM), code);

        assertThat(hers.claimable()).isFalse();
        assertThat(his.claimable())
                .as("Bram has claimed nothing; one each means one each, not one between them")
                .isTrue();
        assertThat(his.lockedBecause()).isNull();
        assertThat(his.whyItIsLocked()).isNull();
        assertThat(his.howManyYouHaveHad()).isZero();
        assertThat(his.howManyYouMayStillHave()).isEqualTo(1);
    }

    /**
     * The weekly cap, which is the same lock with a different sentence — and the difference is
     * the whole of what the customer can do about it.
     */
    @Test
    void a_customer_at_their_weekly_limit_is_told_the_week_turns_over_on_monday() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "One a week", WHAT_A_CAPPED_THING_COSTS, null, 1);
        earn(WHAT_A_CAPPED_THING_COSTS * 2);
        claim(code);

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);
        ResponseEntity<JsonNode> refused = tryToClaim(code);

        assertThat(reading.claimable()).isFalse();
        assertThat(reading.lockedBecause()).isEqualTo("YOU_HAVE_HAD_YOUR_LIMIT");
        assertThat(reading.whyItIsLocked())
                .as("the day the allowance comes back is the only part of this a person can act "
                        + "on, so it is in the sentence")
                .contains("this week")
                .contains("Monday");
        assertThat(reading.maxPerCustomerPerWeek()).isEqualTo(1);
        assertThat(reading.maxPerCustomer())
                .as("no lifetime cap was set and none was invented")
                .isNull();
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).isEqualTo(reading.whyItIsLocked());
    }

    /**
     * Somebody at both caps at once is told about the lifetime one, and that order is the point
     * rather than an accident: a weekly allowance comes back on Monday and a lifetime one never
     * does, so telling them to come back would be sending them away with something untrue.
     */
    @Test
    void somebody_at_both_caps_is_told_about_the_one_waiting_cannot_fix() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "One, once", WHAT_A_CAPPED_THING_COSTS, 1, 1);
        earn(WHAT_A_CAPPED_THING_COSTS);
        claim(code);

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.lockedBecause()).isEqualTo("YOU_HAVE_HAD_YOUR_LIMIT");
        assertThat(reading.whyItIsLocked())
                .as("the lifetime cap is asked first, so the sentence is the one about all of "
                        + "them rather than the one about this week")
                .contains("in all")
                .doesNotContain("Monday");
    }

    /**
     * What is left, said before anybody is locked, because that is the whole of "so that I know
     * whether to hurry": a customer who finds out about a limit by being refused found out too
     * late to do anything about it.
     */
    @Test
    void the_reading_says_how_many_are_left_while_there_are_still_some() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Three in all", WHAT_A_CAPPED_THING_COSTS, 3, null);
        earn(WHAT_A_CAPPED_THING_COSTS);
        claim(code);

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.claimable()).isTrue();
        assertThat(reading.maxPerCustomer()).isEqualTo(3);
        assertThat(reading.howManyYouHaveHad()).isEqualTo(1);
        assertThat(reading.howManyYouMayStillHave()).isEqualTo(2);
    }

    /**
     * And with two caps running it is the tighter of them, because that is the one they will
     * actually meet next. Five in a lifetime and one a week is one, not five.
     */
    @Test
    void what_is_left_is_the_tighter_of_the_two_caps() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Five in all, one a week", WHAT_A_CAPPED_THING_COSTS, 5, 1);

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.claimable()).isTrue();
        assertThat(reading.howManyYouHaveHad()).isZero();
        assertThat(reading.howManyYouMayStillHave())
                .as("the card that promised five would be promising four claims this week's rule "
                        + "is about to refuse")
                .isEqualTo(1);
    }

    /**
     * A cap below one is not a cap. Nought would be an offer nobody may ever claim, said in the
     * one box least likely to be read back, and somebody who typed it meant "no cap" — which is
     * an empty box.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void a_lifetime_cap_below_one_is_refused_at_the_form(int notACap) {
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", offers.aCodeNobodyHasUsed());
        offer.put("title", "Nobody may have one");
        offer.put("costInPoints", WHAT_A_CAPPED_THING_COSTS);
        offer.put("voucherPrefix", "LIM");
        offer.put("maxPerCustomer", notACap);

        ResponseEntity<JsonNode> refused = offers.triesToWrite(offer);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains("is not a limit")
                .contains("Leave it blank");
    }

    /** And the same of the weekly one, on an offer that already exists. */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void a_weekly_cap_below_one_is_refused_on_an_offer_that_exists(int notACap) {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "One a week", WHAT_A_CAPPED_THING_COSTS, null, 1);

        ResponseEntity<JsonNode> refused =
                offers.triesToChange(code, Map.of("maxPerCustomerPerWeek", notACap));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("a week is not a limit");
        assertThat(offers.theOffer(code).maxPerCustomerPerWeek())
                .as("a refused change changes nothing")
                .isEqualTo(1);
    }

    /**
     * Raising a cap unlocks whoever was at it, with no restart and nothing migrated — which is
     * the administrator's half of "a limit is a number rather than a release".
     */
    @Test
    void raising_a_cap_lets_somebody_who_was_at_it_have_another() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "One, for now", WHAT_A_CAPPED_THING_COSTS, 1, null);
        earn(WHAT_A_CAPPED_THING_COSTS * 2);
        claim(code);
        assertThat(offers.asReadBy(seeded.customerIdOf(ANKE), code).claimable()).isFalse();

        offers.changes(code, Map.of("maxPerCustomer", 2));

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);
        assertThat(reading.claimable()).isTrue();
        assertThat(reading.howManyYouMayStillHave()).isEqualTo(1);
        assertThat(claim(code).code()).isEqualTo(code);
    }

    /**
     * And lowering one under somebody who has already had more than it allows locks them out of
     * another rather than putting them in debt. A claim already made is a voucher somebody is
     * holding; a limit is a limit, not a bill.
     */
    @Test
    void lowering_a_cap_below_what_somebody_has_had_locks_them_and_owes_nothing() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Three, then one", WHAT_A_CAPPED_THING_COSTS, 3, null);
        earn(WHAT_A_CAPPED_THING_COSTS * 2);
        ClaimedRewardView first = claim(code);
        claim(code);

        offers.changes(code, Map.of("maxPerCustomer", 1));

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);
        assertThat(reading.claimable()).isFalse();
        assertThat(reading.howManyYouHaveHad()).isEqualTo(2);
        assertThat(reading.howManyYouMayStillHave())
                .as("two had against a cap of one is none left, and never minus one")
                .isZero();
        assertThat(claimsOf(ANKE))
                .as("both vouchers are still theirs; nothing was taken back")
                .extracting(ClaimedRewardView::voucherCode)
                .contains(first.voucherCode());
    }

    /**
     * Nothing about a limit reaches the catalogue with nobody in it. That endpoint is pinned to
     * four entries at four prices by a test nobody may edit, and a field added to it would be a
     * field added to that promise.
     */
    @Test
    void no_limit_leaks_into_the_catalogue_that_has_nobody_in_it() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "One in a lifetime", WHAT_A_CAPPED_THING_COSTS, 1, 1);

        assertThat(theCatalogueAsItIsSent())
                .as("no cap, no count and no remainder on the catalogue that has always been "
                        + "four fields wide")
                .doesNotContain("maxPerCustomer")
                .doesNotContain("howManyYouHaveHad")
                .doesNotContain("howManyYouMayStillHave");
    }

    /** The four the application has always offered, which no cap is ever set on. */
    private static List<String> theFourThatHaveAlwaysBeenThere() {
        return List.of("CHARITY_DONATION", "SNACK_VOUCHER", "CINEMA_TICKET",
                "FAMILY_CINEMA_PACK");
    }

    /** Claimed by Anke, out of the one pot of points every account of hers earns into. */
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

    /** What somebody has claimed, for the assertions that a refusal issued nothing. */
    private List<ClaimedRewardView> claimsOf(String customerName) {
        return List.of(http.getForObject("/api/customers/{id}/redemptions",
                ClaimedRewardView[].class, seeded.customerIdOf(customerName)));
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

    /** The plain catalogue exactly as it goes over the wire, for saying what is not in it. */
    private String theCatalogueAsItIsSent() {
        ResponseEntity<String> read = http.getForEntity("/api/rewards", String.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private static String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }
}
