package io.dataroots.savingstreak.withdrawals;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

/** A withdrawal moves money back to one of its holder's current accounts, leaving points alone. */
class WithdrawingMoneyApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    @Test
    void a_withdrawal_moves_money_back_to_the_current_account_without_taking_points() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        long currentAccount = seeded.currentAccountOf(ANKE);
        DepositView oldest = deposit(savingsAccount, currentAccount, "5.00");
        DepositView next = deposit(savingsAccount, currentAccount, "20.00");
        BalancesView savingsBefore = balancesOf(savingsAccount);
        BigDecimal currentBefore = seeded.currentAccountBalanceOf(ANKE);

        ResponseEntity<WithdrawalView> response = withdraw(savingsAccount, currentAccount, "12.50");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().amount()).isEqualByComparingTo("12.50");
        assertThat(response.getBody().toCurrentAccountId()).isEqualTo(currentAccount);
        assertThat(response.getBody().withdrawnAt()).isNotNull();
        assertThat(response.getBody().allocations()).hasSize(2);
        assertThat(response.getBody().allocations()[0].depositId()).isEqualTo(oldest.id());
        assertThat(response.getBody().allocations()[0].amount()).isEqualByComparingTo("5.00");
        assertThat(response.getBody().allocations()[1].depositId()).isEqualTo(next.id());
        assertThat(response.getBody().allocations()[1].amount()).isEqualByComparingTo("7.50");
        BalancesView savingsAfter = balancesOf(savingsAccount);
        assertThat(savingsAfter.moneyBalance())
                .isEqualByComparingTo(savingsBefore.moneyBalance().subtract(new BigDecimal("12.50")));
        assertThat(seeded.currentAccountBalanceOf(ANKE))
                .isEqualByComparingTo(currentBefore.add(new BigDecimal("12.50")));
        assertThat(savingsAfter.pointsBalance()).isEqualTo(savingsBefore.pointsBalance());
    }

    @Test
    void a_withdrawal_can_empty_an_account_without_touching_the_customers_other_savings_account() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        long otherSavingsAccount = seeded.otherSavingsAccountOf(ANKE);
        long currentAccount = seeded.currentAccountOf(ANKE);
        deposit(savingsAccount, currentAccount, "31.00");
        BigDecimal otherBefore = balancesOf(otherSavingsAccount).moneyBalance();
        BigDecimal allThereIs = balancesOf(savingsAccount).moneyBalance();

        ResponseEntity<WithdrawalView> response = withdraw(savingsAccount, currentAccount,
                allThereIs.setScale(2, RoundingMode.HALF_UP).toPlainString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(balancesOf(savingsAccount).moneyBalance()).isEqualByComparingTo("0.00");
        assertThat(balancesOf(otherSavingsAccount).moneyBalance()).isEqualByComparingTo(otherBefore);
    }

    @Test
    void withdrawals_are_listed_newest_first_with_their_destination_and_moment() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        long currentAccount = seeded.currentAccountOf(ANKE);
        deposit(savingsAccount, currentAccount, "10.00");
        WithdrawalView first = withdraw(savingsAccount, currentAccount, "1.00").getBody();
        WithdrawalView second = withdraw(savingsAccount, currentAccount, "2.00").getBody();

        ResponseEntity<WithdrawalView[]> response = http.getForEntity(
                "/api/savings-accounts/{id}/withdrawals", WithdrawalView[].class, savingsAccount);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Arrays.stream(response.getBody()).map(WithdrawalView::id))
                .startsWith(second.id(), first.id());
        assertThat(response.getBody()[0].toCurrentAccountId()).isEqualTo(currentAccount);
        assertThat(response.getBody()[0].withdrawnAt()).isNotNull();
    }

    @Test
    void a_withdrawal_larger_than_the_balance_is_refused_without_moving_anything() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        long currentAccount = seeded.currentAccountOf(ANKE);
        deposit(savingsAccount, currentAccount, "9.00");
        BalancesView savingsBefore = balancesOf(savingsAccount);
        BigDecimal currentBefore = seeded.currentAccountBalanceOf(ANKE);
        int depositsBefore = depositsInto(savingsAccount).length;

        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = http.postForEntity(
                "/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", "1000000.00", "toCurrentAccountId", currentAccount),
                com.fasterxml.jackson.databind.JsonNode.class, savingsAccount);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("detail").asText())
                .contains("1000000.00")
                .contains(savingsBefore.moneyBalance().setScale(2, RoundingMode.HALF_UP).toPlainString());
        assertThat(balancesOf(savingsAccount).moneyBalance()).isEqualByComparingTo(savingsBefore.moneyBalance());
        assertThat(balancesOf(savingsAccount).pointsBalance()).isEqualTo(savingsBefore.pointsBalance());
        assertThat(seeded.currentAccountBalanceOf(ANKE)).isEqualByComparingTo(currentBefore);
        assertThat(depositsInto(savingsAccount)).hasSize(depositsBefore);
    }

    private BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    private DepositView deposit(long savingsAccountId, long currentAccountId, String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId), DepositView.class,
                savingsAccountId).getBody();
    }

    private DepositView[] depositsInto(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
    }

    private ResponseEntity<WithdrawalView> withdraw(long savingsAccountId, long currentAccountId, String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", currentAccountId), WithdrawalView.class,
                savingsAccountId);
    }

    record WithdrawalView(Long id, BigDecimal amount, Long toCurrentAccountId,
                          java.time.Instant withdrawnAt, AllocationView[] allocations) {
    }

    record AllocationView(Long depositId, BigDecimal amount) {
    }
}
