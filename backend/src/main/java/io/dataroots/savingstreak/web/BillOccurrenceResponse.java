package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.accounts.ABillThatFellDue;
import io.dataroots.savingstreak.accounts.BillOutcome;

/**
 * One date a recurring bill fell due, as the API reports it: the day it was owed, the moment it was
 * settled, how late that was, what it asked for and whether it was paid.
 *
 * <p>{@code dueOn} and {@code settledAt} are both here because they are different facts. The first
 * is the day the money was owed, the second is when the application got round to it, and after
 * downtime or on a wound clock they are different days. A late payment that showed only one of them
 * would be a history that rewrote itself.
 *
 * <p>{@code daysLate} is that gap in whole days, sent rather than left for a page to subtract. It is
 * what turns "this went out this morning" into "this went out sixty-one days late", which is the
 * sentence the customer is actually owed — and a page that worked it out for itself would be a
 * second place this application decides what a day is.
 *
 * <p>{@code amount} is what the date asked for, and it is the same figure on both outcomes: on a
 * paid date it is what left, and on an unpaid one it is what the customer still owes. A nought on
 * the unpaid ones would record that a bill of nine hundred euros was presented for nothing, which is
 * the one thing a record of what happened must not say.
 *
 * <p>{@code outcome} travels as its own word rather than as an inference from the amount, for the
 * reason a bill's state does: a page decides what to call each kind, and that is easier to get right
 * from a word than from the absence of a figure. There are two of them, and there is no third —
 * nothing is ever partly taken.
 *
 * <p>The identifier is here for the page to key its rows on, which is the same use a saving rule's
 * occurrence identifier is put to.
 */
record BillOccurrenceResponse(Long id, long billId, long currentAccountId, LocalDate dueOn,
                              Instant settledAt, long daysLate, BillOutcome outcome,
                              BigDecimal amount) {

    static BillOccurrenceResponse of(ABillThatFellDue settled) {
        return new BillOccurrenceResponse(settled.id(), settled.billId(),
                settled.currentAccountId(), settled.dueOn(), settled.settledAt(),
                settled.daysLate(), settled.outcome(), settled.amount());
    }
}
