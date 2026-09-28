package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One day a saving rule fell due, as the API reports it — which is exactly as a test reads it: the
 * day it was due, the moment it was dealt with, how it went, what moved and the deposit that moved
 * it. Shared by every test that reads a rule's history back, for the same reason as
 * {@link BalancesView} — copies of a shape drift into disagreeing about it.
 *
 * <p>{@code outcome} is read as the word the API sends rather than mapped onto an enumeration of the
 * test's own, for the reason {@link GoalView} gives: a rename in the backend should fail a test
 * rather than be quietly translated back.
 *
 * <p>{@code shortfall} is filled on one outcome only — an occurrence that could not be honoured —
 * and is null on every other. A test asserting that it came back null on a transfer that worked, or
 * on a sweep that found nothing above its floor, is asserting a real part of the contract: the
 * absence is what keeps arithmetic from being reported to a customer as a failure.
 *
 * <p>{@code depositId} is boxed because an occurrence that moved nothing made no deposit, and a test
 * that could not see the absence could not tell the two apart.
 *
 * <p>{@code intoGoals} and {@code leftUnallocated} are where the money went once it landed, and a
 * test adds them up: they come to exactly {@code amount}, every time, and a cent that appeared or
 * vanished between a balance and a goals page would show up in that sum first. {@code intoGoals}
 * holds a goal only if it actually took something, so a test that finds it empty on a rule with no
 * split is seeing the contract rather than an omission.
 *
 * <p>{@code daysLate} is the gap between the two, in whole days, as the API reports it — read rather
 * than worked out here, because a test that subtracted the moments for itself would be asserting its
 * own arithmetic and would pass against an application that reported nothing at all.
 */
public record RuleOccurrenceView(Long id, long ruleId, LocalDate dueOn, Instant settledAt,
                                 long daysLate, String outcome, BigDecimal amount,
                                 BigDecimal shortfall, Long depositId,
                                 List<RuleAllocationView> intoGoals, BigDecimal leftUnallocated) {
}
