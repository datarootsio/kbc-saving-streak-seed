package io.dataroots.savingstreak.savingsgoals;

import java.util.List;

import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalMoveView;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money is moved from what no goal has claimed into a goal, back out of it, and straight from one
 * goal to another — and the savings balance never moves at all.
 *
 * <p>Every test here asserts the same sum as well as whatever it is about: what the goals have
 * claimed plus what no goal has claimed is the balance, exactly. That is the feature's one
 * invariant, and a test that checked only the goal it had just moved money into could watch it hold
 * money the account does not have.
 *
 * <p>The balance is read off the savings account itself as well as off the goals, because "the
 * balance is untouched" is the claim being made: a figure the goals module reported back could be
 * one it had copied, and the account overview is the deposits module's own answer.
 *
 * <p>Its own customer with its own money, for the reason {@link AnAccountWithGoals} gives: the run
 * shares one database, goals accumulate on an account, and a test asserting that an account has
 * 1000.00 to allocate cannot be sharing it with whatever the previous class saved.
 */
class MoneyIsAllocatedToAGoalAndFreedFromItApiTest extends ApiIntegrationTest {

    private AnAccountWithGoals account;

    @BeforeEach
    void anAccountWithMoneyInItAndNoGoalsYet() {
        account = new AnAccountWithGoals(http, "allocating");
        account.savesUp("1000.00");
    }

    @Test
    void with_no_goals_at_all_unallocated_is_the_whole_savings_balance() {
        AllocationsView allocations = account.allocations();

        assertThat(allocations.balance()).isEqualByComparingTo("1000.00");
        assertThat(allocations.allocated()).isEqualByComparingTo("0.00");
        assertThat(allocations.unallocated()).isEqualByComparingTo("1000.00");
        assertThat(allocations.goals()).isEmpty();
    }

    @Test
    void money_moved_into_a_goal_raises_its_allocation_and_lowers_unallocated() {
        GoalView holiday = account.add("Holiday", "600.00", null);

        AllocationsView after = account.putTowards(holiday.id(), "250.00");

        assertThat(after.allocated()).isEqualByComparingTo("250.00");
        assertThat(after.unallocated()).isEqualByComparingTo("750.00");
        assertThat(after.goal(holiday.id()).allocation()).isEqualByComparingTo("250.00");
        assertThat(after.goal(holiday.id()).stillNeeded()).isEqualByComparingTo("350.00");
        assertThat(after.goal(holiday.id()).status()).isEqualTo("STILL_SAVING");
        assertTheBalanceIsStill("1000.00", after);
    }

    @Test
    void money_freed_from_a_goal_lowers_its_allocation_and_raises_unallocated() {
        GoalView holiday = account.add("Holiday", "600.00", null);
        account.putTowards(holiday.id(), "250.00");

        AllocationsView after = account.free(holiday.id(), "100.00");

        assertThat(after.allocated()).isEqualByComparingTo("150.00");
        assertThat(after.unallocated()).isEqualByComparingTo("850.00");
        assertThat(after.goal(holiday.id()).allocation()).isEqualByComparingTo("150.00");
        assertTheBalanceIsStill("1000.00", after);
    }

    /**
     * The money never passes through being spare. Unallocated is the figure to watch: both goals
     * change and it does not, which is what "straight from one to the other" means and what a page
     * offering to reallocate has to be able to promise.
     */
    @Test
    void money_moved_straight_from_one_goal_to_another_changes_both_and_leaves_unallocated_alone() {
        GoalView holiday = account.add("Holiday", "600.00", null);
        GoalView house = account.add("House", "900.00", null);
        account.putTowards(holiday.id(), "300.00");
        AllocationsView before = account.allocations();

        AllocationsView after = account.moveBetween(holiday.id(), house.id(), "120.00");

        assertThat(after.goal(holiday.id()).allocation()).isEqualByComparingTo("180.00");
        assertThat(after.goal(house.id()).allocation()).isEqualByComparingTo("120.00");
        assertThat(after.allocated()).isEqualByComparingTo(before.allocated());
        assertThat(after.unallocated()).isEqualByComparingTo(before.unallocated());
        assertTheBalanceIsStill("1000.00", after);
    }

    /**
     * Arriving is derived from the ledger and the target rather than stored, so it is asserted on a
     * goal read back rather than on the answer to the move that got it there.
     */
    @Test
    void a_goal_whose_allocation_reaches_its_target_reports_completed() {
        GoalView bike = account.add("Bike", "400.00", null);

        account.putTowards(bike.id(), "400.00");

        GoalView reachedIt = account.goal(bike.id());
        assertThat(reachedIt.allocation()).isEqualByComparingTo("400.00");
        assertThat(reachedIt.stillNeeded()).isEqualByComparingTo("0.00");
        assertThat(reachedIt.status()).isEqualTo("COMPLETED");
        // Still live as far as the column goes: it was not given up on, and COMPLETED is a
        // comparison rather than something that happened to it.
        assertThat(reachedIt.state()).isEqualTo("LIVE");
    }

    /** Closed to money arriving, not sealed: a customer who over-allocated can have it back. */
    @Test
    void a_completed_goal_can_still_give_money_back() {
        GoalView bike = account.add("Bike", "400.00", null);
        account.putTowards(bike.id(), "400.00");

        AllocationsView after = account.free(bike.id(), "150.00");

        assertThat(after.goal(bike.id()).allocation()).isEqualByComparingTo("250.00");
        assertThat(after.goal(bike.id()).status()).isEqualTo("STILL_SAVING");
        assertThat(after.unallocated()).isEqualByComparingTo("750.00");
    }

    @Test
    void abandoning_a_goal_holding_money_returns_the_whole_of_it_to_unallocated_in_one_move() {
        GoalView holiday = account.add("Holiday", "600.00", null);
        account.putTowards(holiday.id(), "325.50");

        account.abandon(holiday.id());

        AllocationsView after = account.allocations();
        assertThat(after.allocated()).isEqualByComparingTo("0.00");
        assertThat(after.unallocated()).isEqualByComparingTo("1000.00");
        assertThat(account.goal(holiday.id()).allocation()).isEqualByComparingTo("0.00");
        assertThat(account.goal(holiday.id()).status()).isEqualTo("ABANDONED");
        assertTheBalanceIsStill("1000.00", after);

        List<GoalMoveView> history = account.historyOf(holiday.id());
        assertThat(history).hasSize(2);
        GoalMoveView freed = history.get(0);
        assertThat(freed.amount()).isEqualByComparingTo("325.50");
        assertThat(freed.outOfGoalId()).isEqualTo(holiday.id());
        assertThat(freed.intoGoalId())
                .describedAs("money given up on goes back to being claimed by no goal at all")
                .isNull();
    }

    /**
     * The sum, over a sequence long enough to have drifted: money in, money out, money between two
     * goals, a goal finished and a goal given up on.
     */
    @Test
    void after_a_sequence_of_moves_the_allocations_plus_unallocated_are_the_balance_exactly() {
        GoalView holiday = account.add("Holiday", "600.00", null);
        GoalView house = account.add("House", "900.00", null);
        GoalView bike = account.add("Bike", "120.00", null);

        account.putTowards(holiday.id(), "333.33");
        account.putTowards(house.id(), "250.00");
        account.putTowards(bike.id(), "120.00");
        account.free(holiday.id(), "33.33");
        account.moveBetween(house.id(), holiday.id(), "50.00");
        account.abandon(house.id());

        AllocationsView after = account.allocations();
        assertThat(after.goal(holiday.id()).allocation()).isEqualByComparingTo("350.00");
        assertThat(after.goal(bike.id()).allocation()).isEqualByComparingTo("120.00");
        assertThat(after.goal(bike.id()).status()).isEqualTo("COMPLETED");
        assertThat(after.allocated()).isEqualByComparingTo("470.00");
        assertThat(after.allocated().add(after.unallocated())).isEqualByComparingTo(after.balance());
        assertTheBalanceIsStill("1000.00", after);
    }

    /** Both ends of every move, newest first, so that a goal can say where its money came from. */
    @Test
    void a_goals_history_reports_the_moves_that_made_up_its_allocation_newest_first() {
        GoalView holiday = account.add("Holiday", "600.00", null);
        GoalView house = account.add("House", "900.00", null);
        account.putTowards(holiday.id(), "300.00");
        account.free(holiday.id(), "50.00");
        account.moveBetween(holiday.id(), house.id(), "70.00");

        List<GoalMoveView> history = account.historyOf(holiday.id());

        assertThat(history).hasSize(3);
        assertThat(history.get(0).amount()).isEqualByComparingTo("70.00");
        assertThat(history.get(0).outOfGoalId()).isEqualTo(holiday.id());
        assertThat(history.get(0).intoGoalId()).isEqualTo(house.id());
        assertThat(history.get(0).intoGoalName()).isEqualTo("House");
        assertThat(history.get(1).amount()).isEqualByComparingTo("50.00");
        assertThat(history.get(1).outOfGoalId()).isEqualTo(holiday.id());
        assertThat(history.get(1).intoGoalId()).isNull();
        assertThat(history.get(2).amount()).isEqualByComparingTo("300.00");
        assertThat(history.get(2).outOfGoalId()).isNull();
        assertThat(history.get(2).intoGoalId()).isEqualTo(holiday.id());
        assertThat(history.get(2).intoGoalName()).isEqualTo("Holiday");
        // The move between the two is in both goals' histories: it is one move, and each end of it
        // is part of the story of what that goal is holding.
        assertThat(account.historyOf(house.id())).hasSize(1);
    }

    /**
     * The balance the deposits module derives, and the balance the goals module was handed, are the
     * same figure — and neither of them moved.
     */
    private void assertTheBalanceIsStill(String expected, AllocationsView allocations) {
        assertThat(account.savingsBalance())
                .describedAs("moving money between goals is bookkeeping and moves no money")
                .isEqualByComparingTo(expected);
        assertThat(allocations.balance()).isEqualByComparingTo(expected);
        assertThat(allocations.allocated().add(allocations.unallocated()))
                .describedAs("the allocations plus what no goal has claimed are the balance")
                .isEqualByComparingTo(allocations.balance());
    }
}
