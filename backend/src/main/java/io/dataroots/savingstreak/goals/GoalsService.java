package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.scheme.SchemeService;
import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.goals.HowTheWeeklyMoneyIsSpent.AGoalCompetingForIt;
import static io.dataroots.savingstreak.goals.HowTheWeeklyMoneyIsSpent.TheWeeklyMoneySpent;
import static io.dataroots.savingstreak.goals.WhenAGoalWillBeReached.AGoalOnItsWay;
import static io.dataroots.savingstreak.goals.WhenAGoalWillBeReached.TheProjection;

import static io.dataroots.savingstreak.goals.AReallocationWorthSuggesting.AGoalInTheOrder;
import static io.dataroots.savingstreak.goals.AReallocationWorthSuggesting.AMoveWorthMaking;
import static io.dataroots.savingstreak.goals.AReallocationWorthSuggesting.TheReallocation;

import static io.dataroots.savingstreak.goals.GoalRefused.Kind.AGAINST_THE_RULES;
import static io.dataroots.savingstreak.goals.GoalRefused.Kind.MORE_THAN_THE_GOAL_NEEDS;
import static io.dataroots.savingstreak.goals.GoalRefused.Kind.NOT_ENOUGH_UNALLOCATED;
import static io.dataroots.savingstreak.goals.GoalRefused.Kind.NO_SUCH_GOAL;
import static io.dataroots.savingstreak.goals.GoalRefused.Kind.THE_GOAL_IS_CLOSED;

/**
 * The Goals module's face to the rest of the application: what a savings account is being saved
 * towards, and in what order of importance.
 *
 * <p><strong>It reads no other module.</strong> The savings account arrives as an identifier that
 * somebody else has already vouched for, and nothing in here asks who holds it, what is in it or
 * what has been paid into it. That is not tidiness: a later slice has Deposits ask Goals whether a
 * withdrawal may take money a goal is holding, and a Goals that asked Deposits for a balance would
 * be a cycle the application context could not start. The two things it does take are the clock,
 * which every module reads, and two sentences — {@code AmountOfMoney}'s, about what an amount of
 * money is, and {@code SavingsWeek}'s zone, about which day it is here. Both are a constant or a
 * pure comparison with no repository, no entity and no state behind them: quoting a rule is not
 * reading a module, and restating either of them here is the thing that would be wrong.
 *
 * <p><strong>The one exception is the scheme, and it is worth saying why it is not the rule
 * bending.</strong> What secures a streak week used to be a third such sentence —
 * {@code NewSavingsThisWeek}'s weekly minimum, a constant this class quoted — and it stopped being
 * one when the bank started publishing the figure in dated versions. A capacity that said "a week is
 * not secured at this rate" against a number compiled into the application would go on saying it
 * after the bank had changed its mind, so the figure is read from {@code SchemeService} at the
 * moment the question is put. That is not the cycle this invariant exists to prevent: the scheme
 * module depends on nothing in this application by construction, so it is the one module that can be
 * read from anywhere without arranging the graph around it. The alternative — handing the threshold
 * in at every door, the way the balance is — was considered and rejected: it would put the choice of
 * <em>which published version</em> to read into five callers' hands, and five callers is five places
 * that can choose differently.
 *
 * <p><strong>A goal still holds no money; it holds a claim on some of the account's.</strong> What
 * each goal has claimed is its <em>allocation</em>, and it is the sum of an append-only ledger of
 * moves rather than a column on the goal: a stored per-goal balance would be a second figure that
 * has to agree with the moves and would stop agreeing. What no goal has claimed is
 * <em>unallocated</em> — {@code balance − allocated} — derived on every read and stored nowhere, for
 * the reason {@code WeekAndStreakDerivation} gives about weeks and streaks: a stored figure is a
 * claim about a moment that has since moved. The one invariant over every move is that the
 * allocations on an account never add up to more than the account holds.
 *
 * <p><strong>The balance is handed in and never fetched.</strong> Every method that has to know one
 * takes it as a parameter. That is the load-bearing half of "reads no other module": Deposits has to
 * ask this module whether a withdrawal may take money a goal is holding, so a Goals that asked
 * Deposits for a balance would be a cycle the application context could not start. The controller,
 * which already reads across modules, supplies it from {@code DepositsService.moneyBalanceOf}.
 *
 * <p><strong>The order of importance is strict and total, and this class is the whole of what makes
 * it so.</strong> Live goals hold ranks 1..n with no ties and no gaps; a new goal is ranked last; an
 * abandoned goal gives up its rank and the ones below it close the gap; and reordering sets every
 * rank in one call. That last one is the load-bearing shape. A total order has no valid intermediate
 * state, so an endpoint that moved one goal at a time would have to either leave the order broken
 * between two requests or invent a rule for what happens to everybody else — and a request that
 * named a subset would leave a goal behind with no way to say which.
 *
 * <p>Nothing here ever closes a goal because a date passed. A deadline is soft: it is a thing a
 * later slice measures a projection against, and a house deposit that slips three weeks is still the
 * thing being saved for. The only way a goal leaves the order is that somebody said so.
 *
 * <p><strong>The weekly capacity is the other scarcity, and it is declared rather than derived.</strong>
 * It is the most its holder says they can put away in a week, per savings account. There is no
 * default: until somebody declares one this class answers that none is declared, and it never
 * answers zero, because a customer saying they can save nothing is a different sentence from their
 * having said nothing at all.
 *
 * <p><strong>Spending that capacity across the goals is where they compete</strong>, and the three
 * passes that do it are {@link HowTheWeeklyMoneyIsSpent}'s. Every goal this class reads out carries
 * what the plan gives it each week, worked out on the read and stored nowhere, because it moves
 * whenever the order moves, whenever money moves and whenever the capacity is redeclared. On an
 * account whose holder has declared no capacity it is absent rather than zero, all the way out.
 *
 * <p><strong>And what that rate means for the goal is {@link WhenAGoalWillBeReached}'s</strong>: the
 * Monday it arrives on, and the status that comes of comparing that Monday with the day it is wanted
 * by. The two derivations run together on every read and are answered together — a goal's weekly
 * amount is the input to its projection, so a read that worked out one without the other would be
 * reporting half a plan.
 */
@Service
public class GoalsService {

    private static final Logger log = LoggerFactory.getLogger(GoalsService.class);

    /**
     * What a target is called when it is refused for not being an amount of money, so the sentence
     * reads as a sentence: "A savings goal target has to be an amount of more than zero, and 0.00 is
     * not."
     */
    private static final String WHAT_A_TARGET_IS_CALLED = "savings goal target";

    /**
     * And what an amount being moved between claims is called, so its refusal reads the same way: "A
     * move between savings goals has to be an amount of more than zero, and 0.00 is not."
     */
    private static final String WHAT_A_MOVE_IS_CALLED = "move between savings goals";

    /** What the end of a move that is no goal at all is called, in a log line and in a sentence. */
    private static final String UNALLOCATED = "unallocated";

    /**
     * And what the weekly figure is called when it is refused for not being an amount of money, so
     * that its refusal reads like the other two: "A weekly saving capacity has to be an amount of
     * more than zero, and 0.00 is not."
     */
    private static final String WHAT_A_CAPACITY_IS_CALLED = "weekly saving capacity";

    /**
     * And what a pinned weekly figure is called when it is refused for the same reason, so a
     * customer who has met one of these objections has met all of them: "A weekly amount pinned to a
     * savings goal has to be an amount of more than zero, and 0.00 is not."
     */
    private static final String WHAT_A_PIN_IS_CALLED = "weekly amount pinned to a savings goal";

    /** How a capacity nobody has declared reads in a log line, where a null would say less. */
    private static final String NOT_DECLARED = "not declared";

    /** And how a goal nobody has pinned reads in one, for the same reason. */
    private static final String NOT_PINNED = "not pinned";

    private final SavingsGoalRepository goals;

    /**
     * The moves that made up every allocation on every account. Append-only: nothing in this class
     * updates or deletes a row, and money coming back out of a goal is a new row the other way.
     */
    private final MoneyMovedBetweenGoalsRepository moves;

    /**
     * For the moments this module stamps: when a goal was opened, and when it was given up on.
     *
     * <p>The application's clock, which a trainer can wind, so that a goal created against a
     * wound-forward clock is dated where the trainer wound it to — and so that "a deadline already in
     * the past" is measured against the day the application thinks it is rather than the day the
     * machine does.
     */
    private final Clock clock;

    /**
     * The one row per savings account that says what its holder can put away in a week, for the
     * accounts whose holder has said.
     */
    private final TheWeeklyAmountThatCanBeSavedRepository capacities;

    /**
     * The scheme, asked one question: what a week has to take in this week to secure itself.
     *
     * <p>Read at the moment a capacity is read or declared, kept nowhere and summed into nothing —
     * the same bargain the balance is handed in under, one step further along. It is here because
     * the figure stopped being a constant somebody could quote: the bank publishes it and can
     * reprice it on a Monday, so a capacity that reported "a week is not secured at this rate"
     * against a figure written in Java would go on saying it after the bank had changed its mind.
     */
    private final SchemeService scheme;

    GoalsService(SavingsGoalRepository goals, MoneyMovedBetweenGoalsRepository moves, Clock clock,
                 TheWeeklyAmountThatCanBeSavedRepository capacities, SchemeService scheme) {
        this.goals = goals;
        this.moves = moves;
        this.clock = clock;
        this.capacities = capacities;
        this.scheme = scheme;
    }

    /**
     * The account's live goals, most important first.
     *
     * <p>Live only. An abandoned goal is not a thing being saved towards and has no place in an
     * order it left; it is still readable one at a time through {@link #goalOn}, and through
     * {@link #abandonedGoalsOn} for whoever wants the whole of what was given up on.
     */
    @Transactional(readOnly = true)
    public List<RecordedGoal> goalsOn(long savingsAccountId) {
        Map<Long, BigDecimal> allocations = allocationsOn(savingsAccountId);
        List<SavingsGoal> inRankOrder = liveGoalsOn(savingsAccountId);
        ThePlanOn plan = thePlanOn(savingsAccountId, inRankOrder, allocations);
        List<RecordedGoal> live = inRankOrder.stream()
                .map(goal -> asRecorded(goal, allocationOf(allocations, goal.getId()), plan))
                .toList();
        log.debug("goals read savingsAccountId={} live={} allocated={}",
                savingsAccountId, live.size(), AmountOfMoney.asMoney(totalOf(allocations)));
        return live;
    }

    /** What was given up on, oldest first, so that what somebody was saving for stays readable. */
    @Transactional(readOnly = true)
    public List<RecordedGoal> abandonedGoalsOn(long savingsAccountId) {
        Map<Long, BigDecimal> allocations = allocationsOn(savingsAccountId);
        // Given up on, so none of them is in the plan at all: what each one is given is the nothing
        // the plan answers for a goal the passes never saw, and it is still absent rather than zero
        // on an account whose holder has declared no capacity. None of them is projected either —
        // a goal nobody is saving for has no date to arrive on. So the live plan is not worked out
        // for this read: it would answer nothing for every goal here anyway, and working it out
        // would put a line in the log describing the account's live goals for a request that asked
        // about the ones it gave up on.
        ThePlanOn plan = noLivePlanOn(savingsAccountId);
        List<RecordedGoal> abandoned =
                goals.findBySavingsAccountIdAndStateOrderByIdAsc(savingsAccountId, GoalState.ABANDONED)
                        .stream()
                        .map(goal -> asRecorded(goal, allocationOf(allocations, goal.getId()), plan))
                        .toList();
        log.debug("abandoned goals read savingsAccountId={} abandoned={}", savingsAccountId, abandoned.size());
        return abandoned;
    }

    /**
     * One goal on this account, whatever state it is in.
     *
     * <p>Whatever state, because this is how an abandoned goal is read back: it keeps its name and
     * its target, and the whole point of closing a goal rather than deleting it is that somebody can
     * still see what they had been saving for.
     *
     * @throws GoalRefused if no goal with that identifier is on that account
     */
    @Transactional(readOnly = true)
    public RecordedGoal goalOn(long savingsAccountId, long goalId) {
        SavingsGoal goal = theGoalOn(savingsAccountId, goalId);
        return asRecorded(goal, allocationOf(allocationsOn(savingsAccountId), goalId),
                thePlanOn(savingsAccountId));
    }

    /**
     * Opens a goal on this savings account, ranked last, and answers with the goal that now exists.
     *
     * <p>Last, because the customer has said nothing about where it belongs and the alternative —
     * guessing from the target, or from the deadline — would be this application deciding what
     * matters most to somebody. Reordering is one call away and is how they say otherwise.
     *
     * <p>Four things are refused and nothing else: a goal with no name, a target of nothing or less,
     * a target quoted more finely than money is, and a deadline that has already passed. A goal with
     * no deadline is not refused — it is the ordinary case, and an emergency fund genuinely has a
     * target with no date.
     *
     * @param deadline the day it is wanted by, or null for a goal with no day
     * @throws GoalRefused if the goal is one this module will not open
     */
    @Transactional
    public RecordedGoal addGoal(long savingsAccountId, String name, BigDecimal target, LocalDate deadline) {
        String itsName = name == null ? "" : name.trim();
        log.debug("goal asked for savingsAccountId={} name={} target={} deadline={}",
                savingsAccountId, itsName, target, deadline);
        refuseUnlessAName(savingsAccountId, null, itsName);
        refuseUnlessATarget(savingsAccountId, null, target);
        refuseUnlessStillToCome(savingsAccountId, null, deadline);
        // Last among the live ones, which is one past however many there are. The live ranks are a
        // run of 1..n, so this is the only figure that can be next without leaving a gap or a tie.
        int last = liveGoalsOn(savingsAccountId).size() + 1;
        SavingsGoal goal = goals.save(new SavingsGoal(
                savingsAccountId, itsName, AmountOfMoney.quotedToTheCent(target), deadline, last,
                clock.instant()));
        log.info("goal added savingsAccountId={} goalId={} name={} target={} deadline={} rank={}",
                savingsAccountId, goal.getId(), goal.getName(), AmountOfMoney.asMoney(goal.getTarget()),
                goal.getDeadline(), goal.getRank());
        // A goal nobody has moved money into yet holds nothing, and reading the ledger to be told so
        // would be a query asked for an answer already known. What the plan now gives it is not
        // known, though: it is ranked last, so it takes what the goals above it left, and that has
        // to be worked out against the whole account.
        return asRecorded(goal, BigDecimal.ZERO, thePlanOn(savingsAccountId));
    }

    /**
     * Changes what a goal says it is for: its name, its target, its deadline, or any combination of
     * the three, and answers with the goal as it now reads.
     *
     * <p>Only what was given is changed. Anything passed as null is left exactly as it was, which is
     * what makes renaming a goal whose deadline is long past a thing somebody can do: the deadline is
     * not being changed, so it is not being ruled on. The deadline is the one field with two ways to
     * be absent, and they mean opposite things, so it takes a flag of its own —
     * {@code theDeadlineIsBeingChanged} with a null day is "there is no longer a day I want this by",
     * which a customer who added one by mistake otherwise has no way to say.
     *
     * <p>A goal that has been given up on is refused rather than edited. It is kept so that what
     * somebody was saving for stays readable, and a record that could be rewritten afterwards is not
     * a record.
     *
     * @param theDeadlineIsBeingChanged whether {@code deadline} is an instruction at all, as opposed
     *                                  to the absence that means "leave it alone"
     * @throws GoalRefused if there is no such goal, if it is closed, or if the change is one this
     *                     module will not make
     */
    @Transactional
    public RecordedGoal changeGoal(long savingsAccountId, long goalId, String name, BigDecimal target,
                                   LocalDate deadline, boolean theDeadlineIsBeingChanged) {
        log.debug("goal change asked for savingsAccountId={} goalId={} name={} target={} deadline={} "
                        + "deadlineGiven={}",
                savingsAccountId, goalId, name, target, deadline, theDeadlineIsBeingChanged);
        SavingsGoal goal = theGoalOn(savingsAccountId, goalId);
        refuseUnlessLive(savingsAccountId, goal, "changed");
        String wasNamed = goal.getName();
        BigDecimal wasWorth = goal.getTarget();
        LocalDate wasWantedBy = goal.getDeadline();
        if (name != null) {
            String itsName = name.trim();
            refuseUnlessAName(savingsAccountId, goalId, itsName);
            goal.rename(itsName);
        }
        if (target != null) {
            refuseUnlessATarget(savingsAccountId, goalId, target);
            goal.retarget(AmountOfMoney.quotedToTheCent(target));
        }
        if (theDeadlineIsBeingChanged) {
            // Ruled on exactly as a new goal's is, and only when it is being set: a day already gone
            // is as useless to measure against on a goal that exists as on one being opened. A goal
            // whose deadline merely passed while nobody touched it is untouched by this — a deadline
            // is soft, and nothing here closes a goal because a date went by.
            refuseUnlessStillToCome(savingsAccountId, goalId, deadline);
            goal.reschedule(deadline);
        }
        goals.save(goal);
        log.info("goal changed savingsAccountId={} goalId={} name={} target={} deadline={} "
                        + "wasNamed={} wasWorth={} wasWantedBy={}",
                savingsAccountId, goalId, goal.getName(), AmountOfMoney.asMoney(goal.getTarget()),
                goal.getDeadline(), wasNamed, AmountOfMoney.asMoney(wasWorth), wasWantedBy);
        // Worked out after the change is saved, because the change is one of the things that decides
        // it: a new target moves what the goal still needs, and a new deadline moves the weeks it has
        // left to find it in.
        return asRecorded(goal, allocationOf(allocationsOn(savingsAccountId), goalId),
                thePlanOn(savingsAccountId));
    }

    /**
     * Sets the whole order of importance at once, and answers with the account's goals in it.
     *
     * <p>The list is the order: first is rank 1. It has to be a permutation of exactly the account's
     * live goals — every one of them, each named once, and nothing else — and anything short of that
     * is refused with nothing moved. Three ways to fall short and one refusal for all three, because
     * they are one mistake: the list you sent is not this account's goals.
     *
     * <p>Checked in full before a single rank is written, so "nothing moves" is true of the database
     * and not only of the answer. The transaction would roll a half-applied order back anyway; doing
     * it in this order means the rollback never has to.
     *
     * @throws GoalRefused if the list is not a permutation of the account's live goals
     */
    @Transactional
    public List<RecordedGoal> reorderGoals(long savingsAccountId, List<Long> goalIdsInOrder) {
        List<Long> asked = goalIdsInOrder == null ? List.of() : goalIdsInOrder;
        List<SavingsGoal> live = liveGoalsOn(savingsAccountId);
        List<Long> wasOrder = live.stream().map(SavingsGoal::getId).toList();
        log.debug("goal order asked for savingsAccountId={} order={} wasOrder={}",
                savingsAccountId, asked, wasOrder);
        refuseUnlessAPermutation(savingsAccountId, asked, wasOrder);
        // Nothing has been written up to here, so a refusal above leaves the order exactly as it was.
        renumberAsListed(asked.stream()
                .map(goalId -> live.stream()
                        .filter(candidate -> goalId.equals(candidate.getId()))
                        .findFirst()
                        .orElseThrow())
                .toList());
        log.info("goal order changed savingsAccountId={} order={} wasOrder={} goals={}",
                savingsAccountId, asked, wasOrder, asked.size());
        return goalsOn(savingsAccountId);
    }

    /**
     * Gives up on a goal, and answers with it as it now reads: closed, holding no rank, and still
     * carrying the name and the target it was opened with.
     *
     * <p>Closed rather than deleted, so that what was being saved for is still readable afterwards —
     * the same reason a points batch records its expiry instead of being emptied. It is an explicit
     * act and the only way a goal leaves the order.
     *
     * <p>The goals below it close the gap it left, because the order is strict: a run of 1, 2, 4 has
     * a hole in it that nothing in this application knows how to read, and renumbering here is what
     * stops one ever existing. Abandoning the most important goal promotes every other goal by one,
     * which is what "it no longer matters" means.
     *
     * <p><strong>Whatever it was holding goes back to unallocated, in one recorded move.</strong>
     * Money a customer has given up on is money they can spend on something else, and leaving it
     * claimed by a goal nobody is saving for would have the account report less spare than it has.
     * One move rather than a silent adjustment, so that the ledger still explains every allocation
     * on the account, and so that "where did my holiday money go" reads back as an answer. No
     * balance is needed to do it: money leaving a goal cannot break the invariant, which is only
     * ever about how much has been claimed.
     *
     * @throws GoalRefused if there is no such goal on the account, or it was already given up on
     */
    @Transactional
    public RecordedGoal abandonGoal(long savingsAccountId, long goalId) {
        log.debug("goal abandon asked for savingsAccountId={} goalId={}", savingsAccountId, goalId);
        SavingsGoal goal = theGoalOn(savingsAccountId, goalId);
        refuseUnlessLive(savingsAccountId, goal, "abandoned");
        Integer heldRank = goal.getRank();
        goal.abandon(clock.instant());
        goals.save(goal);
        List<Long> nowInOrder = closeTheGapLeftBy(savingsAccountId);
        BigDecimal wasHolding = allocationOf(allocationsOn(savingsAccountId), goalId);
        if (wasHolding.signum() > 0) {
            moves.save(new MoneyMovedBetweenGoals(savingsAccountId, goalId, null, wasHolding,
                    clock.instant()));
            log.info("goal money moved savingsAccountId={} outOf={} into={} amount={} because=abandoned",
                    savingsAccountId, goalId, UNALLOCATED, AmountOfMoney.asMoney(wasHolding));
        }
        log.info("goal abandoned savingsAccountId={} goalId={} name={} target={} freedRank={} "
                        + "freedToUnallocated={} order={}",
                savingsAccountId, goalId, goal.getName(), AmountOfMoney.asMoney(goal.getTarget()),
                heldRank, AmountOfMoney.asMoney(wasHolding), nowInOrder);
        // It holds nothing now, whatever it was holding a moment ago: the move above gave all of it
        // back, and a goal that has left the order is not competing for anything — so the plan,
        // worked out again over the goals that are left, gives it nothing.
        return asRecorded(goal, BigDecimal.ZERO, thePlanOn(savingsAccountId));
    }

    /**
     * Commits an amount to this goal every week, replacing whatever was committed before, and
     * answers with the goal as the plan now reads it.
     *
     * <p><strong>This is the customer overruling the engine, and that is the point of it.</strong>
     * Everything else the plan says is derived — the order decides who takes a deadline minimum
     * first, and what is left falls to whoever is highest-ranked — and a customer who cannot fix one
     * figure has nothing to weigh against anything. A pinned goal is taken in the first pass, ahead
     * of every deadline whatever its own rank, and is passed over by the two passes below it: what
     * is pinned is what it gets, and the rest of the plan is worked out around what is left.
     *
     * <p><strong>A pin larger than the whole weekly capacity is accepted, not refused.</strong> It is
     * a legal thing for somebody to say about their own money, and its consequence — every goal
     * below it given nothing and reporting that at this rate it never arrives — is information the
     * customer asked for by pinning. Refusing it would be this application deciding it knows better,
     * and the whole feature is built the other way round. What the plan will not do is hand out more
     * in a week than the customer said they can save, so the pinned goal takes the capacity and not
     * a cent more; see {@code HowTheWeeklyMoneyIsSpent.asAt}.
     *
     * <p>An amount of money and nothing else: zero or less is refused, and so is a figure quoted
     * more finely than money is, in {@code AmountOfMoney}'s own words. Zero is refused rather than
     * kept as "nothing a week into this goal", because a goal pinned at zero and a goal that lost
     * the competition read identically in every figure downstream, and the honest way to say the
     * first is to unpin.
     *
     * <p>Pinning against an account whose holder has declared no capacity is accepted too, and the
     * plan still reports no weekly amount for any goal: a pin is a claim on a capacity, and there is
     * nothing yet to claim against. The commitment is kept and takes effect the moment a figure is
     * declared.
     *
     * @throws GoalRefused if there is no such goal on the account, if it was given up on, or if the
     *                     figure is not an amount of money
     */
    @Transactional
    public RecordedGoal pinWeeklyAmount(long savingsAccountId, long goalId, BigDecimal weeklyAmount) {
        log.debug("goal weekly amount pin asked for savingsAccountId={} goalId={} weeklyAmount={}",
                savingsAccountId, goalId, weeklyAmount);
        SavingsGoal goal = theGoalOn(savingsAccountId, goalId);
        refuseUnlessLive(savingsAccountId, goal, "pinned");
        refuseUnlessAPin(savingsAccountId, goalId, weeklyAmount);
        String was = asMoneyOrNotPinned(pinnedWeeklyAmountOf(goal));
        goal.pinWeeklyAmount(AmountOfMoney.quotedToTheCent(weeklyAmount));
        goals.save(goal);
        // The capacity it is being set against is on the line because it is what decides everything
        // the customer will see next: whether this pin fits, and how much of the plan is left for
        // the goals under it. A pin over the capacity is the interesting case and the one a reviewer
        // comes looking for, and a line carrying only the pin would not show it.
        SavingCapacityOnAnAccount capacity = theCapacityOn(savingsAccountId);
        log.info("goal weekly amount pinned savingsAccountId={} goalId={} name={} weeklyAmount={} "
                        + "was={} weeklyCapacity={} overTheCapacity={}",
                savingsAccountId, goalId, goal.getName(),
                AmountOfMoney.asMoney(goal.getPinnedWeeklyAmount()), was,
                asMoneyOrNotDeclared(capacity.weeklyCapacity()),
                isOverTheCapacity(goal.getPinnedWeeklyAmount(), capacity));
        // Worked out after the pin is saved, because the pin is what decides it — for this goal and
        // for every goal under it, which is the half of the answer the customer is actually weighing.
        return asRecorded(goal, allocationOf(allocationsOn(savingsAccountId), goalId),
                thePlanOn(savingsAccountId));
    }

    /**
     * Takes the commitment off a goal, returning it to the engine's ordinary passes, and answers
     * with the goal as the plan now reads it.
     *
     * <p>Nothing else changes with it: the goal keeps its rank, its target and its deadline, so the
     * plan goes back to exactly what it was before the pin. A pin is the one figure here that is
     * stated rather than derived, and unpinning is how somebody takes their statement back.
     *
     * <p>Unpinning a goal nobody pinned is not refused. There is at most one pin, and a customer
     * asking for it to be gone when it already is has got what they asked for; the log says which of
     * the two happened, so a reviewer reading "nothing was pinned" is not left wondering whether a
     * figure went missing.
     *
     * @throws GoalRefused if there is no such goal on the account, or it was given up on
     */
    @Transactional
    public RecordedGoal unpinWeeklyAmount(long savingsAccountId, long goalId) {
        log.debug("goal weekly amount unpin asked for savingsAccountId={} goalId={}",
                savingsAccountId, goalId);
        SavingsGoal goal = theGoalOn(savingsAccountId, goalId);
        refuseUnlessLive(savingsAccountId, goal, "unpinned");
        String was = asMoneyOrNotPinned(pinnedWeeklyAmountOf(goal));
        boolean wasPinned = goal.unpinWeeklyAmount();
        goals.save(goal);
        log.info("goal weekly amount unpinned savingsAccountId={} goalId={} name={} was={} "
                        + "wasPinned={} weeklyCapacity={}",
                savingsAccountId, goalId, goal.getName(), was, wasPinned,
                asMoneyOrNotDeclared(theCapacityOn(savingsAccountId).weeklyCapacity()));
        return asRecorded(goal, allocationOf(allocationsOn(savingsAccountId), goalId),
                thePlanOn(savingsAccountId));
    }

    /**
     * What the account holds, what its goals have claimed of it, and what is left over.
     *
     * <p>The balance arrives as a parameter because this module reads no other module; see the class
     * comment for why that is load-bearing rather than tidy. Everything else is derived here: the
     * allocations from the ledger, and unallocated from the two.
     */
    @Transactional(readOnly = true)
    public AllocationsOnAnAccount allocationsOn(long savingsAccountId, BigDecimal balance) {
        AllocationsOnAnAccount allocations = theMoneyBehindTheGoalsOn(savingsAccountId, balance);
        log.debug("allocations read savingsAccountId={} balance={} allocated={} unallocated={} goals={}",
                savingsAccountId, AmountOfMoney.asMoney(allocations.balance()),
                AmountOfMoney.asMoney(allocations.allocated()),
                AmountOfMoney.asMoney(allocations.unallocated()), allocations.goals().size());
        return allocations;
    }

    /**
     * Moves an amount from one claim on this account's balance to another, and answers with what the
     * account's money looks like afterwards.
     *
     * <p>One method for all three moves a customer can make, because they are one move: out of a
     * goal or out of what no goal has claimed, into a goal or back into what no goal claims. A null
     * identifier on either end is unallocated. Writing three methods would be writing the invariant
     * three times, and the third copy is the one that would be wrong.
     *
     * <p><strong>No money moves and the savings balance is untouched.</strong> The balance is the sum
     * of what the deposits still hold; this writes one row saying which part of it is spoken for.
     *
     * <p>Everything is checked before the row is written, so a refusal leaves the ledger exactly as
     * it was rather than relying on the transaction to take a row back out. What is checked, in the
     * order a person would want to hear it: that the amount is an amount of money, that the two ends
     * are two different things, that both named goals are on this account, that the goal being given
     * money is open to money at all, that the end giving it has that much to give, and that the goal
     * taking it still needs that much.
     *
     * @param outOfGoalId the goal the money leaves, or null for the part no goal has claimed
     * @param intoGoalId  the goal the money arrives at, or null to give it back to no goal at all
     * @param balance     what the savings account holds, from whoever holds that answer
     * @throws GoalRefused if the move is one this module will not make
     */
    @Transactional
    public AllocationsOnAnAccount moveMoney(long savingsAccountId, Long outOfGoalId, Long intoGoalId,
                                            BigDecimal amount, BigDecimal balance) {
        log.debug("goal money move asked for savingsAccountId={} outOf={} into={} amount={} balance={}",
                savingsAccountId, endOf(outOfGoalId), endOf(intoGoalId), amount, balance);
        refuseUnlessAnAmountOfMoney(savingsAccountId, amount);
        refuseUnlessTwoDifferentEnds(savingsAccountId, outOfGoalId, intoGoalId);
        SavingsGoal givingItUp = outOfGoalId == null ? null : theGoalOn(savingsAccountId, outOfGoalId);
        SavingsGoal takingItIn = intoGoalId == null ? null : theGoalOn(savingsAccountId, intoGoalId);
        Map<Long, BigDecimal> allocations = allocationsOn(savingsAccountId);
        BigDecimal unallocated = unallocatedOf(balance, totalOf(allocations));
        if (takingItIn != null) {
            refuseUnlessOpenToMoney(savingsAccountId, takingItIn,
                    allocationOf(allocations, intoGoalId));
        }
        if (givingItUp == null) {
            refuseUnlessThereIsThatMuchSpare(savingsAccountId, amount, unallocated);
        } else {
            refuseUnlessTheGoalIsHoldingThatMuch(savingsAccountId, givingItUp, amount,
                    allocationOf(allocations, outOfGoalId));
        }
        if (takingItIn != null) {
            refuseUnlessTheGoalStillNeedsThatMuch(savingsAccountId, takingItIn, amount,
                    allocationOf(allocations, intoGoalId));
        }
        // Nothing has been written up to here, so every refusal above leaves the ledger untouched.
        MoneyMovedBetweenGoals moved = moves.save(new MoneyMovedBetweenGoals(
                savingsAccountId, outOfGoalId, intoGoalId, AmountOfMoney.quotedToTheCent(amount),
                clock.instant()));
        AllocationsOnAnAccount now = theMoneyBehindTheGoalsOn(savingsAccountId, balance);
        log.info("goal money moved savingsAccountId={} moveId={} outOf={} into={} amount={} "
                        + "allocated={} unallocated={} balance={}",
                savingsAccountId, moved.getId(), endOf(outOfGoalId), endOf(intoGoalId),
                AmountOfMoney.asMoney(moved.getAmount()), AmountOfMoney.asMoney(now.allocated()),
                AmountOfMoney.asMoney(now.unallocated()), AmountOfMoney.asMoney(now.balance()));
        return now;
    }

    /**
     * The moves worth suggesting on this account — this much, out of that goal, into this one — or
     * the sentence saying there is nothing to suggest.
     *
     * <p>The payload of the whole feature, and the answer to "what does saying the house matters more
     * than the holiday actually cost". The rule is {@link AReallocationWorthSuggesting}'s and is
     * argued there; what this method does is hand it the account as it stands and hand the answer
     * back.
     *
     * <p><strong>No balance, and no row written.</strong> Every move it proposes takes from one goal
     * and gives to another, so what the account has allocated altogether does not move and the one
     * invariant this feature has cannot be touched by reading or by taking the advice. And nothing is
     * stored: reading twice with nothing in between gives the same answer twice, because both times
     * it was worked out from the ledger rather than looked up.
     *
     * <p>Reading it moves nothing and applies nothing. The engine never acts on its own, on any
     * trigger, ever — a customer who reads this and does nothing has done a complete thing.
     */
    @Transactional(readOnly = true)
    public ASuggestedReallocation suggestedReallocationOn(long savingsAccountId) {
        log.debug("suggested reallocation asked for savingsAccountId={}", savingsAccountId);
        return theReallocationWorthSuggestingOn(savingsAccountId);
    }

    /**
     * Applies every move worth making right now, and answers with what was applied and what the
     * account's money looks like afterwards.
     *
     * <p><strong>Recomputed at the moment of acceptance, deliberately.</strong> The customer is not
     * handing back a plan they were given earlier and this method does not take one: a suggestion read
     * before a deposit landed, before money was freed, or before the order changed again is advice
     * about an account that no longer exists, and applying it would move figures nothing has stood
     * behind since. What is applied is what is worked out here, and what is answered with is what was
     * applied — so a suggestion that has gone stale quietly becomes the smaller suggestion that is
     * true now, or none at all. None at all is not a refusal: nothing was wrong with the request, and
     * the sentence says why there was nothing left to do.
     *
     * <p><strong>Every move goes through {@link #moveMoney}</strong>, under the same invariant and the
     * same refusals as a customer moving money by hand. An accepted suggestion is not a special kind
     * of transaction — it is the customer doing what they were shown — so it writes the same ledger
     * rows, reads back in the same history, and would be refused by the same sentences. It never is:
     * the amounts are capped at what the helped goal still needs and at what the donor is holding
     * before they are ever proposed, which is the whole reason those caps are in
     * {@link AReallocationWorthSuggesting}.
     *
     * <p>All of it in one transaction, so that a refusal on the third move leaves none of the first
     * two behind. A half-applied suggestion is the one outcome that would be worse than not suggesting
     * anything.
     *
     * @param balance what the savings account holds, from whoever holds that answer
     * @throws GoalRefused if a move this suggestion proposed is one the module will not make
     */
    @Transactional
    public AReallocationApplied acceptTheSuggestedReallocationOn(long savingsAccountId,
                                                                BigDecimal balance) {
        log.debug("suggested reallocation acceptance asked for savingsAccountId={} balance={}",
                savingsAccountId, balance);
        ASuggestedReallocation applied = theReallocationWorthSuggestingOn(savingsAccountId);
        for (ASuggestedMove move : applied.moves()) {
            moveMoney(savingsAccountId, move.outOfGoalId(), move.intoGoalId(), move.amount(), balance);
            log.info("suggested reallocation move applied savingsAccountId={} outOf={} outOfName={} "
                            + "into={} intoName={} amount={} because={}",
                    savingsAccountId, move.outOfGoalId(), move.outOfGoalName(), move.intoGoalId(),
                    move.intoGoalName(), AmountOfMoney.asMoney(move.amount()), move.reason());
        }
        AllocationsOnAnAccount now = theMoneyBehindTheGoalsOn(savingsAccountId, balance);
        log.info("suggested reallocation accepted savingsAccountId={} worthSuggesting={} moves={} "
                        + "moved={} allocated={} unallocated={} balance={} inWords={}",
                savingsAccountId, applied.worthSuggesting(), applied.moves().size(),
                AmountOfMoney.asMoney(whatTheMovesCameTo(applied)),
                AmountOfMoney.asMoney(now.allocated()), AmountOfMoney.asMoney(now.unallocated()),
                AmountOfMoney.asMoney(now.balance()), applied.inWords());
        return new AReallocationApplied(applied, now);
    }

    /**
     * The moves that made up one goal's allocation, newest first: what was put into it, what was
     * taken back out, and what it was moved to and from.
     *
     * <p>Both ends, not only the arrivals. A history that showed what went in would be a history that
     * could not explain what the goal is holding now, and "what did that withdrawal do to my holiday"
     * is a question about what left.
     *
     * @throws GoalRefused if no goal with that identifier is on that account
     */
    @Transactional(readOnly = true)
    public List<RecordedGoalMove> historyOf(long savingsAccountId, long goalId) {
        theGoalOn(savingsAccountId, goalId);
        Map<Long, String> named = whatTheGoalsOnItAreCalled(savingsAccountId);
        List<RecordedGoalMove> history =
                moves.findBySavingsAccountIdOrderByIdDesc(savingsAccountId).stream()
                        .filter(move -> isAnEndOf(move, goalId))
                        .map(move -> asRecorded(move, named))
                        .toList();
        log.debug("goal history read savingsAccountId={} goalId={} moves={}",
                savingsAccountId, goalId, history.size());
        return history;
    }

    /**
     * What the account's holder said they can put away in a week, or that they have not said.
     *
     * <p>Never a figure this class invented. An account whose holder has declared nothing answers
     * with a capacity that is not declared, and whoever is planning with it reports that rather than
     * a number: an invented default would be the application putting words in the customer's mouth
     * and then presenting them back as their own plan.
     */
    @Transactional(readOnly = true)
    public SavingCapacityOnAnAccount savingCapacityOn(long savingsAccountId) {
        SavingCapacityOnAnAccount capacity = theCapacityOn(savingsAccountId);
        log.debug("saving capacity read savingsAccountId={} declared={} weeklyCapacity={} "
                        + "weeklyMinimum={} securesAWeek={}",
                savingsAccountId, capacity.isDeclared(), asMoneyOrNotDeclared(capacity.weeklyCapacity()),
                AmountOfMoney.asMoney(capacity.weeklyMinimum()), capacity.securesAWeek());
        return capacity;
    }

    /**
     * Records what the account's holder says they can put away in a week, replacing whatever they
     * said before, and answers with the figure as the account now reports it.
     *
     * <p>Per savings account, deliberately. A customer saving into two accounts is saving at two
     * rates, and one figure spread over both would be a number neither plan could explain.
     *
     * <p>An amount of money and nothing else: zero or less is refused, and so is a figure quoted
     * more finely than money is, in {@code AmountOfMoney}'s own words — a capacity is an amount the
     * customer can save, so it is the same rule a target and a deposit are held to and it deserves
     * the same sentence. Zero is refused rather than kept as "I can save nothing", because a
     * capacity of zero is a plan in which nothing ever arrives, and the honest way to say that is to
     * declare nothing.
     *
     * <p>A figure below the weekly minimum the scheme publishes is <strong>accepted</strong>. It is
     * what the customer says they can do, and the application does not argue with it; what it does
     * is say back, in the same breath, that a week is not secured at that rate — see
     * {@link SavingCapacityOnAnAccount}. Refusing it would be this feature deciding how much somebody
     * is allowed to be able to save.
     *
     * @throws GoalRefused if the figure is not an amount of money
     */
    @Transactional
    public SavingCapacityOnAnAccount declareSavingCapacity(long savingsAccountId, BigDecimal weeklyCapacity) {
        // Read once, and the same row is both the figure that was in force and the row rewritten:
        // reading it again after the refusals would be a second query for an answer already in hand.
        Optional<TheWeeklyAmountThatCanBeSaved> alreadyDeclared =
                capacities.findBySavingsAccountId(savingsAccountId);
        String was = asMoneyOrNotDeclared(
                alreadyDeclared.map(TheWeeklyAmountThatCanBeSaved::getWeeklyAmount).orElse(null));
        log.debug("saving capacity asked for savingsAccountId={} weeklyCapacity={} was={}",
                savingsAccountId, weeklyCapacity, was);
        refuseUnlessACapacity(savingsAccountId, weeklyCapacity);

        BigDecimal figure = AmountOfMoney.quotedToTheCent(weeklyCapacity);
        Instant declaredAt = clock.instant();
        TheWeeklyAmountThatCanBeSaved row = alreadyDeclared.orElseGet(
                () -> new TheWeeklyAmountThatCanBeSaved(savingsAccountId, figure, declaredAt));
        row.redeclare(figure, declaredAt);
        capacities.save(row);

        SavingCapacityOnAnAccount now = asRecorded(row, theWeeklyMinimumInForce());
        log.info("saving capacity declared savingsAccountId={} weeklyCapacity={} securesAWeek={} "
                        + "weeklyMinimum={} was={}",
                savingsAccountId, AmountOfMoney.asMoney(now.weeklyCapacity()), now.securesAWeek(),
                AmountOfMoney.asMoney(now.weeklyMinimum()), was);
        return now;
    }

    /**
     * Renumbers what is left as 1..n, keeping the order they were already in, and answers with it.
     *
     * <p>Every goal is written rather than only the ones below the hole, and how that is written down
     * is {@link #renumberAsListed}'s business: the same two passes the whole order is set in, because
     * one goal per rank per account is a rule the database keeps.
     */
    private List<Long> closeTheGapLeftBy(long savingsAccountId) {
        List<SavingsGoal> live = liveGoalsOn(savingsAccountId);
        renumberAsListed(live);
        return live.stream().map(SavingsGoal::getId).toList();
    }

    /**
     * Writes 1..n onto these goals, in the order they are listed, in two passes with a flush between
     * them.
     *
     * <p><strong>Two passes because the order is unique in the database.</strong> One goal per rank
     * per account is an index SQLite keeps — see {@code GoalsOnStartUp} — and an index is checked
     * statement by statement, not at the end of the transaction. Swapping two goals in one pass writes
     * "goal 1 is now rank 2" while goal 2 is still sitting on rank 2, and the database is right to
     * refuse it: for that one statement the account really does have two goals fighting over one
     * place. So every goal is first parked on a rank nothing can collide with — the negative of where
     * it is going, and no live goal has ever held a negative one — and only then written to the rank
     * it is meant to have, by which point every positive rank on the account is free.
     *
     * <p>The parked ranks are flushed, and that is the point of the flush: a pass that only marked the
     * entities dirty would leave Hibernate to write both passes at commit, in whatever order it likes,
     * which is the single-pass problem again with a longer stack trace. They exist for the width of
     * one transaction on a pool of one connection, so nothing ever reads them.
     *
     * <p>Every goal is written rather than only the ones that moved. It is a handful of rows, and a
     * renumbering that decided which rows to touch would be a second copy of the rule about what the
     * ranks are — this way the run of 1..n is asserted rather than maintained.
     */
    private void renumberAsListed(List<SavingsGoal> inTheirNewOrder) {
        for (int place = 0; place < inTheirNewOrder.size(); place++) {
            inTheirNewOrder.get(place).rankedAt(-(place + 1));
            goals.save(inTheirNewOrder.get(place));
        }
        goals.flush();
        for (int place = 0; place < inTheirNewOrder.size(); place++) {
            inTheirNewOrder.get(place).rankedAt(place + 1);
            goals.save(inTheirNewOrder.get(place));
        }
        goals.flush();
    }

    /**
     * What the account holds, what its goals have claimed and what is left, with every live goal's
     * own share filled in. The one place the three figures are worked out, so the read and the answer
     * a move gives back cannot disagree about what a move did.
     */
    private AllocationsOnAnAccount theMoneyBehindTheGoalsOn(long savingsAccountId, BigDecimal balance) {
        BigDecimal held = theBalanceHandedIn(savingsAccountId, balance);
        Map<Long, BigDecimal> allocations = allocationsOn(savingsAccountId);
        List<SavingsGoal> inRankOrder = liveGoalsOn(savingsAccountId);
        ThePlanOn plan = thePlanOn(savingsAccountId, inRankOrder, allocations);
        List<RecordedGoal> live = inRankOrder.stream()
                .map(goal -> asRecorded(goal, allocationOf(allocations, goal.getId()), plan))
                .toList();
        BigDecimal allocated = totalOf(allocations);
        return new AllocationsOnAnAccount(savingsAccountId, AmountOfMoney.quotedToTheCent(held),
                allocated, unallocatedOf(held, allocated), live);
    }

    /**
     * What every goal on the account is holding, by identifier, summed from the ledger.
     *
     * <p>Added up here rather than by the database, for the reason {@code DepositsService} gives
     * about the balance: SQLite has no decimal type and keeps an amount as a float, so a sum it
     * worked out itself would accumulate in floating point and lose the cents somebody typed. Each
     * amount is quoted to the cent as it is read, before it is added to anything.
     *
     * <p>A goal with no moves is simply absent, which {@link #allocationOf} reads as nothing — as
     * opposed to zero being written in, which would make the map a second list of the account's
     * goals and one more thing to keep in step.
     */
    private Map<Long, BigDecimal> allocationsOn(long savingsAccountId) {
        Map<Long, BigDecimal> allocations = new HashMap<>();
        for (MoneyMovedBetweenGoals move : moves.findBySavingsAccountIdOrderByIdDesc(savingsAccountId)) {
            BigDecimal amount = AmountOfMoney.quotedToTheCent(move.getAmount());
            if (move.getIntoGoalId() != null) {
                allocations.merge(move.getIntoGoalId(), amount, BigDecimal::add);
            }
            if (move.getOutOfGoalId() != null) {
                allocations.merge(move.getOutOfGoalId(), amount.negate(), BigDecimal::add);
            }
        }
        return allocations;
    }

    /**
     * The account's declared capacity, or the one that says nothing was declared. One place, so that
     * reading it and declaring it cannot come to disagree about what absence looks like.
     */
    private SavingCapacityOnAnAccount theCapacityOn(long savingsAccountId) {
        BigDecimal weeklyMinimum = theWeeklyMinimumInForce();
        return capacities.findBySavingsAccountId(savingsAccountId)
                .map(row -> asRecorded(row, weeklyMinimum))
                .orElseGet(() -> SavingCapacityOnAnAccount.notDeclaredOn(
                        savingsAccountId, weeklyMinimum));
    }

    /**
     * What a week has to take in to secure itself, as the bank is publishing it today.
     *
     * <p>Today's and not any earlier week's, for the reason {@link SavingCapacityOnAnAccount} gives:
     * a declared capacity is a statement about the weeks in front of the customer. One place, so
     * that reading a capacity and declaring one cannot come to disagree about what they are being
     * weighed against.
     */
    private BigDecimal theWeeklyMinimumInForce() {
        return scheme.theSchemeInForce().weeklyThreshold();
    }

    /**
     * The account's plan: what each of its live goals gets each week, and when each of them will be
     * reached at that rate — over goals and allocations the caller has already read.
     *
     * <p>Two derivations and one answer, in that order, because the first is the second's input: a
     * goal's projection is what it still needs divided by what the plan gives it, so working one out
     * without the other would be reporting half a plan. The day both are asked about is read once
     * here, so that the weeks a deadline has left and the Monday a goal arrives on cannot be counted
     * from two different days.
     *
     * <p>The account's declared capacity, the live goals in rank order and what each of them still
     * needs, handed to the three passes. The engine is given five figures per goal and nothing else
     * — see {@link AGoalCompetingForIt} — so that it cannot quietly start reading a sixth, and the
     * projection is given four and a name for the same reason.
     *
     * <p>The pin is the one of the five that is a column rather than a derivation, and it is handed
     * over exactly as it was written: whether the capacity stretches to it is the engine's answer,
     * not this method's.
     */
    private ThePlanOn thePlanOn(long savingsAccountId, List<SavingsGoal> inRankOrder,
                                Map<Long, BigDecimal> allocations) {
        LocalDate today = today();
        List<AGoalCompetingForIt> competing = inRankOrder.stream()
                .map(goal -> new AGoalCompetingForIt(
                        goal.getId(),
                        goal.getName(),
                        goal.getRank(),
                        stillNeededFor(goal, allocationOf(allocations, goal.getId())),
                        goal.getDeadline(),
                        pinnedWeeklyAmountOf(goal)))
                .toList();
        TheWeeklyMoneySpent spent = HowTheWeeklyMoneyIsSpent.asAt(savingsAccountId,
                theCapacityOn(savingsAccountId).weeklyCapacity(), competing, today);
        List<AGoalOnItsWay> onTheirWay = competing.stream()
                .map(goal -> new AGoalOnItsWay(goal.goalId(), goal.name(), goal.rank(),
                        goal.stillNeeded(), goal.deadline(), spent.forGoal(goal.goalId())))
                .toList();
        return new ThePlanOn(spent, WhenAGoalWillBeReached.forAllOf(savingsAccountId, onTheirWay, today));
    }

    /**
     * The same, for a caller holding neither. A goal read on its own still has to be told what the
     * plan gives it and when it arrives, and both are facts about the whole account: the goals above
     * it take their share first.
     */
    private ThePlanOn thePlanOn(long savingsAccountId) {
        return thePlanOn(savingsAccountId, liveGoalsOn(savingsAccountId),
                allocationsOn(savingsAccountId));
    }

    /**
     * The account's goals as the reallocation rule sees them, and the answer it gives.
     *
     * <p>One place, called both by the read and by the acceptance, because those two have to agree
     * about what is worth moving or the button does something other than what the page said. The plan
     * is worked out first because the rule is a comparison against it: which goals are late, and how
     * far, is a fact about what each of them is being given each week.
     *
     * <p>The rule is handed what it decides on and nothing more — where each goal stands, what it
     * holds, what it is aiming at, the day it is wanted by, what the plan gives it and where that
     * leaves it — for the reason the other two derivations are handed their own five figures.
     */
    private ASuggestedReallocation theReallocationWorthSuggestingOn(long savingsAccountId) {
        List<SavingsGoal> inRankOrder = liveGoalsOn(savingsAccountId);
        Map<Long, BigDecimal> allocations = allocationsOn(savingsAccountId);
        ThePlanOn plan = thePlanOn(savingsAccountId, inRankOrder, allocations);
        List<AGoalInTheOrder> considered = inRankOrder.stream()
                .map(goal -> new AGoalInTheOrder(
                        goal.getId(),
                        goal.getName(),
                        goal.getRank(),
                        AmountOfMoney.quotedToTheCent(goal.getTarget()),
                        allocationOf(allocations, goal.getId()),
                        goal.getDeadline(),
                        plan.weeklyAmountFor(goal.getId()),
                        statusOf(goal, plan.projectionFor(goal.getId()))))
                .toList();
        TheReallocation worked =
                AReallocationWorthSuggesting.forTheGoalsOn(savingsAccountId, considered, today());
        return new ASuggestedReallocation(savingsAccountId, worked.worthSuggesting(),
                worked.inWords(), worked.moves().stream().map(GoalsService::asRecorded).toList());
    }

    /** What the moves came to altogether, for the one line that says what accepting them did. */
    private static BigDecimal whatTheMovesCameTo(ASuggestedReallocation applied) {
        return AmountOfMoney.quotedToTheCent(applied.moves().stream()
                .map(ASuggestedMove::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * The plan as it looks to a goal the plan never saw: no goal is given anything and none of them
     * is projected, with the capacity still carried so that "nobody has said" stays distinct from
     * "this goal gets nothing".
     *
     * <p>For the one read that is not about the live plan — {@link #abandonedGoalsOn}. Every goal it
     * answers about has left the order, so the three passes and the projection would each work
     * something out and then answer nothing for all of them; the only thing running them would
     * change is the log, where the line would describe the account's live goals under a request that
     * asked about the abandoned ones. Identical answers, one fewer misleading line, and the goals
     * named in the log are always the goals the read was about.
     */
    private ThePlanOn noLivePlanOn(long savingsAccountId) {
        return new ThePlanOn(
                new TheWeeklyMoneySpent(theCapacityOn(savingsAccountId).weeklyCapacity(), Map.of()),
                Map.of());
    }

    /**
     * What day the application thinks it is, in the zone weeks are counted in — so that a goal's
     * "today" and a streak's are the same day, and a trainer who winds the clock forward moves both.
     */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }

    /** A capacity in a log line: the figure as money, or the word for a customer who has not said. */
    private static String asMoneyOrNotDeclared(BigDecimal weeklyCapacity) {
        return weeklyCapacity == null ? NOT_DECLARED : AmountOfMoney.asMoney(weeklyCapacity);
    }

    /** A pin in a log line: the figure as money, or the word for a goal nobody has pinned. */
    private static String asMoneyOrNotPinned(BigDecimal pinnedWeeklyAmount) {
        return pinnedWeeklyAmount == null ? NOT_PINNED : AmountOfMoney.asMoney(pinnedWeeklyAmount);
    }

    /**
     * Whether this pin asks for more in a week than the customer said they could put away at all —
     * said on the pinning line rather than left for a reader to compare two figures, because it is
     * the one case with a consequence worth seeing: accepted, and every goal under it given nothing.
     *
     * <p>False on an account with no declared capacity. There is no figure to be over, and saying a
     * pin exceeds a capacity nobody has declared would be a warning about a sentence the customer
     * never said.
     */
    private static boolean isOverTheCapacity(BigDecimal pinnedWeeklyAmount,
                                             SavingCapacityOnAnAccount capacity) {
        return capacity.isDeclared() && pinnedWeeklyAmount.compareTo(capacity.weeklyCapacity()) > 0;
    }

    private static BigDecimal allocationOf(Map<Long, BigDecimal> allocations, Long goalId) {
        return AmountOfMoney.quotedToTheCent(allocations.getOrDefault(goalId, BigDecimal.ZERO));
    }

    private static BigDecimal totalOf(Map<Long, BigDecimal> allocations) {
        return AmountOfMoney.quotedToTheCent(
                allocations.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * What no goal has claimed: the balance less what they have. Never stored, and never floored at
     * zero — a balance that has fallen below what the goals claim is a real state of affairs, and a
     * floor would have the allocations and the leftovers quietly stop adding up to the balance.
     */
    private static BigDecimal unallocatedOf(BigDecimal balance, BigDecimal allocated) {
        return AmountOfMoney.quotedToTheCent(balance.subtract(allocated));
    }

    /** What is still to be found for a goal: its target less what it holds, and never below zero. */
    private static BigDecimal stillNeededFor(SavingsGoal goal, BigDecimal allocation) {
        BigDecimal short_ = goal.getTarget().subtract(allocation);
        return AmountOfMoney.quotedToTheCent(short_.signum() < 0 ? BigDecimal.ZERO : short_);
    }

    /**
     * What the customer has committed to this goal every week, or nothing at all if they have
     * committed nothing.
     *
     * <p>Quoted to the cent on the way out for the reason the target is: it has been through SQLite,
     * which has no decimal type and hands 50.00 back as 50.0 — and this figure is both spent by the
     * engine and printed in a log line a reviewer checks against what they typed.
     */
    private static BigDecimal pinnedWeeklyAmountOf(SavingsGoal goal) {
        return goal.getPinnedWeeklyAmount() == null
                ? null
                : AmountOfMoney.quotedToTheCent(goal.getPinnedWeeklyAmount());
    }

    /**
     * Where the goal stands, worked out rather than read off a column. See {@link GoalStatus} for why
     * arriving is derived and giving up is stored.
     *
     * <p>Giving up is the one answer this method keeps for itself, because it is the one that is not
     * a comparison: a goal that has left the order is not on its way anywhere, whatever the plan
     * would have given it. Everything else is {@link WhenAGoalWillBeReached}'s, so that the status
     * and the date it was reached by cannot be worked out in two places and disagree.
     */
    private static GoalStatus statusOf(SavingsGoal goal, TheProjection projection) {
        if (!goal.isLive()) {
            return GoalStatus.ABANDONED;
        }
        return projection.status();
    }

    /**
     * The balance somebody handed in, or a complaint that nobody did. Not a {@link GoalRefused}: no
     * customer can cause it, there is nothing for one to act on, and the only way to get here is a
     * caller that forgot to ask Deposits — which should fail loudly rather than read as an account
     * holding nothing.
     */
    private BigDecimal theBalanceHandedIn(long savingsAccountId, BigDecimal balance) {
        if (balance == null) {
            String reason = "the goals module is handed a savings balance and does not fetch one, and "
                    + "no balance was handed in";
            log.warn("allocations not worked out savingsAccountId={} reason={}", savingsAccountId, reason);
            throw new IllegalArgumentException(reason);
        }
        return balance;
    }

    private Map<Long, String> whatTheGoalsOnItAreCalled(long savingsAccountId) {
        Map<Long, String> named = new HashMap<>();
        goals.findBySavingsAccountId(savingsAccountId)
                .forEach(goal -> named.put(goal.getId(), goal.getName()));
        return named;
    }

    private static boolean isAnEndOf(MoneyMovedBetweenGoals move, long goalId) {
        return Long.valueOf(goalId).equals(move.getOutOfGoalId())
                || Long.valueOf(goalId).equals(move.getIntoGoalId());
    }

    /** How an end of a move reads in a log line: the goal's identifier, or the word for no goal. */
    private static String endOf(Long goalId) {
        return goalId == null ? UNALLOCATED : String.valueOf(goalId);
    }

    private List<SavingsGoal> liveGoalsOn(long savingsAccountId) {
        return goals.findBySavingsAccountIdAndStateOrderByRankAsc(savingsAccountId, GoalState.LIVE);
    }

    private SavingsGoal theGoalOn(long savingsAccountId, long goalId) {
        return goals.findByIdAndSavingsAccountId(goalId, savingsAccountId)
                .orElseThrow(() -> refusing(savingsAccountId, goalId, NO_SUCH_GOAL,
                        "There is no savings goal " + goalId + " on savings account "
                                + savingsAccountId + "."));
    }

    private void refuseUnlessLive(long savingsAccountId, SavingsGoal goal, String whatWasAsked) {
        if (!goal.isLive()) {
            throw refusing(savingsAccountId, goal.getId(), THE_GOAL_IS_CLOSED,
                    "\"" + goal.getName() + "\" was given up on and cannot be " + whatWasAsked + ".");
        }
    }

    /**
     * Whether a goal will take money at all. Two ways to be shut, one answer: a goal given up on, and
     * a goal that has reached its target. The second is the one worth arguing about — over-funding is
     * refused rather than allowed, because a target that could be exceeded would stop meaning
     * anything, and "still needed" and every projection built on it depend on a finite need. Raising
     * the target is how a customer says they want to save more.
     */
    private void refuseUnlessOpenToMoney(long savingsAccountId, SavingsGoal goal, BigDecimal allocation) {
        if (!goal.isLive()) {
            throw refusing(savingsAccountId, goal.getId(), THE_GOAL_IS_CLOSED,
                    "\"" + goal.getName() + "\" was given up on and cannot be given money. "
                            + "Put it towards a goal you are still saving for.");
        }
        if (allocation.compareTo(goal.getTarget()) >= 0) {
            throw refusing(savingsAccountId, goal.getId(), THE_GOAL_IS_CLOSED,
                    "\"" + goal.getName() + "\" has reached its target of "
                            + AmountOfMoney.asMoney(goal.getTarget())
                            + " and takes no more money. Raise its target if you want to save more "
                            + "towards it.");
        }
    }

    /**
     * An amount to move is an amount of money, and what counts as one is {@code AmountOfMoney}'s
     * answer in the same words a deposit, a withdrawal and a goal's target get. Both of its
     * objections land here: nothing or less, and a figure quoted more finely than money is.
     */
    private void refuseUnlessAnAmountOfMoney(long savingsAccountId, BigDecimal amount) {
        if (amount == null) {
            throw refusing(savingsAccountId, null, AGAINST_THE_RULES,
                    "Say how much to move, as an amount of money.");
        }
        AmountOfMoney.whyItIsNotOne(WHAT_A_MOVE_IS_CALLED, amount).ifPresent(reason -> {
            throw refusing(savingsAccountId, null, AGAINST_THE_RULES, reason);
        });
    }

    /**
     * A move goes from one claim on the balance to another one. Unallocated at both ends moves
     * nothing at all, and a goal at both ends moves nothing either; both would write a ledger row
     * that says something happened when nothing did.
     */
    private void refuseUnlessTwoDifferentEnds(long savingsAccountId, Long outOfGoalId, Long intoGoalId) {
        if (outOfGoalId == null && intoGoalId == null) {
            throw refusing(savingsAccountId, null, AGAINST_THE_RULES,
                    "Say which goal the money is going into, or which goal it is coming out of.");
        }
        if (outOfGoalId != null && outOfGoalId.equals(intoGoalId)) {
            throw refusing(savingsAccountId, outOfGoalId, AGAINST_THE_RULES,
                    "A move goes out of one goal and into another, and goal " + outOfGoalId
                            + " is at both ends of this one.");
        }
    }

    /**
     * The invariant itself: the allocations on an account never add up to more than the account
     * holds. The sentence quotes what is spare, because a customer who has to free money from another
     * goal first needs to know how much.
     */
    private void refuseUnlessThereIsThatMuchSpare(long savingsAccountId, BigDecimal amount,
                                                  BigDecimal unallocated) {
        if (amount.compareTo(unallocated) > 0) {
            throw refusing(savingsAccountId, null, NOT_ENOUGH_UNALLOCATED,
                    "This savings account has " + AmountOfMoney.asMoney(unallocated)
                            + " no goal has claimed, and you are allocating "
                            + AmountOfMoney.asMoney(amount)
                            + ". Free money from another goal first.");
        }
    }

    /**
     * A goal cannot give back more than it is holding. Refused as a form to fix rather than as a
     * kind of its own: it is the mirror of asking a goal for money it never had, and what the person
     * does next is type a smaller figure — the sentence says which figure.
     */
    private void refuseUnlessTheGoalIsHoldingThatMuch(long savingsAccountId, SavingsGoal goal,
                                                      BigDecimal amount, BigDecimal allocation) {
        if (amount.compareTo(allocation) > 0) {
            throw refusing(savingsAccountId, goal.getId(), AGAINST_THE_RULES,
                    "\"" + goal.getName() + "\" is holding " + AmountOfMoney.asMoney(allocation)
                            + ", and you are taking " + AmountOfMoney.asMoney(amount) + " out of it.");
        }
    }

    private void refuseUnlessTheGoalStillNeedsThatMuch(long savingsAccountId, SavingsGoal goal,
                                                       BigDecimal amount, BigDecimal allocation) {
        BigDecimal stillNeeded = stillNeededFor(goal, allocation);
        if (amount.compareTo(stillNeeded) > 0) {
            throw refusing(savingsAccountId, goal.getId(), MORE_THAN_THE_GOAL_NEEDS,
                    "\"" + goal.getName() + "\" still needs " + AmountOfMoney.asMoney(stillNeeded)
                            + " to reach its target of " + AmountOfMoney.asMoney(goal.getTarget())
                            + ", and you are putting " + AmountOfMoney.asMoney(amount)
                            + " towards it. Raise its target if you want to save more towards it.");
        }
    }

    private void refuseUnlessAName(long savingsAccountId, Long goalId, String name) {
        if (name.isBlank()) {
            throw refusing(savingsAccountId, goalId, AGAINST_THE_RULES,
                    "Give the goal a name, so you can tell it from the others you are saving for.");
        }
    }

    /**
     * A target is an amount of money, and what counts as one is {@code AmountOfMoney}'s answer,
     * given in the same words a deposit and a withdrawal get. Both of its objections land here: a
     * figure of nothing or less, and a figure quoted more finely than money is.
     */
    private void refuseUnlessATarget(long savingsAccountId, Long goalId, BigDecimal target) {
        if (target == null) {
            throw refusing(savingsAccountId, goalId, AGAINST_THE_RULES,
                    "Say what you are saving up to, as an amount of money.");
        }
        AmountOfMoney.whyItIsNotOne(WHAT_A_TARGET_IS_CALLED, target).ifPresent(reason -> {
            throw refusing(savingsAccountId, goalId, AGAINST_THE_RULES, reason);
        });
    }

    /**
     * A weekly capacity is an amount of money too, and it is held to the same rule in the same words:
     * zero or less is not one, and neither is a figure with more than two decimal places.
     *
     * <p>Zero is refused rather than kept. "I can save nothing this week" is a plan in which no goal
     * ever arrives, and it is indistinguishable in every figure downstream from a customer who has
     * not said anything — so the one that is honest about it is the one with no row.
     *
     * <p>How small a capacity may otherwise be is not this method's business. A figure under the
     * weekly minimum is accepted and reported back with the warning that a week is not secured at
     * that rate; refusing it would be the application deciding how much somebody is allowed to be
     * able to save.
     */
    private void refuseUnlessACapacity(long savingsAccountId, BigDecimal weeklyCapacity) {
        if (weeklyCapacity == null) {
            throw refusingACapacity(savingsAccountId, AGAINST_THE_RULES,
                    "Say the most you can put away in a week, as an amount of money.");
        }
        AmountOfMoney.whyItIsNotOne(WHAT_A_CAPACITY_IS_CALLED, weeklyCapacity).ifPresent(reason -> {
            throw refusingACapacity(savingsAccountId, AGAINST_THE_RULES, reason);
        });
    }

    /**
     * A pinned weekly amount is an amount of money too, held to the same rule in the same words as a
     * target, a move and a capacity: zero or less is not one, and neither is a figure carrying more
     * places than money does.
     *
     * <p><strong>How large it is, is not this method's business.</strong> A pin above the account's
     * whole weekly capacity is legal and is accepted, for the reason {@link #pinWeeklyAmount} gives:
     * refusing it would be the application overruling a customer about their own money, where what
     * the customer wanted was to see what the commitment costs the goals below it.
     *
     * <p>Zero is refused rather than kept. A goal pinned at nothing a week and a goal the capacity
     * never reached read identically in every figure downstream, and the way to say "the engine
     * should not be filling this at all" is to unpin it and let it come last.
     */
    private void refuseUnlessAPin(long savingsAccountId, Long goalId, BigDecimal weeklyAmount) {
        if (weeklyAmount == null) {
            throw refusing(savingsAccountId, goalId, AGAINST_THE_RULES,
                    "Say how much to put into this goal each week, as an amount of money.");
        }
        AmountOfMoney.whyItIsNotOne(WHAT_A_PIN_IS_CALLED, weeklyAmount).ifPresent(reason -> {
            throw refusing(savingsAccountId, goalId, AGAINST_THE_RULES, reason);
        });
    }

    /**
     * A deadline is optional, so no deadline at all passes here untouched. One that has already gone
     * does not: a day in the past is nothing to aim at, and a projection measured against it would
     * report a goal late the moment it was opened.
     *
     * <p>Today is not past. A goal wanted by the end of today is a goal somebody can still reach, and
     * the day is read in the zone weeks are counted in so that a goal's "today" and a streak's are
     * the same day.
     */
    private void refuseUnlessStillToCome(long savingsAccountId, Long goalId, LocalDate deadline) {
        if (deadline == null) {
            return;
        }
        LocalDate today = today();
        if (deadline.isBefore(today)) {
            throw refusing(savingsAccountId, goalId, AGAINST_THE_RULES,
                    "A savings goal is wanted by a day still to come, and " + deadline
                            + " has already passed. Today is " + today + ".");
        }
    }

    /**
     * Whether the list names this account's live goals, each exactly once. A list that omits one, a
     * list that names one twice, and a list carrying a goal from another account — or no goal at all
     * — are all the same objection, and the sentence says which of them it was.
     */
    private void refuseUnlessAPermutation(long savingsAccountId, List<Long> asked, List<Long> live) {
        Set<Long> askedOnce = new HashSet<>(asked);
        if (askedOnce.size() != asked.size()) {
            throw refusing(savingsAccountId, null, AGAINST_THE_RULES,
                    "The new order names the same goal more than once. List each of the "
                            + live.size() + " goals on this account exactly once.");
        }
        if (!askedOnce.equals(new HashSet<>(live))) {
            List<Long> leftOut = new ArrayList<>(live);
            leftOut.removeAll(askedOnce);
            List<Long> notHere = new ArrayList<>(asked);
            notHere.removeAll(live);
            throw refusing(savingsAccountId, null, AGAINST_THE_RULES,
                    "The new order has to name every goal on this account and nothing else. "
                            + "Missing: " + leftOut + ". Not on this account: " + notHere + ".");
        }
    }

    /**
     * Every refusal says why in the log as well as to whoever asked, because only one of the two is
     * kept: the reason reaches the person at the keyboard and nowhere else. The account and the goal
     * are on the line because a reviewer tracing "I could not reorder my goals" needs to know which
     * account's order was being argued about.
     */
    private GoalRefused refusing(long savingsAccountId, Long goalId, GoalRefused.Kind kind, String reason) {
        log.warn("goal rejected savingsAccountId={} goalId={} kind={} reason={}",
                savingsAccountId, goalId, kind, reason);
        return new GoalRefused(kind, reason);
    }

    /**
     * The same, for a refusal about the account's weekly capacity rather than about a goal.
     *
     * <p>A line of its own rather than the one above with a null goal on it, because a reviewer
     * greps for what they were doing: "saving capacity rejected" is the whole of the answer to "why
     * would it not take my weekly figure", where "goal rejected goalId=null" would send them looking
     * for a goal that was never part of it.
     */
    private GoalRefused refusingACapacity(long savingsAccountId, GoalRefused.Kind kind, String reason) {
        log.warn("saving capacity rejected savingsAccountId={} kind={} reason={}",
                savingsAccountId, kind, reason);
        return new GoalRefused(kind, reason);
    }

    /**
     * The capacity row, read out, quoted to the cent for the reason a goal's target is: it has been
     * through SQLite, which holds 50.00 as a float and hands it back as 50.0.
     */
    private static SavingCapacityOnAnAccount asRecorded(TheWeeklyAmountThatCanBeSaved capacity,
                                                        BigDecimal weeklyMinimum) {
        return new SavingCapacityOnAnAccount(
                capacity.getSavingsAccountId(),
                AmountOfMoney.quotedToTheCent(capacity.getWeeklyAmount()),
                capacity.getDeclaredAt(),
                AmountOfMoney.quotedToTheCent(weeklyMinimum));
    }

    /**
     * The row, read out. The target is quoted to the cent on the way, because it has been through
     * SQLite: that dialect has no decimal type and holds an amount as a float, so a target of
     * 1500.00 comes back as 1500.0 and would reach a page as a number rather than as money.
     *
     * <p>The plan arrives worked out rather than being worked out here, because neither the weekly
     * amount nor the date is a fact about this row: the first is what the account's plan left for
     * this goal after the goals above it took their share, and the second is what that rate makes of
     * what the goal still needs. A weekly amount of null means no capacity has been declared, and it
     * travels out as a null for the reason {@link RecordedGoal} gives.
     */
    private static RecordedGoal asRecorded(SavingsGoal goal, BigDecimal allocation, ThePlanOn plan) {
        TheProjection projection = plan.projectionFor(goal.getId());
        return new RecordedGoal(
                goal.getId(),
                goal.getSavingsAccountId(),
                goal.getName(),
                AmountOfMoney.quotedToTheCent(goal.getTarget()),
                goal.getDeadline(),
                goal.getRank(),
                goal.getState(),
                statusOf(goal, projection),
                AmountOfMoney.quotedToTheCent(allocation),
                stillNeededFor(goal, allocation),
                plan.weeklyAmountFor(goal.getId()),
                pinnedWeeklyAmountOf(goal),
                projection.willBeReachedOn(),
                goal.getCreatedAt(),
                goal.getAbandonedAt());
    }

    /**
     * What one account's plan says, for the length of one read: what every live goal on it gets each
     * week, and when each of them will be reached at that rate.
     *
     * <p>The two travel together because they are asked together and about the same instant of the
     * ledger. A goal read out of a plan that had been worked out twice — once for the money and once
     * for the dates — could carry a weekly amount from before a move and a date from after it, and
     * nothing in the answer would say so.
     *
     * <p>Both lookups answer for a goal the plan never saw, which is how an abandoned goal is read
     * back beside the live ones: it is given nothing, and nothing is said about when it arrives.
     */
    private record ThePlanOn(TheWeeklyMoneySpent spent, Map<Long, TheProjection> projected) {

        /** What this goal gets each week: absent when nobody has declared a capacity, else a figure. */
        BigDecimal weeklyAmountFor(Long goalId) {
            return spent.forGoal(goalId);
        }

        /** When this goal will be reached and where that leaves it, or that nothing was said. */
        TheProjection projectionFor(Long goalId) {
            return projected.getOrDefault(goalId, TheProjection.NOTHING_SAID);
        }
    }

    /**
     * One suggested move, read out. Both ends are always a goal here, unlike a move in the ledger:
     * this rule never reaches for the part of the balance no goal has claimed, so neither end is ever
     * the nameless one.
     */
    private static ASuggestedMove asRecorded(AMoveWorthMaking move) {
        return new ASuggestedMove(move.outOfGoalId(), move.outOfGoalName(), move.intoGoalId(),
                move.intoGoalName(), AmountOfMoney.quotedToTheCent(move.amount()), move.reason());
    }

    /**
     * One move, read out, with both of its ends named. An end that is no goal — the part of the
     * balance nobody has claimed — carries a null identifier and a null name, because it is not a row
     * and has no name to carry.
     */
    private static RecordedGoalMove asRecorded(MoneyMovedBetweenGoals move, Map<Long, String> named) {
        return new RecordedGoalMove(
                move.getId(),
                move.getSavingsAccountId(),
                move.getOutOfGoalId(),
                move.getOutOfGoalId() == null ? null : named.get(move.getOutOfGoalId()),
                move.getIntoGoalId(),
                move.getIntoGoalId() == null ? null : named.get(move.getIntoGoalId()),
                AmountOfMoney.quotedToTheCent(move.getAmount()),
                move.getMovedAt());
    }
}
