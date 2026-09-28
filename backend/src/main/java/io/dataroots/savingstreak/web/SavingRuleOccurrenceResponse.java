package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.automation.RecordedOccurrence;

/**
 * One day a saving rule fell due, as the API reports it: the day it was due, the moment it was dealt
 * with, how it went, what moved and the deposit that moved it.
 *
 * <p>{@code dueOn} and {@code settledAt} are both here because they are different facts. The first
 * is the day the rule was supposed to move on, the second is when the application got round to it,
 * and after downtime or on a wound clock they are different days — the gap between them is how late
 * the transfer was, and a customer coming back to an application that was down is owed that rather
 * than a balance that is simply different from the one they remember.
 *
 * <p>{@code daysLate} is that gap in whole days, sent rather than left for a page to subtract. It is
 * what turns "this transfer was made this morning" into "this transfer was a hundred and fifty-three
 * days late", which is the sentence the customer is actually owed — and a page that worked it out
 * for itself would be a second place this application decides what a day is.
 *
 * <p>{@code shortfall} is filled on one outcome only: an occurrence that could not be honoured says
 * how much more the account would have needed, and every other occurrence sends null. Null rather
 * than nought on the other two, because "short of nothing" and "short of EUR 0,00" are different
 * sentences and only the first of them is true of a transfer that moved its whole amount or of a
 * sweep that found nothing above its floor. A page reading this can therefore tell the failure from
 * the arithmetic without inferring anything.
 *
 * <p>{@code depositId} is null on an occurrence that moved nothing, and {@code amount} is nothing.
 * Both are reported rather than left out, because an occurrence that found an empty account is as
 * much a part of the history as one that moved a hundred euros: that is the half a deposits ledger
 * can never hold, and it is why this record is read from the rule rather than derived from the money
 * that moved.
 *
 * <p><strong>{@code intoGoals} and {@code leftUnallocated} are where the money went once it
 * landed</strong>, and they always add up to {@code amount}. That sum is what keeps a customer's
 * balance and their goals page agreeing, and a page can check it: a cent that appeared or vanished
 * between the two would show up here first.
 *
 * <p>{@code intoGoals} is in the order the customer wrote the split, and holds a goal only if that
 * goal actually took something. A goal offered a share it would not take — because it is nearly
 * complete, or because it was given up on since — has no entry, and what it would not take spilled
 * to the next goal in the split or, at the end of it, into {@code leftUnallocated}.
 *
 * <p>{@code leftUnallocated} is the whole amount on a rule with no split, which is the honest
 * reading: the money landed in the account and no goal has claimed it, exactly as with a manual
 * deposit. It is nothing on an occurrence that moved nothing.
 *
 * <p>{@code outcome} travels as its own word rather than as an inference from a null amount, for the
 * reason a rule's state does: a page decides what to call each kind, and that is easier to get right
 * from a word.
 */
record SavingRuleOccurrenceResponse(Long id, long ruleId, LocalDate dueOn, Instant settledAt,
                                    long daysLate, String outcome, BigDecimal amount,
                                    BigDecimal shortfall, Long depositId,
                                    List<SavingRuleAllocationResponse> intoGoals,
                                    BigDecimal leftUnallocated) {

    static SavingRuleOccurrenceResponse of(RecordedOccurrence occurrence) {
        return new SavingRuleOccurrenceResponse(
                occurrence.id(),
                occurrence.ruleId(),
                occurrence.dueOn(),
                occurrence.settledAt(),
                occurrence.daysLate(),
                occurrence.outcome().name(),
                occurrence.amount(),
                occurrence.shortfall(),
                occurrence.depositId(),
                occurrence.intoGoals().stream().map(SavingRuleAllocationResponse::of).toList(),
                occurrence.leftUnallocated());
    }
}
