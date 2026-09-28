package io.dataroots.savingstreak.automation;

import java.time.LocalDate;

/**
 * One day a saving rule is going to fall due on, and what it would move when it does.
 *
 * <p>{@link RecordedOccurrence} in the future tense, and separate from it for the reason the two
 * tenses are different sentences: that one carries a moment it was settled at, an outcome, a deposit
 * and a shortfall, and every one of those is a thing that happened. None of them exists yet here.
 * A record with half its fields null would leave a page unable to tell "this has not happened" from
 * "this happened and moved nothing", which are the two halves this feature keeps apart everywhere
 * else.
 *
 * <p>{@code ruleName} travels with the day because the preview is <em>merged</em>: every rule on an
 * account in one list in date order, which is the question a customer asks, and a list of days with
 * no names on them would leave them matching identifiers against the rule list by hand.
 *
 * <p>{@code trigger} and {@code howMuchMoves} are here for the same reason, and because a page
 * decides what to call each kind from the word rather than by inferring it from whichever figure is
 * not null.
 *
 * <p>The days come from {@link WhichOccurrencesAreDue} and the figure from
 * {@link WhatARuleWouldMove}, which are the same two functions the nightly run asks — so a preview
 * quotes the day the rule will actually fire on, with the month-end clamp and all, and the amount it
 * will actually move when it gets there.
 *
 * <p><strong>{@code owedRatherThanStillToCome} is the one thing about a line that a day alone does
 * not say.</strong> A forecast is counted from each rule's own cursor rather than from today, so a
 * rule the nightly run has not caught up with carries the mornings it is late for at the head of the
 * list — and on a wound clock that is the ordinary state of every rule, because the clock moves in
 * whole days and the cron never fires for the ones it skipped. Those lines are transfers the
 * <em>next</em> run will make for a day that has already passed, and the ones after them are
 * transfers a night in the future will make on the day it names. A page has to be able to say which
 * is which — "these two already fell" is a different sentence from "this is due next Monday" — and
 * working it out by comparing each day against today would be a page re-deriving a boundary this
 * module has already drawn. True exactly when the day is before the one the window opens on, which
 * is the same count the preview's own INFO line reports as {@code owedFromBefore}.
 *
 * <p>Public, because {@link WhatTheRulesWillDo} carries it out of the module.
 */
public record AnOccurrenceToCome(long ruleId, String ruleName, LocalDate dueOn, RuleTrigger trigger,
                                 HowMuchMoves howMuchMoves, WhatWouldMove wouldMove,
                                 boolean owedRatherThanStillToCome) {
}
