package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * One spending category over the last few months as the API reports it: a row per month it was live
 * in, the average of the months behind this one, and this month quoted against it.
 *
 * <p>Shared by every test that asks, for the same reason as {@link MonthOfSpendingView}.
 *
 * <p>{@code months} holds only the months the category existed for, which may be fewer than the
 * window — a category younger than six months is not padded with noughts and an ended one stops at
 * the month it was ended in. Both are claims tests here make on purpose, so the list is asserted on
 * by its length as well as by its contents.
 *
 * <p>{@code trailingAverage} and {@code comparedWithTheAverage} are boxed because a category with no
 * months behind it has no average, and an ended category has no row for this month to be quoted
 * against one. Absent is a state rather than a nought, and a test reading a primitive could not tell
 * "nothing to compare with yet" from "used to spend nothing".
 */
public record CategoryComparedView(long categoryId, String name, String categoryState,
                                   List<MonthComparedView> months, MonthComparedView thisMonth,
                                   BigDecimal trailingAverage, int monthsTheAverageIsOver,
                                   BigDecimal comparedWithTheAverage) {

    /**
     * One month's row out of this category's history, named by the month.
     *
     * <p>An {@link Optional} rather than a row or a failure, because "this category has no row for
     * that month at all" is one of the claims tests here make — a category younger than the window,
     * and an ended one, are deliberately short — and a helper that threw would make that assertion
     * unwritable.
     */
    public Optional<MonthComparedView> month(String yearMonth) {
        return months.stream().filter(row -> row.month().equals(yearMonth)).findFirst();
    }

    /** The same row, for the tests whose subject is its figures rather than its presence. */
    public MonthComparedView theMonth(String yearMonth) {
        return month(yearMonth).orElseThrow(() -> new AssertionError(
                name + " has no row for " + yearMonth + "; it holds "
                        + months.stream().map(MonthComparedView::month).toList()));
    }
}
