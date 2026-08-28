package io.dataroots.savingstreak.pointsspent;

import java.util.Map;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.RewardView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Spending points: the catalogue, a claim, the voucher it issues, and what the points balance does
 * about it.
 *
 * <p>Every test asserts on what its own requests changed rather than on absolute balances, because
 * the run shares one database and earlier tests have left points in these accounts. What a test
 * spends, it earns first: leaning on somebody else's leftovers is how a test ends up depending on
 * another test class it never names.
 *
 * <p>Anke's accounts are the ones spent from. Bram's savings account is the run's untouched one —
 * two other tests assert it has never been paid into — and claiming a reward out of it is only used
 * where the point is that it cannot afford one.
 */
class ClaimingRewardsApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * The catalogue the requirements fix: four rewards at four prices, the same for everybody.
     *
     * <p>The prices are asserted exactly. They are the one thing in this application a customer is
     * asked to save towards, and a price that changed by accident would be a promise quietly broken;
     * a price that changes on purpose is a decision, and a decision should have to edit a test.
     */
    @Test
    void the_catalogue_says_what_points_can_be_spent_on_and_what_each_costs() {
        ResponseEntity<RewardView[]> response = http.getForEntity("/api/rewards", RewardView[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(RewardView::code, RewardView::costInPoints)
                .containsExactly(
                        tuple("CHARITY_DONATION", 10L),
                        tuple("SNACK_VOUCHER", 40L),
                        tuple("CINEMA_TICKET", 100L),
                        tuple("FAMILY_CINEMA_PACK", 180L));
        // Words for a person to read, wherever the page happens to put them.
        assertThat(response.getBody()).allSatisfy(reward -> {
            assertThat(reward.title()).isNotBlank();
            assertThat(reward.description()).isNotBlank();
        });
    }

    /**
     * The whole point of the slice: points go down by what the reward costs, and the money does not
     * move. Saving is not spending — the euros stay in the savings account, and only the reward the
     * saving earned is taken.
     */
    @Test
    void claiming_a_reward_spends_its_cost_and_leaves_the_money_alone() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        earnEnoughFor(savingsAccount, 100);
        BalancesView before = balancesOf(savingsAccount);

        ResponseEntity<ClaimedRewardView> response = claim(savingsAccount, "CINEMA_TICKET");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance() - 100);
        assertThat(after.moneyBalance()).isEqualByComparingTo(before.moneyBalance());
    }

    /**
     * A claim hands something over, and the voucher is that something. Redemption is instant and
     * final, so a claim that answered without one would leave the customer paid up and empty-handed.
     */
    @Test
    void a_claim_issues_a_voucher_for_the_reward_it_names() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        earnEnoughFor(savingsAccount, 40);

        ClaimedRewardView claimed = claim(savingsAccount, "SNACK_VOUCHER").getBody();

        assertThat(claimed.code()).isEqualTo("SNACK_VOUCHER");
        assertThat(claimed.title()).isNotBlank();
        assertThat(claimed.pointsSpent()).isEqualTo(40);
        assertThat(claimed.voucherCode()).matches("SS-SNK-[0-9A-Z]{6}");
        assertThat(claimed.claimedAt()).isNotNull();
    }

    /** Two claims for the same reward are two vouchers, or one of them is somebody else's. */
    @Test
    void every_claim_gets_its_own_voucher() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        earnEnoughFor(savingsAccount, 20);

        ClaimedRewardView first = claim(savingsAccount, "CHARITY_DONATION").getBody();
        ClaimedRewardView second = claim(savingsAccount, "CHARITY_DONATION").getBody();

        assertThat(second.voucherCode()).isNotEqualTo(first.voucherCode());
        assertThat(second.id()).isNotEqualTo(first.id());
    }

    /**
     * Every reward in the catalogue can actually be had. A price nobody can pay and a name the
     * application does not recognise look the same from a screen, and this is the difference.
     */
    @ParameterizedTest
    @CsvSource({
            "CHARITY_DONATION, 10, SS-DON",
            "SNACK_VOUCHER, 40, SS-SNK",
            "CINEMA_TICKET, 100, SS-CIN",
            "FAMILY_CINEMA_PACK, 180, SS-FAM"
    })
    void each_reward_in_the_catalogue_can_be_claimed(String code, long cost, String voucherPrefix) {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        earnEnoughFor(savingsAccount, cost);
        BalancesView before = balancesOf(savingsAccount);

        ClaimedRewardView claimed = claim(savingsAccount, code).getBody();

        assertThat(claimed.pointsSpent()).isEqualTo(cost);
        assertThat(claimed.voucherCode()).startsWith(voucherPrefix + "-");
        assertThat(balancesOf(savingsAccount).pointsBalance()).isEqualTo(before.pointsBalance() - cost);
    }

    /**
     * The claims behind the points that have left, newest first, so that the two lists on the page
     * account for the balance between them: the deposits say what came in, these say what went out.
     */
    @Test
    void claims_are_listed_newest_first_with_what_each_one_cost() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        earnEnoughFor(savingsAccount, 50);

        ClaimedRewardView first = claim(savingsAccount, "CHARITY_DONATION").getBody();
        ClaimedRewardView second = claim(savingsAccount, "SNACK_VOUCHER").getBody();

        ResponseEntity<ClaimedRewardView[]> response = claimsAgainst(savingsAccount);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(ClaimedRewardView::id)
                .startsWith(second.id(), first.id());
        assertThat(response.getBody()).anySatisfy(listed -> {
            assertThat(listed.id()).isEqualTo(second.id());
            assertThat(listed.pointsSpent()).isEqualTo(40);
            assertThat(listed.voucherCode()).isEqualTo(second.voucherCode());
            assertThat(listed.claimedAt()).isEqualTo(second.claimedAt());
        });
    }

    /**
     * Exactly enough is enough. The boundary is worth its own test because it is the one a customer
     * arrives at deliberately: they saved for the reward, and the last euro should buy it.
     *
     * <p>Asserted as a round trip rather than against a figure — deposit what the reward costs, claim
     * it, and the balance is back where it started — because the account already holds points from
     * earlier tests and its absolute balance is not this test's to know.
     */
    @Test
    void a_reward_can_be_claimed_with_exactly_its_cost() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        BalancesView before = balancesOf(savingsAccount);
        deposit(savingsAccount, "40.00");

        claim(savingsAccount, "SNACK_VOUCHER");

        assertThat(balancesOf(savingsAccount).pointsBalance()).isEqualTo(before.pointsBalance());
    }

    /**
     * A claim draws on several deposits at once, taking part of one of them: the points a savings
     * account holds are one pot, however many payments filled it.
     *
     * <p>Three deposits worth 12 points between them, and a reward costing 10, so the claim has to
     * empty two batches and take two points out of a third. Which batch goes first has no consequence
     * anybody can see from out here — a point is a point and none of them expire yet — so what is
     * asserted is that the right number left and the remainder is still there. The order itself is
     * settled in the ledger, against the slice that expires the oldest points and will make it
     * visible.
     */
    @Test
    void a_claim_can_be_paid_for_out_of_several_deposits() {
        long savingsAccount = seeded.otherSavingsAccountOf(ANKE);
        BalancesView before = balancesOf(savingsAccount);
        deposit(savingsAccount, "3.00");
        deposit(savingsAccount, "4.00");
        deposit(savingsAccount, "5.00");

        claim(savingsAccount, "CHARITY_DONATION");

        assertThat(balancesOf(savingsAccount).pointsBalance()).isEqualTo(before.pointsBalance() + 2);
    }

    /**
     * Points spent in one savings account come out of that account. Each one saves towards its own
     * goal, and a reward claimed against one goal must not quietly be paid for by another.
     */
    @Test
    void a_claim_against_one_savings_account_leaves_the_others_alone() {
        long untouched = seeded.otherSavingsAccountOf(ANKE);
        long spendingFrom = seeded.savingsAccountOf(ANKE);
        earnEnoughFor(spendingFrom, 100);
        BalancesView before = balancesOf(untouched);

        claim(spendingFrom, "CINEMA_TICKET");

        BalancesView after = balancesOf(untouched);
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance());
        assertThat(after.moneyBalance()).isEqualByComparingTo(before.moneyBalance());
    }

    private BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    private ResponseEntity<ClaimedRewardView[]> claimsAgainst(long savingsAccountId) {
        return http.getForEntity(
                "/api/savings-accounts/{id}/redemptions", ClaimedRewardView[].class, savingsAccountId);
    }

    private ResponseEntity<ClaimedRewardView> claim(long savingsAccountId, String reward) {
        return http.postForEntity(
                "/api/savings-accounts/{id}/redemptions",
                Map.of("reward", reward),
                ClaimedRewardView.class,
                savingsAccountId);
    }

    /**
     * Earns the account at least the points about to be spent out of it, at a point per euro.
     *
     * <p>Every test that spends calls this first. They used to draw on points that earlier tests in
     * the run happened to have left lying in this account — which held until the test leaving the
     * most of them was rewritten, and then four tests failed somewhere they had never mentioned.
     * A test that needs a hundred points is the test that earns them.
     */
    private void earnEnoughFor(long savingsAccountId, long points) {
        deposit(savingsAccountId, points + ".00");
    }

    /** Points to spend have to be earned first, and a deposit is the only way to earn any. */
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
