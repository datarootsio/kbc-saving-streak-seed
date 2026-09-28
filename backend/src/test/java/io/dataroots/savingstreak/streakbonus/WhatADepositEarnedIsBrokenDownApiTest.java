package io.dataroots.savingstreak.streakbonus;

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
 * What a deposit reports having earned, and what a savings account reports it is paying — the shape
 * of both answers, on the shared application.
 *
 * <p>How the rate is arrived at takes weeks to demonstrate and is the clock-moving classes beside
 * this one. What is here is what holds of every deposit whatever the run behind it is doing: the
 * points-earned figure means everything the deposit earned, the base and the bonus are what that
 * figure is made of, the base is still one point per whole euro, and the rate is a rung of the
 * ladder and never off it.
 *
 * <p>Every test asserts on the change it caused rather than on an absolute figure: the run shares one
 * database and one week, so whatever else has been paid into a seeded account today is already
 * counted, and what a test can honestly claim is what its own deposit did.
 */
class WhatADepositEarnedIsBrokenDownApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * The four figures a deposit answers with, and the arithmetic that ties them together: the base
     * is the whole euros, the total is the base multiplied by the rate and floored, and the bonus is
     * the difference. A customer can check the multiplication rather than take the total on trust.
     */
    @Test
    void a_deposit_reports_its_base_points_its_bonus_the_rate_and_the_total() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        // Savings back at their peak first. Euros that only fill a gap an earlier test's withdrawal
        // left have been saved once already and earn nothing, so a deposit measured from below the
        // peak would be measuring the order the test classes ran in.
        seeded.savingsBackAtTheirPeak(savingsAccount, ANKE);
        BalancesView before = balancesOf(savingsAccount);

        DepositView made = depositAccepted(savingsAccount, seeded.currentAccountOf(ANKE), "24.50");

        assertThat(made.basePoints()).as("one point per whole euro, the cents floored away")
                .isEqualTo(24);
        assertThat(made.multiplierApplied()).isBetween(new BigDecimal("1.00"), new BigDecimal("1.50"));
        assertThat(made.pointsEarned())
                .as("the whole euros at the rate the deposit was paid at, floored")
                .isEqualTo(new BigDecimal(made.basePoints())
                        .multiply(made.multiplierApplied())
                        .longValue());
        assertThat(made.basePoints() + made.streakBonusPoints())
                .as("the two parts always add up to the total")
                .isEqualTo(made.pointsEarned());
        // And the balance moved by exactly that total, base and bonus together.
        assertThat(balancesOf(savingsAccount).pointsBalance())
                .isEqualTo(before.pointsBalance() + made.pointsEarned());
    }

    /** The same four figures come back when the deposit is looked at again in the history. */
    @Test
    void the_history_reports_the_same_figures_the_deposit_was_answered_with() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);

        DepositView made = depositAccepted(savingsAccount, seeded.currentAccountOf(ANKE), "9.99");

        assertThat(Arrays.stream(depositsInto(savingsAccount))
                .filter(listed -> listed.id().equals(made.id()))
                .findFirst())
                .hasValueSatisfying(listed -> {
                    assertThat(listed.pointsEarned()).isEqualTo(made.pointsEarned());
                    assertThat(listed.basePoints()).isEqualTo(made.basePoints());
                    assertThat(listed.streakBonusPoints()).isEqualTo(made.streakBonusPoints());
                    assertThat(listed.multiplierApplied())
                            .isEqualByComparingTo(made.multiplierApplied());
                });
    }

    /**
     * The account says what the next deposit will earn at, beside the run of weeks it comes from.
     * Always a rung of the ladder: the ordinary rate, or that plus some number of tenths, and never
     * past the cap.
     */
    @Test
    void a_savings_account_reports_the_rate_it_is_currently_paying() {
        BalancesView account = balancesOf(seeded.savingsAccountOf(ANKE));

        assertThat(account.currentMultiplier())
                .isBetween(new BigDecimal("1.00"), new BigDecimal("1.50"));
        // A rung rather than anything in between: the ladder climbs in tenths from the ordinary rate.
        assertThat(account.currentMultiplier().subtract(new BigDecimal("1.00"))
                .remainder(new BigDecimal("0.10")))
                .isEqualByComparingTo("0.00");
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

    private DepositView[] depositsInto(long savingsAccountId) {
        return http.getForObject(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
    }

    private BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }
}
