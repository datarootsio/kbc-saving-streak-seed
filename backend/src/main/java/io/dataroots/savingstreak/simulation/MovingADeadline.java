package io.dataroots.savingstreak.simulation;

import java.time.LocalDate;
import java.util.Optional;

import io.dataroots.savingstreak.goals.GoalState;
import io.dataroots.savingstreak.goals.RecordedGoal;

/**
 * A goal on this account, wanted by a different day — the one change a customer can ask about that
 * moves no money at all.
 *
 * <p><strong>It changes no euro, no rate and no week, and it is still the change that answers the
 * hardest of the four questions.</strong> Not a cent more is put away in this branch than in the one
 * beside it, the run of secured weeks is the same run, every batch expires on the day it was always
 * going to and every anniversary pays the same tenth. What moves is the <em>plan</em>: {@code
 * HowTheWeeklyMoneyIsSpent} funds each dated goal to its deadline minimum — what it still needs over
 * the whole weeks left before the day it is wanted by — in rank order, before anything is left over
 * for anybody. Push a deadline out and that division has more weeks under it, so the minimum falls,
 * so the goals behind it in the order are reached out of the money it stops taking. "If I give
 * myself two more months on the car, what does that do to the holiday" is a question about the
 * competition between goals, and this is the only kind of change that touches it.
 *
 * <p><strong>Nothing about the real goal moves.</strong> A branch is a question, and asking one
 * writes nothing: the goal keeps the deadline its holder gave it, on the goals screen and in the
 * ledger, until they press adopt — and adopting is Goals' own write, refused in Goals' own words for
 * a date Goals will not accept. This record carries the day a branch wants the goal by and hands it
 * to one projection; it is never a deadline anybody has been given.
 *
 * <p><strong>The statuses are recomputed against the moved day, and not merely the dates.</strong>
 * {@code WhenAGoalWillBeReached} is asked with the branch's deadline rather than the account's, so a
 * goal projected past the day it was wanted by is {@code OFF_TRACK} in the column where nothing
 * changes and {@code ON_TRACK} in the column where the date moved — the same five words {@code
 * GoalStatus} already names, decided by the same comparison, said about a different day. The visible
 * half of that on the answer is the marker for a deadline gone by: it is raised on exactly the
 * comparison that makes a goal {@code OFF_TRACK}, so a branch in which it is missing is a branch in
 * which the goal arrives in time.
 *
 * <p><strong>What it deliberately does not do is bring the goal forward in the walk.</strong> A
 * branch's goals are a projection over the window rather than an allocation of the branch's
 * deposits, which is the reading {@link TheNightReplayed} argues for at length: the fold never gives
 * a deposit to a goal, because how money reaches a goal is a rule Goals owns and a second statement
 * of it here would be free to drift. So this change is asked once, where the plan is worked out, and
 * on no morning of the year. It is the only one of the four kinds that touches no step of the night.
 *
 * <p><strong>A deadline brought <em>in</em> is as askable as one pushed out</strong>, and it is the
 * half a customer is likelier to regret: the minimum rises, the goals behind it are starved, and the
 * branch says so in the days they are reached. Nothing here prefers one direction, because the
 * question is what the change would do rather than whether it is a good idea.
 *
 * <p>Both fields may be missing or nonsense on the way in, deliberately: a change is built out of
 * whatever was typed and refused by {@link #whyItCannotBeAsked} in one sentence before a single day
 * is folded, so nothing below is ever asked of one that has not been found askable.
 */
public record MovingADeadline(Long goalId, LocalDate wantedByInstead) implements AnAdjustment {

    @Override
    public AKindOfAdjustment kind() {
        return AKindOfAdjustment.MOVE_A_DEADLINE;
    }

    @Override
    public String asAsked() {
        return "MOVE_A_DEADLINE goalId=" + (goalId == null ? "not said" : goalId)
                + " wantedBy=" + (wantedByInstead == null ? "not said" : wantedByInstead);
    }

    /**
     * Why moving a deadline cannot be asked about: no goal was named, the goal named is not one this
     * account is still saving towards, or the day it would be wanted by instead is not a day inside
     * the year this simulation is drawn over.
     *
     * <p><strong>A goal that is not on this account and a goal that was given up on are one
     * answer here, and the sentence says both out loud.</strong> The snapshot carries the account's
     * <em>live</em> goals, which is the whole of what a plan competes between and the whole of what a
     * branch can project; an abandoned goal has left the order, holds no money and has no date to be
     * late for, so from inside a fold it is indistinguishable from a number somebody made up. Rather
     * than put a second reading of the goals into the snapshot for the sake of telling two refusals
     * apart, the one sentence names both cases — which is the same trade {@code
     * GoalRefused.NO_SUCH_GOAL} already makes when it answers for a goal on somebody else's account
     * in the words of a goal that does not exist. A customer who gave a goal up and is hunting for it
     * on this screen is told what became of it.
     *
     * <p>The day is {@link AnAdjustment#whyThatDayIsOutsideTheWindow}'s sentence rather than a fourth
     * wording of one objection, and it is asked last: a customer who named a goal that is not there
     * has a different thing to fix first.
     */
    @Override
    public Optional<String> whyItCannotBeAsked(TheStartingPoint standing) {
        if (goalId == null) {
            return Optional.of("Say which goal you would want by a different day.");
        }
        if (theGoalOnTheAccount(standing).isEmpty()) {
            return Optional.of("There is no savings goal " + goalId + " still being saved towards "
                    + "on savings account " + standing.savingsAccountId() + ", so there is no "
                    + "deadline on it to move. A goal that was given up on has left the order and "
                    + "has no day to be wanted by.");
        }
        return AnAdjustment.whyThatDayIsOutsideTheWindow(
                "The day a goal is wanted by instead", wantedByInstead, standing);
    }

    /**
     * The day this branch wants that goal by: the new day for the goal this change names, and
     * whatever it was already for every other goal.
     *
     * <p>Answered for one goal at a time rather than handed out as a map, because that is how the
     * projection asks — goal by goal, in the order they compete in — and because a change that
     * returned the deadline it was given for every goal but its own is the do-nothing answer this
     * question's default already gives. Two of these in one scenario compose: each is asked in turn
     * with what the one before it answered, so a customer who moves the car twice is asking about the
     * second day.
     */
    @Override
    public LocalDate theDayItWantsThatGoalBy(long goal, LocalDate deadline) {
        return goalId != null && goalId == goal ? wantedByInstead : deadline;
    }

    /**
     * The goal this change names, as the snapshot holds it, or nothing at all when the account is not
     * saving towards it.
     *
     * <p>{@code LIVE} is insisted on although the snapshot's allocations only ever carry live goals,
     * for the reason {@link TheNightReplayed} insists on it where it builds the same list: a refusal
     * that depended on another module's read staying narrow is a refusal that goes quiet the day it
     * widens, and going quiet here would be a branch projecting a goal nobody is saving for.
     */
    /**
     * Adopting a moved deadline changes the goal's deadline, through the module that owns it.
     *
     * <p>One press, and it is the press the customer would have made on the goal screen: this goal,
     * this day, nothing else touched. What a deadline may be is Goals' rule and it is asked of Goals
     * — a day that has already passed comes back as the sentence Goals already refuses one in, with
     * the rest of the adoption rolled back behind it, and this module never learns what that rule
     * says. Whether the goal is on this account, and whether it is still one being saved towards,
     * are Goals' answers for the same reason.
     *
     * <p><strong>The two empty boxes are guarded here, and only because the module cannot be asked
     * without them.</strong> A goal nobody named is not a goal Goals can refuse — there is no
     * identifier to look up — and a day nobody typed would be read by {@code changeGoal} as an
     * instruction to take the goal's deadline off it altogether, which is a different change from
     * the one the customer built and one they never asked for. Both sentences are this kind's own,
     * word for word the ones it refuses an unaskable change in, so a customer who left the box empty
     * meets one form of words whether they were simulating or adopting.
     */
    @Override
    public AChangeThePlanNowCarries adoptedThrough(ThePressesACustomerWouldHaveMade presses) {
        if (goalId == null) {
            throw SimulationRefused.againstTheRules(
                    "Say which goal you would want by a different day.");
        }
        if (wantedByInstead == null) {
            throw SimulationRefused.againstTheRules("The day a goal is wanted by instead is missing, "
                    + "and a change to a future has to say which day it happens on.");
        }
        return presses.wantThatGoalBy(goalId, wantedByInstead);
    }

    private Optional<RecordedGoal> theGoalOnTheAccount(TheStartingPoint standing) {
        return standing.goals().goals().stream()
                .filter(goal -> goal.state() == GoalState.LIVE)
                .filter(goal -> goal.id() != null && goal.id().equals(goalId))
                .findFirst();
    }
}
