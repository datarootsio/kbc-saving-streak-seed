package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * One month of one category as the comparison reports it: which month, what stood over it, what it
 * cost, and the difference.
 *
 * <p>Shared by every test that asks, for the same reason as {@link CategorySpendingView} — copies of
 * a shape drift into disagreeing about it.
 *
 * <p>{@code month} is text, {@code "2026-03"}, because that is what a month is and because a test
 * asserting which six months came back is asserting about months rather than about days.
 *
 * <p>{@code budgeted}, {@code carriedIn}, {@code allowed} and {@code left} are boxed because they
 * are genuinely absent in a month no figure stood in, and that is the claim this whole read exists
 * to make: a month before the customer had a budget shows what they spent and says plainly that
 * there was no budget. A test reading a primitive would be unable to tell an absent figure from a
 * nought, which is exactly the bug the distinction exists to prevent.
 */
public record MonthComparedView(String month, String rollover, BigDecimal budgeted,
                                BigDecimal carriedIn, BigDecimal allowed, BigDecimal committed,
                                BigDecimal discretionary, BigDecimal spent, BigDecimal left,
                                boolean overspent) {
}
