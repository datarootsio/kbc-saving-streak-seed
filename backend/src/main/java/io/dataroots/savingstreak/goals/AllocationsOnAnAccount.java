package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a savings account holds and how much of it the goals have spoken for: the balance it was
 * handed, what the goals have claimed altogether, what no goal has claimed, and every live goal with
 * its own share.
 *
 * <p><strong>The balance is a figure this module was handed and not one it fetched.</strong> Goals
 * reads no other module — Deposits has to ask Goals whether a withdrawal may take money a goal is
 * holding, and a Goals that asked Deposits for a balance would be a cycle the application context
 * could not start — so whoever calls supplies it, and it travels back out here beside the figures
 * derived from it so that nothing downstream has to pair them up again.
 *
 * <p><strong>{@code unallocated} is {@code balance − allocated}, worked out on every read and stored
 * nowhere.</strong> Two stored figures that have to agree eventually stop agreeing, and this is the
 * pair that would: every move writes one ledger row and a stored unallocated would have to be
 * written in the same breath, forever, without ever being missed.
 *
 * <p>It can be negative, and is reported as it falls rather than floored at zero. A balance that
 * dropped below what the goals had claimed is a real state of affairs somebody has to see, and a
 * floor would have {@code allocated + unallocated} quietly stop adding up to the balance — which is
 * the one sum anybody reading this record is checking.
 */
public record AllocationsOnAnAccount(long savingsAccountId, BigDecimal balance, BigDecimal allocated,
                                     BigDecimal unallocated, List<RecordedGoal> goals) {
}
