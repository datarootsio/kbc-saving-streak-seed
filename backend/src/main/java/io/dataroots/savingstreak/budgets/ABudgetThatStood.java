package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;

/**
 * The figure that governed one category in one month, and the rule that month handed its difference
 * on under.
 *
 * <p><strong>The two together, because the fold needs both and they must have come from one
 * row.</strong> Two parallel maps — one of amounts, one of rules — would be two answers to "what
 * stood in March" that a later reader could index differently, and the month that quoted one row's
 * amount beside another row's rule would be a month nobody could reconcile. A budget is superseded
 * as a whole, so it is read as a whole.
 *
 * <p>A value rather than the entity, for the reason everything else that leaves {@link MonthlyBudget}
 * is: {@link WhatCarriesIntoAMonth} is a pure function over months and has no business holding a row
 * that a transaction could still be writing to.
 *
 * <p>Package-private, and it stays that way. What leaves the module about a budget is
 * {@link ABudgetOnACategory}, and what leaves about a month is {@link WhatACategoryCostInAMonth};
 * this is the shape the two faces inside the module pass the fold's input in.
 */
record ABudgetThatStood(BigDecimal amount, RolloverRule rollover) {

    /** The row as the fold reads it, with a rule that predates the column read as the default. */
    static ABudgetThatStood of(MonthlyBudget budget) {
        return new ABudgetThatStood(budget.getAmount(), budget.getRollover());
    }
}
