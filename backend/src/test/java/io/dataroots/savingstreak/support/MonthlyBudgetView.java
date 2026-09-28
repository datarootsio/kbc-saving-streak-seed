package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One monthly budget as the API reports it: which category it is on, what that category is allowed
 * to cost, the months the figure governs, and what became of it.
 *
 * <p>Shared by every test that asks, for the same reason as {@link SpendingCategoryView} — copies of
 * a shape drift into disagreeing about it. It is the shape both declaring a budget and stopping one
 * answer with, so a test that names a figure and then stops it is comparing one thing.
 *
 * <p>{@code effectiveFrom} and {@code stoodThrough} are months written as text, {@code "2026-03"},
 * because that is what a month is. A test asserting that a figure superseded in June still governs
 * April reads exactly those two fields rather than working the answer out from a date.
 *
 * <p>{@code state} is what tells a figure in force from one replaced by a better one and from one
 * its holder stopped, which is the fact a test about superseding has to be able to read without
 * inferring it from a null.
 *
 * <p>{@code rollover} is what the customer said should happen to the difference when a month this
 * figure governs ends, as text — {@code "NOTHING_ROLLS_OVER"} and its two siblings — because a test
 * asserting that changing a rule superseded the figure reads the rule off the row that now stands
 * rather than inferring it from a carry two months later.
 */
public record MonthlyBudgetView(long budgetId, long currentAccountId, long categoryId,
                                String categoryName, BigDecimal amount, String rollover,
                                String effectiveFrom, String stoodThrough, String state,
                                Instant declaredAt, Instant stoodDownAt) {
}
