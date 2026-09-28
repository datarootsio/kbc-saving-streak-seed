package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a savings account holds and how much of it its goals have claimed, as the API reports it and
 * so as a test reads it: the balance, what is allocated, what no goal has claimed, and every live
 * goal with its own share.
 *
 * <p>Read back after every move, because the one thing every test about allocating asserts is that
 * {@code allocated} plus {@code unallocated} still equals {@code balance}. A test that read only the
 * goal it had just moved money into could not see the invariant at all.
 */
public record AllocationsView(Long savingsAccountId, BigDecimal balance, BigDecimal allocated,
                              BigDecimal unallocated, List<GoalView> goals) {

    /** The goal with this identifier, for a test asserting on what one goal ended up holding. */
    public GoalView goal(long goalId) {
        return goals.stream()
                .filter(goal -> goal.id() == goalId)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "goal " + goalId + " is not among the live goals on this account"));
    }
}
