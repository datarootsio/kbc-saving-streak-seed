package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.goals.AllocationsOnAnAccount;

/**
 * What a savings account holds and how much of it its goals have spoken for: the balance, what the
 * goals have claimed altogether, what no goal has claimed, and every live goal with its own share.
 *
 * <p>One shape for reading the picture and for what a move gives back, so that a page that has just
 * moved money does not have to fetch the account again to see what it did — and so that the two can
 * never drift into describing the account differently.
 *
 * <p>{@code allocated} plus {@code unallocated} is {@code balance}, exactly, on every answer. That is
 * the invariant this feature is built on, and it is reported rather than asserted: both figures come
 * from the same read, and {@code unallocated} is derived from the other two rather than kept.
 */
record AllocationsResponse(long savingsAccountId, BigDecimal balance, BigDecimal allocated,
                           BigDecimal unallocated, List<GoalResponse> goals) {

    static AllocationsResponse of(AllocationsOnAnAccount allocations) {
        return new AllocationsResponse(
                allocations.savingsAccountId(),
                allocations.balance(),
                allocations.allocated(),
                allocations.unallocated(),
                allocations.goals().stream().map(GoalResponse::of).toList());
    }
}
