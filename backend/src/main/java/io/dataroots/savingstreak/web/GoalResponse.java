package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.goals.RecordedGoal;

/**
 * A savings goal as the API reports one: what it is called, what it is for, when it is wanted by,
 * where it stands in the order of importance, and whether it is still being saved towards.
 *
 * <p>One shape for a goal just created, a goal just changed, a goal read out of the account's list
 * and a goal that has been given up on — so nothing rendering a goal has to know which of those it
 * is holding in order to render it.
 *
 * <p>{@code rank} is null exactly when the goal is not live, and {@code abandonedAt} is filled
 * exactly when it is not: a goal that has been given up on holds no place in a competition it left.
 * The state travels as its own word rather than as an inference from a null, for the reason the
 * money-movement ledger's direction does — a page decides what to call each kind, and that is easier
 * to get right from a word.
 *
 * <p>{@code deadline} is a day rather than a moment, for the reason {@code PointsExpiringNext} gives:
 * a deadline is a day, and the zone it is read in is the backend's to pick rather than the browser's.
 *
 * <p>The moments come off the application's clock, so a goal opened against a wound-forward clock
 * reads where the trainer wound it to.
 *
 * <p>{@code weeklyAmount} is what the plan gives this goal each week, out of the capacity its
 * holder declared. It is null — and never a zero — on an account where nobody has declared one:
 * "nothing says how fast this fills" and "this goal is given nothing" are different sentences, and a
 * page that drew the first as the second would show the customer a plan they never made. Once a
 * capacity exists every goal carries a figure, and 0.00 is a real answer: the capacity ran out
 * before this goal, or it has arrived, or it was given up on.
 *
 * <p>{@code pinnedWeeklyAmount} is what the customer committed to putting into this goal each week,
 * and null on a goal they have committed nothing to — which is the ordinary case and means the
 * engine decides. It travels beside {@code weeklyAmount} rather than replacing it because the two
 * are different answers: the pin is what was asked for, the weekly amount is what the plan could
 * give, and they differ exactly when the pin asks for more than the capacity has left by the time
 * this goal's turn comes. A page that showed only one of them could not tell the customer why their
 * 500.00 a week is being planned at 50.00.
 *
 * <p>{@code allocation} is what the goal has claimed of the account's balance and {@code stillNeeded}
 * is the rest of its target; both are derived on every read from the ledger of moves, and neither is
 * stored. {@code status} is the third derived figure and is the one a page reads to tell a goal that
 * has arrived, or one arriving in time, from one that is going to be late — {@code state} is the
 * column, which says only whether it was given up on, and {@code COMPLETED} is deliberately not a
 * value it can hold.
 *
 * <p>{@code willBeReachedOn} is the day the goal arrives at the rate the plan is filling it: a
 * Monday, in the zone savings weeks are counted in, so that a goal's week and a streak's week are
 * the same seven days. It is a day rather than a moment for the reason {@code deadline} is.
 *
 * <p><strong>It is null on every goal with no week left to count</strong> — one that has arrived,
 * one the plan gives nothing, one on an account where nobody has declared a capacity, and one that
 * was given up on — and {@code status} is what says which of those happened. A page never has to
 * infer the reason from the absence: {@code COMPLETED}, {@code UNREACHABLE}, {@code STILL_SAVING}
 * and {@code ABANDONED} are four different sentences, and the date is missing in all four.
 */
record GoalResponse(Long id, Long savingsAccountId, String name, BigDecimal target,
                    LocalDate deadline, Integer rank, String state, String status,
                    BigDecimal allocation, BigDecimal stillNeeded, BigDecimal weeklyAmount,
                    BigDecimal pinnedWeeklyAmount, LocalDate willBeReachedOn, Instant createdAt,
                    Instant abandonedAt) {

    static GoalResponse of(RecordedGoal goal) {
        return new GoalResponse(goal.id(), goal.savingsAccountId(), goal.name(), goal.target(),
                goal.deadline(), goal.rank(), goal.state().name(), goal.status().name(),
                goal.allocation(), goal.stillNeeded(), goal.weeklyAmount(),
                goal.pinnedWeeklyAmount(), goal.willBeReachedOn(), goal.createdAt(),
                goal.abandonedAt());
    }
}
