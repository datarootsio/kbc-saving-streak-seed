package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a rule would move, as honestly as it can be said in advance, and where it would land.
 *
 * <p><strong>A fixed amount can be promised and a sweep cannot, and this record's whole job is to
 * keep the two apart.</strong> "Fifty euros every Monday" is a figure a customer can add up twelve
 * months of; "everything above eight hundred" next March depends on a balance nobody has yet, and a
 * confident figure for it would be a fiction. A preview that invents figures is worse than no
 * preview, so a sweep quotes its {@code floor} — which <em>is</em> knowable, because it is what the
 * customer said — and today's balance only as an illustration, marked as one.
 *
 * <p>{@code amount} is therefore read together with {@code anIllustrationRatherThanAPromise} and
 * never on its own. On a fixed amount the flag is false and the figure is the instruction itself. On
 * a sweep the flag is true and the figure is what today's balance would give: a worked example of
 * the rule, not a forecast of the day it names. A page that shows the figure and drops the flag is a
 * page promising something this application did not say.
 *
 * <p><strong>One exception, and it is the whole point of the dry run.</strong> Asked what a rule
 * would move <em>today</em>, a sweep's figure is not an illustration at all — it is what would
 * actually move if the rule fired this minute, out of the balance that is actually there. The flag
 * is false there for both kinds, because there is nothing left to be uncertain about.
 *
 * <p>{@code floor} is filled exactly on a sweep and {@code amount}'s companion figure on a fixed
 * amount is the rule's own, so between the two a page can always say the sentence the customer
 * wrote. Both are quoted to the cent.
 *
 * <p><strong>{@code intoGoals} and {@code leftUnallocated} are where the money would land</strong>,
 * and they add to {@code amount} exactly, the way {@link RecordedOccurrence}'s pair adds to what
 * moved. They come from {@link HowAnAmountIsSplit} — the same function the firing hands the money to
 * — so the preview and the transfer it predicts cannot disagree about the cents.
 *
 * <p>What they do <em>not</em> claim is which goal will still have room. A goal that is nearly
 * complete takes only what it needs on the night and the rest spills to the next goal in the split;
 * that is a fact about how full a goal is at the moment it fires, and a forecast asserting it eleven
 * months out would be inventing the very figure this record refuses to invent. So these read the
 * customer's instruction priced out, and {@link WhatAGoalGot} on the occurrence is what actually
 * happened.
 *
 * <p>{@code intoGoals} is empty and {@code leftUnallocated} is the whole figure on a rule with no
 * split, which is the honest reading: such a rule deposits unallocated, exactly as a manual deposit
 * does.
 */
public record WhatWouldMove(BigDecimal amount, BigDecimal floor,
                            boolean anIllustrationRatherThanAPromise,
                            List<WhatAGoalWouldGet> intoGoals, BigDecimal leftUnallocated) {
}
