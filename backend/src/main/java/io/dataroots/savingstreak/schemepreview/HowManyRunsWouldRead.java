package io.dataroots.savingstreak.schemepreview;

import java.math.BigDecimal;

/**
 * The size of what publishing this would do, rolled up over every customer the bank has.
 *
 * <p><strong>Gained, lost and untouched rather than one "changed" figure</strong>, because they are
 * three different conversations. A scheme that moves two hundred customers is a big change; a
 * scheme that moves two hundred customers <em>down</em> is a different thing entirely, and a
 * preview that reported only "changed" would let the second one read like the first.
 *
 * <p><strong>A direction is decided on the run first, then on the rate, then on the best-ever
 * run.</strong> The first of the three that differs decides which way the customer went. That
 * ordering is the customer's own order of noticing: the run is the number on the front of the
 * screen, the rate is what their next euro earns, and the best-ever is the one they only look at
 * occasionally. A candidate that shortens a run and raises the rate is counted as a loss, and it
 * should be — the customer will be looking at the run.
 *
 * <p><strong>The largest fall is carried beside the counts because an average hides the person the
 * bank will hear from.</strong> Twenty customers losing a week each and one customer losing
 * twenty-six are the same total and are not the same decision. Both are falls: a candidate under
 * which nobody loses anything reports nought for both, never a negative.
 *
 * @param customersExamined         how many customers the two derivations were run over
 * @param runsThatWouldReadDifferently how many of them read differently in any of the three figures
 * @param whoWouldGain              how many of those moved upwards
 * @param whoWouldLose              how many of those moved downwards
 * @param whoAreUntouched           how many read exactly the same either way
 * @param theLargestFallInWeeks     the most weeks any one customer's current run would lose, or
 *                                  nought if nobody loses any
 * @param theLargestFallInRate      the most rate any one customer would lose, or nought if nobody
 *                                  loses any
 */
public record HowManyRunsWouldRead(int customersExamined, int runsThatWouldReadDifferently,
                                   int whoWouldGain, int whoWouldLose, int whoAreUntouched,
                                   int theLargestFallInWeeks, BigDecimal theLargestFallInRate) {
}
