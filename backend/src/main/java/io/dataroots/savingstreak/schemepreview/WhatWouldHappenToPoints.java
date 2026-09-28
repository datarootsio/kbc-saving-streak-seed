package io.dataroots.savingstreak.schemepreview;

import java.time.LocalDate;

/**
 * What publishing this would do to points: nothing at all to any batch already earned, and a
 * different lifetime for every batch earned from the Monday it takes effect.
 *
 * <p><strong>The first half is a sentence rather than an inference, and that is the whole reason
 * this record exists.</strong> Everything else in a preview is a difference somebody reads off two
 * columns. This one is an <em>absence</em> of a difference, and an absence cannot be read off a
 * table: an administrator shortening the lifetime from twelve months to six has to be told, in
 * words, that they are not about to kill six months of everybody's points tonight. A batch keeps
 * the moment it was promised because that moment was written down when it was earned, and a preview
 * that left that to be worked out from a missing row would be the one place this feature was silent
 * about the thing it was built to prevent.
 *
 * <p><strong>The second half names the day rather than only the number of months.</strong> "Six
 * months instead of twelve" is the change; "a batch earned on Monday would die on the fifth of
 * April" is the consequence, worked out by the same function the ledger uses so that the preview
 * and the sweep cannot disagree about which day a month lands on. February is exactly why that is
 * not arithmetic anybody should do twice.
 *
 * @param nothingAlreadyEarnedChangesItsExpiry always true, and said out loud for the reason above
 * @param howLongABatchLastsNow                months a batch earned today is promised
 * @param howLongABatchWouldLast               months a batch earned from the effective date gets
 * @param aBatchEarnedOnTheEffectiveDateWouldDieOn the day a batch earned on the effective Monday
 *                                                 would expire under the candidate
 * @param saidPlainly                          both halves in one sentence, for a screen to print
 */
public record WhatWouldHappenToPoints(boolean nothingAlreadyEarnedChangesItsExpiry,
                                      int howLongABatchLastsNow, int howLongABatchWouldLast,
                                      LocalDate aBatchEarnedOnTheEffectiveDateWouldDieOn,
                                      String saidPlainly) {
}
