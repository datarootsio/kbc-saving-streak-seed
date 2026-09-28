package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.automation.WhatAGoalWouldGet;

/**
 * One line of a rule's split as a preview reports it: which goal, the whole percentage its holder
 * gave it, and what that share comes to out of the figure being quoted.
 *
 * <p>{@link SavingRuleAllocationResponse} is the same sentence in the past tense, and the two are
 * separate on purpose. That one is what a goal actually received on a night the money moved, after a
 * nearly-complete goal took only what it still needed; this one is the instruction, priced out at a
 * figure nobody has moved yet. A preview claiming to know how full a goal will be eleven months out
 * would be inventing the figure the whole preview refuses to invent — so this says where the money
 * <em>would</em> land by the customer's own instruction, and the occurrence says where it went.
 *
 * <p>The share is here beside the amount because the percentage is what was promised, and the amount
 * is that percentage applied to a figure which is itself only as certain as
 * {@code anIllustrationRatherThanAPromise} on the surrounding record says it is.
 *
 * <p>Every line of the split is reported, including one whose share comes to nothing out of a small
 * figure — the opposite of {@link SavingRuleAllocationResponse}, which leaves out a goal that took
 * nothing because nothing happened. Here nothing has happened to any of them yet.
 *
 * <p>The amount is quoted to the cent, because it has been through SQLite, which has no decimal type
 * and holds an amount as a float.
 */
record SavingRuleGoalShareResponse(long goalId, int share, BigDecimal amount) {

    static SavingRuleGoalShareResponse of(WhatAGoalWouldGet wouldGet) {
        return new SavingRuleGoalShareResponse(wouldGet.goalId(), wouldGet.share(),
                wouldGet.amount());
    }
}
