package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One current account's month as the API reports it: which month, the days it runs between, the
 * account's totals, and a row per category under them.
 *
 * <p>Shared by every test that asks, for the same reason as {@link MonthAheadView} — copies of a
 * shape drift into disagreeing about it. It is the shape both {@code /spending} and
 * {@code /spending/2026-03} answer with, so a test comparing this month against a month already gone
 * is comparing one thing.
 *
 * <p>{@code month} is text, {@code "2026-03"}, because that is what a month is; {@code from} and
 * {@code until} are the days it runs between, which is what lets a test assert that a read taken on
 * a wound clock is about the month the clock is actually in.
 */
public record MonthOfSpendingView(long currentAccountId, String month, LocalDate from,
                                  LocalDate until, BigDecimal budgeted, BigDecimal carriedIn,
                                  BigDecimal allowed, BigDecimal committed,
                                  BigDecimal discretionary, BigDecimal spent, BigDecimal left,
                                  BigDecimal uncategorised, List<CategorySpendingView> categories) {

    /**
     * One category's row out of the month, named by identifier.
     *
     * <p>An {@link Optional} rather than a row or a failure, because "this category is not in this
     * month at all" is one of the claims tests here make — an ended category with nothing in a month
     * is deliberately left out of it — and a helper that threw would make that assertion unwritable.
     */
    public Optional<CategorySpendingView> category(long categoryId) {
        return categories.stream().filter(row -> row.categoryId() == categoryId).findFirst();
    }

    /** The same row, for the tests whose subject is its figures rather than its presence. */
    public CategorySpendingView theCategory(long categoryId) {
        return category(categoryId).orElseThrow(() -> new AssertionError(
                "category " + categoryId + " is not in the month " + month + ", which holds "
                        + categories.stream().map(CategorySpendingView::name).toList()));
    }
}
