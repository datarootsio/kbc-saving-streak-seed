package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.budgets.AMonthInTheComparison;
import io.dataroots.savingstreak.budgets.RolloverRule;

/**
 * One month of one category as the comparison reports it: which month, what stood over it, what it
 * cost, and the difference.
 *
 * <p>The same figures {@link CategorySpendingResponse} carries, with the month said out loud,
 * because six of these are a list and a row that did not name its month would be a row a page had to
 * name from its position in an array.
 *
 * <p><strong>{@code month} travels as {@code "2026-03"}</strong>, which is what a month is, matching
 * {@link MonthOfSpendingResponse}. A date would invite a page to print a day nobody meant.
 *
 * <p><strong>{@code budgeted}, {@code carriedIn}, {@code allowed} and {@code left} are null in a
 * month no figure stood in, and a page must draw that as "no budget" rather than as a nought.</strong>
 * That is the whole of what this read exists to say about the months before a customer had a budget:
 * the spending is a fact and is reported, and the absence of a limit is reported as an absence. A
 * blank drawn as 0.00 would tell somebody they had overspent a figure they never set, which is
 * exactly the wrong lesson to take from their own history.
 *
 * <p>{@code allowed} and {@code left} may be negative and are sent as they are, for the reason the
 * month's own response gives: an envelope overfilled leaves the next month allowing less than
 * nothing, and a month can plainly cost more than it was allowed to.
 *
 * <p>{@code rollover} is the rule that stood in <em>this</em> month rather than the one standing
 * now, so a row already gone reads as the month its holder actually lived through.
 */
record MonthComparedResponse(String month, RolloverRule rollover, BigDecimal budgeted,
                             BigDecimal carriedIn, BigDecimal allowed, BigDecimal committed,
                             BigDecimal discretionary, BigDecimal spent, BigDecimal left,
                             boolean overspent) {

    static MonthComparedResponse of(AMonthInTheComparison month) {
        return new MonthComparedResponse(month.month().toString(), month.rollover(),
                month.budgeted(), month.carriedIn(), month.allowed(), month.committed(),
                month.discretionary(), month.spent(), month.left(), month.overspent());
    }
}
