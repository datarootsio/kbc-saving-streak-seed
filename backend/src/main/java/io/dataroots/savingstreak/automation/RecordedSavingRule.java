package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A saving rule as the rest of the application sees one: what it is called, where the money comes
 * from and goes to, what makes it move, which day that is, how much moves, and whether it is still
 * standing.
 *
 * <p>Public, unlike the row it is read from, because it is what the web layer names. The row itself
 * stays inside this module; a module that hands out its entities to be read elsewhere has no
 * boundary left to speak of.
 *
 * <p><strong>{@code dayOfMonth} on a rule that fires on payday and is still standing is the income's
 * day, not the rule's.</strong> Such a rule stores no day at all — asking a customer for it a second
 * time would be asking them to keep two answers in step — so this field is read from the income
 * declared against the current account the rule draws from, on every read. It is null on a standing
 * payday rule whose holder has declared no income, which is an honest answer: nothing yet says when
 * that rule moves, and a page has something to draw for it.
 *
 * <p><strong>On an ended payday rule it is the day that rule was moving on when it was ended</strong>,
 * written onto the row at that moment and read back unchanged for ever after. An ended rule is a
 * record rather than an instruction, and one that went on following the declaration would answer
 * "which day did this move on" differently every time its holder moved payday.
 *
 * <p>{@code dayOfWeek} is filled exactly on a weekly rule, and {@code dayOfMonth} on a monthly one
 * or a payday one with an income behind it. Whoever renders a rule reads {@code trigger} to know
 * which of them to expect, rather than inferring the kind from whichever is not null.
 *
 * <p>{@code amount} is filled exactly on a fixed-amount rule and {@code floor} exactly on a sweep,
 * for the reason {@link HowMuchMoves} gives: the two are different sentences, and a single figure
 * would leave a page unable to say whether it is what moves or what stays behind. Both are quoted to
 * the cent on the way out, because they have been through SQLite, which has no decimal type and
 * holds an amount as a float — so 50.00 comes back as 50.0 and would reach a page as a number rather
 * than as money.
 *
 * <p><strong>{@code split} is how what the rule moves is spread across its holder's goals</strong>,
 * in the order they wrote it, with whole-percentage shares adding to a hundred. It is empty on a
 * rule with no split, which is the ordinary rule: what it moves lands unallocated, exactly as a
 * manual deposit does, and a rule is not forced to know about goals.
 *
 * <p>The shares are what the customer said rather than what a firing did with them. A goal that was
 * abandoned after the split was written is still named here, because taking it out would be quietly
 * re-wording somebody's instruction; what actually happened to its share on a given night is on the
 * occurrence, which is the record rather than the instruction.
 *
 * <p><strong>{@code pausedAt} is filled exactly while {@code state} is {@link RuleState#PAUSED}</strong>
 * — the moment its holder stopped it — and is null on every other rule, including one that has been
 * paused before and resumed. It is the moment the pause it is <em>in</em> began, because that is the
 * one a page has anything to say about; what every earlier pause covered is the record the module
 * keeps for itself, and not something a rule as it reads today can carry.
 *
 * <p>{@code endedAt} is filled exactly when {@code state} is {@link RuleState#ENDED}. The state
 * travels as its own value rather than as an inference from either moment, for the reason a goal's
 * does: a page decides what to call each kind, and that is easier to get right from a word — and a
 * paused rule has to be able to say that it is paused rather than merely show no next day.
 *
 * <p><strong>{@code nextFiresOn} and {@code nextMoves} are the question a customer opens the page
 * with</strong>: the day this rule moves money next, and what it will move when it does. They are
 * the first line of the same twelve-month preview {@link WhatTheRulesWillDo} holds, derived from the
 * same two functions the nightly run asks — {@link WhichOccurrencesAreDue} for the day and
 * {@link WhatARuleWouldMove} for the figure — so a rule's list entry and the transfer it predicts
 * cannot disagree.
 *
 * <p><strong>{@code nextFiresOn} can be a day already past, and that is the true answer.</strong> It
 * is the day the rule fires <em>for</em> the next time it fires, and a rule the nightly run has not
 * caught up with fires next for the oldest morning it owes. On a wound clock every rule is in that
 * state until the job is run, so answering with next week instead would be denying a transfer that
 * is about to happen — which is the one thing this pair exists not to do.
 *
 * <p><strong>What it is not is a day the run will pass over.</strong> Two kinds of morning sit
 * behind a rule's cursor and are not owed at all: one that fell inside a pause its holder has since
 * resumed from, which is never made up, and, on a payday rule, a month in which no salary was ever
 * credited. Both are excluded here by the same two answers the night uses — the pause record and the
 * record of salaries credited — because "the day it next fires" is a claim about what the next run
 * will do, and a day the run has already decided to skip is not it.
 *
 * <p>Both are null together, and there are four honest reasons for it: the rule is paused, so
 * nothing falls due until its holder resumes it; the rule has been ended, so it is a record rather
 * than an instruction; it fires on payday and nobody has declared an income for it to follow; or its
 * next day lies past the twelve months this application looks over, which no rule's does today but
 * which a fourth trigger could one day make true. {@code state} is what says which, and it is why a
 * page reads the state rather than inferring "stopped" from a missing day.
 *
 * <p>Derived on every read and stored nowhere, which is what makes them right: change the rule,
 * change the balance, or let the night fire it, and the next read says something else with nothing
 * to invalidate.
 */
public record RecordedSavingRule(Long id, long savingsAccountId, long currentAccountId, String name,
                                 RuleTrigger trigger, DayOfWeek dayOfWeek, Integer dayOfMonth,
                                 HowMuchMoves howMuchMoves, BigDecimal amount, BigDecimal floor,
                                 List<AShareOfWhatMoves> split, RuleState state, Instant createdAt,
                                 Instant pausedAt, Instant endedAt, LocalDate nextFiresOn,
                                 WhatWouldMove nextMoves) {
}
