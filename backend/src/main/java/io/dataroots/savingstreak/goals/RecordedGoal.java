package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A savings goal as the rest of the application sees one: what it is called, what it is for, when it
 * is wanted by, where it stands in the order, and whether it is still being saved towards.
 *
 * <p>Public, unlike the row it is read from, because it is what the web layer names. The row itself
 * stays inside this module; a module that hands out its entities to be read elsewhere has no
 * boundary left to speak of.
 *
 * <p>{@code rank} is null exactly when the goal is not live, which is the same nullability the row
 * carries and for the same reason: a goal that has been given up on is not competing for anything.
 * Whoever renders a list can therefore tell the two families apart from the state alone.
 *
 * <p>The target is quoted to the cent on the way out. It goes through SQLite, which has no decimal
 * type and holds an amount as a float, so a target of 1500.00 comes back as 1500.0 and would reach a
 * page as a number rather than as money.
 *
 * <p>{@code allocation} is what the goal has claimed of the account's balance, {@code stillNeeded}
 * is the rest of its target, and {@code status} says whether it has arrived. All three are derived
 * on every read: the allocation is the sum of the moves in the ledger, and the other two are
 * comparisons against the target. None of them is a column, because a column would be a second copy
 * of an answer the ledger already gives and the two would eventually disagree.
 *
 * <p>{@code stillNeeded} never goes below zero. A goal cannot be given more than it needs, so a
 * negative would mean the ledger and the target had drifted, and reporting one as a figure to save
 * would be reporting a fault as a plan.
 *
 * <p>{@code weeklyAmount} is what the plan gives this goal each week: the account's declared weekly
 * capacity, spent across the goals in their order of importance by
 * {@link HowTheWeeklyMoneyIsSpent}. Derived on every read like the other three, and for the same
 * reason — it changes when the order changes, when money moves, and when the capacity is redeclared,
 * and none of those writes a column here.
 *
 * <p><strong>It is null exactly when no capacity has been declared, and never a zero then.</strong>
 * Nobody having said how fast anything fills is a different sentence from a plan in which this goal
 * is given nothing, and a page that read a zero for the first would draw the second. A goal the
 * capacity never reached, a goal that has arrived, and a goal that was given up on all read 0.00
 * once a capacity exists: they are given nothing, which is a figure.
 *
 * <p><strong>{@code pinnedWeeklyAmount} is the one figure here the customer stated rather than this
 * application derived</strong>: what they committed to putting into this goal every week. It is null
 * on a goal nobody has pinned, which is the ordinary case and means the engine decides what this
 * goal gets.
 *
 * <p>It travels beside {@code weeklyAmount} rather than replacing it, because the two are different
 * answers and a reader needs both: the pin is what was asked for, and the weekly amount is what the
 * plan could give. They differ exactly when the pin asks for more than what is left of the capacity
 * by the time this goal's turn comes — a pin of 500.00 against a capacity of 50.00 reads
 * {@code pinnedWeeklyAmount=500.00 weeklyAmount=50.00}, and a page showing only one of them could
 * not say why.
 *
 * <p>{@code willBeReachedOn} is the day the goal arrives at this rate: {@code stillNeeded} divided by
 * {@code weeklyAmount}, rounded up to whole weeks and counted forward in {@code SavingsWeek}, so it
 * is always a Monday in {@code Europe/Brussels} — see {@link WhenAGoalWillBeReached}. Derived on the
 * read like everything above it, and it moves whenever any of them does: freeing money from a goal
 * raises what it still needs and pushes this date out, and allocating to one pulls it in.
 *
 * <p><strong>It is null on a goal with no week to count</strong>: one that has arrived, one the plan
 * gives nothing, one on an account where no capacity has been declared, and one that was given up
 * on. {@code status} says which of those it was, so nobody reading an absent date has to guess —
 * which is why the date and the status travel together rather than either being inferred from the
 * other.
 */
public record RecordedGoal(Long id, long savingsAccountId, String name, BigDecimal target,
                           LocalDate deadline, Integer rank, GoalState state, GoalStatus status,
                           BigDecimal allocation, BigDecimal stillNeeded, BigDecimal weeklyAmount,
                           BigDecimal pinnedWeeklyAmount, LocalDate willBeReachedOn,
                           Instant createdAt, Instant abandonedAt) {
}
