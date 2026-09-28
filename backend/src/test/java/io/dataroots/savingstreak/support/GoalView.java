package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A savings goal as the API reports one, which is exactly as a test reads it: what it is called,
 * what it is for, when it is wanted by, where it stands in the order of importance, and whether it
 * is still being saved towards.
 *
 * <p>The state is read as the word the API sends rather than mapped onto an enum of the test's own,
 * for the reason {@link MoneyMovementView} gives: a rename in the backend should fail a test rather
 * than be quietly translated back.
 *
 * <p>{@code rank} and {@code abandonedAt} are boxed because each is null for exactly one family of
 * goal — a live goal has a rank and no abandoning moment, and a goal that was given up on has the
 * other. A test that reads either one has to be able to see that it is absent.
 *
 * <p>{@code weeklyAmount} is what the plan gives the goal each week, and it is boxed for the same
 * reason {@code rank} is: it is absent, and not zero, on an account whose holder has declared no
 * weekly capacity, and a primitive would read that absence back as 0.00 and pass the very test it
 * exists to fail.
 *
 * <p>{@code allocation}, {@code stillNeeded} and {@code status} are the three figures the backend
 * derives on every read rather than storing: what the goal has claimed of the account's balance, the
 * rest of its target, and whether it has arrived, is arriving in time, or is going to be late.
 * {@code status} is read as the word the API sends, like {@code state}, so that a rename in the
 * backend fails a test rather than being translated back.
 *
 * <p>{@code pinnedWeeklyAmount} is what the customer committed to putting into this goal each week,
 * and it is boxed for the reason {@code weeklyAmount} is: absent is the ordinary case — nobody has
 * pinned anything and the engine decides — and a test that could not see the absence could not tell
 * a goal that was unpinned from one pinned at whatever figure a zero would stand for. It is the
 * figure that was <em>asked</em> for, where {@code weeklyAmount} is what the plan could give, and the
 * two differ whenever a pin asks for more than the capacity has left.
 *
 * <p>{@code willBeReachedOn} is the Monday the goal arrives on at the rate the plan is filling it,
 * and it is boxed for the reason {@code weeklyAmount} is: it is absent on a goal that has arrived,
 * on one the plan gives nothing, and on every goal of an account where nobody has declared a
 * capacity, and a test that could not see the absence could not tell a projection from no projection
 * at all.
 */
public record GoalView(Long id, Long savingsAccountId, String name, BigDecimal target,
                       LocalDate deadline, Integer rank, String state, String status,
                       BigDecimal allocation, BigDecimal stillNeeded, BigDecimal weeklyAmount,
                       BigDecimal pinnedWeeklyAmount, LocalDate willBeReachedOn, Instant createdAt,
                       Instant abandonedAt) {
}
