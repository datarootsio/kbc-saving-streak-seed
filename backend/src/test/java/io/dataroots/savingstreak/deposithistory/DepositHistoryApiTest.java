package io.dataroots.savingstreak.deposithistory;

import java.math.BigDecimal;
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
 * The deposits behind a balance: every payment that produced it, what each one earned, and when.
 *
 * <p>Every test asserts on the deposits it made rather than on the whole list, because the run
 * shares one database and what a test put there is the only thing it can honestly claim.
 */
class DepositHistoryApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    @Test
    void a_deposit_appears_in_the_accounts_history_carrying_the_points_it_earned() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);

        DepositView made = deposit(savingsAccount, seeded.currentAccountOf(ANKE), "18.40").getBody();

        ResponseEntity<DepositView[]> response = depositsInto(savingsAccount);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).anySatisfy(listed -> {
            assertThat(listed.id()).isEqualTo(made.id());
            assertThat(listed.amount()).isEqualByComparingTo("18.40");
            assertThat(listed.pointsEarned()).isEqualTo(18);
            assertThat(listed.depositedAt()).isEqualTo(made.depositedAt());
        });
    }

    /**
     * Newest first, because someone checking a balance against their own records starts from what
     * they did last. Only the head of the list is asserted on: earlier tests in the run have left
     * deposits of their own further down it.
     */
    @Test
    void deposits_are_listed_newest_first() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        long currentAccount = seeded.currentAccountOf(ANKE);

        DepositView first = deposit(savingsAccount, currentAccount, "1.00").getBody();
        DepositView second = deposit(savingsAccount, currentAccount, "2.00").getBody();
        DepositView third = deposit(savingsAccount, currentAccount, "3.00").getBody();

        assertThat(depositsInto(savingsAccount).getBody())
                .extracting(DepositView::id)
                .startsWith(third.id(), second.id(), first.id());
    }

    /**
     * An account nobody has paid into has an empty history rather than a missing one: "no deposits
     * yet" is an answer, and a customer who has just opened an account should read it as one.
     *
     * <p>Bram's is the seeded account no test in the run deposits into successfully. Its money
     * balance is asserted first, so that a later test which does start depositing into it fails
     * here saying so, rather than quietly leaving this one asserting nothing.
     */
    @Test
    void a_savings_account_with_no_deposits_has_an_empty_history() {
        long neverDepositedInto = seeded.savingsAccountOf(BRAM);
        assertThat(balancesOf(neverDepositedInto).moneyBalance()).isEqualByComparingTo("0.00");

        ResponseEntity<DepositView[]> response = depositsInto(neverDepositedInto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    /**
     * The lists and the balances are two readings of the same records reached by different code: the
     * balances sum the deposits and sum the points ledger, while the lists pair each deposit with the
     * batch it earned and each claim with what it cost. That they agree is the whole point of showing
     * them — a balance nobody can add up for themselves is one they have to take on trust.
     *
     * <p>The money balance is the account's own deposits and nothing else: claiming a reward spends
     * points, never euros. The points balance is the customer's and takes every account they hold to
     * explain — everything earned, wherever it was earned, less what has been spent, plus what has
     * been given back. Both sides are needed, and a page showing only one account's deposits would
     * be showing a figure that does not follow from them.
     *
     * <p><strong>A refund is earned points that no deposit explains, and that is the third term
     * here.</strong> This assertion used to read {@code earned - spent == balance} and that
     * equation stopped being true the day a voucher could be cancelled: a cancellation credits the
     * points back as a fresh batch under its own reason, so the balance rises without any deposit
     * rising with it. It went on passing only because every test that cancels one opens a customer
     * of its own — so the first test that ever cancels one of this seeded customer's vouchers would
     * have broken a deposit-history test that has nothing to do with vouchers, and it would have
     * looked like a regression in this file. The refund is observable from the same list the
     * spending is: a cancelled claim is still in the customer's claims with what it cost, and what
     * it cost is exactly what came back, because a cancellation refunds the claim's own price
     * rather than a figure of its own.
     *
     * <p>Adding the term makes the assertion stronger rather than weaker. It still says the two
     * lists add up to the balance and nothing may be missing from either; it now also says that a
     * cancelled claim gives back precisely what it took, which is a thing the old equation could
     * not have caught and this one fails on.
     *
     * <p>Asserted across the whole of both lists rather than this test's own two deposits, because
     * the claim is an invariant about the customer and not about what this test put in.
     */
    @Test
    void the_listed_amounts_and_points_add_up_to_the_two_balances() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        long currentAccount = seeded.currentAccountOf(ANKE);
        deposit(savingsAccount, currentAccount, "7.30");
        deposit(savingsAccount, currentAccount, "0.40");

        DepositView[] history = depositsInto(savingsAccount).getBody();
        ClaimedRewardView[] claimed = claimsBy(ANKE).getBody();
        BalancesView balances = balancesOf(savingsAccount);

        assertThat(Arrays.stream(history).map(DepositView::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(balances.moneyBalance());
        // Earned across every account this customer holds, because that is the pot the balance is:
        // this one's deposits alone would be short by whatever the other one has earned.
        long earned = seeded.savingsAccountsOf(ANKE).stream()
                .flatMap(account -> Arrays.stream(depositsInto(account).getBody()))
                .mapToLong(DepositView::pointsEarned)
                .sum();
        long spent = Arrays.stream(claimed).mapToLong(ClaimedRewardView::pointsSpent).sum();
        // And back again for every claim somebody revoked: a cancellation is the one way points
        // arrive that no deposit accounts for, so the equation without this term is one a single
        // cancelled voucher of this customer's would break.
        long refunded = Arrays.stream(claimed)
                .filter(claim -> "CANCELLED".equals(claim.state()))
                .mapToLong(ClaimedRewardView::pointsSpent)
                .sum();
        assertThat(earned - spent + refunded).isEqualTo(balances.pointsBalance());
    }

    /**
     * Asking after an account that does not exist is a mistake, not an account with nothing in it.
     * Answering it with an empty history would tell the caller their identifier was fine.
     */
    @Test
    void the_history_of_a_savings_account_that_does_not_exist_is_not_found() {
        // Read as text: a refusal carries an error body rather than a list, and asking for the
        // response as a list would fail to read it before the status could be looked at.
        ResponseEntity<String> response = http.getForEntity(
                "/api/savings-accounts/{id}/deposits", String.class, seeded.anIdNoSavingsAccountHas());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    private ResponseEntity<ClaimedRewardView[]> claimsBy(String customerName) {
        return http.getForEntity("/api/customers/{id}/redemptions", ClaimedRewardView[].class,
                seeded.customerIdOf(customerName));
    }

    private ResponseEntity<DepositView[]> depositsInto(long savingsAccountId) {
        return http.getForEntity(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
    }

    private ResponseEntity<DepositView> deposit(long savingsAccountId, long currentAccountId, String amount) {
        return http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                DepositView.class,
                savingsAccountId);
    }
}
