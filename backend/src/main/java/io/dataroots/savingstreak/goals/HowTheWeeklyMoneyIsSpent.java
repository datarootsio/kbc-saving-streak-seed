package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The engine: given what a customer says they can put away in a week, the goals in their order of
 * importance and what each one still needs, what each goal gets each week.
 *
 * <p>This is where the goals compete. The balance is the feature's hard scarcity and breaking it is
 * a refusal; this is the planning one, and when the goals' weekly needs add up to more than the
 * capacity somebody is going to be late — which is the thing the customer is here to see.
 *
 * <p><strong>Three passes, always in rank order.</strong>
 *
 * <ol>
 * <li><strong>Pinned goals take their pinned amount.</strong> A pin is a commitment the customer
 * made, so the engine plans around it rather than over it: it is taken before any deadline minimum,
 * whatever the pinned goal's rank, and a pinned goal is passed over by both the passes below so that
 * nothing quietly tops a commitment up to what a deadline would have wanted. Taken in rank order and
 * only as far as the capacity reaches — see {@link #asAt}, where that one cap is argued.</li>
 * <li><strong>Each remaining goal with a deadline takes its minimum</strong> — what it still needs
 * divided by the whole weeks left, rounded <em>up</em> to the cent — in rank order, until the
 * capacity runs out. A goal that reaches the front with nothing left gets nothing, and that is the
 * competition doing its work rather than a fault. Rounding up matters: rounded down, a goal arrives
 * a cent short on the day it was due and the next slice would call that on track. The weeks are
 * counted from the Monday this week began on, which is the anchor {@link WhenAGoalWillBeReached}
 * counts its projection forward from — see {@link #wholeWeeksLeftUntil}, where the whole of that
 * agreement lives.</li>
 * <li><strong>Whatever is left goes to the highest-ranked unpinned goal that is not finished.</strong>
 * Money the customer can save should be going somewhere.</li>
 * </ol>
 *
 * <p><strong>A strict waterfall was rejected</strong> — rank 1 taking everything it needs before
 * rank 2 sees a cent — because it breaks on the first goal with no deadline: an emergency fund
 * "needs" everything, forever, and starves everything under it. <strong>Proportional weights were
 * rejected</strong> for the opposite reason: they spread the pain evenly and nobody can see who
 * lost, which is the one thing the customer needs to see.
 *
 * <p><strong>A goal with no capacity behind it gets no weekly figure at all, and not a zero.</strong>
 * Nobody having said how fast anything fills is a different sentence from a plan in which a goal
 * gets nothing, and {@link TheWeeklyMoneySpent#forGoal} is the one place that distinction is made.
 *
 * <p><strong>Completed and abandoned goals claim nothing and consume nothing.</strong> A goal that
 * has reached its target needs nothing, so it takes no minimum and is passed over for the surplus;
 * an abandoned goal has left the order altogether and never reaches this class.
 *
 * <p>Static, with no state, no clock and no bean, exactly as {@code WeekAndStreakDerivation} is, and
 * for the same reason: the answer is a function of figures the caller already holds, it is worked
 * out on every read and it is stored nowhere. A stored plan would be a claim about a moment that has
 * since moved — the customer reorders their goals, or frees money from one, and every figure in it
 * is wrong with nothing to say so.
 *
 * <p>The day is the caller's, not a clock's. Which day it is decides which Monday the weeks are
 * counted from, and so how many whole weeks are left before a deadline; a class that read a clock of
 * its own could answer about a different day from the one the rest of the read is about — and the
 * projection this plan is judged against would then be counting from a different Monday. *
 * <p><strong>Public for the simulator's fold, and only the function is opened.</strong> That fold
 * asks a branch's own capacity and a branch's own goals the very question this class answers for the
 * account as it stands, so that "twenty-five euros more a week" can be shown reaching a goal earlier
 * — and the day it names has to be the day this names, or the simulator and the goals screen would
 * be two answers to one question. It is the same widening seven of the rule classes already carry
 * and it stands on the same ground: there is no repository here, no entity and no state, only a
 * function of figures the caller already holds. Nothing about how the account's goals are read,
 * ranked or written is opened by it; {@code GoalsService} is still the only way in to any of that.
 */
public final class HowTheWeeklyMoneyIsSpent {

    private static final Logger log = LoggerFactory.getLogger(HowTheWeeklyMoneyIsSpent.class);

    /** How a capacity nobody has declared reads in the log line, where a null would say less. */
    private static final String NOT_DECLARED = "not declared";

    private HowTheWeeklyMoneyIsSpent() {
    }

    /**
     * What each goal gets each week, worked out from the capacity, the order and what each goal
     * still needs.
     *
     * <p><strong>Nothing given out ever adds up to more than the capacity, pins included.</strong>
     * A pin is taken as far as what is unspent reaches and no further. It is the one place the three
     * passes could have broken that sum, and it does not, because the capacity is the customer's own
     * sentence about the most they can put away in a week: a plan handing out more than that is a
     * plan nobody could carry out, and every figure downstream — what the goals below are given,
     * when each of them arrives — would be quoting money that was never going to be saved. Pinning
     * more than the capacity is still accepted rather than refused, and the consequence the customer
     * asked for by pinning is still delivered in full: the pinned goal takes the whole capacity
     * ahead of every deadline, and every goal below it is given nothing and says so.
     *
     * @param weeklyCapacity the most the customer says they can put away in a week, or null if they
     *                       have not said — in which case no goal is given a figure at all, not even
     *                       a pinned one: a pin is a claim on a capacity, and there is no capacity
     *                       here to claim against
     * @param inRankOrder    the account's live goals, most important first
     * @param today          the day the weeks left before a deadline are counted from
     */
    public static TheWeeklyMoneySpent asAt(long savingsAccountId, BigDecimal weeklyCapacity,
                                           List<AGoalCompetingForIt> inRankOrder, LocalDate today) {
        // Every goal starts on nothing, in rank order, so that a goal the passes never reach reads
        // as having been given nothing rather than as having been left out of the plan.
        Map<Long, BigDecimal> spent = new LinkedHashMap<>();
        for (AGoalCompetingForIt goal : inRankOrder) {
            spent.put(goal.goalId(), BigDecimal.ZERO);
        }

        BigDecimal left = weeklyCapacity == null
                ? BigDecimal.ZERO
                : AmountOfMoney.quotedToTheCent(weeklyCapacity);
        if (weeklyCapacity != null) {
            // Pass one: the commitments the customer has already made, in rank order and out of the
            // same capacity as everything else.
            for (AGoalCompetingForIt goal : inRankOrder) {
                if (goal.isFinished() || !goal.isPinned()) {
                    continue;
                }
                // As far as what is left reaches, exactly as the second pass takes a minimum. A pin
                // larger than what is still unspent is a legal thing to have said and is not refused
                // anywhere; what it cannot be is given, because the capacity is the customer's own
                // sentence about the most they can put away in a week, and a plan spending more than
                // that is a plan they cannot carry out. What the pin does buy is everything: it is
                // taken before any deadline minimum, so the goals below it are given whatever is
                // left and told they will not arrive — which is the information the customer asked
                // for by pinning.
                BigDecimal taken = goal.pinnedWeeklyAmount().min(left);
                if (taken.signum() <= 0) {
                    continue;
                }
                spent.merge(goal.goalId(), taken, BigDecimal::add);
                left = left.subtract(taken);
            }

            // Pass two: what each dated goal needs every week to arrive on time, in rank order,
            // until there is nothing left to give.
            for (AGoalCompetingForIt goal : inRankOrder) {
                if (goal.isFinished() || goal.isPinned() || goal.deadline() == null) {
                    continue;
                }
                BigDecimal taken = theDeadlineMinimumFor(goal, today).min(left);
                if (taken.signum() <= 0) {
                    continue;
                }
                spent.merge(goal.goalId(), taken, BigDecimal::add);
                left = left.subtract(taken);
            }

            // Pass three: money the customer can save should be going somewhere.
            if (left.signum() > 0) {
                for (AGoalCompetingForIt goal : inRankOrder) {
                    if (goal.isFinished() || goal.isPinned()) {
                        continue;
                    }
                    spent.merge(goal.goalId(), left, BigDecimal::add);
                    left = BigDecimal.ZERO;
                    break;
                }
            }
        }

        // The whole derivation on one line, so that a reviewer can walk the three passes by hand:
        // the capacity they were spending, the day it was worked out for and the Monday the weeks
        // were counted from — the same Monday the projection counts forward from — and every goal in
        // rank order with what it still needs, when it is wanted by, how many whole weeks that is,
        // the minimum that came out of the division, and what it ended up being given. Guarded,
        // because writing the goals out is work and this runs on every read of a goal.
        if (log.isDebugEnabled()) {
            log.debug("the weekly money is spent savingsAccountId={} weeklyCapacity={} today={} "
                            + "thisWeekStartsOn={} goals={} spent=[{}] givenOut={} leftOver={}",
                    savingsAccountId, asMoneyOrNotDeclared(weeklyCapacity), today,
                    SavingsWeek.containing(today).startsOn(), inRankOrder.size(),
                    asSpent(inRankOrder, spent, weeklyCapacity, today),
                    AmountOfMoney.asMoney(totalOf(spent)),
                    weeklyCapacity == null ? NOT_DECLARED : AmountOfMoney.asMoney(left));
        }
        return new TheWeeklyMoneySpent(
                weeklyCapacity == null ? null : AmountOfMoney.quotedToTheCent(weeklyCapacity), spent);
    }

    /**
     * What a dated goal has to be given every week to arrive on the day it is wanted: what it still
     * needs, divided by the whole weeks left, rounded <strong>up</strong> to the cent.
     *
     * <p>Up, deliberately. Rounded down, a goal needing 10.00 over three weeks would be given 3.33 a
     * week and arrive a cent short on the day it was due, and the slice that compares a projection
     * against a deadline would call that on track. 3.34 arrives with a cent to spare, and a cent to
     * spare is the right side to be wrong on.
     *
     * <p>The contract this figure carries: a goal given exactly this much a week is reported
     * {@link GoalStatus#ON_TRACK} by the same read. {@code weekly >= stillNeeded / weeksLeft}, so
     * {@code ceil(stillNeeded / weekly) <= weeksLeft}, so the projection — that many weeks on from
     * the same Monday {@link #wholeWeeksLeftUntil} counted from — lands on or before the deadline.
     * It holds only because both counts start on that Monday; see there.
     */
    private static BigDecimal theDeadlineMinimumFor(AGoalCompetingForIt goal, LocalDate today) {
        return goal.stillNeeded()
                .divide(BigDecimal.valueOf(wholeWeeksLeftUntil(goal.deadline(), today)), 2,
                        RoundingMode.CEILING);
    }

    /**
     * How many whole weeks there are to save in before a deadline: how many Mondays there are, from
     * the one this week began on, that still land on or before the day the goal is wanted by.
     *
     * <p><strong>Counted from this week's Monday, not from today, and rounded down.</strong> That is
     * the one anchor both halves of a read count from. {@link WhenAGoalWillBeReached} answers "when
     * will I have it" with {@code thisWeek's Monday + ceil(stillNeeded / weeklyAmount)} weeks,
     * because money arrives in whole savings weeks; so the deadline a goal can actually be met on is
     * the last of those Mondays that is not past it, which is exactly
     * {@code floor((deadline - thisWeek's Monday) / 7)}. Divide what is still needed by that figure
     * and the projection provably lands on or before the deadline: the plan funds the goal to its
     * own minimum and the same read calls it {@link GoalStatus#ON_TRACK}.
     *
     * <p>It used to count {@code ceil((deadline - today) / 7)} from today. The two anchors agree
     * only when the deadline is an exact multiple of seven days away, and everywhere else they
     * disagreed the expensive way round: the engine handed a goal precisely the figure it had
     * computed for arriving on time and the projection, counting from a Monday that could be up to
     * six days earlier, reported the goal late. Counting from today also allowed more weeks than a
     * Monday-grained projection can use — a goal wanted in eight days was given two weeks to find
     * the money in when only one Monday falls on or before that day, so it was funded at half what
     * arriving in time costs.
     *
     * <p>Never fewer than one. A deadline inside this week, one today, or one that went by while the
     * goal stayed open — a deadline is soft and nothing in this module closes a goal because a date
     * passed — means the whole of what is left is wanted now, and dividing by nothing is not an
     * answer. The projection will still say that goal is late, which it is: the earliest Monday the
     * money can all be there by is next Monday, and the goal was wanted before it.
     */
    private static long wholeWeeksLeftUntil(LocalDate deadline, LocalDate today) {
        long days = ChronoUnit.DAYS.between(SavingsWeek.containing(today).startsOn(), deadline);
        return Math.max(1, Math.floorDiv(days, 7));
    }

    /** What the plan gave out altogether, for the log line to be checked against the capacity. */
    private static BigDecimal totalOf(Map<Long, BigDecimal> spent) {
        return spent.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** A capacity in the log line: the figure as money, or the word for a customer who has not said. */
    private static String asMoneyOrNotDeclared(BigDecimal weeklyCapacity) {
        return weeklyCapacity == null ? NOT_DECLARED : AmountOfMoney.asMoney(weeklyCapacity);
    }

    /**
     * The goals written out for the log line, in the order the passes went through them, each with
     * everything that decided its figure.
     *
     * <p>The minimum is worked out again here rather than carried out of the pass that used it.
     * It is a pure division of two figures already on the line, this runs only under the debug
     * guard, and threading a second map through three passes to save it would be more moving parts
     * in the loop the reader is trying to follow.
     */
    private static String asSpent(List<AGoalCompetingForIt> inRankOrder, Map<Long, BigDecimal> spent,
                                  BigDecimal weeklyCapacity, LocalDate today) {
        List<String> said = new ArrayList<>();
        for (AGoalCompetingForIt goal : inRankOrder) {
            StringBuilder line = new StringBuilder()
                    .append("rank=").append(goal.rank())
                    .append(" goalId=").append(goal.goalId())
                    .append(" \"").append(goal.name()).append("\"")
                    .append(" needs=").append(AmountOfMoney.asMoney(goal.stillNeeded()));
            if (goal.isFinished()) {
                line.append(" finished, so it claims nothing");
            } else if (goal.isPinned()) {
                // The minimum its deadline would have asked for is written out beside the pin, even
                // though no pass uses it, because that comparison is the whole question a reviewer
                // has about a pinned goal: a pin under it is why the goal is late and why nothing
                // topped it up, and a pin over it is why the goal arrives early.
                line.append(" pinned at ").append(AmountOfMoney.asMoney(goal.pinnedWeeklyAmount()));
                if (goal.deadline() != null) {
                    line.append(" by=").append(goal.deadline())
                            .append(" wholeWeeksLeft=").append(wholeWeeksLeftUntil(goal.deadline(), today))
                            .append(" theDeadlineWouldHaveWanted=")
                            .append(AmountOfMoney.asMoney(theDeadlineMinimumFor(goal, today)));
                }
            } else if (goal.deadline() == null) {
                line.append(" no deadline, so it claims nothing in the second pass");
            } else {
                line.append(" by=").append(goal.deadline())
                        .append(" wholeWeeksLeft=").append(wholeWeeksLeftUntil(goal.deadline(), today))
                        .append(" minimum=").append(AmountOfMoney.asMoney(theDeadlineMinimumFor(goal, today)));
            }
            line.append(" gets=").append(weeklyCapacity == null
                    ? NOT_DECLARED
                    : AmountOfMoney.asMoney(spent.getOrDefault(goal.goalId(), BigDecimal.ZERO)));
            said.add(line.toString());
        }
        return String.join("; ", said);
    }

    /**
     * One goal as the engine sees it: where it stands in the order, what it still needs, the day it
     * is wanted by if it has one, and the weekly amount pinned to it if the customer has pinned one.
     *
     * <p>Not the row and not {@link RecordedGoal}. The engine needs five figures and nothing else —
     * not the name, except to say which goal a log line is about — and a derivation handed a whole
     * entity is a derivation that can quietly start reading a sixth.
     */
    public record AGoalCompetingForIt(Long goalId, String name, Integer rank,
                                      BigDecimal stillNeeded, LocalDate deadline,
                                      BigDecimal pinnedWeeklyAmount) {

        /** Whether it has arrived: it needs nothing, so it claims nothing and takes no surplus. */
        boolean isFinished() {
            return stillNeeded.signum() <= 0;
        }

        /** Whether the customer has committed a weekly amount to it, which the plan spends first. */
        boolean isPinned() {
            return pinnedWeeklyAmount != null;
        }
    }

    /**
     * What the plan spends, per goal: the capacity it was spending, and what each goal was given out
     * of it.
     *
     * <p>{@link #forGoal} is the one place that answers "no capacity" with no figure rather than
     * with a zero. Everything downstream asks it rather than reading the map, so that the
     * distinction between a customer who has said nothing and a plan in which a goal gets nothing
     * cannot be made two ways.
     */
    public record TheWeeklyMoneySpent(BigDecimal weeklyCapacity, Map<Long, BigDecimal> byGoal) {

        /**
         * What this goal gets each week: null when nothing says how fast anything fills, and
         * otherwise a figure — 0.00 for a goal the capacity never reached, for one that has arrived,
         * and for one that has left the order altogether.
         */
        public BigDecimal forGoal(Long goalId) {
            return weeklyCapacity == null
                    ? null
                    : AmountOfMoney.quotedToTheCent(byGoal.getOrDefault(goalId, BigDecimal.ZERO));
        }
    }
}
