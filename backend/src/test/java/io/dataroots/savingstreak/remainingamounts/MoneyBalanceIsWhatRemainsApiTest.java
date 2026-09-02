package io.dataroots.savingstreak.remainingamounts;

import java.math.BigDecimal;
import java.util.Arrays;
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
 * A savings account's money balance is what remains across the deposits made into it.
 *
 * <p>Nothing a customer can see changes here, and that is the assertion: what remains of a deposit
 * is the whole of it until something can take money back out, so a balance summed from what remains
 * is the same figure as one summed from what was put in. Which is exactly why it is worth pinning —
 * the change swapped the figure the balance is derived from, and a swap that quietly reported a
 * different number would be a balance nobody could explain.
 *
 * <p>Every test asserts on what its own requests changed, because the run shares one database.
 */
class MoneyBalanceIsWhatRemainsApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * The balance and the history agree, deposit for deposit. Read one after the other with nothing
     * in between, so the two answers are about the same set of deposits.
     */
    @Test
    void the_money_balance_accounts_for_every_deposit_the_account_still_holds() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        deposit(savingsAccount, "4.30");
        deposit(savingsAccount, "0.70");

        BigDecimal balance = balancesOf(savingsAccount).moneyBalance();
        BigDecimal acrossItsDeposits = Arrays.stream(depositsInto(savingsAccount))
                .map(DepositView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(balance).isEqualByComparingTo(acrossItsDeposits);
    }

    /** A new deposit puts the whole of itself into the balance, none of it having gone anywhere. */
    @Test
    void a_deposit_raises_the_balance_by_the_whole_of_what_was_put_in() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        BigDecimal before = balancesOf(savingsAccount).moneyBalance();

        deposit(savingsAccount, "16.99");

        assertThat(balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(before.add(new BigDecimal("16.99")));
    }

    /**
     * The cents survive the sum. SQLite has no decimal type and keeps an amount as a float, so a
     * balance added up in the database rather than in the application is where a cent goes missing.
     */
    @Test
    void a_balance_summed_from_amounts_carrying_cents_keeps_them() {
        long savingsAccount = seeded.otherSavingsAccountOf(ANKE);
        BigDecimal before = balancesOf(savingsAccount).moneyBalance();

        deposit(savingsAccount, "0.01");
        deposit(savingsAccount, "0.02");
        deposit(savingsAccount, "10.07");

        assertThat(balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(before.add(new BigDecimal("10.10")));
    }

    private BalancesView balancesOf(long savingsAccountId) {
        ResponseEntity<BalancesView> response = http.getForEntity(
                "/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private DepositView[] depositsInto(long savingsAccountId) {
        return http.getForObject(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
    }

    private void deposit(long savingsAccountId, String amount) {
        ResponseEntity<DepositView> response = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", seeded.currentAccountOf(ANKE)),
                DepositView.class,
                savingsAccountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
