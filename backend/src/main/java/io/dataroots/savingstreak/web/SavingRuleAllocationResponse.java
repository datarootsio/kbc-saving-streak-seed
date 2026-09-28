package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.automation.WhatAGoalGot;

/**
 * What one goal actually received out of one firing of a rule, as the API reports it.
 *
 * <p>What it <em>received</em>, which is not what the rule's split offered it: a goal that was
 * nearly complete takes the part it still needed and the rest spills to the next goal in the split.
 * A page redoing the percentages against the rule as it reads today would draw a different answer
 * from the one the money took, which is why this is reported rather than left to be worked out.
 *
 * <p>A goal that took nothing is not in the list. Nothing moved and nothing was written in the goals
 * ledger either, and an entry of 0.00 would be this API claiming a movement that never happened.
 * What no goal in the split would have is the occurrence's {@code leftUnallocated}, which is one
 * figure for all of it.
 *
 * <p>The amount is quoted to the cent, because it has been through SQLite, which has no decimal type
 * and holds an amount as a float — so 30.00 comes back as 30.0 and would reach a page as a number
 * rather than as money.
 */
record SavingRuleAllocationResponse(long goalId, BigDecimal amount) {

    static SavingRuleAllocationResponse of(WhatAGoalGot got) {
        return new SavingRuleAllocationResponse(got.goalId(), got.amount());
    }
}
