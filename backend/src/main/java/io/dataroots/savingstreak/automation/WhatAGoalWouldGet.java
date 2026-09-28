package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;

/**
 * One line of a rule's split as a forecast reads it: which goal, the whole percentage its holder
 * gave it, and what that share comes to out of the figure being quoted.
 *
 * <p><strong>{@link WhatAGoalGot} is the same sentence in the past tense, and the two are separate
 * on purpose.</strong> That one is the record — what a goal actually received on a night the money
 * moved, after a nearly-complete goal took only what it still needed and the rest spilled down the
 * split. This one is the instruction — what the customer said should happen — priced out at a figure
 * nobody has moved yet. A forecast that claimed to be a record would be claiming to know how full
 * each goal will be on a morning eleven months out, and it does not.
 *
 * <p>So this says what the split <em>says</em> and stops there. The share is here beside the amount
 * for that reason: the percentage is the promise, and the amount is the percentage applied to a
 * figure which is itself only as certain as {@link WhatWouldMove} says it is.
 *
 * <p>The cents come from {@link HowAnAmountIsSplit}, which is the same function the firing hands the
 * money to, so a preview and the transfer it predicts cannot disagree about which goal the leftover
 * cent belongs to. Every line of the split is here, including one whose share comes to nothing out
 * of a small figure, because this is the instruction being read back rather than a ledger of
 * movements — the opposite of {@link WhatAGoalGot}, which leaves out a goal that took nothing
 * because nothing happened.
 *
 * <p>Public, unlike {@link RuleSplit}, because {@link WhatWouldMove} carries it out of the module.
 */
public record WhatAGoalWouldGet(long goalId, int share, BigDecimal amount) {
}
