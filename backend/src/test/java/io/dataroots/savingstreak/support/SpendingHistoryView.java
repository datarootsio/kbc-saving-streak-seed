package io.dataroots.savingstreak.support;

import java.util.List;
import java.util.Optional;

/**
 * One current account's last few months as the API reports it: which months they are, and a row of
 * history per category.
 *
 * <p>Shared by every test that asks, for the same reason as {@link MonthOfSpendingView}. It is the
 * shape {@code /spending/history} answers with, which is one request for the whole comparison rather
 * than one per month.
 *
 * <p>{@code months} is the window every category is drawn against, oldest first, as {@code "2026-03"}
 * each — which is what lets a test assert that a read taken on a wound clock covers the six months
 * the clock is actually in and not the six a wall clock would have named.
 */
public record SpendingHistoryView(long currentAccountId, String month, String earliest,
                                  List<String> months, List<CategoryComparedView> categories) {

    /**
     * One category's history out of the account's, named by identifier.
     *
     * <p>An {@link Optional}, because "this category is not in the comparison at all" is a claim a
     * test makes on purpose: a category ended before the window began is a record of months this
     * read is not about.
     */
    public Optional<CategoryComparedView> category(long categoryId) {
        return categories.stream().filter(row -> row.categoryId() == categoryId).findFirst();
    }

    /** The same row, for the tests whose subject is its figures rather than its presence. */
    public CategoryComparedView theCategory(long categoryId) {
        return category(categoryId).orElseThrow(() -> new AssertionError(
                "category " + categoryId + " is not in the comparison, which holds "
                        + categories.stream().map(CategoryComparedView::name).toList()));
    }
}
