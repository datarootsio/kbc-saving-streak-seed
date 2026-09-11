package io.dataroots.savingstreak.customerpoints;

import java.util.Arrays;
import java.util.Map;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Points belong to the customer, not to the savings account the money went into: everything a person
 * saves earns into one pot, and everything they claim comes out of it.
 *
 * <p>Anke is the customer this is asked about, because she is seeded holding two savings accounts —
 * one pot filled from two places is the only way to ask whether the two add up. Bram holds one and it
 * is never paid into, which is what makes him the customer whose pot must stay empty while hers fills.
 *
 * <p>Every test asserts on what its own requests changed rather than on absolute balances: the run
 * shares one database, and what a test put there is the only thing it can honestly claim.
 */
class PointsBelongToTheCustomerApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * The whole point of the change: two accounts, one balance, and it is the sum of both.
     *
     * <p>Twenty-five into one and ten into the other, so a balance that had followed either account
     * on its own would be short by the other's — and the two amounts are different, so a figure that
     * happened to count one of them twice would be wrong as well.
     */
    @Test
    void points_earned_in_two_savings_accounts_add_up_to_one_balance() {
        long oneGoal = seeded.savingsAccountOf(ANKE);
        long anotherGoal = seeded.otherSavingsAccountOf(ANKE);
        long before = seeded.pointsBalanceOf(ANKE);

        deposit(oneGoal, "25.00");
        deposit(anotherGoal, "10.00");

        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(before + 35);
    }

    /**
     * And it is the same balance wherever it is read. The overview reports it once for the customer,
     * and each of their savings accounts reports it beside its own money — one figure, so a customer
     * who opens the account they did not save into is not told they have nothing to spend.
     */
    @Test
    void the_same_balance_is_reported_beside_every_account_the_customer_holds() {
        long oneGoal = seeded.savingsAccountOf(ANKE);
        long anotherGoal = seeded.otherSavingsAccountOf(ANKE);

        deposit(oneGoal, "12.00");

        long onTheOverview = seeded.pointsBalanceOf(ANKE);
        assertThat(balancesOf(oneGoal).pointsBalance()).isEqualTo(onTheOverview);
        assertThat(balancesOf(anotherGoal).pointsBalance()).isEqualTo(onTheOverview);
    }

    /**
     * A reward is paid for out of the pot, so points earned in one account buy something even though
     * the account claimed against is the other one — and there is no account claimed against at all.
     * Nothing about which goal the euros went towards decides what a person can spend.
     */
    @Test
    void a_reward_is_paid_for_out_of_points_earned_in_another_account() {
        long earnedIn = seeded.savingsAccountOf(ANKE);
        long neverPaidInto = seeded.otherSavingsAccountOf(ANKE);
        deposit(earnedIn, "100.00");
        long before = seeded.pointsBalanceOf(ANKE);

        ClaimedRewardView claimed = claim(ANKE, "CINEMA_TICKET").getBody();

        assertThat(claimed.pointsSpent()).isEqualTo(100);
        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(before - 100);
        // Read again from the account that never earned any of it: the pot it reports is the one the
        // claim came out of, because there is only one.
        assertThat(balancesOf(neverPaidInto).pointsBalance()).isEqualTo(before - 100);
    }

    /**
     * One pot per customer, and not one pot for everybody. This is the line the change moved and the
     * line it did not: points stopped belonging to an account and did not start belonging to the bank.
     */
    @Test
    void one_customers_points_are_never_anothers() {
        long bramsBefore = seeded.pointsBalanceOf(BRAM);

        deposit(seeded.savingsAccountOf(ANKE), "30.00");

        assertThat(seeded.pointsBalanceOf(BRAM)).isEqualTo(bramsBefore);
    }

    /**
     * What the customer has claimed is one list, so the two lists on their page account for the
     * balance between them: everything the deposits into every account earned, less everything the
     * claims took out.
     */
    @Test
    void what_the_customer_has_claimed_is_one_list_however_many_accounts_earned_it() {
        deposit(seeded.savingsAccountOf(ANKE), "10.00");
        deposit(seeded.otherSavingsAccountOf(ANKE), "40.00");

        ClaimedRewardView first = claim(ANKE, "CHARITY_DONATION").getBody();
        ClaimedRewardView second = claim(ANKE, "SNACK_VOUCHER").getBody();

        ResponseEntity<ClaimedRewardView[]> theirClaims = http.getForEntity(
                "/api/customers/{id}/redemptions", ClaimedRewardView[].class, seeded.customerIdOf(ANKE));

        assertThat(theirClaims.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(theirClaims.getBody())
                .extracting(ClaimedRewardView::id)
                .contains(first.id(), second.id());
        // Everything earned across every account they hold, less everything they have claimed, is
        // the balance — the invariant that says the pot really is one.
        long earned = seeded.savingsAccountsOf(ANKE).stream()
                .flatMap(account -> Arrays.stream(
                        http.getForObject("/api/savings-accounts/{id}/deposits", DepositView[].class, account)))
                .mapToLong(DepositView::pointsEarned)
                .sum();
        long spent = Arrays.stream(theirClaims.getBody()).mapToLong(ClaimedRewardView::pointsSpent).sum();
        assertThat(earned - spent).isEqualTo(seeded.pointsBalanceOf(ANKE));
    }

    private BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    private ResponseEntity<ClaimedRewardView> claim(String customerName, String reward) {
        ResponseEntity<ClaimedRewardView> claimed = http.postForEntity(
                "/api/customers/{id}/redemptions",
                Map.of("reward", reward),
                ClaimedRewardView.class,
                seeded.customerIdOf(customerName));
        assertThat(claimed.getStatusCode())
                .describedAs("a claim this test needs in order to have spent anything")
                .isEqualTo(HttpStatus.CREATED);
        return claimed;
    }

    /** Points to spend have to be earned first, and a deposit is the only way to earn any. */
    private void deposit(long savingsAccountId, String amount) {
        ResponseEntity<DepositView> made = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", seeded.currentAccountOf(ANKE)),
                DepositView.class,
                savingsAccountId);
        assertThat(made.getStatusCode())
                .describedAs("a deposit this test needs in order to have points at all")
                .isEqualTo(HttpStatus.CREATED);
    }
}
