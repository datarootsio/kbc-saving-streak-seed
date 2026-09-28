package io.dataroots.savingstreak.support;

import java.time.LocalDate;

/**
 * One day a saving rule is going to fall due on, as the preview reports it — which is exactly as a
 * test reads it.
 *
 * <p>{@link RuleOccurrenceView} in the future tense. A test reads this against that one: the whole
 * claim of the preview is that the day and the figure quoted here are the day and the figure the
 * occurrence carries once the clock has reached them.
 *
 * <p>{@code ruleName} is here because the preview merges every rule on the account into one list, so
 * a test asserting the order across two rules can say which line belongs to which without matching
 * identifiers by hand.
 */
public record OccurrenceToComeView(long ruleId, String ruleName, LocalDate dueOn, String trigger,
                                   String howMuchMoves, WhatWouldMoveView wouldMove,
                                   boolean owedRatherThanStillToCome) {
}
