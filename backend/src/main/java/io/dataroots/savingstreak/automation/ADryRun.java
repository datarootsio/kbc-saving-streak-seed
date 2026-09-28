package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a rule nobody has saved would do if it fired this minute: what it would move, out of what
 * balance, and which of the three things that happen to an occurrence would happen to it.
 *
 * <p><strong>This is the preview people actually want</strong>, because it is the one they want
 * before pressing save rather than after. It is asked of a rule as typed rather than of a rule that
 * exists, and asking writes nothing at all — no rule, no occurrence, no deposit, no point.
 *
 * <p><strong>Today, which is why a sweep can be promised here.</strong> The twelve-month preview has
 * to mark a sweep's figure as an illustration, because what it moves next March depends on a balance
 * nobody has yet. This does not: the balance is the one in the account right now, so
 * {@link WhatWouldMove#anIllustrationRatherThanAPromise} is false for both kinds of rule and the
 * figure is what would actually move. {@code balance} is here beside it so the subtraction can be
 * read rather than taken on trust.
 *
 * <p><strong>{@code outcome} is the same word the occurrence would carry</strong>, out of the same
 * comparison {@code AutomationService.fire} makes, rather than a second opinion about what "would
 * work" means. A rule asking for more than the account holds says {@link
 * OccurrenceOutcome#NOT_ENOUGH_MONEY} with the {@code shortfall}, because a fixed amount moves all
 * of itself or none of it; a sweep whose account is already at or under its floor says
 * {@link OccurrenceOutcome#NOTHING_TO_MOVE}, which is arithmetic rather than a failure and is the
 * distinction a customer is owed before they commit to the rule rather than after.
 *
 * <p>{@code shortfall} is filled on that one outcome and null on the other two, exactly as it is on
 * {@link RecordedOccurrence}: "short of nothing" and "short of EUR 0,00" are different sentences,
 * and only the first is true of a rule the account can cover.
 *
 * <p>A rule this application would refuse to keep is refused here too, in the same sentences — see
 * {@code AutomationService.whatAnUnsavedRuleWouldDo}. A dry run that answered where the save would
 * be refused would be a preview of a rule that cannot exist.
 *
 * <p>{@code asAt} is the day the application's clock reads, so that a trainer who has wound it can
 * see which day the answer is about.
 */
public record ADryRun(LocalDate asAt, BigDecimal balance, OccurrenceOutcome outcome,
                      WhatWouldMove wouldMove, BigDecimal shortfall) {
}
