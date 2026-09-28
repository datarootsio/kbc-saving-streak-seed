package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When each goal will be reached at the rate the plan is filling it, and whether that is in time.
 *
 * <p><strong>The projection is the only thing this class really computes.</strong> What a goal still
 * needs, divided by what {@link HowTheWeeklyMoneyIsSpent} gives it each week, rounded <em>up</em> to
 * whole weeks, counted forward in {@link SavingsWeek} — Mondays, {@code Europe/Brussels} — so that a
 * goal's week and a streak's week are the same seven days. Every status below is a comparison
 * against it, which is why the projection is what a goal reports and the status is what it derives:
 * a status without a date behind it is a verdict nobody can check.
 *
 * <p><strong>The Monday the weeks start on is the anchor the engine counts from too.</strong>
 * {@code HowTheWeeklyMoneyIsSpent.wholeWeeksLeftUntil} asks how many of these Mondays still land on
 * or before a deadline and divides what is still needed by that, so a goal funded to exactly its
 * deadline minimum projects on or before the day it is wanted by and this class calls it
 * {@link GoalStatus#ON_TRACK}. Anchoring the two halves of one read on different days — the engine
 * on today, this on today's Monday — is what made the application fund a goal correctly and, in the
 * same response, tell the customer they were going to be late.
 *
 * <p>The date answered is the Monday the money is all there <em>by</em>, not the Monday the last
 * week starts on. A goal given the whole of what it needs this week is reached by next Monday, and
 * saying so is an answer to "when will I have it"; naming this week's Monday would be naming a day
 * that has already gone.
 *
 * <table>
 * <caption>The five statuses, and the comparison behind each</caption>
 * <tr><td>{@link GoalStatus#COMPLETED}</td><td>the allocation has reached the target</td></tr>
 * <tr><td>{@link GoalStatus#ON_TRACK}</td><td>there is a deadline and the projection lands on or before it</td></tr>
 * <tr><td>{@link GoalStatus#OFF_TRACK}</td><td>there is a deadline and the projection lands after it</td></tr>
 * <tr><td>{@link GoalStatus#UNREACHABLE}</td><td>the plan gives this goal nothing, so it never arrives at all</td></tr>
 * <tr><td>{@link GoalStatus#NO_DEADLINE}</td><td>there is no deadline: a projection, and nothing to be late for</td></tr>
 * </table>
 *
 * <p><strong>There is deliberately no {@code AT_RISK} band.</strong> It needs a threshold nobody can
 * defend — a fortnight? a tenth of the time left? — and a projection three days past a deadline
 * already says exactly what it is.
 *
 * <p><strong>A goal with no deadline still gets a projection.</strong> That is the answer to "when
 * will I have it", and it is the whole reason the projection is reported separately from the status:
 * an emergency fund with no date is filling at a rate, and the rate has a date in it. What it cannot
 * be is on or off track, because there is nothing to be late for.
 *
 * <p><strong>A goal the plan gives nothing is {@code UNREACHABLE} and reports no date.</strong>
 * Dividing by nothing is not an answer, and a date infinitely far away would be a projection a page
 * had to special-case anyway — the status says the thing the customer needs to hear, which is that
 * at this rate it never arrives.
 *
 * <p><strong>Withdrawals need nothing of their own here.</strong> Freeing money from a goal lowers
 * its allocation, which raises what it still needs, which pushes this division out by whole weeks.
 * That is the brief's question about withdrawals answered by arithmetic that was already here.
 *
 * <p>Static, with no state, no clock and no bean, exactly as {@link HowTheWeeklyMoneyIsSpent} and
 * {@code WeekAndStreakDerivation} are, and for the same reason: it is a function of figures the
 * caller already holds, worked out on every read and stored nowhere. A stored projection would be a
 * claim about a moment that has since moved — the customer reorders their goals and every date in it
 * is wrong with nothing to say so.
 *
 * <p>The day is the caller's, not a clock's, for the reason the engine's is: which day it is decides
 * which Monday the weeks are counted from, and a class reading a clock of its own could answer about
 * a different day from the one the rest of the read is about. *
 * <p><strong>Public for the simulator's fold, and only the function is opened.</strong> That fold
 * asks a branch's own capacity and a branch's own goals the very question this class answers for the
 * account as it stands, so that "twenty-five euros more a week" can be shown reaching a goal earlier
 * — and the day it names has to be the day this names, or the simulator and the goals screen would
 * be two answers to one question. It is the same widening seven of the rule classes already carry
 * and it stands on the same ground: there is no repository here, no entity and no state, only a
 * function of figures the caller already holds. Nothing about how the account's goals are read,
 * ranked or written is opened by it; {@code GoalsService} is still the only way in to any of that.
 */
public final class WhenAGoalWillBeReached {

    private static final Logger log = LoggerFactory.getLogger(WhenAGoalWillBeReached.class);

    /**
     * How far out a projection is worth naming: a thousand years of Mondays.
     *
     * <p>A bound rather than a date somebody reads. A capacity of a cent a week against a target of
     * a thousand euros divides out to nineteen centuries, which is a goal that does not arrive in
     * any span this application will put in front of a customer — and past {@code LocalDate}'s own
     * limit the arithmetic stops being a date at all and starts being an exception in the middle of
     * a page read. Both of those are the same answer said twice: at this rate it never arrives, which
     * is what {@link GoalStatus#UNREACHABLE} says.
     */
    private static final BigDecimal MOST_WEEKS_WORTH_PROJECTING = BigDecimal.valueOf(52_000);

    /** How a goal with no date of its own reads in the log line, where a null would say less. */
    private static final String NO_DEADLINE = "no deadline";

    private WhenAGoalWillBeReached() {
    }

    /**
     * When each of the account's goals will be reached, and where each one stands, in the order they
     * compete in.
     *
     * <p>One INFO line for the whole read, carrying every goal's status and the date behind it, so
     * that a reviewer can see exactly what the customer was told and not merely that something was
     * worked out. At INFO rather than DEBUG, unlike the engine's line: what a customer was told about
     * their goals is a business event, and a log that only carried it when somebody had turned DEBUG
     * on would carry it on none of the reads anybody is asking about afterwards.
     *
     * <p>Said only when there is a live plan to describe. An account with no goals on it was told
     * nothing, so a line saying nothing was worked out is a line that only makes the ones that
     * matter harder to find — and a caller reading the abandoned goals is not asking about this
     * plan at all, which is why it does not build one (see {@code GoalsService.noLivePlanOn}). The
     * goals written out are always the ones the answer was about.
     *
     * @param inRankOrder the account's live goals, most important first, each carrying what it still
     *                    needs and what the plan gives it each week
     * @param today       the day the weeks are counted from — its Monday is where they start, and it
     *                    is the same Monday {@code HowTheWeeklyMoneyIsSpent.wholeWeeksLeftUntil}
     *                    counts a deadline's weeks from
     */
    public static Map<Long, TheProjection> forAllOf(long savingsAccountId,
                                                    List<AGoalOnItsWay> inRankOrder,
                                                    LocalDate today) {
        Map<Long, TheProjection> projected = new LinkedHashMap<>();
        for (AGoalOnItsWay goal : inRankOrder) {
            projected.put(goal.goalId(), projectionFor(goal, today));
        }
        if (!inRankOrder.isEmpty() && log.isInfoEnabled()) {
            log.info("when the goals will be reached savingsAccountId={} today={} thisWeekStartsOn={} "
                            + "goals={} projected=[{}]",
                    savingsAccountId, today, SavingsWeek.containing(today).startsOn(),
                    inRankOrder.size(), asProjected(inRankOrder, projected));
        }
        return projected;
    }

    /**
     * One goal's projection and the status it derives.
     *
     * <p>The order of the questions is the whole of the rule, and each one is asked before the one
     * under it can be misleading. A goal that has arrived is {@code COMPLETED} whatever anybody has
     * declared about weekly capacity — it needs no more weeks, so there is no date to name and
     * nothing to be late for. A goal on an account where nobody has declared a capacity gets no
     * projection and no verdict at all rather than a bad one: nothing says how fast it fills, which
     * is a different sentence from a plan in which it fills slowly.
     */
    private static TheProjection projectionFor(AGoalOnItsWay goal, LocalDate today) {
        if (goal.hasArrived()) {
            return new TheProjection(null, GoalStatus.COMPLETED);
        }
        if (goal.weeklyAmount() == null) {
            return TheProjection.NOTHING_SAID;
        }
        if (goal.weeklyAmount().signum() <= 0) {
            return new TheProjection(null, GoalStatus.UNREACHABLE);
        }
        BigDecimal weeks = wholeWeeksOfSavingLeftFor(goal);
        if (weeks.compareTo(MOST_WEEKS_WORTH_PROJECTING) > 0) {
            return new TheProjection(null, GoalStatus.UNREACHABLE);
        }
        LocalDate willBeReachedOn =
                SavingsWeek.containing(today).startsOn().plusWeeks(weeks.longValueExact());
        if (goal.deadline() == null) {
            return new TheProjection(willBeReachedOn, GoalStatus.NO_DEADLINE);
        }
        return new TheProjection(willBeReachedOn, willBeReachedOn.isAfter(goal.deadline())
                ? GoalStatus.OFF_TRACK
                : GoalStatus.ON_TRACK);
    }

    /**
     * How many whole weeks of saving it takes to find the rest of the target at this rate: what is
     * still needed over what the plan gives it, rounded <strong>up</strong>.
     *
     * <p>Up, because a week is the unit money arrives in. A goal needing 101.00 at 25.00 a week is
     * not four weeks and a bit — after four weeks it is a euro short, and it is reached in the fifth.
     * Rounding down would name a Monday on which the goal has not arrived, which is the one thing a
     * projection must never do.
     */
    private static BigDecimal wholeWeeksOfSavingLeftFor(AGoalOnItsWay goal) {
        return goal.stillNeeded().divide(goal.weeklyAmount(), 0, RoundingMode.CEILING);
    }

    /**
     * The goals written out for the log line, in the order they compete in, each with everything
     * that decided its verdict: what it still needs, what it is being given, the day it is wanted by
     * if it has one, the date it will be reached on and the status that came out of comparing the
     * two.
     */
    private static String asProjected(List<AGoalOnItsWay> inRankOrder,
                                      Map<Long, TheProjection> projected) {
        List<String> said = new ArrayList<>();
        for (AGoalOnItsWay goal : inRankOrder) {
            TheProjection projection = projected.getOrDefault(goal.goalId(), TheProjection.NOTHING_SAID);
            said.add("rank=" + goal.rank()
                    + " goalId=" + goal.goalId()
                    + " \"" + goal.name() + "\""
                    + " needs=" + AmountOfMoney.asMoney(goal.stillNeeded())
                    + " gets=" + (goal.weeklyAmount() == null
                            ? "not planned"
                            : AmountOfMoney.asMoney(goal.weeklyAmount()) + " a week")
                    + " by=" + (goal.deadline() == null ? NO_DEADLINE : goal.deadline())
                    + " willBeReachedOn=" + (projection.willBeReachedOn() == null
                            ? "no projection"
                            : projection.willBeReachedOn())
                    + " status=" + projection.status());
        }
        return String.join("; ", said);
    }

    /**
     * One goal as the projection sees one: where it stands in the order, what it still needs, the
     * day it is wanted by if it has one, and what the plan gives it each week.
     *
     * <p>Not the row and not {@link RecordedGoal}, for the reason {@code AGoalCompetingForIt} is
     * neither: four figures and a name decide the answer, and a derivation handed a whole entity is
     * one that can quietly start reading a fifth.
     *
     * <p>{@code weeklyAmount} is null exactly when nobody has declared a weekly capacity, which is
     * the distinction {@code TheWeeklyMoneySpent.forGoal} exists to make. A zero is a plan in which
     * this goal is given nothing, and the two get different answers here.
     */
    public record AGoalOnItsWay(Long goalId, String name, Integer rank, BigDecimal stillNeeded,
                                LocalDate deadline, BigDecimal weeklyAmount) {

        /** Whether it has arrived: it needs nothing more, so there is no week left to count. */
        boolean hasArrived() {
            return stillNeeded.signum() <= 0;
        }
    }

    /**
     * When a goal will be reached, and where that leaves it.
     *
     * <p>The date is null on every goal that has no week to count: one that has arrived, one the
     * plan gives nothing, and one on an account where nobody has said how fast anything fills. The
     * status says which of those it was, so that a page reading an absent date never has to guess.
     */
    public record TheProjection(LocalDate willBeReachedOn, GoalStatus status) {

        /**
         * What a goal reads as when nothing has been said about how fast it fills: no date and no
         * verdict, only that it is still being saved towards.
         *
         * <p>Also what a goal that is not in the plan at all reads as — one that was given up on, or
         * one being read beside an account's live ones — so that a caller asking about it gets an
         * answer rather than a null it has to interpret.
         */
        public static final TheProjection NOTHING_SAID = new TheProjection(null, GoalStatus.STILL_SAVING);
    }
}
