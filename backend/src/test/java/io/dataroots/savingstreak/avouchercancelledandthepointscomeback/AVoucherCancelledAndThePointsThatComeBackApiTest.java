package io.dataroots.savingstreak.avouchercancelledandthepointscomeback;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.SeededAccounts;
import io.dataroots.savingstreak.support.VoucherAtTheCounterView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A claim that should never have been made, undone: whoever runs the scheme revokes the voucher
 * and says why, the customer's points come back, and the thing goes back in the window.
 *
 * <p><strong>Three consequences, and each test asserts the one it is about.</strong> A
 * cancellation moves a state, a balance and a stock figure, and a single test asserting all three
 * at once would pass while two of them were broken in ways that happened to cancel out. What is
 * asserted together is only what has to be true together — a refusal and the balance in one test,
 * because "refused and already refunded" is the one failure worth making impossible rather than
 * unlikely.
 *
 * <p><strong>A customer of its own, every time.</strong> One database file serves the whole run
 * and the seeded pair's points, claims and limits are written by the rest of it, so a test that
 * leaned on Anke would be asserting about her history as well as its own — and a limit or a
 * balance is exactly the sort of figure that goes wrong when it does. Each test opens somebody
 * with nothing behind them and earns exactly what it is about to spend, which is what makes
 * "they cannot afford another one" a fact this class can arrange rather than hope for.
 *
 * <p><strong>Nothing here reads a column.</strong> That the stock came back is asserted as "the
 * card says one more is left", because that is the only place the figure exists: what is left is
 * a subtraction done on every read rather than a number anybody keeps, and a cancellation returns
 * stock by the claim ceasing to count. A test that read a stored figure would pass just as well
 * against a design that decremented one.
 *
 * <p>Every offer written here is withdrawn afterwards, so that the test pinning the customer's
 * catalogue to exactly four entries at four prices goes on passing.
 */
class AVoucherCancelledAndThePointsThatComeBackApiTest extends ApiIntegrationTest {

    /** What a person running the scheme actually types into the box. */
    private static final String THE_REASON_GIVEN = "Claimed twice by mistake at the Leuven desk.";

    /** So that no two customers this class opens can collide, here or in a parallel run. */
    private static final AtomicLong OPENED = new AtomicLong();

    private SeededAccounts seeded;

    private OffersToCancelClaimsFromThisTestWrites offers;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
        offers = new OffersToCancelClaimsFromThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * The whole of the ticket in one press: the voucher is revoked, the reason is kept, and the
     * points are back.
     *
     * <p>The balance is read after the claim rather than before it, so that what is asserted is
     * what the cancellation did rather than what the claim and the cancellation did between them.
     */
    @Test
    void cancelling_a_voucher_revokes_it_and_hands_the_points_back() {
        String customer = aCustomerOfItsOwn();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Something claimed by mistake", 20, null);
        earn(customer, 20);
        ClaimedRewardView claimed = claim(customer, code);
        assertThat(pointsBalanceOf(customer)).as("every point they had went on the claim")
                .isEqualTo(0);

        VoucherAtTheCounterView cancelled = offers.cancels(claimed.voucherCode(), THE_REASON_GIVEN);

        assertThat(cancelled.state()).isEqualTo("CANCELLED");
        assertThat(cancelled.good())
                .as("a revoked voucher is not one a counter should hand anything over for")
                .isFalse();
        assertThat(cancelled.cancelledBecause()).isEqualTo(THE_REASON_GIVEN);
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThat(pointsBalanceOf(customer))
                .as("exactly what the claim cost, back in the customer's pot")
                .isEqualTo(20);
    }

    /**
     * A reason is required, and a blank one is not a reason.
     *
     * <p>"A mistake can be undone" is only half of what somebody running the scheme is promised;
     * the other half is "and explained", and a voucher revoked with nothing attached is a code
     * that simply stopped working — which is exactly the position the customer and the till were
     * in before any of this existed. A bad request rather than a conflict, because nothing is in
     * an unexpected state: a box was not filled in.
     *
     * <p>The voucher and the balance are both asserted afterwards, because a refusal that had
     * already revoked the claim, or already credited the points, would be worse than no rule.
     */
    @Test
    void a_voucher_cannot_be_cancelled_without_a_reason() {
        String customer = aCustomerOfItsOwn();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Still good", 5, null);
        earn(customer, 5);
        ClaimedRewardView claimed = claim(customer, code);

        ResponseEntity<JsonNode> refused =
                offers.triesToCancel(claimed.voucherCode(), Map.of("reason", "   "));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("why");
        assertThat(offers.atACounter(claimed.voucherCode()).state())
                .as("a refused cancellation leaves the voucher exactly as it was")
                .isEqualTo("ISSUED");
        assertThat(pointsBalanceOf(customer))
                .as("and puts nothing into the ledger")
                .isEqualTo(0);
    }

    /**
     * The customer's own list is where they find out, so it carries all three things they need:
     * that it was cancelled, why, and what came back.
     *
     * <p>What came back is {@code pointsSpent}, deliberately and not a field of its own. A
     * cancellation refunds exactly what the claim cost, so a second figure beside it could only
     * ever agree or be wrong — and the one already there is the figure the customer recognises
     * from the day they claimed.
     */
    @Test
    void the_customers_list_shows_the_cancellation_its_reason_and_what_came_back() {
        String customer = aCustomerOfItsOwn();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "A mistake", 15, null);
        earn(customer, 15);
        ClaimedRewardView claimed = claim(customer, code);

        offers.cancels(claimed.voucherCode(), THE_REASON_GIVEN);

        ClaimedRewardView onTheirList = theClaimFor(customer, claimed.voucherCode());
        assertThat(onTheirList.state()).isEqualTo("CANCELLED");
        assertThat(onTheirList.cancelledBecause()).isEqualTo(THE_REASON_GIVEN);
        assertThat(onTheirList.cancelledAt()).isNotNull();
        assertThat(onTheirList.pointsSpent())
                .as("what it cost is what came back, and what it cost never changes")
                .isEqualTo(15);
        assertThat(onTheirList.usedAt()).isNull();
        assertThat(onTheirList.usedByCounter()).isNull();
    }

    /**
     * The refunded points are ordinary points: spendable on anything in the catalogue.
     *
     * <p>Asserted by spending them rather than by reading a balance, and the refusal before the
     * cancellation is the half that makes it worth asserting at all — the customer is genuinely
     * unable to claim the second thing until the first is undone, so the claim that follows is
     * paid for by the refund and by nothing else. A refund credited somewhere unspendable would
     * leave that second claim refused exactly as it is here.
     */
    @Test
    void the_refunded_points_can_be_spent_on_anything_else() {
        String customer = aCustomerOfItsOwn();
        String wrong = offers.aCodeNobodyHasUsed();
        String right = offers.aCodeNobodyHasUsed();
        offers.onSale(wrong, "Claimed by mistake", 30, null);
        offers.onSale(right, "What they actually wanted", 30, null);
        earn(customer, 30);
        ClaimedRewardView wrongOne = claim(customer, wrong);
        assertThat(tryToClaim(customer, right).getStatusCode())
                .as("nothing left to spend until the mistake is undone")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        offers.cancels(wrongOne.voucherCode(), THE_REASON_GIVEN);

        ClaimedRewardView theRightOne = claim(customer, right);
        assertThat(theRightOne.pointsSpent()).isEqualTo(30);
        assertThat(theRightOne.voucherCode()).isNotEqualTo(wrongOne.voucherCode());
        assertThat(pointsBalanceOf(customer))
                .as("the refund was spent, so it was real")
                .isEqualTo(0);
    }

    /**
     * <strong>Cancelling returns the stock, so a sold-out offer becomes claimable again.</strong>
     *
     * <p>The whole sentence in sequence: the last one goes, the card locks, the claim is refused,
     * the cancellation happens, the card unlocks — and then it can actually be claimed. Splitting
     * it would let a bug that unlocked the card without making the claim possible pass half of it.
     *
     * <p>Nothing restocks here. The administrator's own figure is still one, and it says one
     * afterwards; what changed is the subtraction, because the claim stopped counting.
     */
    @Test
    void cancelling_the_last_one_puts_it_back_in_the_window() {
        String customer = aCustomerOfItsOwn();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "The only one", 5, 1);
        earn(customer, 10);
        ClaimedRewardView theLastOne = claim(customer, code);

        RewardForACustomerView soldOut = readingOf(customer, code);
        assertThat(soldOut.whatIsLeft()).isEqualTo(0);
        assertThat(soldOut.claimable()).isFalse();
        assertThat(soldOut.lockedBecause()).isEqualTo("NOTHING_LEFT");
        assertThat(tryToClaim(customer, code).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        offers.cancels(theLastOne.voucherCode(), THE_REASON_GIVEN);

        RewardForACustomerView backInTheWindow = readingOf(customer, code);
        assertThat(backInTheWindow.whatIsLeft())
                .as("the claim stopped counting, so the subtraction says one again")
                .isEqualTo(1);
        assertThat(backInTheWindow.claimable()).isTrue();
        assertThat(backInTheWindow.lockedBecause()).isNull();
        assertThat(offers.theOfferAsItStands(code).stock())
                .as("nothing restocked anything; the administrator's figure never moved")
                .isEqualTo(1);
        assertThat(claim(customer, code).pointsSpent())
                .as("and it is genuinely claimable rather than merely unlocked")
                .isEqualTo(5);
    }

    /**
     * A cancelled claim does not count against a purchase limit either, and for the same reason:
     * an allowance spent on a claim nobody ended up making would be the customer paying for
     * somebody else's mistake twice.
     *
     * <p>A lifetime cap of one, which is the tightest statement of it — had the claim gone on
     * counting, there would be no second claim to make for the rest of the customer's life.
     */
    @Test
    void a_cancelled_claim_does_not_count_against_a_limit() {
        String customer = aCustomerOfItsOwn();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "One each, ever", 5, null);
        offers.changes(code, Map.of("maxPerCustomer", 1));
        earn(customer, 10);
        ClaimedRewardView theirOne = claim(customer, code);

        RewardForACustomerView atTheirLimit = readingOf(customer, code);
        assertThat(atTheirLimit.claimable()).isFalse();
        assertThat(atTheirLimit.lockedBecause()).isEqualTo("YOU_HAVE_HAD_YOUR_LIMIT");
        assertThat(atTheirLimit.howManyYouHaveHad()).isEqualTo(1);

        offers.cancels(theirOne.voucherCode(), THE_REASON_GIVEN);

        RewardForACustomerView afterwards = readingOf(customer, code);
        assertThat(afterwards.howManyYouHaveHad())
                .as("a claim that was undone is not one they have had")
                .isEqualTo(0);
        assertThat(afterwards.claimable()).isTrue();
        assertThat(afterwards.lockedBecause()).isNull();
        assertThat(claim(customer, code).pointsSpent())
                .as("and the allowance is genuinely theirs again")
                .isEqualTo(5);
    }

    /**
     * A cancelled voucher cannot be handed over, and the refusal says which of the three it is —
     * in the words whoever cancelled it typed.
     *
     * <p>This is the hardest conversation the counter surface has: the code is real, the customer
     * believes it is good, and nothing they did caused it. A till told only "not good" would have
     * to guess between three quite different things to say and would probably pick the one that
     * blames the person in front of them. A conflict rather than a not-found, because the code is
     * real and the reading endpoint still answers for it.
     */
    @Test
    void a_cancelled_voucher_cannot_be_handed_over_and_the_refusal_says_it_was_cancelled() {
        String customer = aCustomerOfItsOwn();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Revoked", 5, null);
        earn(customer, 5);
        ClaimedRewardView claimed = claim(customer, code);
        offers.cancels(claimed.voucherCode(), THE_REASON_GIVEN);

        ResponseEntity<JsonNode> refused =
                offers.triesToHandOver(claimed.voucherCode(), "Leuven Bondgenotenlaan, till 2");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("cancelled");
        assertThat(reasonGivenBy(refused))
                .as("the words somebody typed, so the till can say something true out loud")
                .contains(THE_REASON_GIVEN);
        VoucherAtTheCounterView afterwards = offers.atACounter(claimed.voucherCode());
        assertThat(afterwards.state())
                .as("a refusal changes nothing: a cancelled voucher is cancelled, not half-used")
                .isEqualTo("CANCELLED");
        assertThat(afterwards.usedAt()).isNull();
        assertThat(afterwards.usedByCounter()).isNull();
        assertThat(afterwards.cancelledBecause()).isEqualTo(THE_REASON_GIVEN);
    }

    /**
     * An already-used voucher cannot be cancelled, and nothing is refunded.
     *
     * <p>The customer has had the thing. Handing the points back as well would be paying them
     * twice for one claim, and the balance is the load-bearing assertion here: a refusal that had
     * already credited would be worse than no rule at all.
     */
    @Test
    void an_already_used_voucher_cannot_be_cancelled_and_nothing_is_refunded() {
        String customer = aCustomerOfItsOwn();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Handed over already", 5, null);
        earn(customer, 5);
        ClaimedRewardView claimed = claim(customer, code);
        assertThat(offers.triesToHandOver(claimed.voucherCode(), "Gent Korenmarkt").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> refused =
                offers.triesToCancel(claimed.voucherCode(), Map.of("reason", THE_REASON_GIVEN));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("already used");
        assertThat(pointsBalanceOf(customer))
                .as("the customer has had the thing; the points are not coming back as well")
                .isEqualTo(0);
        assertThat(offers.atACounter(claimed.voucherCode()).state()).isEqualTo("USED");
    }

    /**
     * And a voucher cannot be cancelled twice, which is what terminal means — and what stops one
     * claim being refunded over and over.
     */
    @Test
    void a_voucher_that_has_already_been_cancelled_cannot_be_cancelled_again() {
        String customer = aCustomerOfItsOwn();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Revoked once", 5, null);
        earn(customer, 5);
        ClaimedRewardView claimed = claim(customer, code);
        offers.cancels(claimed.voucherCode(), THE_REASON_GIVEN);

        ResponseEntity<JsonNode> refused = offers.triesToCancel(claimed.voucherCode(),
                Map.of("reason", "Cancelled again by somebody who did not look"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("cancelled");
        assertThat(pointsBalanceOf(customer))
                .as("one claim, one refund, however many times somebody presses")
                .isEqualTo(5);
        assertThat(theClaimFor(customer, claimed.voucherCode()).cancelledBecause())
                .as("and the reason on the row is still the one that was actually given")
                .isEqualTo(THE_REASON_GIVEN);
    }

    /** A code nobody has ever held is a 404, because the address names nothing. */
    @Test
    void a_voucher_code_nobody_has_ever_held_cannot_be_cancelled() {
        ResponseEntity<JsonNode> refused =
                offers.triesToCancel("SS-CNL-ZZZZZZ", Map.of("reason", THE_REASON_GIVEN));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(refused)).contains("SS-CNL-ZZZZZZ");
    }

    /**
     * An administrator can lower the stock to what is genuinely out there afterwards, and the
     * figure they are refused against does not include the claim they themselves revoked.
     *
     * <p>Not a criterion of the ticket, and here because it is the one other place in the
     * application that counts what has gone out of an offer — so it is the one other place a
     * cancellation could have been forgotten. Two claims made and one revoked is one standing, so
     * one is the lowest the stock can honestly be; being told "2 have already been claimed" would
     * be the screen refusing a correction on the strength of the scheme's own mistake.
     */
    @Test
    void the_stock_can_be_lowered_to_what_is_genuinely_out_after_a_cancellation() {
        String customer = aCustomerOfItsOwn();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Two went out and one came back", 5, 4);
        earn(customer, 10);
        ClaimedRewardView kept = claim(customer, code);
        ClaimedRewardView revoked = claim(customer, code);
        offers.cancels(revoked.voucherCode(), THE_REASON_GIVEN);

        assertThat(offers.changes(code, Map.of("stock", 1)).stock())
                .as("one claim is standing, so one is a figure that is not a lie")
                .isEqualTo(1);
        ResponseEntity<JsonNode> refused = offers.triesToChange(code, Map.of("stock", 0));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("and the figure quoted back is the one still out there, not the one revoked")
                .contains("1 of");
        assertThat(kept.voucherCode()).isNotEqualTo(revoked.voucherCode());
    }

    /**
     * <strong>Nothing about the four the application has always offered changes.</strong> A
     * voucher from a seeded offer is cancelled exactly as any other is — there is nothing special
     * about it — and the offer goes on saying nothing at all about stock, which is what makes a
     * cancellation harmless to something that never runs out.
     */
    @Test
    void a_voucher_from_one_of_the_four_seeded_offers_cancels_like_any_other() {
        String customer = aCustomerOfItsOwn();
        earn(customer, 10);
        ClaimedRewardView claimed = claim(customer, "CHARITY_DONATION");

        VoucherAtTheCounterView cancelled =
                offers.cancels(claimed.voucherCode(), "Donated in error.");

        assertThat(cancelled.state()).isEqualTo("CANCELLED");
        assertThat(pointsBalanceOf(customer)).isEqualTo(10);
        assertThat(readingOf(customer, "CHARITY_DONATION").whatIsLeft())
                .as("an offer nobody gave a stock figure to still says nothing about stock")
                .isNull();
    }

    /**
     * A customer this test opened, with nothing earned, nothing claimed and no limit part-used.
     *
     * <p>Its own customer every time, and the class javadoc says why at length: a refund is a
     * delta on a balance and a limit is a count over a whole history, and the seeded pair's
     * history is written by the rest of the run. The name carries a number so that no two callers
     * can collide and so that a failing run says which account it is talking about.
     */
    private String aCustomerOfItsOwn() {
        long distinct = OPENED.incrementAndGet();
        String name = "cancelled claim " + distinct;
        ResponseEntity<JsonNode> opened = http.postForEntity("/api/customers",
                Map.of("name", name, "contactDetails",
                        "cancelled.claim." + distinct + "@example.be"),
                JsonNode.class);
        assertThat(opened.getStatusCode())
                .describedAs("opening a customer of this test's own: " + opened.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return name;
    }

    private RewardForACustomerView readingOf(String customer, String code) {
        return offers.asReadBy(seeded.customerIdOf(customer), code);
    }

    /** Claimed by the customer, out of the one pot of points every account of theirs earns into. */
    private ClaimedRewardView claim(String customer, String code) {
        ResponseEntity<ClaimedRewardView> claimed = http.postForEntity(
                "/api/customers/{id}/redemptions", Map.of("reward", code),
                ClaimedRewardView.class, seeded.customerIdOf(customer));
        assertThat(claimed.getStatusCode()).describedAs("claiming \"" + code + "\"")
                .isEqualTo(HttpStatus.CREATED);
        return claimed.getBody();
    }

    /** The same claim read as unshaped JSON, for the ones this ticket refuses. */
    private ResponseEntity<JsonNode> tryToClaim(String customer, String code) {
        return http.postForEntity("/api/customers/{id}/redemptions", Map.of("reward", code),
                JsonNode.class, seeded.customerIdOf(customer));
    }

    /** One claim off the customer's own list, which is where a cancellation has to show up. */
    private ClaimedRewardView theClaimFor(String customer, String voucherCode) {
        ResponseEntity<ClaimedRewardView[]> read = http.getForEntity(
                "/api/customers/{id}/redemptions", ClaimedRewardView[].class,
                seeded.customerIdOf(customer));
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return List.of(read.getBody()).stream()
                .filter(one -> one.voucherCode().equals(voucherCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the customer's own list has no voucher " + voucherCode + " on it"));
    }

    /**
     * Earns exactly the points this test is about to spend, at a point per whole euro. A test
     * that needs points is the test that earns them — leaning on what an earlier test left in an
     * account is how a failure lands in a class that never mentioned this one.
     */
    private void earn(String customer, long points) {
        ResponseEntity<DepositView> deposit = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", points + ".00", "fromCurrentAccountId",
                        seeded.currentAccountOf(customer)),
                DepositView.class, seeded.savingsAccountOf(customer));
        assertThat(deposit.getStatusCode())
                .describedAs("a deposit this test needs in order to have points to spend")
                .isEqualTo(HttpStatus.CREATED);
    }

    /** What the customer has to spend, which is the figure a refund has to move. */
    private long pointsBalanceOf(String customer) {
        return seeded.pointsBalanceOf(customer);
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        assertThat(refusal.getBody()).as("a problem document with a reason in it").isNotNull();
        return refusal.getBody().path("detail").asText();
    }
}
