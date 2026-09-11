package io.dataroots.savingstreak.weeklysavings;

import java.math.BigDecimal;
import java.time.Instant;
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
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer says how much new saving they have put away in the week they are part-way through, and
 * how much more that week asks for — reported beside every account they hold, because the week is
 * theirs and not any one account's.
 *
 * <p>Every test asserts on the change it caused rather than on an absolute figure: the run shares
 * one database and one week, so whatever else has been paid into a seeded account today is already
 * counted, and what a test can honestly claim is what its own deposit did to the figure.
 */
class ThisWeeksNewSavingsApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * The account reports the week alongside its balances, and the three weekly figures agree with
     * each other: what the week asks for, what has landed, and what is left to find.
     */
    @Test
    void a_savings_account_reports_what_has_landed_in_the_current_week() {
        BalancesView account = balancesOf(seeded.savingsAccountOf(ANKE));

        assertThat(account.newSavingsThisWeek()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        // The one figure a week costs, reported rather than left for a screen to write down.
        assertThat(account.weeklyMinimum()).isEqualByComparingTo("50.00");
        assertThat(account.stillNeededThisWeek()).isEqualByComparingTo(stillNeededGiven(account));
    }

    /** The whole amount, and in the answer to the very next read rather than after anything runs. */
    @Test
    void a_deposit_raises_this_weeks_new_savings_by_its_full_amount_immediately() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        BalancesView before = balancesOf(savingsAccount);

        ResponseEntity<DepositView> response = deposit(savingsAccount, seeded.currentAccountOf(ANKE), "12.50");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.newSavingsThisWeek())
                .isEqualByComparingTo(before.newSavingsThisWeek().add(new BigDecimal("12.50")));
        assertThat(after.stillNeededThisWeek()).isEqualByComparingTo(stillNeededGiven(after));
    }

    /** Several deposits in one week add up, which is what "part-way through a week" means. */
    @Test
    void deposits_in_the_same_week_add_up_towards_what_the_week_asks_for() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        long currentAccount = seeded.currentAccountOf(ANKE);
        BalancesView before = balancesOf(savingsAccount);

        depositAccepted(savingsAccount, currentAccount, "20.00");
        depositAccepted(savingsAccount, currentAccount, "0.99");
        depositAccepted(savingsAccount, currentAccount, "9.01");

        assertThat(balancesOf(savingsAccount).newSavingsThisWeek())
                .isEqualByComparingTo(before.newSavingsThisWeek().add(new BigDecimal("30.00")));
    }

    /**
     * Gross, not net. A withdrawal takes the money back out without un-happening the deposit it came
     * out of, so a week that took the money in has still taken it in — however much of it leaves
     * again, and even if the account is emptied.
     */
    @Test
    void a_withdrawal_leaves_this_weeks_new_savings_where_it_was() {
        long savingsAccount = seeded.otherSavingsAccountOf(ANKE);
        long currentAccount = seeded.currentAccountOf(ANKE);
        depositAccepted(savingsAccount, currentAccount, "60.00");
        BalancesView before = balancesOf(savingsAccount);

        ResponseEntity<?> withdrawn = withdraw(savingsAccount, currentAccount, "60.00");

        assertThat(withdrawn.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BalancesView after = balancesOf(savingsAccount);
        // The money has gone, which is what makes this worth asserting: the account holds less than
        // it did and the week's progress is untouched.
        assertThat(after.moneyBalance())
                .isEqualByComparingTo(before.moneyBalance().subtract(new BigDecimal("60.00")));
        assertThat(after.newSavingsThisWeek()).isEqualByComparingTo(before.newSavingsThisWeek());
        assertThat(after.stillNeededThisWeek()).isEqualByComparingTo(before.stillNeededThisWeek());
    }

    /**
     * One week per customer, counting every account they pay into. A customer saving towards two
     * goals is saving: money into either one is a week's saving, and the figure reads the same
     * whichever of their accounts it is read beside.
     *
     * <p>And somebody else's week is untouched by it, which is the line this does not cross: the
     * week stopped belonging to an account and did not start belonging to the bank.
     */
    @Test
    void a_deposit_into_any_of_a_customers_accounts_counts_towards_their_one_week() {
        long paidInto = seeded.savingsAccountOf(ANKE);
        long theSameCustomersOther = seeded.otherSavingsAccountOf(ANKE);
        long somebodyElses = seeded.savingsAccountOf(BRAM);
        BalancesView otherBefore = balancesOf(theSameCustomersOther);
        BalancesView elsesBefore = balancesOf(somebodyElses);

        depositAccepted(paidInto, seeded.currentAccountOf(ANKE), "55.00");

        // Read beside the account that was not paid into: the week has the EUR 55 in it, because the
        // week is the customer's.
        assertThat(balancesOf(theSameCustomersOther).newSavingsThisWeek())
                .isEqualByComparingTo(otherBefore.newSavingsThisWeek().add(new BigDecimal("55.00")));
        assertThat(balancesOf(somebodyElses).newSavingsThisWeek())
                .isEqualByComparingTo(elsesBefore.newSavingsThisWeek());
    }

    /**
     * A week past what it asks for needs nothing more, rather than needing a negative amount. The
     * deposit is bigger than the whole minimum on its own, so the week is over the line however
     * little else has landed in it.
     */
    @Test
    void a_week_that_has_taken_in_more_than_it_asks_for_needs_nothing_further() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        long currentAccount = seeded.currentAccountOf(BRAM);
        BalancesView before = balancesOf(savingsAccount);

        // Through the helper that insists the deposit landed, because Bram's current account is the
        // deliberately shallow one: a refused deposit has to fail this test rather than leave it
        // asserting that a week nothing landed in needs nothing.
        depositAccepted(savingsAccount, currentAccount, "80.00");

        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.newSavingsThisWeek())
                .isEqualByComparingTo(before.newSavingsThisWeek().add(new BigDecimal("80.00")));
        // The one absolute figure this class asserts, and it is safe as one: 80.00 is past the whole
        // minimum on its own, so nothing else that landed in the week can change the answer.
        assertThat(after.stillNeededThisWeek()).isEqualByComparingTo("0.00");
    }

    /**
     * The week is a new figure beside the old ones and changes none of them: a deposit still earns
     * one point per whole euro, and the money balance is still what was paid in.
     */
    @Test
    void counting_the_week_leaves_the_points_a_deposit_earns_alone() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        BalancesView before = balancesOf(savingsAccount);

        DepositView made = depositAccepted(savingsAccount, seeded.currentAccountOf(ANKE), "7.60");

        assertThat(made.pointsEarned()).isEqualTo(7);
        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance() + 7);
        assertThat(after.moneyBalance())
                .isEqualByComparingTo(before.moneyBalance().add(new BigDecimal("7.60")));
    }

    /** What the week still needs, worked out from the two figures the account itself reported. */
    private static BigDecimal stillNeededGiven(BalancesView account) {
        BigDecimal outstanding = account.weeklyMinimum().subtract(account.newSavingsThisWeek());
        return outstanding.signum() > 0 ? outstanding : BigDecimal.ZERO;
    }

    /**
     * A deposit this test needed to land, with the refusal ruled out rather than assumed: the seeded
     * current accounts are shallow on purpose and a refused deposit would otherwise leave a test
     * asserting that nothing changed — and passing.
     */
    private DepositView depositAccepted(long savingsAccountId, long currentAccountId, String amount) {
        ResponseEntity<DepositView> response = deposit(savingsAccountId, currentAccountId, amount);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<DepositView> deposit(long savingsAccountId, long currentAccountId, String amount) {
        return http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                DepositView.class,
                savingsAccountId);
    }

    private ResponseEntity<WithdrawalView> withdraw(long savingsAccountId, long currentAccountId, String amount) {
        return http.postForEntity(
                "/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", currentAccountId),
                WithdrawalView.class,
                savingsAccountId);
    }

    private BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    /** As much of a withdrawal as this class reads back: that it happened, and for how much. */
    record WithdrawalView(Long id, BigDecimal amount, Instant withdrawnAt) {
    }
}
