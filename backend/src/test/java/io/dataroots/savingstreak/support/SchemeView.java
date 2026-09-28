package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One published version of the scheme as the API reports it. Shared for the same reason as
 * {@link TermsVersionView}: a second copy of the shape can drift into disagreeing about it, and then
 * one of them is testing a contract nobody serves.
 *
 * <p>It is the same record in both places the API sends one — on its own as the version in force
 * today, and listed as the history the bank has published — because that is what the API does, and a
 * test with two shapes for it would stop noticing the day the two stopped being identical.
 *
 * <p>Every rate and every amount is a {@link BigDecimal}, because they are multiples, percentages
 * and money, and a {@code double} in a test is how a test learns to accept 0.1000000000000001. They
 * are compared by value rather than by scale wherever the rule under test is the figure rather than
 * its formatting — except where a test says otherwise, because how many places the ladder's step
 * carries is itself part of what this module promises.
 *
 * <p>{@code whatChanged} is never null, on any version including the seeded one, which is where the
 * scheme differs from a product's terms. A test that expected an empty line on version 1 would be
 * expecting the backend to have published a rate with no explanation.
 */
public record SchemeView(int version, LocalDate effectiveFrom, BigDecimal weeklyThreshold,
                         BigDecimal theOrdinaryRate, BigDecimal extraForEachFurtherWeek,
                         BigDecimal theMostAStreakPays, int howLongABatchOfPointsLasts,
                         List<BigDecimal> balanceRungs, BigDecimal whatShareOfABudgetIsRunningLow,
                         int howManyOutstandingIsASpiral, int daysBeforeAMaturityIsWorthSaying,
                         int daysBeforeAnAnniversaryIsWorthSaying, String whatChanged) {
}
