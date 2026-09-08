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
 * A customer reports the run of consecutive weeks they have secured and the longest run they have
 * ever had, beside the week they are part-way through — read beside any account they hold, because
 * the run is theirs.
 *
 * <p>What the resource says, on the shared application — the shape of the answer, the two figures
 * agreeing with each other, and one customer's run being their own. How a run grows and how it is
 * lost take weeks to demonstrate and are the clock-moving classes beside this one.
 *
 * <p>Every test asserts on the change it caused rather than on an absolute figure: the run shares one
 * database and one week, so whatever else has been paid into a seeded account today is already
 * counted, and what a test can honestly claim is what its own deposit did to the figures.
 */
class TheStreakACustomerIsOnApiTest extends ApiIntegrationTest {

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
    void a_customer_reports_the_current_run_of_weeks_and_the_best_one_ever() {
        BalancesView account = balancesOf(seeded.savingsAccountOf(ANKE));

        assertThat(account.currentStreakWeeks()).isNotNegative();
        assertThat(account.bestStreakWeeks()).isNotNegative();
        assertThat(account.bestStreakWeeks()).isGreaterThanOrEqualTo(account.currentStreakWeeks());
    }

    /**
     * A deposit that carries a week past what it asks for leaves the customer on a run of at least
     * this week, and leaves the best-ever figure at least that long.
     *
     * <p>At least, rather than exactly one: this customer may already have been on a run when the
     * test started, and asserting the exact figure would be asserting about weeks this test did not
     * put there. The exact figures from a customer with no history at all are the clock-moving
     * classes' business.
     */
    @Test
    void securing_this_week_puts_the_customer_on_a_run_of_at_least_this_week() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);

        depositAccepted(savingsAccount, seeded.currentAccountOf(ANKE), "55.00");

        BalancesView after = balancesOf(savingsAccount);
        assertThat(after.stillNeededThisWeek()).isEqualByComparingTo("0.00");
        assertThat(after.currentStreakWeeks()).isGreaterThanOrEqualTo(1);
        assertThat(after.bestStreakWeeks()).isGreaterThanOrEqualTo(after.currentStreakWeeks());
    }

    /**
     * One run per customer, whichever of their accounts the money goes into: a deposit that secures
     * the week secures it wherever it is read from, so the account that was not paid into reports
     * the run and the week the deposit made.
     *
     * <p>And somebody else's run is untouched, which is the line this does not cross. Both halves in
     * one test on purpose: "the run is shared" and "the run is not everybody's" are the two things
     * the change had to get right, and either alone would pass on a figure that ignored the customer.
     */
    @Test
    void a_deposit_into_one_savings_account_lengthens_the_customers_own_run_and_nobody_elses() {
        long paidInto = seeded.savingsAccountOf(ANKE);
        long theSameCustomersOther = seeded.otherSavingsAccountOf(ANKE);
        long somebodyElses = seeded.savingsAccountOf(BRAM);
        BalancesView elsesBefore = balancesOf(somebodyElses);

        depositAccepted(paidInto, seeded.currentAccountOf(ANKE), "75.00");

        // Read beside the account that was not paid into: the week it reports is secured and the run
        // it reports is at least this week, because both are the customer's.
        BalancesView otherAfter = balancesOf(theSameCustomersOther);
        assertThat(otherAfter.stillNeededThisWeek()).isEqualByComparingTo("0.00");
        assertThat(otherAfter.currentStreakWeeks()).isGreaterThanOrEqualTo(1);
        // The same two figures on the account that was paid into, because there is one answer.
        BalancesView paidIntoAfter = balancesOf(paidInto);
        assertThat(otherAfter.newSavingsThisWeek())
                .isEqualByComparingTo(paidIntoAfter.newSavingsThisWeek());
        assertThat(otherAfter.currentStreakWeeks()).isEqualTo(paidIntoAfter.currentStreakWeeks());
        assertThat(otherAfter.bestStreakWeeks()).isEqualTo(paidIntoAfter.bestStreakWeeks());
        // And the other customer saved nothing, so nothing of theirs moved.
        BalancesView elsesAfter = balancesOf(somebodyElses);
        assertThat(elsesAfter.newSavingsThisWeek())
                .isEqualByComparingTo(elsesBefore.newSavingsThisWeek());
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
