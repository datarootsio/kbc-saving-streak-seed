package io.dataroots.savingstreak.anofferthatcanrunout;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An offer somebody can run out of: how many exist, how many are left, the last one going, and the
 * restock that brings it back.
 *
 * <p><strong>What is left is never asserted as a stored number, because there is no stored
 * number.</strong> Every assertion below is about what the API says after something happened —
 * three left, then two, then none — and that is the only shape a test of a derived figure can
 * honestly take. A test that read a column would pass just as well against the design this ticket
 * refuses: a second figure, decremented by hand, that drifts the first time anything else moves
 * it.
 *
 * <p><strong>The card and the refusal are asserted together wherever both exist.</strong> The
 * whole point of scarcity being a lock rather than only a rule is that a customer is told before
 * they commit, and the two have to be the same sentence — the property the previous slice
 * established and this one inherits.
 *
 * <p><strong>Every test earns what it is about to spend and asserts on the delta.</strong> One
 * database serves the run; leaning on what another test left in the account is how a failure lands
 * in a class that never mentioned this one. And every offer written here is withdrawn afterwards,
 * so that the test pinning the customer's catalogue to four entries at four prices goes on
 * passing.
 *
 * <p>Anke is the customer, because she is the one the claiming tests already spend from.
 */
class AnOfferThatCanRunOutApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    private OffersWithStockThisTestWrites offers;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
        offers = new OffersWithStockThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * A scarce offer says how many are left, and the figure goes down by one each time somebody
     * takes one.
     *
     * <p>Asserted across two claims rather than one, because a derived figure and a decremented
     * one both look right after the first claim; what tells them apart is that this one is the
     * subtraction being done again, from the same total, against a claim table that grew.
     */
    @Test
    void a_scarce_offer_says_how_many_are_left_and_the_figure_falls_as_they_go() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Three hampers", 5, 3);
        earn(10);

        assertThat(readingOf(code).whatIsLeft())
                .as("nothing has been claimed, so all three of them are left")
                .isEqualTo(3);

        claim(code);
        assertThat(readingOf(code).whatIsLeft()).isEqualTo(2);

        claim(code);
        assertThat(readingOf(code).whatIsLeft()).isEqualTo(1);
        assertThat(readingOf(code).claimable())
                .as("one left is still one somebody can have")
                .isTrue();
    }

    /**
     * An offer nobody gave a stock figure to says nothing at all about stock — which is the safety
     * argument of the whole ticket.
     *
     * <p>Asserted against the four the application has always had rather than against something
     * this test wrote, because they are the ones a customer knew yesterday. Null and not nought:
     * those are opposite facts, and an unlimited offer reported as "none left" would grey the
     * entire catalogue.
     */
    @Test
    void the_four_offers_that_have_always_been_there_say_nothing_about_stock_and_stay_claimable() {
        long customerId = seeded.customerIdOf(ANKE);

        assertThat(offers.theCatalogueAsReadBy(customerId))
                .filteredOn(entry -> theFourThatHaveAlwaysBeenThere().contains(entry.code()))
                .hasSize(4)
                .allSatisfy(entry -> {
                    assertThat(entry.whatIsLeft())
                            .as("no stock set is unlimited, which is the absence of the rule and "
                                    + "not nought of the thing")
                            .isNull();
                    assertThat(entry.claimable()).isTrue();
                    assertThat(entry.lockedBecause()).isNull();
                });
        assertThat(offers.theOfferAsItStands("CINEMA_TICKET").stock())
                .as("and the back office reads back the empty box somebody never filled in")
                .isNull();
    }

    /**
     * The last one can be claimed, and the claim after it is refused with nothing taken.
     *
     * <p>The two halves of the ticket's central sentence in one test, deliberately: "claiming the
     * last one succeeds" and "the next one is refused" are one fact about one offer told in
     * sequence, and splitting them would let a bug that refused the last one as well pass one of
     * the two.
     *
     * <p>The balance is the load-bearing assertion. A refusal that had already spent the points
     * would be worse than no rule at all, and the order the module checks in — stock immediately
     * before the spend — is what makes that impossible rather than unlikely.
     */
    @Test
    void the_last_one_can_be_claimed_and_the_next_claim_is_refused_with_nothing_spent() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "The only one", 5, 1);
        earn(10);

        ClaimedRewardView theLastOne = claim(code);
        assertThat(theLastOne.code()).isEqualTo(code);

        long afterTheLastOne = pointsBalance();
        ResponseEntity<JsonNode> refused = tryToClaim(code);

        assertThat(refused.getStatusCode())
                .as("the offer is real and the request is well formed; what says no is that "
                        + "somebody already had the last one, which is what a conflict means")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(pointsBalance())
                .as("a refusal takes nothing, and stock is asked before the points precisely so "
                        + "that this cannot go the other way")
                .isEqualTo(afterTheLastOne);
        assertThat(claimsOf(ANKE).stream().filter(claim -> code.equals(claim.code())).count())
                .as("exactly one of it went out, whatever anybody pressed afterwards")
                .isEqualTo(1);
    }

    /**
     * A sold-out offer is shown, locked, saying it has sold out — and the refusal says the same
     * words.
     *
     * <p>Shown and locked asserted together, on purpose. "It says it has sold out" would pass just
     * as well against a catalogue that dropped it entirely, and dropping it is exactly what this
     * feature refuses to do: a customer who had been saving for something deserves to know what
     * became of it rather than watch it vanish.
     */
    @Test
    void a_sold_out_offer_is_shown_locked_as_sold_out_and_refused_in_the_same_words() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Sells out", 5, 1);
        earn(10);
        claim(code);

        RewardForACustomerView reading = readingOf(code);
        ResponseEntity<JsonNode> refused = tryToClaim(code);

        assertThat(reading.claimable()).isFalse();
        assertThat(reading.lockedBecause()).isEqualTo("NOTHING_LEFT");
        assertThat(reading.whatIsLeft())
                .as("nought left rather than nothing said: the offer is scarce and it is empty")
                .isEqualTo(0);
        assertThat(reading.whyItIsLocked()).contains("Sells out");
        assertThat(reasonGivenBy(refused))
                .as("the card and the refusal are the same fact said at two moments, so they are "
                        + "the same sentence")
                .isEqualTo(reading.whyItIsLocked());
    }

    /**
     * Raising the stock puts a sold-out offer back in the window on the very next read.
     *
     * <p>No job run, no flag cleared and nothing else touched in between, which is the whole
     * payoff of deriving what is left rather than storing it. Asserted by claiming afterwards as
     * well as by reading, because a card that unlocked while the claim still refused would be the
     * worse of the two possible bugs.
     */
    @Test
    void raising_the_stock_puts_a_sold_out_offer_back_in_the_window_immediately() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Back in stock", 5, 1);
        earn(15);
        claim(code);
        assertThat(readingOf(code).claimable()).isFalse();

        OfferView restocked = offers.changes(code, Map.of("stock", 3));

        assertThat(restocked.stock())
                .as("the back office reads back the total somebody set, not how many are left")
                .isEqualTo(3);
        RewardForACustomerView reading = readingOf(code);
        assertThat(reading.claimable()).isTrue();
        assertThat(reading.lockedBecause()).isNull();
        assertThat(reading.whyItIsLocked()).isNull();
        assertThat(reading.whatIsLeft())
                .as("three exist and one has gone, so two are left — the subtraction done again "
                        + "rather than a counter somebody remembered to put back")
                .isEqualTo(2);
        assertThat(claim(code).code()).isEqualTo(code);
    }

    /**
     * Setting the stock below what has already gone out is refused, quoting how many that is.
     *
     * <p>The figure has to be in the sentence, because it is the whole of what the administrator
     * did not know: they typed a perfectly good number and the application is telling them the
     * smallest one they are allowed. Refused rather than clamped, because a stock of one against
     * two claims would read as sold out and look exactly like an offer that had simply run out,
     * when it is in fact a catalogue that has issued more of something than it says exists.
     */
    @Test
    void setting_the_stock_below_what_has_already_gone_out_is_refused_quoting_how_many() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Two have gone", 5, 5);
        earn(15);
        claim(code);
        claim(code);

        ResponseEntity<JsonNode> refused = offers.triesToChange(code, Map.of("stock", 1));

        assertThat(refused.getStatusCode())
                .as("nothing about the form is malformed; what says no is that two of them are "
                        + "already out there, which is a fact about the application's state")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("the sentence quotes how many have gone, because that is the smallest stock "
                        + "they are allowed to set")
                .contains("2");
        assertThat(offers.theOfferAsItStands(code).stock())
                .as("and the refusal changed nothing")
                .isEqualTo(5);
    }

    /**
     * Setting it to exactly what has gone out is allowed, and the offer reads as sold out.
     *
     * <p>The fencepost beside the rule above, and the reason the comparison is strictly below.
     * "There are two of these and both have gone" is a perfectly honest thing to write down, and
     * it is how somebody closes a line off without withdrawing the offer.
     */
    @Test
    void setting_the_stock_to_exactly_what_has_gone_out_is_allowed_and_reads_as_sold_out() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Exactly what went", 5, 5);
        earn(15);
        claim(code);
        claim(code);

        OfferView closedOff = offers.changes(code, Map.of("stock", 2));

        assertThat(closedOff.stock()).isEqualTo(2);
        RewardForACustomerView reading = readingOf(code);
        assertThat(reading.claimable()).isFalse();
        assertThat(reading.lockedBecause()).isEqualTo("NOTHING_LEFT");
        assertThat(reading.whatIsLeft()).isEqualTo(0);
    }

    /**
     * An offer written with nought in the box is sold out from the minute it is published.
     *
     * <p>Nought is a real and different answer from an empty box, and this is the test that keeps
     * them apart. "There are none of these at the moment" is something somebody can honestly write
     * down, and reading it as "no limit" — which is what an empty box means — would put an offer
     * nobody has any of onto a customer's screen as claimable.
     */
    @Test
    void an_offer_written_with_no_stock_at_all_is_sold_out_from_the_start() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "None of these yet", 5, 0);
        earn(10);

        RewardForACustomerView reading = readingOf(code);

        assertThat(reading.claimable()).isFalse();
        assertThat(reading.lockedBecause()).isEqualTo("NOTHING_LEFT");
        assertThat(reading.whatIsLeft()).isEqualTo(0);
        assertThat(tryToClaim(code).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * A stock below nought is refused where it is written, because there is no number of things
     * smaller than none.
     *
     * <p>A fact about the form rather than about the state of the application, which is why it is
     * a bad request and not the conflict the rule above gets. Left unchecked the arithmetic would
     * clamp it and report the offer as sold out, which is the right answer to a question nobody
     * asked.
     */
    @Test
    void a_stock_below_nought_is_refused_when_it_is_written() {
        Map<String, Object> impossible = new HashMap<>();
        impossible.put("code", offers.aCodeNobodyHasUsed());
        impossible.put("title", "Fewer than none");
        impossible.put("costInPoints", 5);
        impossible.put("voucherPrefix", "NEG");
        impossible.put("stock", -1);

        ResponseEntity<JsonNode> refused = offers.triesToWrite(impossible);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("-1");
    }

    /**
     * The stock is set from the administration screen and read back there, and the customer's
     * read shows what is left rather than the total.
     *
     * <p>The two figures being different is the point. An administrator is shown five because
     * five is what they typed and what they have to be able to correct; a customer is shown four
     * because four is what they can still have. One number serving both pages would be one of the
     * two pages lying.
     */
    @Test
    void the_administrator_reads_back_the_total_and_the_customer_reads_what_is_left() {
        String code = offers.aCodeNobodyHasUsed();
        OfferView written = offers.onSale(code, "Five of them", 5, 5);
        earn(10);
        claim(code);

        assertThat(written.stock()).isEqualTo(5);
        assertThat(offers.theOfferAsItStands(code).stock())
                .as("the total does not move when somebody claims; only the claims do")
                .isEqualTo(5);
        assertThat(readingOf(code).whatIsLeft()).isEqualTo(4);
    }

    /**
     * The catalogue with nobody in it says nothing about stock and is not changed by a scarce
     * offer being in it.
     *
     * <p>{@code /api/rewards} is the shape this whole feature is built not to move: four fields, a
     * price and no derivation. A scarce offer is published into it like any other — there is no
     * customer and no verdict at that address — and what must stay true is that the entries gain
     * no field for a page to start depending on.
     */
    @Test
    void the_catalogue_with_nobody_in_it_gains_no_field_when_an_offer_is_scarce() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Scarce", 5, 2);

        assertThat(offers.theCatalogueWithNobodyInIt())
                .extracting(io.dataroots.savingstreak.support.RewardView::code)
                .contains(code);
        assertThat(theCatalogueAsItIsSent())
                .as("no stock and no remaining count leaks into the catalogue that has always "
                        + "been four fields wide")
                .doesNotContain("stock")
                .doesNotContain("whatIsLeft");
    }

    /** The four the application has always offered, on which no stock is ever set. */
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
    private List<ClaimedRewardView> claimsOf(String customerName) {
        ResponseEntity<ClaimedRewardView[]> read = http.getForEntity(
                "/api/customers/{id}/redemptions", ClaimedRewardView[].class,
                seeded.customerIdOf(customerName));
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
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
