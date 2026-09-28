package io.dataroots.savingstreak.avouchermarkedusedatacounter;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import io.dataroots.savingstreak.support.VoucherAtTheCounterView;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A voucher stops being a code the application forgets about: somebody at a counter looks it up,
 * sees what it is and whether it is good, and marks it handed over once.
 *
 * <p>Driven entirely over HTTP, like everything else, and the two surfaces it uses are the ones a
 * till actually has — a code in a URL and a counter's name in a body. Nothing here knows what a
 * voucher is stored as, which is the point: the state a counter reads is the state the API sends.
 *
 * <p>Every test earns its own points and claims its own voucher rather than reaching for one an
 * earlier test left lying about. A voucher's life is a sequence of one-way doors, so a test that
 * borrowed somebody else's would be a test whose result depended on the order the run happened to
 * be in.
 *
 * <p>Anke is the customer throughout. Bram's accounts are the run's untouched ones and two other
 * tests assert that nothing has ever been paid into them, so nothing here goes near him.
 */
class AVoucherMarkedUsedAtACounterApiTest extends ApiIntegrationTest {

    /** Free text, because a counter's name is free text. Distinctive, so it can be asserted on. */
    private static final String THE_COUNTER = "Leuven Bondgenotenlaan, till 2";

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * The first thing that happens at a till: the code is typed in and the screen says what the
     * person in front of it is entitled to, whose it is, and that it is good.
     *
     * <p>{@code good} is asserted beside the state rather than instead of it, because the screen
     * draws its panel from the boolean and says the word from the state, and a pair that disagreed
     * would put a green panel over the word "used".
     */
    @Test
    void a_voucher_can_be_looked_up_by_its_code_and_says_what_it_is_and_whose_it_is() {
        ClaimedRewardView claimed = aVoucherFor("SNACK_VOUCHER", 40);

        ResponseEntity<VoucherAtTheCounterView> response = lookUp(claimed.voucherCode());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        VoucherAtTheCounterView voucher = response.getBody();
        assertThat(voucher.voucherCode()).isEqualTo(claimed.voucherCode());
        assertThat(voucher.code()).isEqualTo("SNACK_VOUCHER");
        assertThat(voucher.title()).isEqualTo(claimed.title());
        assertThat(voucher.pointsSpent()).isEqualTo(40);
        assertThat(voucher.customerId()).isEqualTo(seeded.customerIdOf(ANKE));
        assertThat(voucher.customerName()).isEqualTo(ANKE);
        assertThat(voucher.claimedAt()).isEqualTo(claimed.claimedAt());
        assertThat(voucher.state()).isEqualTo("ISSUED");
        assertThat(voucher.good()).isTrue();
        assertThat(voucher.usedAt()).isNull();
        assertThat(voucher.usedByCounter()).isNull();
    }

    /**
     * The one press the whole screen exists for. The moment and the counter are both recorded,
     * because a redemption nobody can attribute is a redemption nobody can ask about afterwards.
     */
    @Test
    void marking_a_voucher_used_records_the_moment_and_the_counter() {
        ClaimedRewardView claimed = aVoucherFor("CHARITY_DONATION", 10);

        ResponseEntity<VoucherAtTheCounterView> response = markUsed(claimed.voucherCode(), THE_COUNTER);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        VoucherAtTheCounterView used = response.getBody();
        assertThat(used.state()).isEqualTo("USED");
        assertThat(used.good()).isFalse();
        assertThat(used.usedByCounter()).isEqualTo(THE_COUNTER);
        assertThat(used.usedAt()).isNotNull();
        // Handed over at or after the moment it was claimed, which is the only ordering a test
        // that cannot name the clock's reading is entitled to assert.
        assertThat(used.usedAt()).isAfterOrEqualTo(claimed.claimedAt());
    }

    /** And the reading afterwards says the same thing, so the till can be asked again. */
    @Test
    void a_voucher_reads_as_used_ever_afterwards() {
        ClaimedRewardView claimed = aVoucherFor("CHARITY_DONATION", 10);
        markUsed(claimed.voucherCode(), THE_COUNTER);

        VoucherAtTheCounterView voucher = lookUp(claimed.voucherCode()).getBody();

        assertThat(voucher.state()).isEqualTo("USED");
        assertThat(voucher.good()).isFalse();
        assertThat(voucher.usedByCounter()).isEqualTo(THE_COUNTER);
    }

    /**
     * The whole reason a voucher has a state: the same six characters cannot buy two coffees.
     *
     * <p>The refusal quotes the day it went and the counter that took it, because the person at
     * the till has somebody in front of them who believes the code is good, and "already used"
     * with nothing after it is an accusation rather than an explanation.
     *
     * <p>A conflict rather than a bad request: the code is perfectly good and was typed correctly,
     * and telling a counter to correct it would send them hunting for a mistake nobody made.
     */
    @Test
    void marking_an_already_used_voucher_is_refused_with_the_day_it_was_used() {
        ClaimedRewardView claimed = aVoucherFor("CHARITY_DONATION", 10);
        VoucherAtTheCounterView used = markUsed(claimed.voucherCode(), THE_COUNTER).getBody();

        ResponseEntity<JsonNode> again = markUsedExpectingRefusal(claimed.voucherCode(), "Another till");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(again)).contains(THE_COUNTER);
        // The day as this application counts days, borrowed from where that zone already lives
        // rather than read off the instant in whatever zone the test machine is set to — which is
        // the same mistake a browser makes, and the reason the backend names the day at all.
        assertThat(reasonGivenBy(again)).contains(
                LocalDate.ofInstant(used.usedAt(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toString());
    }

    /** And the second attempt changed nothing: the counter that took it is still the first one. */
    @Test
    void a_refused_second_use_leaves_the_voucher_exactly_as_the_first_one_left_it() {
        ClaimedRewardView claimed = aVoucherFor("CHARITY_DONATION", 10);
        VoucherAtTheCounterView first = markUsed(claimed.voucherCode(), THE_COUNTER).getBody();

        markUsedExpectingRefusal(claimed.voucherCode(), "Another till");

        VoucherAtTheCounterView after = lookUp(claimed.voucherCode()).getBody();
        assertThat(after.usedByCounter()).isEqualTo(THE_COUNTER);
        assertThat(after.usedAt()).isEqualTo(first.usedAt());
    }

    /**
     * A code nobody has ever issued. Not found rather than a bad request, because the request was
     * perfectly well formed and the answer is that there is no such thing — and the code comes
     * back in the sentence, because a typo at a till is how this refusal actually happens.
     */
    @Test
    void an_unknown_voucher_code_is_refused_as_unknown() {
        ResponseEntity<JsonNode> looked = http.getForEntity(
                "/api/staff/vouchers/{code}", JsonNode.class, "SS-DON-ZZZZZZ");

        assertThat(looked.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(looked)).contains("SS-DON-ZZZZZZ");
    }

    /** And the same answer when somebody tries to spend one, rather than a different one. */
    @Test
    void an_unknown_voucher_code_cannot_be_marked_used_either() {
        ResponseEntity<JsonNode> response =
                markUsedExpectingRefusal("SS-CIN-ZZZZZZ", THE_COUNTER);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * A counter's name is required, because a voucher marked used by nobody is unauditable.
     *
     * <p>A bad request rather than a refusal about the voucher: the voucher is fine and the form
     * was not filled in, which are different sentences for different people. The voucher is
     * untouched afterwards, which is the half of this worth asserting — a validation that refused
     * the request and wrote the state anyway would be worse than no validation.
     */
    @Test
    void a_voucher_cannot_be_marked_used_by_nobody() {
        ClaimedRewardView claimed = aVoucherFor("CHARITY_DONATION", 10);

        ResponseEntity<JsonNode> response = markUsedExpectingRefusal(claimed.voucherCode(), "   ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(lookUp(claimed.voucherCode()).getBody().state()).isEqualTo("ISSUED");
    }

    /**
     * The customer's own page is where they find out their code is spent, so a used voucher reads
     * as used there with the day on it. Anything less invites somebody to carry a dead code to a
     * till.
     */
    @Test
    void the_customers_own_list_shows_a_used_voucher_as_used_with_the_day() {
        ClaimedRewardView claimed = aVoucherFor("CHARITY_DONATION", 10);
        VoucherAtTheCounterView used = markUsed(claimed.voucherCode(), THE_COUNTER).getBody();

        assertThat(claimsBy(ANKE))
                .filteredOn(one -> one.voucherCode().equals(claimed.voucherCode()))
                .singleElement()
                .satisfies(one -> {
                    assertThat(one.state()).isEqualTo("USED");
                    assertThat(one.usedAt()).isEqualTo(used.usedAt());
                    assertThat(one.usedByCounter()).isEqualTo(THE_COUNTER);
                });
    }

    /** And a voucher nobody has taken yet reads as issued there, with nothing hanging off it. */
    @Test
    void the_customers_own_list_shows_an_unused_voucher_as_issued() {
        ClaimedRewardView claimed = aVoucherFor("CHARITY_DONATION", 10);

        assertThat(claimsBy(ANKE))
                .filteredOn(one -> one.voucherCode().equals(claimed.voucherCode()))
                .singleElement()
                .satisfies(one -> {
                    assertThat(one.state()).isEqualTo("ISSUED");
                    assertThat(one.usedAt()).isNull();
                    assertThat(one.usedByCounter()).isNull();
                });
    }

    /**
     * Using a voucher is somebody handing over a coffee. The points were spent on the day it was
     * claimed and the euros never moved at all, so a redemption that touched either would be
     * charging twice for one thing.
     */
    @Test
    void using_a_voucher_moves_no_points_and_no_money() {
        ClaimedRewardView claimed = aVoucherFor("SNACK_VOUCHER", 40);
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        BalancesView before = balancesOf(savingsAccount);

        markUsed(claimed.voucherCode(), THE_COUNTER);

        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance());
        assertThat(after.moneyBalance()).isEqualByComparingTo(before.moneyBalance());
        assertThat(after.mostEverSaved()).isEqualByComparingTo(before.mostEverSaved());
    }

    /**
     * The code as typed at a till, which is not always the code as printed. A phone that
     * lower-cases the first character is not the customer's mistake, and the alphabet a voucher is
     * built from has no lower case in it, so nothing can be turned into a different real code.
     */
    @Test
    void a_code_typed_in_lower_case_with_spaces_around_it_still_finds_its_voucher() {
        ClaimedRewardView claimed = aVoucherFor("CHARITY_DONATION", 10);

        VoucherAtTheCounterView found =
                lookUp(" " + claimed.voucherCode().toLowerCase() + " ").getBody();

        assertThat(found.voucherCode()).isEqualTo(claimed.voucherCode());
    }

    /** Points to spend, and then the claim that turns them into the voucher under test. */
    private ClaimedRewardView aVoucherFor(String reward, long cost) {
        deposit(seeded.savingsAccountOf(ANKE), cost + ".00");
        ResponseEntity<ClaimedRewardView> claimed = http.postForEntity(
                "/api/customers/{id}/redemptions",
                Map.of("reward", reward),
                ClaimedRewardView.class,
                seeded.customerIdOf(ANKE));
        assertThat(claimed.getStatusCode())
                .describedAs("the claim this test needs a voucher out of")
                .isEqualTo(HttpStatus.CREATED);
        return claimed.getBody();
    }

    private ResponseEntity<VoucherAtTheCounterView> lookUp(String voucherCode) {
        return http.getForEntity(
                "/api/staff/vouchers/{code}", VoucherAtTheCounterView.class, voucherCode);
    }

    private ResponseEntity<VoucherAtTheCounterView> markUsed(String voucherCode, String counter) {
        return http.postForEntity(
                "/api/staff/vouchers/{code}/use",
                Map.of("counter", counter),
                VoucherAtTheCounterView.class,
                voucherCode);
    }

    /**
     * The same request read as a problem document, because a refusal's body is a reason and not a
     * voucher. Kept apart from {@link #markUsed} so that no test can quietly assert on a voucher
     * deserialised out of an error.
     */
    private ResponseEntity<JsonNode> markUsedExpectingRefusal(String voucherCode, String counter) {
        return http.postForEntity(
                "/api/staff/vouchers/{code}/use",
                Map.of("counter", counter),
                JsonNode.class,
                voucherCode);
    }

    private ClaimedRewardView[] claimsBy(String customerName) {
        return http.getForObject("/api/customers/{id}/redemptions", ClaimedRewardView[].class,
                seeded.customerIdOf(customerName));
    }

    private BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    /** The sentence the backend wrote, which is the only thing a screen ever shows about a refusal. */
    private String reasonGivenBy(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).as("a problem document with a reason in it").isNotNull();
        return response.getBody().path("detail").asText();
    }

    private void deposit(long savingsAccountId, String amount) {
        ResponseEntity<DepositView> response = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", seeded.currentAccountOf(ANKE)),
                DepositView.class,
                savingsAccountId);
        assertThat(response.getStatusCode())
                .describedAs("a deposit this test needs in order to have points to spend")
                .isEqualTo(HttpStatus.CREATED);
    }
}
