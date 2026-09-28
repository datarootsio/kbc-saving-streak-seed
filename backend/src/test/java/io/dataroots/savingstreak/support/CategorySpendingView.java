package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * One spending category's month as the API reports it: what it was allowed to cost, what its bills
 * committed to it, what its holder chose to spend, the two added, and what that leaves.
 *
 * <p>Shared by every test that asks, for the same reason as {@link SpendingCategoryView} — copies of
 * a shape drift into disagreeing about it.
 *
 * <p>{@code budgeted}, {@code carriedIn}, {@code allowed} and {@code left} are boxed because they
 * are genuinely absent on a category nobody has put a figure on, and that is a claim tests here make
 * on purpose: a blank is not a nought, and a test reading a primitive would be unable to tell the
 * two apart — which is exactly the bug the distinction exists to prevent.
 *
 * <p>{@code carriedIn} is what the months before this one handed the category under their own rules,
 * and {@code allowed} is the budget and that carry together — which is the figure {@code left} is
 * taken from. Both may be negative on an envelope its holder overfilled, and a test asserting that
 * reads the negative figure back rather than a clamp at nought, because the consequence is the whole
 * reason anybody keeps envelopes.
 *
 * <p>{@code rollover} is the rule that stood in <em>this</em> month, as text, which is what lets a
 * test assert that a month already gone quotes the rule it was actually kept under rather than the
 * one standing now.
 */
public record CategorySpendingView(long categoryId, String name, String categoryState,
                                   String rollover, BigDecimal budgeted, BigDecimal carriedIn,
                                   BigDecimal allowed, BigDecimal committed,
                                   BigDecimal discretionary, BigDecimal spent, BigDecimal left,
                                   boolean overspent) {
}
