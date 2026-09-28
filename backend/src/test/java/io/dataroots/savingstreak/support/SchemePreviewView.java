package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the API answers when somebody asks what publishing a candidate scheme would do.
 *
 * <p>Shared with the other views for the reason they all give: one shape a test reads the preview
 * through, so that three tests cannot end up with three opinions about what came back. The version
 * in force and the version this would become are the same {@link SchemeView} every other scheme
 * reading uses, which is the claim the response itself makes about its own shape.
 */
public record SchemePreviewView(String whatThisIs, int overHowManyWeeks,
                                LocalDate asIfItHadBeenTheRuleSince, boolean itWouldChangeNothing,
                                SchemeView theVersionInForce, SchemeView theVersionThisWouldBecome,
                                List<AFigureView> figures, TheRunsView runs,
                                List<AnAffectedCustomerView> theWorstAffected, ThePointsView points,
                                List<ALineView> notifications) {

    /** One figure of the scheme, as it reads now and as the candidate would have it. */
    public record AFigureView(String figure, String asItReadsNow, String asItWouldRead,
                              boolean itWouldChange) {
    }

    /** The roll-up over every customer the bank has. */
    public record TheRunsView(int customersExamined, int runsThatWouldReadDifferently,
                              int whoWouldGain, int whoWouldLose, int whoAreUntouched,
                              int theLargestFallInWeeks, BigDecimal theLargestFallInRate) {
    }

    /** One named customer with their run and rate before and after. */
    public record AnAffectedCustomerView(long customerId, String name, int currentRunNow,
                                         int currentRunWouldRead, int bestRunNow,
                                         int bestRunWouldRead, BigDecimal rateNow,
                                         BigDecimal rateWouldRead, int theFallInWeeks,
                                         BigDecimal theFallInRate) {
    }

    /** What happens to points already earned, and to a batch earned after the change. */
    public record ThePointsView(boolean nothingAlreadyEarnedChangesItsExpiry,
                                int howLongABatchLastsNow, int howLongABatchWouldLast,
                                LocalDate aBatchEarnedOnTheEffectiveDateWouldDieOn,
                                String saidPlainly) {
    }

    /** One of the five lines the scheme draws, with the count on either side of it. */
    public record ALineView(String line, int wouldBeToldAndIsNot, int isToldAndWouldNotBe) {
    }
}
