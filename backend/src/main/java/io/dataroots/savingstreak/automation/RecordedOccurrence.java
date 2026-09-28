package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One day a saving rule fell due, as the rest of the application sees it: the day it was due, the
 * moment it was dealt with, how it went, what moved and the deposit that moved it.
 *
 * <p>Public, unlike the row it is read from, because it is what the web layer names. The row itself
 * stays inside this module; a module that hands out its entities to be read elsewhere has no
 * boundary left to speak of.
 *
 * <p><strong>{@code dueOn} and {@code settledAt} are two different facts and both are here.</strong>
 * The first is the day the rule was supposed to move on, the second is when the application got
 * round to it, and on a wound clock or after downtime they are different days. The gap between them
 * is how late the transfer was, which is a thing a customer coming back to an application that was
 * down is owed an answer about.
 *
 * <p><strong>{@code daysLate} is that gap, said rather than left to be worked out.</strong> The
 * feature's promise about downtime is not only that the transfer is made up but that the lateness is
 * <em>reported as lateness</em>: a customer looking at five transfers all stamped with this morning
 * has no way to tell a catch-up from five transfers that were always meant for today, and the whole
 * reason the money is not back-dated is that the two are different histories. It is derived from the
 * other two here rather than stored beside them, because a third copy of one subtraction is a third
 * thing that can disagree.
 *
 * <p><strong>{@code shortfall} is the other figure, and only one of the three outcomes carries
 * it.</strong> An occurrence that could not be honoured says how much more the account would have
 * needed; every other occurrence leaves it null, because an occurrence that moved its whole amount
 * was short of nothing and a sweep that found nothing above its floor was not short either — its
 * figure was derived from the balance. The absence is therefore a reading rather than a gap, and it
 * is what keeps "there was nothing to move" from being reported to a customer as a failure.
 *
 * <p>{@code amount} is what actually moved, and it is nothing on an occurrence that moved nothing.
 * {@code depositId} names the deposit it made and is null when it made none — which is also the
 * whole reason this record exists beside the deposits ledger rather than being derived from it: an
 * occurrence that found an empty account is as much a part of the history as one that moved a
 * hundred euros, and a ledger of movements has no row for a movement that did not happen.
 *
 * <p>The amount is quoted to the cent on the way out, because it has been through SQLite, which has
 * no decimal type and holds an amount as a float — so 50.00 comes back as 50.0 and would reach a
 * page as a number rather than as money.
 *
 * <p><strong>{@code intoGoals} and {@code leftUnallocated} are where the money went once it
 * landed</strong>, and together they always add up to {@code amount}. That sum is the promise: a
 * customer's balance and their goals page have to agree, and an occurrence that could not say where
 * every cent of it went would be the place they stopped agreeing.
 *
 * <p>{@code intoGoals} is in the order the customer wrote the split, and holds a goal only if that
 * goal actually took something — a goal offered a share it would not take has no entry, because
 * nothing moved and a 0.00 would claim otherwise. What it would not take is in
 * {@code leftUnallocated} along with everything else no goal would have.
 *
 * <p><strong>{@code leftUnallocated} is derived here rather than stored</strong>, as
 * {@code amount − } what the goals took, for the reason this application derives what no goal has
 * claimed as {@code balance − allocated}: two stored figures that have to agree eventually stop
 * agreeing. It is the whole amount on a rule with no split, which is the honest reading — such a
 * rule deposits unallocated, exactly as a manual deposit does — and nothing at all on an occurrence
 * that moved nothing.
 */
public record RecordedOccurrence(Long id, long ruleId, LocalDate dueOn, Instant settledAt,
                                 long daysLate, OccurrenceOutcome outcome, BigDecimal amount,
                                 BigDecimal shortfall, Long depositId, List<WhatAGoalGot> intoGoals,
                                 BigDecimal leftUnallocated) {
}
