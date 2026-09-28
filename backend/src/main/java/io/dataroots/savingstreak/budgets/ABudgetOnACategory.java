package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

/**
 * One monthly budget as its holder finds it: which category it is on, what that category is allowed
 * to cost, the first month the figure governs, the last one if it has stopped, and what became of
 * it.
 *
 * <p>A record rather than the entity, like everything that leaves this module.
 *
 * <p><strong>The category's name travels with it</strong>, for the reason it travels on
 * {@link TheCategoryABillIsIn}: a figure beside an identifier is a figure nobody can act on, and the
 * page that has just declared a budget draws it without going back for the word.
 *
 * <p><strong>{@code effectiveFrom} and {@code stoodThrough} are months and are sent rather than left
 * to be inferred</strong>, the same bargain {@code TheMonthAhead} strikes about the ends of its
 * window. They are what makes a superseded budget readable as the history it is — "this was your
 * figure from March to May" — and a page working out the second of them from the row that replaced
 * it would be doing arithmetic this module has already done.
 *
 * <p>{@code stoodThrough} is nothing at all while the budget stands, which is not a missing value:
 * a figure in force has no last month, because the customer has not said there is one.
 *
 * <p>{@code amount} is quoted to the cent here, once, where the record leaves the module. SQLite has
 * no decimal type and hands EUR 250.00 back as 250.0, and a caller printing one would otherwise
 * print a figure that does not read as money.
 *
 * <p><strong>{@code rollover} is on it because it is part of the figure rather than beside it.</strong>
 * What a category is allowed to cost and what becomes of the difference are one declaration and are
 * superseded together, so a page that has just named a figure reads back both halves of what it
 * said. There is no carry on it: what carried into a month is a fact about that month and about the
 * months before it, and it belongs on {@link WhatACategoryCostInAMonth}, which is the read that
 * knows which month is being asked about.
 */
public record ABudgetOnACategory(long budgetId, long currentAccountId, long categoryId,
                                 String categoryName, BigDecimal amount, RolloverRule rollover,
                                 YearMonth effectiveFrom, YearMonth stoodThrough, BudgetState state,
                                 Instant declaredAt, Instant stoodDownAt) {

    /**
     * The row as the rest of the application reads it, with the category it is on beside it. The
     * one place the entity becomes a record.
     */
    static ABudgetOnACategory of(MonthlyBudget budget, SpendingCategory category) {
        return new ABudgetOnACategory(budget.getId(), budget.getCurrentAccountId(),
                budget.getCategoryId(), category.getName(),
                AmountOfMoney.quotedToTheCent(budget.getAmount()), budget.getRollover(),
                budget.effectiveFrom(), budget.stoodThrough(), budget.getState(),
                budget.getDeclaredAt(), budget.getStoodDownAt());
    }
}
