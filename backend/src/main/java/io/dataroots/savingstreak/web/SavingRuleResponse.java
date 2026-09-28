package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.automation.RecordedSavingRule;

/**
 * A saving rule as the API reports one: what it is called, where the money comes from, what makes it
 * move, which day that is, how much moves, and whether it is still standing.
 *
 * <p>One shape for a rule just left standing, a rule just changed, a rule read out of the account's
 * list and a rule that has been ended — so nothing rendering a rule has to know which of those it is
 * holding in order to render it.
 *
 * <p>{@code dayOfWeek} is filled exactly on a weekly rule. {@code dayOfMonth} is filled on a monthly
 * rule, and on a rule that fires on payday it is <strong>the day its holder declared their income
 * lands</strong> — read from that declaration on every read rather than stored, so that moving
 * payday moves every rule waiting for it. It is null on a payday rule whose holder has declared no
 * income yet, which is an answer a page has something to draw for: nothing yet says when that rule
 * moves.
 *
 * <p>{@code amount} is filled exactly on a fixed-amount rule and {@code floor} exactly on a sweep.
 * The two are different sentences — what will move, as against what will be left behind — and a
 * single figure would leave a page unable to say which it was showing. {@code howMuchMoves} is the
 * word that says which to expect, rather than a page inferring the kind from whichever is not null.
 *
 * <p><strong>{@code split} is how what the rule moves is spread across the account's goals</strong>,
 * in the order the customer wrote it. It is empty on a rule with no split, which is the ordinary
 * rule: what it moves lands unallocated, exactly as a manual deposit does.
 *
 * <p>The trigger, the kind of amount and the state travel as their own words rather than as
 * inferences from a null, for the reason a goal's state does: a page decides what to call each kind,
 * and that is easier to get right from a word — and a rule its holder has paused says {@code PAUSED}
 * here rather than merely showing no next day, which is the difference between a page that can
 * explain itself and one that leaves somebody guessing why their saving stopped.
 *
 * <p>{@code pausedAt} is filled exactly while the state is {@code PAUSED}, and {@code endedAt}
 * exactly when it is {@code ENDED}.
 *
 * <p><strong>{@code nextFiresOn} and {@code nextMoves} are the question a customer opens the page
 * with</strong>: the day this rule moves money next, and what it will move when it does. They are
 * the first line of the same twelve-month preview {@code /saving-rules/preview} answers with, off
 * the same walk of the same calendar, so a rule's entry in the list and the preview cannot disagree
 * about the day it next moves.
 *
 * <p>Both are null together, and there are four honest reasons for it: the rule is paused, so
 * nothing falls due until its holder resumes it; it has been ended, so it is a record rather than an
 * instruction; it fires on payday and nobody has declared an income for it to follow; or its next
 * day lies past the twelve months this application looks over. {@code state} is what says which,
 * which is why a page reads the state rather than inferring "stopped" from a missing day.
 *
 * <p>The moments come off the application's clock, so a rule left standing against a wound-forward
 * clock reads where the trainer wound it to.
 */
record SavingRuleResponse(Long id, long savingsAccountId, long fromCurrentAccountId, String name,
                          String trigger, String dayOfWeek, Integer dayOfMonth, String howMuchMoves,
                          BigDecimal amount, BigDecimal floor, List<SavingRuleSplitResponse> split,
                          String state, Instant createdAt, Instant pausedAt, Instant endedAt,
                          LocalDate nextFiresOn, SavingRuleWouldMoveResponse nextMoves) {

    static SavingRuleResponse of(RecordedSavingRule rule) {
        return new SavingRuleResponse(
                rule.id(),
                rule.savingsAccountId(),
                rule.currentAccountId(),
                rule.name(),
                rule.trigger().name(),
                rule.dayOfWeek() == null ? null : rule.dayOfWeek().name(),
                rule.dayOfMonth(),
                rule.howMuchMoves().name(),
                rule.amount(),
                rule.floor(),
                rule.split().stream().map(SavingRuleSplitResponse::of).toList(),
                rule.state().name(),
                rule.createdAt(),
                rule.pausedAt(),
                rule.endedAt(),
                rule.nextFiresOn(),
                SavingRuleWouldMoveResponse.of(rule.nextMoves()));
    }
}
