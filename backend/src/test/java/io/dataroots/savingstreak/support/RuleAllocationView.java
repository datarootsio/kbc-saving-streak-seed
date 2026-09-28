package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * What one goal actually received out of one firing of a rule, as the API reports it — which is
 * exactly as a test reads it.
 *
 * <p>A goal that took nothing has no entry at all, so a test asserting on the size of this list is
 * asserting a real part of the contract: nothing moved, nothing was written in the goals ledger, and
 * a 0.00 would claim a movement that never happened.
 */
public record RuleAllocationView(long goalId, BigDecimal amount) {
}
