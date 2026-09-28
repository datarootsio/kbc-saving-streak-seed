package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.budgets.CategoryState;
import io.dataroots.savingstreak.budgets.RolloverRule;
import io.dataroots.savingstreak.budgets.WhatACategoryCostInAMonth;

/**
 * One spending category's month as the API reports it: what it was allowed to cost, what its bills
 * committed to it, what its holder chose to spend, the two added, and what that leaves.
 *
 * <p>Every figure comes down already worked out, because the page draws a bar and a number and
 * decides nothing. {@code spent} is {@code committed + discretionary}, {@code allowed} is
 * {@code budgeted + carriedIn}, and {@code left} is {@code allowed - spent}, all to the cent, so
 * that a customer can check the row with a pencil and so that the account's card and the budget
 * screen cannot arrive at two different answers about one category.
 *
 * <p><strong>{@code carriedIn} is what the months before this one handed this category</strong>,
 * under the rule that stood in each of them, and {@code allowed} is what this month therefore
 * allows. Both are sent rather than left to the page: a bar drawn against {@code budgeted} on a
 * category carrying forty euros forward would call a month overspent that is nothing of the kind,
 * and a page adding the two itself would be a second place this application decides what a month
 * allows.
 *
 * <p><strong>{@code allowed} may be negative, and a page draws it as the negative figure it
 * is.</strong> An envelope overfilled last month leaves this one with less than nothing to spend;
 * that consequence is the whole reason anybody keeps envelopes, and a page clamping it at nought
 * would remove the mechanic while leaving the word.
 *
 * <p>{@code rollover} is the rule that stood in <em>this</em> month rather than the one standing
 * now, so a month already gone reads as the month its holder actually lived through. It is null
 * exactly where {@code budgeted} is, because there is no rule on a limit nobody set.
 *
 * <p><strong>{@code budgeted} and {@code left} are null when nothing was declared, and a page must
 * draw that as "no budget" rather than as a nought.</strong> The distinction is the whole of the
 * story a category with no figure tells: its spending is being watched and not policed, which is a
 * thing a customer is allowed to choose. {@code budgeted} rendered as 0.00 would tell somebody they
 * had overspent a limit they never set.
 *
 * <p>{@code left} may be negative and is sent as it is. A category can cost more than it was allowed
 * to — nothing refuses a spend for being over a budget, because a budget is a plan and the balance
 * is the only hard constraint here — and clamping it at nought would hide exactly the month a
 * customer needs to see.
 *
 * <p>{@code categoryState} is on it because a month can hold a category its holder ended halfway
 * through it: the spending happened, so the figure it was measured against is still reported, and
 * this is how a page says the word behind it is one they have stopped using.
 *
 * <p>Colour is not here and will not be. How to draw a category nearing its limit is a reading of
 * these figures and belongs to the page, the same bargain {@code NotificationReason} strikes about
 * severity.
 */
record CategorySpendingResponse(long categoryId, String name, CategoryState categoryState,
                                RolloverRule rollover, BigDecimal budgeted, BigDecimal carriedIn,
                                BigDecimal allowed, BigDecimal committed, BigDecimal discretionary,
                                BigDecimal spent, BigDecimal left, boolean overspent) {

    static CategorySpendingResponse of(WhatACategoryCostInAMonth category) {
        return new CategorySpendingResponse(category.categoryId(), category.name(),
                category.categoryState(), category.rollover(), category.budgeted(),
                category.carriedIn(), category.allowed(), category.committed(),
                category.discretionary(), category.spent(), category.left(),
                // Sent rather than left to the page, because "over" is a comparison against an
                // absent budget as well as a small one, and a page comparing left < 0 would call
                // every unbudgeted category overspent the moment it printed a null as a nought.
                category.isOverspent());
    }
}
