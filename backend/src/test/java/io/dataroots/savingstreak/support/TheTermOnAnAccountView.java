package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What one savings account's fixed term says today, as the API reports it: how long it was locked
 * for, the day it matures, how long is left, and what breaking it now would cost.
 *
 * <p>Shared here rather than copied into each test that reads one, for the reason every other view
 * in this package gives: two copies of a shape drift into disagreeing about it, and then one of
 * them is testing a contract nobody serves.
 *
 * <p><strong>{@code whatBreakingWouldCost} is the figure a test asserts the charge against.</strong>
 * The claim worth testing is not that the backend can multiply — it is that the price quoted before
 * anything is confirmed is the price actually charged, and a test proves that by reading this,
 * breaking the term, and finding the same number in the ledger.
 *
 * <p>{@code termMonths} is nought for every account that is not on a term, which is three of the
 * four products the bank sells, and the reading still answers rather than refusing. A test asserting
 * that is asserting that a screen can draw every savings account without first asking what kind it
 * is holding.
 */
public record TheTermOnAnAccountView(long savingsAccountId, int termMonths, LocalDate maturesOn,
                                     boolean matured, boolean locked, long daysLeft,
                                     BigDecimal balance, int earlyExitPenaltyDays,
                                     BigDecimal whatBreakingWouldCost,
                                     /**
                                      * The ending this account agreed to, by name, and null when it
                                      * is not on a term. A test asserting on it is asserting that
                                      * the ending comes off the version the account was opened
                                      * under rather than off what the product sells today, which is
                                      * the pair the whole feature exists to keep apart.
                                      */
                                     String maturityAction,
                                     /** That ending as the sentence the terms say it in. */
                                     String whatHappensAtMaturity) {
}
