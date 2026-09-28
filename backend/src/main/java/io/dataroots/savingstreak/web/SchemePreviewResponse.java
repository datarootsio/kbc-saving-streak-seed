package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.notifications.HowManyStandOnTheFarSideOfALine;
import io.dataroots.savingstreak.schemepreview.ACustomerWhoseRunWouldRead;
import io.dataroots.savingstreak.schemepreview.AFigureAsItWouldRead;
import io.dataroots.savingstreak.schemepreview.HowManyRunsWouldRead;
import io.dataroots.savingstreak.schemepreview.WhatThisSchemeWouldDo;
import io.dataroots.savingstreak.schemepreview.WhatWouldHappenToPoints;

/**
 * What comes back from previewing a candidate version of the scheme: what it is, what it changes,
 * who it would have moved over the last twenty-six weeks, what it does to points, and how much more
 * or less this application would be saying to people tonight.
 *
 * <p><strong>{@link #whatThisIs} is a field of the answer and not a caption on a screen.</strong>
 * The figures below are counterfactual — what everybody's run <em>would</em> read had this been the
 * rule since {@link #asIfItHadBeenTheRuleSince}, not what will happen when it takes effect, which is
 * nothing, because a week is judged by the scheme in force on its own Monday. A page could carry
 * that sentence and a page could forget it; a figure pasted into an email has already lost it. In
 * the body it cannot be separated from the numbers it is about.
 *
 * <p><strong>The two versions are nested as the same {@link SchemeResponse} every other read
 * serves</strong>, for the reason {@link SchemeVersionPublishedResponse} gives at length: a version
 * of the scheme has one shape in this API, and the screen that previews is the screen that
 * pre-fills its form from the version in force, so it already has a renderer for exactly this. The
 * candidate carries the version number it would be published as, which is the module's answer and
 * not something a page should be adding one to work out.
 *
 * <p><strong>{@link #itWouldChangeNothing} is answered here rather than left to a page comparing
 * fields.</strong> It is the reading the whole tool is checked against — preview the version already
 * in force and it must say nothing changes — and a screen that decided it from a subset of what came
 * back would eventually call a scheme harmless because it had only looked at the figures.
 *
 * <p>Every nested record mirrors the domain value beside it, the way every other composite answer in
 * this layer does, so that the JSON is this application's own shape rather than whatever a module's
 * record happens to be called this week.
 */
record SchemePreviewResponse(String whatThisIs, int overHowManyWeeks,
                             LocalDate asIfItHadBeenTheRuleSince, boolean itWouldChangeNothing,
                             SchemeResponse theVersionInForce,
                             SchemeResponse theVersionThisWouldBecome,
                             List<AFigureResponse> figures, TheRunsResponse runs,
                             List<AffectedCustomerResponse> theWorstAffected,
                             ThePointsResponse points,
                             List<ALineResponse> notifications) {

    static SchemePreviewResponse of(WhatThisSchemeWouldDo wouldDo) {
        return new SchemePreviewResponse(
                wouldDo.whatThisIs(),
                wouldDo.overHowManyWeeks(),
                wouldDo.asIfItHadBeenTheRuleSince(),
                wouldDo.itWouldChangeNothing(),
                SchemeResponse.of(wouldDo.theVersionInForce()),
                SchemeResponse.of(wouldDo.theVersionThisWouldBecome()),
                wouldDo.figures().stream().map(AFigureResponse::of).toList(),
                TheRunsResponse.of(wouldDo.runs()),
                wouldDo.theWorstAffected().stream().map(AffectedCustomerResponse::of).toList(),
                ThePointsResponse.of(wouldDo.points()),
                wouldDo.notifications().stream().map(ALineResponse::of).toList());
    }

    /**
     * One figure of the scheme as it reads now and as it would read, with the comparison already
     * made.
     *
     * <p>Both sides as text, which the domain record argues for: the figures are amounts,
     * multiples, percentages, counts and a whole ladder, and the answer to "what did I change" is
     * one table with one column per side. The typed figures are in the two nested versions above
     * for anything that wants to compute.
     */
    record AFigureResponse(String figure, String asItReadsNow, String asItWouldRead,
                           boolean itWouldChange) {

        static AFigureResponse of(AFigureAsItWouldRead figure) {
            return new AFigureResponse(figure.figure(), figure.asItReadsNow(),
                    figure.asItWouldRead(), figure.itWouldChange());
        }
    }

    /** The size of what publishing this would do, rolled up over every customer the bank has. */
    record TheRunsResponse(int customersExamined, int runsThatWouldReadDifferently,
                           int whoWouldGain, int whoWouldLose, int whoAreUntouched,
                           int theLargestFallInWeeks, BigDecimal theLargestFallInRate) {

        static TheRunsResponse of(HowManyRunsWouldRead runs) {
            return new TheRunsResponse(runs.customersExamined(),
                    runs.runsThatWouldReadDifferently(), runs.whoWouldGain(), runs.whoWouldLose(),
                    runs.whoAreUntouched(), runs.theLargestFallInWeeks(),
                    runs.theLargestFallInRate());
        }
    }

    /** One customer, named, with their run and rate as they read now and as they would read. */
    record AffectedCustomerResponse(long customerId, String name, int currentRunNow,
                                    int currentRunWouldRead, int bestRunNow, int bestRunWouldRead,
                                    BigDecimal rateNow, BigDecimal rateWouldRead,
                                    int theFallInWeeks, BigDecimal theFallInRate) {

        static AffectedCustomerResponse of(ACustomerWhoseRunWouldRead who) {
            return new AffectedCustomerResponse(who.customerId(), who.name(), who.currentRunNow(),
                    who.currentRunWouldRead(), who.bestRunNow(), who.bestRunWouldRead(),
                    who.rateNow(), who.rateWouldRead(), who.theFallInWeeks(), who.theFallInRate());
        }
    }

    /** Nothing already earned moves, and what a batch earned from the effective date would last. */
    record ThePointsResponse(boolean nothingAlreadyEarnedChangesItsExpiry,
                             int howLongABatchLastsNow, int howLongABatchWouldLast,
                             LocalDate aBatchEarnedOnTheEffectiveDateWouldDieOn,
                             String saidPlainly) {

        static ThePointsResponse of(WhatWouldHappenToPoints points) {
            return new ThePointsResponse(points.nothingAlreadyEarnedChangesItsExpiry(),
                    points.howLongABatchLastsNow(), points.howLongABatchWouldLast(),
                    points.aBatchEarnedOnTheEffectiveDateWouldDieOn(), points.saidPlainly());
        }
    }

    /**
     * One of the five lines the scheme draws, with the count on either side of it.
     *
     * <p>The line travels as its name, the way every other enum crossing this layer does, so that a
     * value added to the vocabulary is a string a page has not learned rather than a shape it
     * cannot read.
     */
    record ALineResponse(String line, int wouldBeToldAndIsNot, int isToldAndWouldNotBe) {

        static ALineResponse of(HowManyStandOnTheFarSideOfALine counted) {
            return new ALineResponse(counted.line().name(), counted.wouldBeToldAndIsNot(),
                    counted.isToldAndWouldNotBe());
        }
    }
}
