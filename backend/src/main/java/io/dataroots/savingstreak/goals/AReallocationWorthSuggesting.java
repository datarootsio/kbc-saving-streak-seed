package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The payload of the whole feature: when the money already allocated no longer matches the order of
 * importance, the concrete moves that would fix it — this much, out of that goal, into this one —
 * each saying why it is being suggested.
 *
 * <p><strong>The rule, in one breath.</strong> A goal is worth suggesting money <em>into</em> when it
 * is {@link GoalStatus#OFF_TRACK} or {@link GoalStatus#UNREACHABLE}. The money is taken from the
 * lowest-ranked live goal that is holding any, working upward, never from a goal ranked above the one
 * being helped, never from a goal that has reached its target, and never more than the helped goal
 * needs to come back on track. If nothing can be moved there is no suggestion, and that is a real
 * answer rather than an empty list dressed up as one — see {@link TheReallocation#worthSuggesting()}.
 *
 * <p><strong>Every move this class proposes is one {@code GoalsService.moveMoney} would accept.</strong>
 * That is the whole reason the amounts are capped here and it is the question tickets 05, 06 and 07
 * each left open. The engine's weekly plan is deliberately <em>not</em> capped at what a goal still
 * needs — the third pass hands the whole surplus to the highest-ranked unfinished goal, and a pin is
 * honoured as it was written — because a weekly amount is a plan for money that has not arrived yet
 * and nothing refuses a plan. A suggested move is the opposite: it is an instruction the customer can
 * press a button on, and it goes through the ordinary allocation path under the ordinary refusals. A
 * suggestion quoting a figure {@code MORE_THAN_THE_GOAL_NEEDS} would refuse would be this application
 * advising something it would then decline to do. So a move is capped twice — at what brings the goal
 * back on track, and at what the goal still needs at all — and the donor is never asked for more than
 * it is holding, which is the other refusal a suggestion could have walked into.
 *
 * <p><strong>Derived on demand and stored nowhere</strong>, exactly as {@link HowTheWeeklyMoneyIsSpent}
 * and {@link WhenAGoalWillBeReached} are. A stored suggestion is advice that goes stale the instant a
 * deposit lands, and then needs invalidation rules which are themselves a source of the bugs this
 * feature exists to prevent. Recomputing it is a walk over a handful of goals, and it is always honest.
 *
 * <p><strong>Money flows one way, upward, and no goal both gives and receives.</strong> That falls out
 * of the rule rather than being a case in it, and it is worth saying because it is what makes a
 * suggestion readable. The goals are walked in rank order and each one worth helping takes from the
 * bottom upward against what the goals below it are holding <em>at that point in the walk</em> — so a
 * goal is only ever asked for money once every goal below it is empty or has already arrived, which is
 * exactly the state in which there is nothing left to top it back up with. A goal that has been given
 * money is never afterwards asked for any either, because money is only ever taken from goals ranked
 * below the one being helped and the helped goals are visited from the top. So the list never contains
 * a move out of a goal beside a move into the same goal, and a customer reading it never has to net
 * two rows against each other.
 *
 * <p><strong>Unallocated money is not touched.</strong> The rule names goals at both ends, and the
 * part of the balance no goal has claimed is a different conversation — "you are holding money no goal
 * is saving" is worth saying and is not this suggestion, which is about an order that has changed
 * under money that has not. It is also why this derivation needs no balance: every move it proposes
 * leaves {@code allocated} exactly where it was, so the one invariant this feature has cannot be
 * touched by taking the advice.
 *
 * <p>Static, with no state, no clock and no bean, for the reason the other two derivations are: it is
 * a function of figures the caller already holds. The day is the caller's for the same reason theirs
 * is — which Monday the weeks are counted from decides how many whole weeks a deadline has left, and
 * a class reading a clock of its own could answer about a different day from the rest of the read.
 */
final class AReallocationWorthSuggesting {

    private static final Logger log = LoggerFactory.getLogger(AReallocationWorthSuggesting.class);

    /** How a goal with no date of its own reads in the log line, where a null would say less. */
    private static final String NO_DEADLINE = "no deadline";

    private AReallocationWorthSuggesting() {
    }

    /**
     * The moves worth suggesting on one account, or the sentence saying why there are none.
     *
     * <p>One DEBUG line for the read, carrying every goal it considered and what each one decided:
     * where it stands, what it is holding, what it still needs, what the plan gives it, its status,
     * and — for the ones worth helping — what it would take to bring them back on track. A reviewer
     * reading that line can work the answer out by hand, which is the only way to tell a suggestion
     * that is right from one that merely looks plausible. DEBUG rather than INFO because reading a
     * suggestion moves nothing: what happens on a business event is {@code GoalsService} applying it,
     * and that is a line per move at INFO.
     *
     * @param inRankOrder the account's live goals, most important first, each carrying what it is
     *                    holding, what it is aiming at, the day it is wanted by, what the plan gives
     *                    it each week and where that leaves it
     * @param today       the day the weeks left before a deadline are counted from — its Monday is
     *                    the same anchor {@link HowTheWeeklyMoneyIsSpent} and
     *                    {@link WhenAGoalWillBeReached} count from
     */
    static TheReallocation forTheGoalsOn(long savingsAccountId, List<AGoalInTheOrder> inRankOrder,
                                         LocalDate today) {
        // What each goal is holding as the walk goes on, so that a goal drained to help the one above
        // it asks the goals below it for what it now needs rather than for what it needed before.
        Map<Long, BigDecimal> holding = new LinkedHashMap<>();
        Map<Long, BigDecimal> wanted = new LinkedHashMap<>();
        for (AGoalInTheOrder goal : inRankOrder) {
            holding.put(goal.goalId(), goal.allocation());
        }

        List<AMoveWorthMaking> moves = new ArrayList<>();
        for (AGoalInTheOrder helped : inRankOrder) {
            if (!helped.isWorthHelping()) {
                continue;
            }
            BigDecimal stillWanted = whatWouldBringItBack(helped, holding.get(helped.goalId()), today);
            wanted.put(helped.goalId(), stillWanted);
            if (stillWanted.signum() <= 0) {
                continue;
            }
            // The lowest-ranked goal holding anything, working upward. Never a goal ranked above the
            // one being helped — that would be robbing the more important goal to pay the less
            // important one, which is the order of importance read backwards — and never one that has
            // reached its target, because a completed goal is the one place in this feature where
            // money has already arrived somewhere the customer asked for.
            for (int place = inRankOrder.size() - 1; place >= 0; place--) {
                AGoalInTheOrder donor = inRankOrder.get(place);
                if (donor.rank() <= helped.rank()) {
                    break;
                }
                BigDecimal holds = holding.get(donor.goalId());
                if (donor.hasArrivedHolding(holds)) {
                    continue;
                }
                BigDecimal taken = holds.min(stillWanted);
                if (taken.signum() <= 0) {
                    continue;
                }
                moves.add(new AMoveWorthMaking(donor.goalId(), donor.name(), helped.goalId(),
                        helped.name(), taken, whyItIsSuggested(helped, donor, taken, today)));
                holding.put(donor.goalId(), holds.subtract(taken));
                holding.put(helped.goalId(), holding.get(helped.goalId()).add(taken));
                stillWanted = stillWanted.subtract(taken);
                if (stillWanted.signum() <= 0) {
                    break;
                }
            }
        }

        TheReallocation suggestion = moves.isEmpty()
                ? TheReallocation.nothingToSuggest(whyThereIsNothingToSuggest(inRankOrder))
                : new TheReallocation(true, whatItAmountsTo(moves), moves);
        if (log.isDebugEnabled()) {
            log.debug("a reallocation worth suggesting savingsAccountId={} today={} "
                            + "thisWeekStartsOn={} goals={} considered=[{}] worthSuggesting={} "
                            + "moves={} moving={} inWords={}",
                    savingsAccountId, today, SavingsWeek.containing(today).startsOn(),
                    inRankOrder.size(), asConsidered(inRankOrder, wanted, today),
                    suggestion.worthSuggesting(), suggestion.moves().size(),
                    AmountOfMoney.asMoney(totalOf(suggestion.moves())), suggestion.inWords());
        }
        return suggestion;
    }

    /**
     * What this goal would have to be given <em>now</em> to stop being late: what it still needs, less
     * what the weeks it has left will bring it at the rate the plan is filling it, and never more than
     * it still needs at all.
     *
     * <p>The arithmetic is {@link WhenAGoalWillBeReached}'s own, turned around. That class reaches
     * {@link GoalStatus#ON_TRACK} when {@code ceil(stillNeeded / weekly)} whole weeks from this week's
     * Monday land on or before the deadline — so the goal is in time exactly when what it still needs
     * fits into {@code weeksLeft × weekly}, and the shortfall is what a move has to cover. Counted
     * from the same Monday, because two halves of one read counting from two different days is the
     * mistake ticket 06 had to go back and fix.
     *
     * <p>A goal the plan gives nothing, and a goal with no day to be late for, both want the whole of
     * what they still need. There is no rate to wait at: at nothing a week it never arrives, and the
     * only figure that changes that is the one that finishes it.
     *
     * <p><strong>Capped at what the goal still needs, always.</strong> That is the cap that makes a
     * suggestion something the allocation endpoint will accept — see this class's own comment — and it
     * is the reason a deadline that has already gone does not ask for more than the target: the weeks
     * left go to nothing, not negative.
     */
    private static BigDecimal whatWouldBringItBack(AGoalInTheOrder goal, BigDecimal holds,
                                                   LocalDate today) {
        BigDecimal stillNeeded = goal.stillNeededHolding(holds);
        if (stillNeeded.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (goal.weeklyAmount() == null || goal.weeklyAmount().signum() <= 0
                || goal.deadline() == null) {
            return stillNeeded;
        }
        BigDecimal whatTheWeeksLeftWillBring = goal.weeklyAmount()
                .multiply(BigDecimal.valueOf(wholeWeeksLeftUntil(goal.deadline(), today)));
        BigDecimal shortfall = stillNeeded.subtract(whatTheWeeksLeftWillBring);
        return AmountOfMoney.quotedToTheCent(
                shortfall.signum() <= 0 ? BigDecimal.ZERO : shortfall.min(stillNeeded));
    }

    /**
     * How many whole weeks of saving are left before a deadline: how many Mondays there are, from the
     * one this week began on, that still land on or before the day the goal is wanted by.
     *
     * <p>The same count {@code HowTheWeeklyMoneyIsSpent.wholeWeeksLeftUntil} makes, with one
     * difference that matters here: it is floored at nothing rather than at one. There, dividing what
     * is still needed by nothing is not an answer, so a deadline inside this week is given one week to
     * find the money in. Here the question is what the weeks left will bring, and a deadline that has
     * gone brings nothing — flooring at one would quietly forgive a week of lateness and suggest a
     * move a week too small.
     */
    private static long wholeWeeksLeftUntil(LocalDate deadline, LocalDate today) {
        long days = ChronoUnit.DAYS.between(SavingsWeek.containing(today).startsOn(), deadline);
        return Math.max(0, Math.floorDiv(days, 7));
    }

    /**
     * Why one move is being suggested, naming the goal it helps, where that goal stands, and where the
     * money is coming from.
     *
     * <p>On every move rather than once on the list, because the customer is looking at a row and
     * asking "why is it taking my holiday money" — and a reason kept at the top of the page is a
     * reason that does not travel with the row somebody is reading.
     */
    private static String whyItIsSuggested(AGoalInTheOrder helped, AGoalInTheOrder donor,
                                           BigDecimal taken, LocalDate today) {
        return whereItStands(helped, today) + ". \"" + donor.name() + "\" is ranked below it at "
                + donor.rank() + " and is holding money, so " + AmountOfMoney.asMoney(taken)
                + " is worth moving out of it into \"" + helped.name() + "\".";
    }

    /**
     * Where the goal being helped stands, in the customer's own terms: what the plan gives it, the day
     * it is wanted by, and why that does not add up.
     *
     * <p><strong>Keyed on what the plan actually gives the goal, not on its status.</strong>
     * {@link GoalStatus#UNREACHABLE} is two different sentences wearing one name — {@code
     * WhenAGoalWillBeReached} answers it both when the plan gives a goal nothing and when it gives it
     * so little that the projection runs past the thousand years it is willing to name. Reading the
     * status alone told a goal getting 0.01 a week that it was getting nothing, and never named the
     * deadline it was about to miss, which is the one fact the customer needs. So the first question
     * is the same one {@link #whatWouldBringItBack} asks — is there a rate at all — and only then does
     * the status separate "too little ever" from "late".
     *
     * <p>Three things to say, and a goal with a day of its own is told that day in every one of them:
     * the plan gives it nothing, the plan gives it so little that no arrival date is worth naming, or
     * it is simply going to be late. They send the customer somewhere different — raise the weekly
     * capacity, free money from elsewhere, or take this move — which is why they are not one sentence
     * with the figures swapped.
     */
    private static String whereItStands(AGoalInTheOrder helped, LocalDate today) {
        String named = "\"" + helped.name() + "\"";
        if (helped.weeklyAmount() == null || helped.weeklyAmount().signum() <= 0) {
            return named
                    + (helped.deadline() == null ? "" : " is wanted by " + helped.deadline() + " and")
                    + " is getting nothing towards it each week, so at this rate it never arrives";
        }
        String aWeek = AmountOfMoney.asMoney(helped.weeklyAmount()) + " a week";
        if (helped.status() == GoalStatus.UNREACHABLE) {
            return helped.deadline() == null
                    ? named + " has no deadline to miss, but at " + aWeek + " it is so far off that "
                            + "no arrival date is worth naming"
                    : named + " is wanted by " + helped.deadline() + " and will not be there in time: "
                            + wholeWeeksLeftUntil(helped.deadline(), today) + " whole weeks are left "
                            + "and the plan gives it only " + aWeek + ", which is so little that no "
                            + "arrival date is worth naming";
        }
        return named + " is wanted by " + helped.deadline() + " and will not be there in time: "
                + wholeWeeksLeftUntil(helped.deadline(), today)
                + " whole weeks are left and the plan gives it " + aWeek;
    }

    /**
     * The whole suggestion in one sentence: how many moves, how much altogether, and which goals they
     * are for. What a page puts above the list, and what a customer repeats back when they decide.
     */
    private static String whatItAmountsTo(List<AMoveWorthMaking> moves) {
        Set<String> helped = new LinkedHashSet<>();
        moves.forEach(move -> helped.add("\"" + move.intoGoalName() + "\""));
        return moves.size() + (moves.size() == 1 ? " move" : " moves")
                + " worth making, " + AmountOfMoney.asMoney(totalOf(moves))
                + " altogether, towards " + String.join(" and ", helped) + ".";
    }

    /**
     * Why there is nothing to suggest — which is an answer, and not the same answer four times over.
     *
     * <p>Four sentences because the customer does four different things next: open a goal, say what
     * they can put away in a week, nothing at all, or free money from somewhere this rule will not
     * touch. An empty list of moves says none of that, which is exactly what the ticket means by "no
     * suggestion is a real answer, not an empty list dressed up as one".
     */
    private static String whyThereIsNothingToSuggest(List<AGoalInTheOrder> inRankOrder) {
        if (inRankOrder.isEmpty()) {
            return "There are no savings goals on this account to move money between.";
        }
        if (inRankOrder.stream().allMatch(goal -> goal.weeklyAmount() == null)) {
            return "Nothing has been said about how much can be put away each week, so nothing here "
                    + "says a goal is going to be late. Declare a weekly saving capacity first.";
        }
        List<AGoalInTheOrder> late = inRankOrder.stream().filter(AGoalInTheOrder::isWorthHelping).toList();
        if (late.isEmpty()) {
            return "Every goal on this account is on its way in time, so there is nothing worth "
                    + "moving.";
        }
        return "\"" + late.get(0).name() + "\" is not going to arrive in time, but no goal ranked "
                + "below it is holding money that could be moved. Free money from somewhere else, or "
                + "raise what you can put away each week.";
    }

    /** What the suggested moves come to altogether, for the sentence and for the log line. */
    private static BigDecimal totalOf(List<AMoveWorthMaking> moves) {
        return AmountOfMoney.quotedToTheCent(
                moves.stream().map(AMoveWorthMaking::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * The goals written out for the log line, in the order they were walked, each with everything that
     * decided whether it was worth helping and what it asked for.
     */
    private static String asConsidered(List<AGoalInTheOrder> inRankOrder, Map<Long, BigDecimal> wanted,
                                       LocalDate today) {
        List<String> said = new ArrayList<>();
        for (AGoalInTheOrder goal : inRankOrder) {
            StringBuilder line = new StringBuilder()
                    .append("rank=").append(goal.rank())
                    .append(" goalId=").append(goal.goalId())
                    .append(" \"").append(goal.name()).append("\"")
                    .append(" holding=").append(AmountOfMoney.asMoney(goal.allocation()))
                    .append(" needs=").append(AmountOfMoney.asMoney(
                            goal.stillNeededHolding(goal.allocation())))
                    .append(" gets=").append(goal.weeklyAmount() == null
                            ? "not planned"
                            : AmountOfMoney.asMoney(goal.weeklyAmount()) + " a week")
                    .append(" by=").append(goal.deadline() == null ? NO_DEADLINE : goal.deadline())
                    .append(" wholeWeeksLeft=").append(goal.deadline() == null
                            ? NO_DEADLINE
                            : String.valueOf(wholeWeeksLeftUntil(goal.deadline(), today)))
                    .append(" status=").append(goal.status());
            if (goal.isWorthHelping()) {
                line.append(" worthHelpingWith=")
                        .append(AmountOfMoney.asMoney(
                                wanted.getOrDefault(goal.goalId(), BigDecimal.ZERO)));
            } else {
                line.append(" notWorthHelping");
            }
            said.add(line.toString());
        }
        return String.join("; ", said);
    }

    /**
     * One goal as this derivation sees one: where it stands in the order, what it is holding, what it
     * is aiming at, the day it is wanted by, what the plan gives it each week and where that leaves
     * it.
     *
     * <p>Not the row and not {@link RecordedGoal}, for the reason {@code AGoalCompetingForIt} and
     * {@code AGoalOnItsWay} are neither. What it does carry that they do not is the <em>allocation</em>
     * rather than only what is still needed, because this is the one derivation where what a goal
     * holds changes as the answer is worked out: a goal drained to help the one above it needs more
     * afterwards than it did before, and a figure computed once outside could not say so.
     *
     * <p>{@code weeklyAmount} is null exactly when nobody has declared a weekly capacity, which is the
     * distinction {@code TheWeeklyMoneySpent.forGoal} exists to make; the status is {@code
     * STILL_SAVING} in that case, so no such goal is ever worth helping.
     */
    record AGoalInTheOrder(Long goalId, String name, Integer rank, BigDecimal target,
                           BigDecimal allocation, LocalDate deadline, BigDecimal weeklyAmount,
                           GoalStatus status) {

        /**
         * Whether money is worth suggesting into it: it is going to be late, or at this rate it never
         * arrives at all. Both are the plan saying the customer will not get what they asked for, and
         * they are the two statuses a move can do something about — a goal that has arrived wants
         * nothing, one on its way in time needs nothing, and one on an account where nobody has said
         * what they can save has not been told anything yet.
         */
        boolean isWorthHelping() {
            return status == GoalStatus.OFF_TRACK || status == GoalStatus.UNREACHABLE;
        }

        /** What is still to be found for it while it holds this much, and never below zero. */
        BigDecimal stillNeededHolding(BigDecimal holds) {
            BigDecimal short_ = target.subtract(holds);
            return AmountOfMoney.quotedToTheCent(short_.signum() < 0 ? BigDecimal.ZERO : short_);
        }

        /**
         * Whether it has reached its target while holding this much, which is the one goal this rule
         * will not take money out of.
         *
         * <p>Asked of what it is holding as the walk stands rather than of the status it was read
         * with, so that the answer is about the same figures every other decision here is about. A
         * completed goal is never drained anyway — nothing in the walk gives it money and only giving
         * could have changed it — but asking the running figure is what keeps the two from ever
         * disagreeing.
         */
        boolean hasArrivedHolding(BigDecimal holds) {
            return holds.compareTo(target) >= 0;
        }
    }

    /** One move worth making: out of this goal, into that one, this much, and why. */
    record AMoveWorthMaking(Long outOfGoalId, String outOfGoalName, Long intoGoalId,
                            String intoGoalName, BigDecimal amount, String reason) {
    }

    /**
     * What the account has to say about reallocating: whether there is anything worth suggesting, the
     * whole of it in one sentence, and the moves.
     *
     * <p>{@code worthSuggesting} is the field, and not the emptiness of the list. "There is nothing to
     * suggest" is a sentence with four different reasons behind it, and a caller reading an empty list
     * would have to invent one of them; this way the answer says which, and a page can print it.
     */
    record TheReallocation(boolean worthSuggesting, String inWords, List<AMoveWorthMaking> moves) {

        static TheReallocation nothingToSuggest(String why) {
            return new TheReallocation(false, why, List.of());
        }
    }
}
