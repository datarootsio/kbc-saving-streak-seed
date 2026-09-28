package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * One month of one category as the comparison reads it: which month it was, what stood over it, what
 * it cost, and the difference between the two.
 *
 * <p><strong>The same figures as {@link WhatACategoryCostInAMonth}, with the month said out
 * loud.</strong> A month read on its own is asked about by name, so the answer does not have to
 * repeat which month it is; six of them in a row are a list, and a row that did not say which month
 * it was would be a row a page had to name from its position in an array. That is the whole of the
 * difference, and it is why this is a record of its own rather than a field added to the month read
 * — every screen drawing one month would then carry a month it already knew.
 *
 * <p><strong>The arithmetic is not done here and never will be.</strong> {@link #of} takes a
 * {@link WhatACategoryCostInAMonth} that has already been assembled by the one factory that adds
 * these figures up, and copies it. A second subtraction here would be a second answer to what a
 * category's month cost, and the month card and the comparison beside it would disagree the first
 * time either changed — which is precisely the bug a customer comparing this month with the last few
 * would be the first to find.
 *
 * <p><strong>{@code budgeted} is null when no figure stood in this month, and that is a state rather
 * than a nought.</strong> It is the point of the whole read: a month before a customer had a budget
 * shows what they spent and says plainly that there was no budget, because a blank read as a zero
 * would tell them they had overspent a limit they never set. The precedent is
 * {@code SavingCapacityOnAnAccount} — not declared is not zero — and {@code rollover},
 * {@code carriedIn}, {@code allowed} and {@code left} are absent with it, absent together or present
 * together, exactly as they are on the month read this is copied from.
 *
 * <p>{@code allowed} and {@code left} may be negative, on an envelope its holder overfilled and on a
 * month that cost more than it was allowed to. Both are reported as the negative figures they are,
 * for the reason {@link WhatACategoryCostInAMonth} gives at length: clamping either at nought would
 * hide exactly the month the comparison exists to show.
 */
public record AMonthInTheComparison(YearMonth month, RolloverRule rollover, BigDecimal budgeted,
                                    BigDecimal carriedIn, BigDecimal allowed, BigDecimal committed,
                                    BigDecimal discretionary, BigDecimal spent, BigDecimal left,
                                    boolean overspent) {

    /**
     * One month of the comparison, out of the month read that has already worked its figures out.
     *
     * @param month   the month these figures are about, which is what makes a row in a list of six
     *                readable on its own
     * @param figures that month as {@link WhatACategoryCostInAMonth} assembled it — the one place
     *                {@code spent}, {@code allowed} and {@code left} are ever worked out
     */
    static AMonthInTheComparison of(YearMonth month, WhatACategoryCostInAMonth figures) {
        return new AMonthInTheComparison(month, figures.rollover(), figures.budgeted(),
                figures.carriedIn(), figures.allowed(), figures.committed(),
                figures.discretionary(), figures.spent(), figures.left(), figures.isOverspent());
    }

    /**
     * Whether a figure was ever declared for this category in this month, which is what tells an
     * absent budget from a small one.
     *
     * <p>Asked here rather than by every screen comparing {@code budgeted} against null, for the
     * reason {@code WhatACategoryCostInAMonth.isBudgeted} and
     * {@code SavingCapacityOnAnAccount.isDeclared} both exist: the comparison is made once so that
     * it cannot be made two ways.
     */
    public boolean isBudgeted() {
        return budgeted != null;
    }
}
