package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One move in a goal's history, as the rest of the application reads one: out of one goal or out of
 * what no goal had claimed, into another goal or back into what no goal claims, an amount, and when.
 *
 * <p>Both ends carry a name as well as an identifier, so that a page can say "out of Holiday, into
 * House" without fetching every goal on the account to look the numbers up — and so that a move out
 * of a goal that has since been given up on still reads as the thing it was. A null identifier and a
 * null name are <em>unallocated</em>: the part of the balance no goal has claimed, which is derived
 * and has no row to have a name in.
 *
 * <p>The amount is quoted to the cent on the way out, for the reason {@link RecordedGoal}'s target
 * is: it has been through SQLite, which has no decimal type and hands 50.00 back as 50.0.
 */
public record RecordedGoalMove(Long id, long savingsAccountId,
                               Long outOfGoalId, String outOfGoalName,
                               Long intoGoalId, String intoGoalName,
                               BigDecimal amount, Instant movedAt) {
}
