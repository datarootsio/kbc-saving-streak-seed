package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One month of interest as a test reads it, which is exactly as the API answers it. Shared by every
 * test that reads one back, for the same reason as {@link BalancesView} — copies of a shape drift
 * into disagreeing about it, and then one of them is testing a contract nobody serves.
 *
 * <p>Every figure the arithmetic was made of is on it, because that is what the tests here assert:
 * not that a balance grew, but that it grew by the average daily balance at a twelfth of the rate
 * the account's own terms name, over the days the period covered. A view carrying only the amount
 * would let the rule be reimplemented wrongly and still pass.
 *
 * <p>{@code until} is the day the period ended and not its last day, the half-open reading the
 * backend uses for every stretch of time — so two consecutive periods meet rather than overlap, and
 * a test putting a year of them end to end can say so.
 *
 * <p>The amounts and the rate are {@link BigDecimal} and compared by value throughout, because how
 * many places a figure carries over the wire is a formatting question and not the rule under test.
 */
public record InterestPostingView(int periodOrdinal, LocalDate from, LocalDate until,
                                  BigDecimal averageDailyBalance, BigDecimal lowestDailyBalance,
                                  BigDecimal annualRatePercent, boolean bonusEarned,
                                  int termsVersion, BigDecimal interest, Instant postedAt) {
}
