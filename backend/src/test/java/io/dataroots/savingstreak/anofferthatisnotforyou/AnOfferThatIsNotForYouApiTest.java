package io.dataroots.savingstreak.anofferthatisnotforyou;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.EnrolmentView;
import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.RewardView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An offer with rules about who it is for, and the lock that says which rule stopped you.
 *
 * <p><strong>The lock is the bigger half of this ticket, exactly as the window was.</strong> A
 * rule that only ever refused a claim would be a rule a customer meets at the last step, after
 * they have decided they want the thing — which is the failure the whole spec is written
 * against. Worse here than for a window: a season at least tells you to come back, and "you are
 * not eligible" discovered at the button tells you nothing at all. So every test below asserts
 * the card and the refusal together where both exist, and asserts that the two sentences are the
 * same string.
 *
 * <p><strong>The comparison itself is not tested here.</strong> Whether nine weeks meets a rule
 * of ten, whether meeting two of three is meeting them, and which of several unmet rules is the
 * one named are a function of two values and are covered as one, in
 * {@code rewards.WhoAnOfferIsForTest}. Arranging a streak, a badge and a points history at once
 * over HTTP, for every combination, is precisely what the record the module is handed exists to
 * make unnecessary. What is asserted here is everything that test cannot say: that the verdict
 * reaches a card, that the sentence is the refusal's sentence, that nothing is spent, that
 * crossing a threshold unlocks the offer with no other action, and that an offer with no rules
 * behaves exactly as it always has.
 *
 * <p><strong>Every customer here is one this test opened.</strong> A rule about somebody's
 * standing is a rule about their whole history, and the seeded pair's history is written by the
 * rest of the run — a test asserting that Anke has not earned five hundred points in all would
 * start failing the day another class deposits more, from a package it never mentions. A
 * customer opened here starts with nothing earned, no run of weeks and an empty trophy case,
 * which is the only footing on which "they do not qualify, and then they do" can be asserted at
 * all.
 *
 * <p><strong>Every test asserts on what its own requests changed, and puts the catalogue
 * back.</strong> The run shares one database and the catalogue is the most shared thing in it;
 * {@link OffersWithRulesThisTestWrites#putTheCatalogueBack} withdraws everything written here so
 * that the test pinning the customer's catalogue to four entries at four prices goes on passing.
 */
class AnOfferThatIsNotForYouApiTest extends ApiIntegrationTest {

    /** So that no two customers this class opens can collide, in this run or in another class. */
    private static final AtomicInteger OPENED = new AtomicInteger();

    /**
     * A challenge the bank has always offered, whose bronze rung is EUR 100 of new saving. Named
     * as a constant because it is used both as the badge an offer asks for and as the challenge
     * a customer is put on to win it — and the whole point of the badge test is that those two
     * strings are the same string.
     */
    private static final String A_CHALLENGE_THE_BANK_RUNS = "SAVE_FIVE_HUNDRED";

    private static final String WHAT_BRONZE_ON_IT_TAKES = "100.00";

    private SeededAccounts seeded;

    private OffersWithRulesThisTestWrites offers;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
        offers = new OffersWithRulesThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * The half a customer would notice first: an offer they do not qualify for is on the page,
     * greyed, saying which rule stopped them and both sides of it.
     *
     * <p>Shown and locked asserted together, on purpose. "It says they are not eligible" would
     * pass just as well against a catalogue that left the offer out entirely, and leaving it out
     * is precisely what this feature refuses to do — an offer nobody can see is a scheme that
     * never tells anybody what it wants from them.
     *
     * <p>Both sides of the threshold in the sentence, because a refusal naming only the
     * requirement is an assertion the customer has to take on trust.
     */
    @Test
    void an_offer_that_is_not_for_them_is_shown_locked_with_the_rule_that_stopped_them() {
        long customerId = aCustomerWithNothingBehindThem("a streak they do not have");
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Long-run hamper", 5, 12, null, null);

        RewardForACustomerView reading = offers.asReadBy(customerId, code);

        assertThat(reading.claimable()).isFalse();
        assertThat(reading.lockedBecause()).isEqualTo("NOT_FOR_YOU");
        assertThat(reading.whyItIsLocked())
                .as("the rule and both sides of it, because a customer told only the requirement "
                        + "has to take the refusal on trust")
                .contains("Long-run hamper")
                .contains("12")
                .contains("0");
    }

    /**
     * And pressing it anyway is refused, in the same words, with nothing spent.
     *
     * <p>The balance is the load-bearing half, and the order of the module's checks is what
     * makes it impossible rather than unlikely: who an offer is for is asked immediately after
     * the window and a long way before the points. A customer with enough points who is refused
     * for a rule must keep every one of them.
     *
     * <p>The two strings are compared to each other rather than each to a literal. That is the
     * property this whole feature rests on — one sentence written in one place — and a test
     * asserting them separately would pass the day they drifted.
     */
    @Test
    void claiming_an_offer_that_is_not_for_them_is_refused_in_the_same_words_and_takes_nothing() {
        String customer = aCustomerOfItsOwn("a badge they have not won");
        long customerId = seeded.customerIdOf(customer);
        earn(customer, 50);
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Members' hamper", 5, null, A_CHALLENGE_THE_BANK_RUNS, null);
        long before = seeded.pointsBalanceOf(customer);

        RewardForACustomerView reading = offers.asReadBy(customerId, code);
        ResponseEntity<JsonNode> refused = tryToClaim(customerId, code);

        assertThat(reading.claimable()).isFalse();
        assertThat(reading.lockedBecause()).isEqualTo("NOT_FOR_YOU");
        assertThat(refused.getStatusCode())
                .as("the offer is real and the request is well formed; what says no is who they "
                        + "are today, which is what a conflict means")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("word for word the sentence the card was locked with, so that the page and "
                        + "the refusal cannot tell somebody two different stories")
                .isEqualTo(reading.whyItIsLocked());
        assertThat(reasonGivenBy(refused)).contains(A_CHALLENGE_THE_BANK_RUNS);
        assertThat(seeded.pointsBalanceOf(customer))
                .as("they could easily afford it; being refused for a rule takes nothing")
                .isEqualTo(before);
    }

    /**
     * A customer who crosses a threshold sees the offer unlock, with no other action.
     *
     * <p>The criterion this ticket is really about, and the reason nothing about eligibility is
     * stored: the lock is derived when the card is read, so the read after the deposit is the
     * first read that can answer differently, and there is nothing to press, run or wait for in
     * between. A nightly flag would have made this a test about a job.
     *
     * <p>A lifetime of points earned is the threshold used, because it is the one a test can
     * cross in a single request. The figures are deliberately far apart rather than exact: what
     * a deposit earns depends on the run of weeks behind it, and a test asserting an exact
     * lifetime would be asserting the bonus rules as well as this rule.
     */
    @Test
    void a_customer_who_crosses_a_threshold_sees_the_offer_unlock_with_no_other_action() {
        String customer = aCustomerOfItsOwn("a lifetime they are about to reach");
        long customerId = seeded.customerIdOf(customer);
        earn(customer, 10);
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Long service hamper", 5, null, null, 500L);
        assertThat(offers.asReadBy(customerId, code).claimable())
                .as("ten euros of saving is nowhere near five hundred points earned")
                .isFalse();

        earn(customer, 700);

        RewardForACustomerView unlocked = offers.asReadBy(customerId, code);
        assertThat(unlocked.claimable()).isTrue();
        assertThat(unlocked.lockedBecause()).isNull();
        assertThat(unlocked.whyItIsLocked()).isNull();
        assertThat(claim(customerId, code).code())
                .as("and the claim the card now offers actually goes through")
                .isEqualTo(code);
    }

    /**
     * The same again for a badge, because a badge is the one threshold that is not a number.
     *
     * <p>It is also the one that proves the trophy case is read at the moment the card is drawn
     * rather than out of something stale: the badge is won by a deposit, and the very next read
     * of the rewards page finds the offer open. Nobody visits the challenges tab in between, and
     * that is the assertion — the standing the rewards page is judged against is assembled when
     * the page is asked for, and assembling it judges the challenges first.
     *
     * <p>The badge is named by the challenge's code, which is what the trophy case carries and
     * what an administrator types into the form. Asserted out of the customer's own achievements
     * rather than assumed, so that the string the rule matches on is demonstrably the string the
     * application mints.
     */
    @Test
    void an_offer_gated_on_a_badge_unlocks_on_the_deposit_that_wins_it() {
        String customer = aCustomerOfItsOwn("a badge they are about to win");
        long customerId = seeded.customerIdOf(customer);
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Achievers' hamper", 5, null, A_CHALLENGE_THE_BANK_RUNS, null);
        assertThat(offers.asReadBy(customerId, code).claimable())
                .as("an empty trophy case holds no badge")
                .isFalse();
        enrolIn(customerId, A_CHALLENGE_THE_BANK_RUNS);

        earn(customer, Integer.parseInt(WHAT_BRONZE_ON_IT_TAKES.split("\\.")[0]));

        assertThat(achievementsOf(customerId))
                .as("the deposit won a rung of the challenge the offer asks for")
                .extracting(AchievementView::challenge)
                .contains(A_CHALLENGE_THE_BANK_RUNS);
        RewardForACustomerView unlocked = offers.asReadBy(customerId, code);
        assertThat(unlocked.claimable()).isTrue();
        assertThat(unlocked.lockedBecause()).isNull();
        assertThat(claim(customerId, code).code()).isEqualTo(code);
    }

    /**
     * Every threshold that is set has to be met, and the sentence names the first one that is
     * not — which is the streak, because that is the order the three sit in on the form.
     *
     * <p>Asserted over HTTP as well as at the rule, because this is the one place the order is
     * visible to a person: a customer who satisfies the streak is then told about the badge, and
     * each sentence is a different instruction. Meeting two of three is asserted to be meeting
     * nothing, which is the whole of "ANDed".
     */
    @Test
    void all_the_thresholds_an_offer_sets_must_be_met_and_the_first_unmet_one_is_the_sentence() {
        String customer = aCustomerOfItsOwn("two rules of three");
        long customerId = seeded.customerIdOf(customer);
        earn(customer, 600);
        enrolIn(customerId, A_CHALLENGE_THE_BANK_RUNS);
        earn(customer, Integer.parseInt(WHAT_BRONZE_ON_IT_TAKES.split("\\.")[0]));
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Everything hamper", 5, 30, A_CHALLENGE_THE_BANK_RUNS, 500L);

        RewardForACustomerView reading = offers.asReadBy(customerId, code);

        assertThat(reading.claimable())
                .as("they hold the badge and they have earned the lifetime; the run of weeks "
                        + "alone is short, and short of one rule is short")
                .isFalse();
        assertThat(reading.whyItIsLocked())
                .as("the streak is the first rule in the declared order, so it is the one said")
                .contains("run of")
                .contains("30");
        assertThat(reasonGivenBy(tryToClaim(customerId, code)))
                .isEqualTo(reading.whyItIsLocked());
    }

    /**
     * An offer with no rules on it behaves exactly as it always has, for a customer with nothing
     * at all to their name — which is the safety argument of the whole ticket.
     *
     * <p>Asserted against the four seeded entries rather than against something this test wrote,
     * because they are the ones a customer knew yesterday, and asserted for the emptiest
     * customer this application can produce: no points, no run of weeks, no badges. If a null
     * threshold were ever read as a floor of nought, this is the person it would lock out.
     *
     * <p>Not by counting them: the run shares a database and another class's offer may be on
     * sale while this one looks.
     */
    @Test
    void the_four_offers_that_have_always_been_there_are_for_a_customer_with_nothing() {
        long customerId = aCustomerWithNothingBehindThem("the four that were always there");

        assertThat(offers.theCatalogueAsReadBy(customerId))
                .filteredOn(entry -> theFourThatHaveAlwaysBeenThere().contains(entry.code()))
                .hasSize(4)
                .allSatisfy(entry -> {
                    assertThat(entry.claimable()).isTrue();
                    assertThat(entry.lockedBecause()).isNull();
                    assertThat(entry.whyItIsLocked()).isNull();
                });
    }

    /**
     * The rules are set from the administration screen, read back there, and changed there.
     *
     * <p>Read back is the half worth insisting on, and it is the spec's own argument for a
     * closed list of three thresholds rather than an expression: <em>a rule nobody can read back
     * in the administration form is a rule nobody can debug</em>. What comes back has to be the
     * three figures somebody typed, unresolved and unsummarised.
     *
     * <p>Changed one at a time, because absence means "leave it alone" field by field — an
     * administrator raising a streak requirement on an offer that also asks for a badge has not
     * taken the badge off.
     */
    @Test
    void the_rules_are_set_read_back_and_changed_from_the_administration_screen() {
        String code = offers.aCodeNobodyHasUsed();

        OfferView written = offers.writes(code, "A gated hamper", 5, 4, "SAVE_EVERY_WEEK", 250L);
        assertThat(written.minimumStreakWeeks()).isEqualTo(4);
        assertThat(written.requiresBadge()).isEqualTo("SAVE_EVERY_WEEK");
        assertThat(written.minimumLifetimePointsEarned()).isEqualTo(250L);

        OfferView raised = offers.changes(code, Map.of("minimumStreakWeeks", 8));
        assertThat(raised.minimumStreakWeeks()).isEqualTo(8);
        assertThat(raised.requiresBadge())
                .as("a change names one rule and leaves the others exactly as they were")
                .isEqualTo("SAVE_EVERY_WEEK");
        assertThat(raised.minimumLifetimePointsEarned()).isEqualTo(250L);
        assertThat(offers.theOffer(code).minimumStreakWeeks()).isEqualTo(8);
    }

    /**
     * An offer nobody restricted says so as an absence, and not as a nought.
     *
     * <p>Three nulls on the back office's own read, which is what every seeded offer says about
     * itself and what the form shows as an empty box. A nought would be a rule on the screen
     * that restricts nobody.
     */
    @Test
    void an_offer_with_no_rules_reads_back_as_three_absences() {
        String code = offers.aCodeNobodyHasUsed();

        OfferView written = offers.writes(code, "An open hamper", 5, null, null, null);

        assertThat(written.minimumStreakWeeks()).isNull();
        assertThat(written.requiresBadge()).isNull();
        assertThat(written.minimumLifetimePointsEarned()).isNull();
    }

    /**
     * A threshold of nothing is refused where it is written, in a sentence.
     *
     * <p>Not a loose rule but a rule that restricts nobody while sitting on the form looking
     * like a restriction — and the person it misleads is the administrator who set it, who will
     * wonder for a fortnight why the gated hamper is being claimed by everybody. Answered at the
     * form, which is what a bad request is for.
     */
    @Test
    void a_threshold_of_nothing_is_refused_in_words_about_the_threshold() {
        Map<String, Object> noStreakAtAll = new HashMap<>();
        noStreakAtAll.put("code", offers.aCodeNobodyHasUsed());
        noStreakAtAll.put("title", "A rule that is not one");
        noStreakAtAll.put("costInPoints", 5);
        noStreakAtAll.put("voucherPrefix", "NIL");
        noStreakAtAll.put("minimumStreakWeeks", 0);

        ResponseEntity<JsonNode> refused = offers.triesToWrite(noStreakAtAll);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("weeks").contains("every customer");

        Map<String, Object> noLifetimeAtAll = new HashMap<>();
        noLifetimeAtAll.put("code", offers.aCodeNobodyHasUsed());
        noLifetimeAtAll.put("title", "Another rule that is not one");
        noLifetimeAtAll.put("costInPoints", 5);
        noLifetimeAtAll.put("voucherPrefix", "NIL");
        noLifetimeAtAll.put("minimumLifetimePointsEarned", 0);

        ResponseEntity<JsonNode> alsoRefused = offers.triesToWrite(noLifetimeAtAll);

        assertThat(alsoRefused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(alsoRefused)).contains("points").contains("every customer");
    }

    /**
     * The catalogue with nobody in it says nothing about who an offer is for, and is not changed
     * by one.
     *
     * <p>{@code /api/rewards} is the shape this whole feature is built not to move: four fields,
     * a price and no derivation. An offer with rules on it is published into it like any other —
     * the endpoint has no customer in it to decide otherwise — and what must stay true is that
     * nothing a page could start depending on leaks into it. A threshold on that card would be
     * the worst leak of the three: a page that had it would do the comparison itself.
     */
    @Test
    void the_catalogue_with_nobody_in_it_gains_no_field_when_an_offer_has_rules() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "A gated hamper", 5, 4, "SAVE_EVERY_WEEK", 250L);

        assertThat(offers.theCatalogueWithNobodyInIt())
                .extracting(RewardView::code)
                .contains(code);
        assertThat(theCatalogueAsItIsSent())
                .as("no threshold, no lock and no verdict leaks into the catalogue that has "
                        + "always been four fields wide")
                .doesNotContain("minimumStreakWeeks")
                .doesNotContain("requiresBadge")
                .doesNotContain("minimumLifetimePointsEarned")
                .doesNotContain("claimable")
                .doesNotContain("lockedBecause");
    }

    /** The four the application has always offered, which no rule is ever set on. */
    private static List<String> theFourThatHaveAlwaysBeenThere() {
        return List.of("CHARITY_DONATION", "SNACK_VOUCHER", "CINEMA_TICKET",
                "FAMILY_CINEMA_PACK");
    }

    /**
     * A customer this test opened, with nothing earned, no run of weeks and an empty trophy
     * case.
     *
     * <p>Its own customer every time, and the class javadoc says why at length: a rule about
     * somebody's standing is a rule about their whole history, and the seeded pair's history is
     * written by the rest of the run. The name carries the purpose and a number so that no two
     * callers can collide and so that a failing run says which account it is talking about.
     */
    private String aCustomerOfItsOwn(String purpose) {
        long distinct = OPENED.incrementAndGet();
        String name = "not for you " + distinct;
        ResponseEntity<JsonNode> opened = http.postForEntity("/api/customers",
                Map.of("name", name,
                        "contactDetails", "not.for.you." + distinct + "@example.be"),
                JsonNode.class);
        assertThat(opened.getStatusCode())
                .describedAs("opening a customer of this test's own for " + purpose + ": "
                        + opened.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return name;
    }

    /** The same, for the tests that only ever need the identifier. */
    private long aCustomerWithNothingBehindThem(String purpose) {
        return seeded.customerIdOf(aCustomerOfItsOwn(purpose));
    }

    /**
     * Earns points by saving, at a point per whole euro. A test that needs a history is the test
     * that makes one — leaning on what another test left behind is how a failure lands in a
     * class that never mentioned this one.
     */
    private void earn(String customer, long euros) {
        ResponseEntity<DepositView> deposit = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", euros + ".00", "fromCurrentAccountId",
                        seeded.currentAccountOf(customer)),
                DepositView.class, seeded.savingsAccountOf(customer));
        assertThat(deposit.getStatusCode())
                .describedAs("a deposit this test needs in order to have a history")
                .isEqualTo(HttpStatus.CREATED);
    }

    /** Takes the customer on to a challenge, so that saving afterwards can win them a badge. */
    private void enrolIn(long customerId, String challenge) {
        ResponseEntity<EnrolmentView> enrolled = http.postForEntity(
                "/api/customers/{id}/challenges/{code}/enrolments", null, EnrolmentView.class,
                customerId, challenge);
        assertThat(enrolled.getStatusCode())
                .describedAs("putting customer " + customerId + " on to " + challenge)
                .isEqualTo(HttpStatus.CREATED);
    }

    /** What is in their trophy case, which is where the badge codes a rule matches on come from. */
    private List<AchievementView> achievementsOf(long customerId) {
        ResponseEntity<AchievementView[]> read = http.getForEntity(
                "/api/customers/{id}/achievements", AchievementView[].class, customerId);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** Claimed by the customer, insisted on, for the tests whose subject is the unlocking. */
    private ClaimedRewardView claim(long customerId, String code) {
        ResponseEntity<ClaimedRewardView> claimed = http.postForEntity(
                "/api/customers/{id}/redemptions", Map.of("reward", code),
                ClaimedRewardView.class, customerId);
        assertThat(claimed.getStatusCode()).describedAs("claiming \"" + code + "\"")
                .isEqualTo(HttpStatus.CREATED);
        return claimed.getBody();
    }

    /** The same claim read as unshaped JSON, for the ones this ticket refuses. */
    private ResponseEntity<JsonNode> tryToClaim(long customerId, String code) {
        return http.postForEntity("/api/customers/{id}/redemptions", Map.of("reward", code),
                JsonNode.class, customerId);
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
