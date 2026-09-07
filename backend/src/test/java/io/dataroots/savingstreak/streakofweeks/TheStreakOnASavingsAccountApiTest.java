package io.dataroots.savingstreak.streakofweeks;

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
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A savings account reports the run of consecutive weeks it has secured and the longest run it has
 * ever had, beside the week it is part-way through.
 *
 * <p>What the resource says, on the shared application — the shape of the answer, the two figures
 * agreeing with each other, and one account's run being its own. How a run grows and how it is lost
 * take weeks to demonstrate and are the clock-moving classes beside this one.
 *
 * <p>Every test asserts on the change it caused rather than on an absolute figure: the run shares one
 * database and one week, so whatever else has been paid into a seeded account today is already
 * counted, and what a test can honestly claim is what its own deposit did to the figures.
 */
class TheStreakOnASavingsAccountApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * Both figures are there, both are counts of weeks, and they agree with each other: a run
     * happening now is a run that has happened, so the best-ever figure is never the smaller of the
     * two.
     */
    @Test
    void a_savings_account_reports_the_current_run_of_weeks_and_the_best_one_ever() {
        BalancesView account = balancesOf(seeded.savingsAccountOf(ANKE));

        assertThat(account.currentStreakWeeks()).isNotNegative();
        assertThat(account.bestStreakWeeks()).isNotNegative();
        assertThat(account.bestStreakWeeks()).isGreaterThanOrEqualTo(account.currentStreakWeeks());
    }

    /**
     * A deposit that carries a week past what it asks for leaves the account on a run of at least
     * this week, and leaves the best-ever figure at least that long.
     *
     * <p>At least, rather than exactly one: this account may already have been on a run when the
     * test started, and asserting the exact figure would be asserting about weeks this test did not
     * put there. The exact figures from an account with no history at all are the clock-moving
     * classes' business.
     */
    @Test
    void securing_this_week_puts_the_account_on_a_run_of_at_least_this_week() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);

        depositAccepted(savingsAccount, seeded.currentAccountOf(ANKE), "55.00");

        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.stillNeededThisWeek()).isEqualByComparingTo("0.00");
        assertThat(after.currentStreakWeeks()).isGreaterThanOrEqualTo(1);
        assertThat(after.bestStreakWeeks()).isGreaterThanOrEqualTo(after.currentStreakWeeks());
    }

    /**
     * Each savings account carries its own run. A deposit into one is not quietly lengthening
     * another's — including another held by the same customer, which is the only way to ask the
     * question at all.
     */
    @Test
    void a_deposit_into_one_savings_account_lengthens_no_other_ones_run() {
        long paidInto = seeded.savingsAccountOf(ANKE);
        long theSameCustomersOther = seeded.otherSavingsAccountOf(ANKE);
        long somebodyElses = seeded.savingsAccountOf(BRAM);
        BalancesView otherBefore = balancesOf(theSameCustomersOther);
        BalancesView elsesBefore = balancesOf(somebodyElses);

        depositAccepted(paidInto, seeded.currentAccountOf(ANKE), "75.00");

        BalancesView otherAfter = balancesOf(theSameCustomersOther);
        assertThat(otherAfter.currentStreakWeeks()).isEqualTo(otherBefore.currentStreakWeeks());
        assertThat(otherAfter.bestStreakWeeks()).isEqualTo(otherBefore.bestStreakWeeks());
        BalancesView elsesAfter = balancesOf(somebodyElses);
        assertThat(elsesAfter.currentStreakWeeks()).isEqualTo(elsesBefore.currentStreakWeeks());
        assertThat(elsesAfter.bestStreakWeeks()).isEqualTo(elsesBefore.bestStreakWeeks());
    }

    /**
     * Counting the run leaves the points a deposit earns alone: one per whole euro, the cents
     * floored away, exactly as before there was a run to count.
     */
    @Test
    void counting_the_run_of_weeks_leaves_the_points_a_deposit_earns_alone() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        BalancesView before = balancesOf(savingsAccount);

        DepositView made = depositAccepted(savingsAccount, seeded.currentAccountOf(ANKE), "7.60");

        assertThat(made.pointsEarned()).isEqualTo(7);
        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance() + 7);
        assertThat(after.moneyBalance())
                .isEqualByComparingTo(before.moneyBalance().add(new BigDecimal("7.60")));
    }

    /**
     * A deposit this test needed to land, with the refusal ruled out rather than assumed: the seeded
     * current accounts are shallow on purpose and a refused deposit would otherwise leave a test
     * asserting that nothing changed — and passing.
     */
    private DepositView depositAccepted(long savingsAccountId, long currentAccountId, String amount) {
        ResponseEntity<DepositView> response = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                DepositView.class,
                savingsAccountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }
}
