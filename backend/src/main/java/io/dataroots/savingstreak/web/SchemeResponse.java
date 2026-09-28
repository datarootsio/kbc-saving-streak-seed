package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;

/**
 * One published version of the scheme as the API reports it: every figure the bank has decided
 * about saving, the Monday it takes effect, its version number, and the line saying what changed.
 *
 * <p>Sent out in two places and identical in both — on its own as the version in force today, and
 * listed as the history the bank has published. One shape for both, because they are the same thing
 * at two moments: a page that rendered a version differently depending on which list it came out of
 * would be two renderers to keep in agreement, and the workspace that pre-fills a form from the
 * version in force reads exactly the fields the history prints.
 *
 * <p><strong>Every rate is a multiple or a percentage and every amount is in euros.</strong> The
 * backend holds rates as basis points and money as cents, because that is how neither of them
 * acquires a scale somebody chose by accident; not one of those integers gets this far. What
 * arrives here is what a page prints: {@code 50.00} is EUR 50 a week, {@code 1.0000} is the
 * ordinary rate per euro, {@code 0.1000} is what a further week adds, and {@code 80.00} is four
 * fifths of a budget. The frontend does no arithmetic on any of them — it has never priced anything
 * in this application and it does not start here.
 *
 * <p><strong>Nothing here is nullable, including the line saying what changed.</strong> Where a
 * product's first version leaves that line empty because nothing changed, the scheme requires one
 * on every version including the seeded one: nobody opted into the scheme, it applies to everybody
 * from its Monday, and a rate change with a date and no explanation is one of the three things this
 * feature exists to fix. A page can print it unconditionally.
 *
 * <p><strong>Whether a version has started is not a field here, and that is on purpose.</strong>
 * The Monday is, and the application's own clock is a read the page already makes; a boolean beside
 * the date would be a second answer to the same question, computed at a different moment, and the
 * two would disagree for anybody whose tab was open across a Monday morning. The screen that marks
 * future-dated versions as not yet in force compares the date it was given with the day the
 * application says it is.
 */
record SchemeResponse(int version, LocalDate effectiveFrom, BigDecimal weeklyThreshold,
                      BigDecimal theOrdinaryRate, BigDecimal extraForEachFurtherWeek,
                      BigDecimal theMostAStreakPays, int howLongABatchOfPointsLasts,
                      List<BigDecimal> balanceRungs, BigDecimal whatShareOfABudgetIsRunningLow,
                      int howManyOutstandingIsASpiral, int daysBeforeAMaturityIsWorthSaying,
                      int daysBeforeAnAnniversaryIsWorthSaying, String whatChanged) {

    static SchemeResponse of(TheSchemeAsPublished scheme) {
        return new SchemeResponse(
                scheme.version(),
                scheme.effectiveFrom(),
                scheme.weeklyThreshold(),
                scheme.theOrdinaryRate(),
                scheme.extraForEachFurtherWeek(),
                scheme.theMostAStreakPays(),
                scheme.howLongABatchOfPointsLasts(),
                scheme.balanceRungs(),
                scheme.whatShareOfABudgetIsRunningLow(),
                scheme.howManyOutstandingIsASpiral(),
                scheme.daysBeforeAMaturityIsWorthSaying(),
                scheme.daysBeforeAnAnniversaryIsWorthSaying(),
                scheme.whatChanged());
    }
}
