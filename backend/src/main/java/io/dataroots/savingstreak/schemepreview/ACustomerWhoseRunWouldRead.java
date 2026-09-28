package io.dataroots.savingstreak.schemepreview;

import java.math.BigDecimal;

/**
 * One customer whose run of weeks reads differently under the candidate, with both readings side by
 * side: the run happening now, the best there has ever been, and the rate the run is paid at.
 *
 * <p><strong>The number has a face behind it, and that is the entire reason this list exists.</strong>
 * "Eleven customers lose weeks" is a figure somebody signs off. "Anke Peeters goes from six weeks to
 * one, and from 1,50× to 1,00×" is a sentence somebody reads twice. The roll-up says how big the
 * change is; this says what it is like to be on the receiving end of it.
 *
 * <p><strong>Both the run happening now and the best there has ever been, because they are two
 * different injuries.</strong> A shortened current run costs a customer the rate on their next
 * deposit. A shortened best-ever costs them something they were told they had achieved and cannot
 * get back by saving this week, which is the one a complaint gets written about.
 *
 * <p><strong>The fall in weeks is carried rather than left to subtraction</strong>, because it is
 * what the list is ordered by and an order a screen cannot reproduce from the fields it was given
 * is an order a screen will get wrong. It is negative for a customer the candidate is kind to,
 * which is how a scheme that only gives reads: a list of negative falls.
 *
 * @param customerId       who, by the identifier the rest of this API uses
 * @param name             who, as a person reading the preview would recognise them
 * @param currentRunNow    the run of consecutive secured weeks the customer is on today
 * @param currentRunWouldRead what that run would read had the candidate been the rule for
 *                            twenty-six weeks
 * @param bestRunNow       the longest run the customer has ever had, as it reads today
 * @param bestRunWouldRead what the longest run would read under the counterfactual
 * @param rateNow          what a euro saved by this customer is multiplied by today
 * @param rateWouldRead    what it would be multiplied by under the counterfactual
 * @param theFallInWeeks   how many weeks the current run would lose, negative if it would gain
 * @param theFallInRate    how much rate the customer would lose, negative if they would gain
 */
public record ACustomerWhoseRunWouldRead(long customerId, String name, int currentRunNow,
                                         int currentRunWouldRead, int bestRunNow,
                                         int bestRunWouldRead, BigDecimal rateNow,
                                         BigDecimal rateWouldRead, int theFallInWeeks,
                                         BigDecimal theFallInRate) {
}
