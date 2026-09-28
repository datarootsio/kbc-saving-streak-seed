package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.budgets.ABudgetOnACategory;
import io.dataroots.savingstreak.budgets.BudgetState;
import io.dataroots.savingstreak.budgets.RolloverRule;

/**
 * One monthly budget as the API reports it: which category it is on, what that category is allowed
 * to cost, the months the figure governs, and what became of it.
 *
 * <p>One shape for what declaring a budget gives back and for what stopping one leaves behind — the
 * same bargain {@link SpendingCategoryResponse} and {@link CategorisedBillResponse} strike — so that
 * a page which has just named a figure does not have to fetch a month back to see what it did.
 *
 * <p><strong>The months travel as text, not as a date.</strong> {@code "2026-03"} is what a month
 * is: writing it as the first of the month would invite a page to print a day nobody meant, and
 * writing it as a number would invite arithmetic on it. It is the same shape the month read sends
 * its own month in, so that a page comparing the two is comparing strings that are equal when the
 * months are.
 *
 * <p>{@code stoodThrough} is null while the budget stands, which is not a missing value: a figure in
 * force has no last month, because the customer has not said there is one. {@code state} is what a
 * page switches on and is deliberately not inferred from that null, for the reason a category's
 * state is not inferred from its ending moment — and it is the field that tells a figure replaced by
 * a better one from a category its holder has stopped policing.
 *
 * <p><strong>{@code rollover} is sent back because it is half of what was declared.</strong> A page
 * that has just changed a rule draws the rule it now has without fetching a month to find out, and a
 * superseded row quotes the rule it governed its months under — which is what makes the record of a
 * customer changing their mind readable as the history it is.
 *
 * <p>Nothing here says what the category has actually cost, or what carried into it. Those are
 * questions about a month, they are derived from the spends, the bill occurrences and the months
 * before, and they come back from {@code /spending} with the month named beside them.
 */
record MonthlyBudgetResponse(long budgetId, long currentAccountId, long categoryId,
                             String categoryName, BigDecimal amount, RolloverRule rollover,
                             String effectiveFrom, String stoodThrough, BudgetState state,
                             Instant declaredAt, Instant stoodDownAt) {

    static MonthlyBudgetResponse of(ABudgetOnACategory budget) {
        return new MonthlyBudgetResponse(budget.budgetId(), budget.currentAccountId(),
                budget.categoryId(), budget.categoryName(), budget.amount(), budget.rollover(),
                budget.effectiveFrom().toString(),
                budget.stoodThrough() == null ? null : budget.stoodThrough().toString(),
                budget.state(), budget.declaredAt(), budget.stoodDownAt());
    }
}
