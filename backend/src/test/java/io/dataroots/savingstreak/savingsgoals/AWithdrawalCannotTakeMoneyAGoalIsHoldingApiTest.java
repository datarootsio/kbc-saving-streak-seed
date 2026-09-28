package io.dataroots.savingstreak.savingsgoals;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsgoals.AnAccountWithGoals.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A withdrawal draws on what no goal has claimed, and stops there.
 *
 * <p>Beyond that it is refused and the customer is told to free money from a goal first. Nothing is
 * taken off a goal on their behalf: reallocating somebody's money without being asked is the one
 * thing this whole feature exists to refuse, and doing it to the lowest-ranked goal would be no
 * better for being rule-governed. What moves a completion date is the freeing, and the customer does
 * that looking straight at the goal they are taking from.
 *
 * <p>Every test here reads the account's money back three ways — the savings balance the deposits
 * module derives, the current account the money would have landed in, and the allocations the goals
 * report — because a refusal that took the money anyway and a refusal that took it off a goal are
 * two different bugs and neither is visible in a status code.
 *
 * <p>Its own customer with its own money, for the reason {@link AnAccountWithGoals} gives: the run
 * shares one database and goals accumulate on an account.
 */
class AWithdrawalCannotTakeMoneyAGoalIsHoldingApiTest extends ApiIntegrationTest {

    private AnAccountWithGoals account;

    @BeforeEach
    void anAccountWithAThousandInIt() {
        account = new AnAccountWithGoals(http, "withdrawing");
        account.savesUp("1000.00");
    }

    @Test
    void a_withdrawal_within_what_no_goal_has_claimed_is_made_and_lowers_unallocated() {
        GoalView holiday = account.add("Holiday", "800.00", null);
        account.putTowards(holiday.id(), "600.00");
        BigDecimal currentBefore = account.currentAccountBalance();

        account.withdraw("250.00");

        assertThat(account.savingsBalance()).isEqualByComparingTo("750.00");
        assertThat(account.currentAccountBalance())
                .isEqualByComparingTo(currentBefore.add(new BigDecimal("250.00")));
        AllocationsView after = account.allocations();
        assertThat(after.allocated()).isEqualByComparingTo("600.00");
        assertThat(after.unallocated()).isEqualByComparingTo("150.00");
        assertThat(after.goal(holiday.id()).allocation()).isEqualByComparingTo("600.00");
        assertTheSumStillAddsUp(after);
    }

    @Test
    void a_withdrawal_larger_than_unallocated_but_within_the_balance_is_refused_quoting_both_figures() {
        GoalView holiday = account.add("Holiday", "800.00", null);
        account.putTowards(holiday.id(), "600.00");

        ResponseEntity<JsonNode> refused = account.tryToWithdraw("500.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .describedAs("a customer who has to free money first needs to know how much is spare")
                .contains("400.00")
                .contains("1000.00")
                .contains("500.00")
                .contains("Free money from a goal first");
    }

    /**
     * The older refusal wins, because it is the more basic truth. Being told what is unallocated
     * first would send somebody off to free money from a goal that was never going to be enough —
     * the account does not hold the figure they typed at all.
     */
    @Test
    void a_withdrawal_larger_than_the_balance_is_still_refused_for_the_balance() {
        GoalView holiday = account.add("Holiday", "800.00", null);
        account.putTowards(holiday.id(), "600.00");

        ResponseEntity<JsonNode> refused = account.tryToWithdraw("1500.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains("There is not enough in that savings account")
                .contains("1500.00")
                .contains("1000.00");
        assertThat(reasonGivenBy(refused))
                .describedAs("the goals have nothing to say about money the account does not hold")
                .doesNotContain("Free money from a goal first");
    }

    @Test
    void a_refused_withdrawal_moves_no_money_and_leaves_every_goal_holding_what_it_held() {
        GoalView holiday = account.add("Holiday", "500.00", null);
        GoalView roof = account.add("New roof", "500.00", null);
        account.putTowards(holiday.id(), "400.00");
        account.putTowards(roof.id(), "450.00");
        BigDecimal currentBefore = account.currentAccountBalance();
        long pointsBefore = account.savings().pointsBalance();

        ResponseEntity<JsonNode> refused = account.tryToWithdraw("300.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(account.savingsBalance()).isEqualByComparingTo("1000.00");
        assertThat(account.currentAccountBalance()).isEqualByComparingTo(currentBefore);
        assertThat(account.savings().pointsBalance()).isEqualTo(pointsBefore);
        AllocationsView after = account.allocations();
        assertThat(after.goal(holiday.id()).allocation()).isEqualByComparingTo("400.00");
        assertThat(after.goal(roof.id()).allocation()).isEqualByComparingTo("450.00");
        assertThat(after.allocated()).isEqualByComparingTo("850.00");
        assertThat(after.unallocated()).isEqualByComparingTo("150.00");
        assertTheSumStillAddsUp(after);
        assertThat(account.historyOf(holiday.id()))
                .describedAs("a refusal that wrote a ledger row would be a reallocation nobody asked for")
                .hasSize(1);
    }

    /**
     * The promise that makes this ticket safe. A customer who never opens a goal has claimed nothing,
     * so everything is unallocated and every withdrawal they could make before this feature existed
     * is still the withdrawal they make now — including emptying the account to the last cent.
     */
    @Test
    void on_an_account_with_no_goals_a_withdrawal_behaves_exactly_as_it_did_before() {
        BigDecimal currentBefore = account.currentAccountBalance();
        assertThat(account.allocations().goals()).isEmpty();

        account.withdraw("1000.00");

        assertThat(account.savingsBalance()).isEqualByComparingTo("0.00");
        assertThat(account.currentAccountBalance())
                .isEqualByComparingTo(currentBefore.add(new BigDecimal("1000.00")));
        AllocationsView after = account.allocations();
        assertThat(after.allocated()).isEqualByComparingTo("0.00");
        assertThat(after.unallocated()).isEqualByComparingTo("0.00");
        assertTheSumStillAddsUp(after);
    }

    @Test
    void freeing_the_money_from_a_goal_first_lets_the_same_withdrawal_through() {
        GoalView holiday = account.add("Holiday", "800.00", null);
        account.putTowards(holiday.id(), "800.00");
        ResponseEntity<JsonNode> refused = account.tryToWithdraw("300.00");
        assertThat(refused.getStatusCode())
                .describedAs("with 200.00 spare, 300.00 is the withdrawal this feature refuses")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        account.free(holiday.id(), "100.00");
        account.withdraw("300.00");

        assertThat(account.savingsBalance()).isEqualByComparingTo("700.00");
        AllocationsView after = account.allocations();
        assertThat(after.goal(holiday.id()).allocation()).isEqualByComparingTo("700.00");
        assertThat(after.allocated()).isEqualByComparingTo("700.00");
        assertThat(after.unallocated()).isEqualByComparingTo("0.00");
        assertTheSumStillAddsUp(after);
    }

    /**
     * A withdrawal is not saving, so it earns nothing and takes nothing back. What it does do is
     * count against the week it was made in, which is the streak's business and not this feature's —
     * the figures here are the ones a withdrawal has always moved.
     */
    @Test
    void a_withdrawal_that_goes_through_still_touches_no_points_and_still_counts_against_the_week() {
        GoalView holiday = account.add("Holiday", "400.00", null);
        account.putTowards(holiday.id(), "400.00");
        long pointsBefore = account.savings().pointsBalance();
        BigDecimal newSavingsBefore = account.savings().newSavingsThisWeek();

        account.withdraw("200.00");

        assertThat(account.savings().pointsBalance())
                .describedAs("a euro earns its point when it lands, which is upstream of any goal")
                .isEqualTo(pointsBefore);
        assertThat(account.savings().newSavingsThisWeek())
                .isEqualByComparingTo(newSavingsBefore.subtract(new BigDecimal("200.00")));
    }

    /** The invariant, asserted after every one of these: what is claimed plus what is not is all of it. */
    private static void assertTheSumStillAddsUp(AllocationsView allocations) {
        List<GoalView> goals = allocations.goals();
        BigDecimal claimedByTheGoals = goals.stream()
                .map(GoalView::allocation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocations.allocated()).isEqualByComparingTo(claimedByTheGoals);
        assertThat(allocations.allocated().add(allocations.unallocated()))
                .isEqualByComparingTo(allocations.balance());
    }
}
