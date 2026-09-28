package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A saving rule as the API reports one, which is exactly as a test reads it: what it is called,
 * where the money comes from, what makes it move, which day that is, how much moves, and whether it
 * is still standing.
 *
 * <p>The trigger, the kind of amount, the day of the week and the state are read as the words the
 * API sends rather than mapped onto enumerations of the test's own, for the reason {@link GoalView}
 * gives: a rename in the backend should fail a test rather than be quietly translated back.
 *
 * <p>{@code dayOfWeek} and {@code dayOfMonth} are each absent on every rule whose trigger does not
 * need them, and {@code dayOfMonth} is absent on a payday rule whose holder has declared no income
 * — the day of such a rule is that declaration's, read on every read. Both are therefore boxed: a
 * test that could not see the absence could not tell a rule with no day from one that moves on the
 * first of the month.
 *
 * <p>{@code split} is how what the rule moves is spread across the account's goals, in the order
 * their holder wrote it, and it is empty on the ordinary rule that has none. Read as a list rather
 * than as a map, because the order is load-bearing: it settles which goal a leftover cent goes to
 * and which goal a share spills to, and a test reading it into a map could not see it.
 *
 * <p>{@code pausedAt} is filled exactly while the rule is paused, and {@code endedAt} exactly once
 * it has been ended. A test reads the state for what a rule is and these for when it became it, so
 * that "it says it is paused" and "it has been paused since this moment" stay two assertions rather
 * than one inferred from the other.
 *
 * <p>{@code amount} and {@code floor} are boxed for the same reason and it is sharper here: exactly
 * one of them is ever filled, and a test that read the other back as 0.00 would pass the very
 * assertion it exists to make — that a rule says which of the two it moves.
 *
 * <p>{@code nextFiresOn} and {@code nextMoves} are the day this rule moves money next and what it
 * will move when it does, and they are null together. Both are boxed for the reason the days are: a
 * test that could not see the absence could not tell a rule whose holder has paused it from one that
 * is about to fire, which is the difference this pair exists to report.
 */
public record SavingRuleView(Long id, long savingsAccountId, long fromCurrentAccountId, String name,
                             String trigger, String dayOfWeek, Integer dayOfMonth,
                             String howMuchMoves, BigDecimal amount, BigDecimal floor,
                             List<SavingRuleSplitView> split, String state, Instant createdAt,
                             Instant pausedAt, Instant endedAt, LocalDate nextFiresOn,
                             WhatWouldMoveView nextMoves) {
}
