package io.dataroots.savingstreak.pointsearned;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

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
 * A deposit moves three things — money out of a current account, money into a savings account, and
 * points onto that savings account — and either all three happen or none of them does. It is the
 * behavioural guarantee this slice rests on: money that left one account without arriving in the
 * other, or points earned by a deposit nobody made, is a balance no customer could be talked out of.
 *
 * <p>This class used to reach that guarantee by making the last step fail: a deposit of more euros
 * than a points balance can count, which was recorded and then could not be credited. That deposit
 * is now refused before anything is written, for want of that much money in the current account,
 * and the test that asserted a server error is gone — as the comment it carried said it should be,
 * rather than being left to pass without exercising anything.
 *
 * <p>What is left is the guarantee stated from outside, which is where it was always meant to be
 * read: the three figures move together, and a refusal moves none of them. Both are asserted on the
 * same three figures, so neither test can pass by looking at only the side that happened to be
 * right.
 */
class DepositIsAllOrNothingApiTest extends ApiIntegrationTest {

    /** More than any seeded current account holds, which is what a refusal needs and nothing more. */
    private static final String MORE_THAN_THE_ACCOUNT_HOLDS = "1000000.00";

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    @Test
    void a_deposit_that_goes_through_moves_the_money_and_the_points_together() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        Everything before = everything(savingsAccount);

        ResponseEntity<JsonNode> response =
                deposit(savingsAccount, seeded.currentAccountOf(ANKE), "3.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Everything after = everything(savingsAccount);
        assertThat(after.inTheCurrentAccount())
                .isEqualByComparingTo(before.inTheCurrentAccount().subtract(new BigDecimal("3.00")));
        assertThat(after.saved()).isEqualByComparingTo(before.saved().add(new BigDecimal("3.00")));
        assertThat(after.points()).isEqualTo(before.points() + 3);
        assertThat(after.depositsRecorded()).isEqualTo(before.depositsRecorded() + 1);
    }

    /**
     * A refusal is the same three figures left alone. The interesting one is the first: the money is
     * taken out of the current account inside the same transaction as everything else, so a
     * withdrawal that survived a refusal would be money gone from an account with nothing anywhere
     * to say where it went.
     */
    @Test
    void a_deposit_that_is_refused_moves_none_of_them() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        Everything before = everything(savingsAccount);

        ResponseEntity<JsonNode> response =
                deposit(savingsAccount, seeded.currentAccountOf(ANKE), MORE_THAN_THE_ACCOUNT_HOLDS);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Everything after = everything(savingsAccount);
        assertThat(after.inTheCurrentAccount()).isEqualByComparingTo(before.inTheCurrentAccount());
        assertThat(after.saved()).isEqualByComparingTo(before.saved());
        assertThat(after.points()).isEqualTo(before.points());
        assertThat(after.depositsRecorded()).isEqualTo(before.depositsRecorded());
    }

    /** Everything one deposit can change, read the only way a customer could read it. */
    private record Everything(BigDecimal inTheCurrentAccount, BigDecimal saved, long points,
                              int depositsRecorded) {
    }

    private Everything everything(long savingsAccountId) {
        BalancesView balances =
                http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
        DepositView[] recorded = http.getForObject(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
        return new Everything(seeded.currentAccountBalanceOf(ANKE), balances.moneyBalance(),
                balances.pointsBalance(), recorded.length);
    }

    private ResponseEntity<JsonNode> deposit(long savingsAccountId, long currentAccountId, String amount) {
        return http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                JsonNode.class,
                savingsAccountId);
    }
}
