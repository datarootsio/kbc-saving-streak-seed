package io.dataroots.savingstreak.pointsearned;

import java.math.BigDecimal;
import java.util.Map;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money moves from a current account into a savings account, and the savings account earns a point
 * for every whole euro of it, immediately.
 *
 * <p>Every test asserts on the change it caused rather than on an absolute balance: the run shares
 * one database, so what a test put there is the only thing it can honestly claim.
 */
class DepositEarnsPointsApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    @Test
    void a_whole_euro_deposit_raises_the_money_balance_and_the_points_balance_by_the_amount() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        BalancesView before = balancesOf(savingsAccount);

        ResponseEntity<DepositView> response = deposit(savingsAccount, seeded.currentAccountOf(ANKE), "25.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().amount()).isEqualByComparingTo("25.00");
        assertThat(response.getBody().depositedAt()).isNotNull();

        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.moneyBalance()).isEqualByComparingTo(before.moneyBalance().add(new BigDecimal("25.00")));
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance() + 25);
    }

    @Test
    void a_deposit_with_cents_earns_points_on_the_whole_euros_only() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        BalancesView before = balancesOf(savingsAccount);

        deposit(savingsAccount, seeded.currentAccountOf(ANKE), "12.50");

        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.moneyBalance()).isEqualByComparingTo(before.moneyBalance().add(new BigDecimal("12.50")));
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance() + 12);
    }

    @Test
    void a_deposit_under_one_euro_moves_the_money_and_earns_nothing() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        BalancesView before = balancesOf(savingsAccount);

        ResponseEntity<DepositView> response = deposit(savingsAccount, seeded.currentAccountOf(ANKE), "0.75");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.moneyBalance()).isEqualByComparingTo(before.moneyBalance().add(new BigDecimal("0.75")));
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance());
    }

    /**
     * Two deposits, and both balances are the sum of what each one contributed. This is what
     * "derived, never stored" means from outside: there is no figure that could have been written
     * once and left behind, because the second deposit finds the first one still counted.
     */
    @Test
    void two_deposits_leave_balances_equal_to_the_sum_of_what_each_one_earned() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        long currentAccount = seeded.currentAccountOf(ANKE);
        BalancesView before = balancesOf(savingsAccount);

        deposit(savingsAccount, currentAccount, "3.60");
        deposit(savingsAccount, currentAccount, "1.60");

        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.moneyBalance()).isEqualByComparingTo(before.moneyBalance().add(new BigDecimal("5.20")));
        // Four, not five: each deposit earns on its own whole euros, and the leftover cents of one
        // never combine with another's to earn a point neither of them earned.
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance() + 4);
    }

    /**
     * A deposit is a movement, so the money it puts into the savings account is money it took out of
     * the current account. Both sides are asserted in one test on purpose: either figure on its own
     * can be right while the pair of them adds up to money appearing from nowhere.
     */
    @Test
    void a_deposit_takes_the_money_out_of_the_current_account_it_came_from() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        BigDecimal inTheCurrentAccountBefore = seeded.currentAccountBalanceOf(ANKE);
        BalancesView savedBefore = balancesOf(savingsAccount);

        deposit(savingsAccount, seeded.currentAccountOf(ANKE), "7.30");

        assertThat(seeded.currentAccountBalanceOf(ANKE))
                .isEqualByComparingTo(inTheCurrentAccountBefore.subtract(new BigDecimal("7.30")));
        assertThat(balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(savedBefore.moneyBalance().add(new BigDecimal("7.30")));
    }

    /**
     * Cents survive the round trip through the database. SQLite has no decimal type and keeps an
     * amount as a float, and a balance that is written back on every deposit is the figure with the
     * most chances to drift: two deposits of amounts that no float holds exactly, and the balance is
     * still to the cent.
     */
    @Test
    void a_balance_written_back_on_every_deposit_stays_exact_to_the_cent() {
        BigDecimal before = seeded.currentAccountBalanceOf(ANKE);

        deposit(seeded.savingsAccountOf(ANKE), seeded.currentAccountOf(ANKE), "18.40");
        deposit(seeded.savingsAccountOf(ANKE), seeded.currentAccountOf(ANKE), "0.75");

        assertThat(seeded.currentAccountBalanceOf(ANKE))
                .isEqualByComparingTo(before.subtract(new BigDecimal("19.15")));
    }

    /**
     * Separate accounts run separate streaks. A customer saving towards two goals sees each balance
     * mean what it says, rather than the two pooling.
     */
    @Test
    void a_deposit_into_one_savings_account_leaves_the_customers_other_ones_untouched() {
        long untouched = seeded.otherSavingsAccountOf(ANKE);
        BalancesView before = balancesOf(untouched);

        deposit(seeded.savingsAccountOf(ANKE), seeded.currentAccountOf(ANKE), "40.00");

        BalancesView after = balancesOf(untouched);
        assertThat(after.moneyBalance()).isEqualByComparingTo(before.moneyBalance());
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance());
    }

    private ResponseEntity<DepositView> deposit(long savingsAccountId, long currentAccountId, String amount) {
        return http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                DepositView.class,
                savingsAccountId);
    }

    private BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }
}
