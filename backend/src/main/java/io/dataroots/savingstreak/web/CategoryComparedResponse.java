package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.budgets.CategoryState;
import io.dataroots.savingstreak.budgets.HowACategoryHasBeenGoing;

/**
 * One spending category over the last few months as the API reports it: a row per month, the average
 * of the months behind this one, and this month quoted against it.
 *
 * <p><strong>{@code months} holds only the months the category existed for</strong>, oldest first,
 * which may be fewer than the window the account's read names. A category declared two months ago
 * has two rows, and an ended one stops at the month it was ended in — neither is padded with
 * noughts, because a nought is a claim that somebody spent nothing on a thing that was not one of
 * the things they spent on. Drawing the short row against the account's column headings is the
 * page's job, and it has both.
 *
 * <p><strong>{@code trailingAverage} is over the months before this one and is null when there are
 * none.</strong> An average that included this month would be comparing a month with itself, and a
 * category in its first month has nothing behind it — which is an absence and not a nought, the same
 * reading the declared saving capacity takes. A page must draw it as "nothing to compare with yet"
 * rather than as EUR 0.00, which would say that the customer used to spend nothing.
 *
 * <p>{@code monthsTheAverageIsOver} says how many months actually went into it — between one and
 * three — because a customer told "EUR 92.00 on average" wants to know whether that is three months
 * or one, and a page counting the rows itself would be working out something already known here.
 *
 * <p><strong>{@code comparedWithTheAverage} is this month's spending less that average</strong>:
 * positive for a month costing more than usual, negative for one costing less. Sent rather than left
 * to the page, for the reason every other figure in this feature is — two screens subtracting it
 * themselves would be two places this application decides what "more than usual" means. It is null
 * where the average is, and where this month has no row at all, which is an ended category.
 *
 * <p>{@code thisMonth} is the row for the month the clock is in, repeated out of {@code months} so
 * that a page drawing the headline figure does not have to search the list for it. It is null on a
 * category that is not live this month, which is the honest answer rather than a row of noughts.
 *
 * <p>Colour is not here and will not be. How to draw a month that cost more than usual is a reading
 * of these figures and belongs to the page, the same bargain {@code NotificationReason} strikes
 * about severity.
 */
record CategoryComparedResponse(long categoryId, String name, CategoryState categoryState,
                                List<MonthComparedResponse> months, MonthComparedResponse thisMonth,
                                BigDecimal trailingAverage, int monthsTheAverageIsOver,
                                BigDecimal comparedWithTheAverage) {

    static CategoryComparedResponse of(HowACategoryHasBeenGoing category) {
        return new CategoryComparedResponse(category.categoryId(), category.name(),
                category.categoryState(),
                category.months().stream().map(MonthComparedResponse::of).toList(),
                category.thisMonth() == null ? null : MonthComparedResponse.of(category.thisMonth()),
                category.trailingAverage(), category.monthsTheAverageIsOver(),
                category.comparedWithTheAverage());
    }
}
