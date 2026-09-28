package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;

/**
 * One part of a split as somebody sent it: an amount, and the category they want it filed under — or
 * no category at all.
 *
 * <p>Its own record rather than a pair, for the reason {@link ASpendAsAsked} is one: a part is a
 * thing with two fields where one of them is legitimately nothing, and a reader of
 * {@code (null, amount)} at a call site cannot tell a category nobody named from a category somebody
 * mistyped.
 *
 * <p><strong>A missing category is not a missing value.</strong> It means uncategorised, which is
 * exactly what a customer in a hurry records and exactly what a correction is for. A category that
 * <em>was</em> named and is not one of this account's standing ones is a different thing entirely,
 * and {@link SpendsService} refuses it in words that say which category it could not find.
 */
public record APartAsAsked(Long categoryId, BigDecimal amount) {
}
