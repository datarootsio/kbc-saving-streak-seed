package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.goals.AllocationDirection;
import io.dataroots.savingstreak.goals.AllocationsOnAnAccount;
import io.dataroots.savingstreak.goals.GoalsService;
import io.dataroots.savingstreak.goals.RecordedGoal;
import io.dataroots.savingstreak.sharedpots.WhatAPotIsSavingForIsItsOwnersDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * What a savings account is being saved towards, under the account it belongs to.
 *
 * <p>Its own controller rather than more methods on {@code SavingsAccountController}, because it is
 * a different resource with a different lifetime: the account overview is read on every visit to
 * every screen and assembles five modules, and these paths are one module's and one screen's.
 *
 * <p><strong>This class is what vouches for the savings account.</strong> The Goals module reads no
 * other module, so it cannot tell an account that exists from a number somebody made up; every
 * handler here asks Accounts first and refuses in the words Accounts owns. That is the same order
 * and the same refusal the deposit history and the timeline use, and it is what stops an account
 * nobody has heard of being answered with the empty goal list of an account that simply has none.
 *
 * <p>Beyond that it reads the request and judges nothing. Whether the characters that arrived are a
 * number at all, and whether the characters that arrived are a date at all, are questions about the
 * request and are answered here; whether the figure is a target this application will keep, and
 * whether the day is one still to come, are rules, and they belong to Goals, which refuses on its
 * own.
 *
 * <p><strong>This class is also what asks whose goals these are.</strong> A shared pot's goals are
 * goals on the pot's savings account — that is the whole of how a pot saves for something named, and
 * the Goals module is untouched by it. What a pot adds is a rule about who may write them: an owner
 * decides what the group is saving for, every member may read it, and a stranger may do neither.
 * Goals cannot ask that question — it reads no other module and has never heard of a pot — so it is
 * asked here, before Goals is, exactly as the account itself is vouched for here. The answer comes
 * from {@link WhatAPotIsSavingForIsItsOwnersDecision}, which says nothing at all about an account a
 * customer holds.
 *
 * <p><strong>Who is asking travels as {@code ?customerId=}, on every path here including the reads,
 * and that is a decision worth arguing.</strong> The rest of this feature carries the acting
 * customer in the body of a POST or a PUT and in the query of a DELETE, because every one of those
 * paths is new and belongs to the pot. These paths are neither: they existed before pots did, they
 * are a personal account's paths, and seven of the sixteen are GETs that a member has to be allowed
 * through and a stranger refused — and a GET has no body to put anybody in. So the choice was
 * between asking one question two ways on one screen, or asking it one way everywhere. One way wins.
 * A query parameter is also the only shape that leaves the existing requests alone to the byte:
 * {@code NewGoalRequest}, {@code ChangeGoalRequest}, {@code GoalOrderRequest},
 * {@code PinnedWeeklyAmountRequest} and {@code SavingCapacityRequest} are exactly what they were, so
 * a page saving for one person sends what it has always sent and gets what it has always got. It is
 * optional for the same reason, and never read at all unless a pot holds the account: a personal
 * account has a holder, their goals are their own decision, and there is no role in it to check.
 *
 * <p>Nothing here is authentication and the spec says so out loud. The application trusts the
 * customer it is handed, exactly as {@code POST /api/customers/{customerId}/gifts} trusts its path
 * variable. What is being kept is a domain rule about a claimed identity.
 *
 * <p><strong>This class is also what hands Goals a savings balance.</strong> Goals keeps a claim on
 * part of a balance and never fetches one: Deposits has to ask Goals whether a withdrawal may take
 * money a goal is holding, so a Goals that asked Deposits for a balance would be a cycle the
 * application context could not start. Reading across modules is what a controller is for, and this
 * one reads {@code DepositsService.moneyBalanceOf} and passes the figure in. Passing it in is not
 * deciding anything: no rule about how much may be allocated is taken here.
 */
@RestController
@RequestMapping("/api/savings-accounts/{savingsAccountId}/goals")
class SavingsGoalController {

    private static final Logger log = LoggerFactory.getLogger(SavingsGoalController.class);

    /**
     * What a customer clears the date box to: no longer a day this goal is wanted by, as opposed to
     * a deadline nobody mentioned, which leaves the one it has alone.
     */
    private static final String NO_LONGER_WANTED_BY_A_DAY = "";

    private final AccountsService accounts;
    private final GoalsService goals;

    /** Read for one figure and one only: what the savings account holds, to hand to Goals. */
    private final DepositsService deposits;

    /**
     * Asked, of every request that arrives here, whether the person named in it may do this to the
     * account named in it — and answering nothing at all for the accounts a customer holds.
     */
    private final WhatAPotIsSavingForIsItsOwnersDecision whoseDecisionItIs;

    SavingsGoalController(AccountsService accounts, GoalsService goals, DepositsService deposits,
                          WhatAPotIsSavingForIsItsOwnersDecision whoseDecisionItIs) {
        this.accounts = accounts;
        this.goals = goals;
        this.deposits = deposits;
        this.whoseDecisionItIs = whoseDecisionItIs;
    }

    /**
     * What the account holds, what its goals have claimed of it, and what no goal has claimed.
     *
     * <p>In one read transaction, so that the balance and the allocations describe the same instant
     * of the ledger. Two reads a moment apart is how a page comes to show a balance that does not
     * equal what is allocated plus what is not, which is the one sum anybody reading this is checking.
     *
     * <p>A path beside the goal list rather than a richer goal list, because the goal list is ticket
     * 01's answer and several tests read it as one: what a goal has claimed travels on each goal in
     * both, and the account-wide figures need somewhere of their own to live.
     */
    @Transactional(readOnly = true)
    @GetMapping("/allocations")
    AllocationsResponse allocationsOn(@PathVariable long savingsAccountId,
                                      @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "allocations");
        whoseDecisionItIs.insistTheyMayRead(savingsAccountId, customerId, "read what is allocated on");
        return AllocationsResponse.of(
                goals.allocationsOn(savingsAccountId, deposits.moneyBalanceOf(savingsAccountId)));
    }

    /**
     * The moves that made up one goal's allocation, newest first — what went in, what came back out,
     * and what it was moved to and from.
     */
    @GetMapping("/{goalId}/allocations")
    List<GoalMoveResponse> historyOf(@PathVariable long savingsAccountId, @PathVariable long goalId,
                                     @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "goal history");
        whoseDecisionItIs.insistTheyMayRead(savingsAccountId, customerId, "read what is allocated on");
        return goals.historyOf(savingsAccountId, goalId).stream().map(GoalMoveResponse::of).toList();
    }

    /**
     * Moves money into the goal in the path, or out of it, and answers with the account's money as it
     * now stands.
     *
     * <p>The direction and the goal at the other end are the whole of the shape: into this goal from
     * what no goal has claimed, out of it back to the same, or straight between this goal and the one
     * named — three moves, one request, and no amount ever negative.
     *
     * <p>In one transaction with the balance it reads, so that a deposit committing between the read
     * and the move cannot have the invariant checked against a balance that no longer exists.
     */
    @Transactional
    @PostMapping("/{goalId}/allocations")
    AllocationsResponse moveMoney(@PathVariable long savingsAccountId, @PathVariable long goalId,
                                  @RequestBody AllocationRequest request,
                                  @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "goal allocation");
        whoseDecisionItIs.insistTheyMayDecide(savingsAccountId, customerId,
                "move money between the goals of");
        if (request == null || request.amount() == null) {
            String reason = "Say how much to move, and whether it is going into the goal or out of it.";
            log.warn("goal allocation rejected savingsAccountId={} goalId={} reason={}",
                    savingsAccountId, goalId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        AllocationDirection direction = directionIn(savingsAccountId, goalId, request.direction());
        boolean intoTheGoal = direction == AllocationDirection.INTO_THE_GOAL;
        // Boxed on purpose, and both ends the same shape: null is an end that is no goal at all, and
        // a ternary mixing a long with a Long would unbox the absent one into a failure nobody asked
        // for.
        Long theGoalInThePath = goalId;
        Long theGoalAtTheOtherEnd = request.otherGoalId();
        AllocationsOnAnAccount moved = goals.moveMoney(
                savingsAccountId,
                intoTheGoal ? theGoalAtTheOtherEnd : theGoalInThePath,
                intoTheGoal ? theGoalInThePath : theGoalAtTheOtherEnd,
                amountIn(savingsAccountId, goalId, request.amount()),
                deposits.moneyBalanceOf(savingsAccountId));
        return AllocationsResponse.of(moved);
    }

    /**
     * The moves worth suggesting on this account — this much, out of that goal, into this one — or the
     * sentence saying there is nothing to suggest.
     *
     * <p>A GET, and it means it: reading this writes no row, moves no money and commits this
     * application to nothing. The engine never applies a reallocation on its own, on any trigger,
     * ever, and a read that quietly did would be the one thing this feature promises not to do.
     *
     * <p>No balance is read for it, unlike every other path here that touches allocations. Every move
     * it proposes takes from one goal and gives to another, so what the account has allocated
     * altogether does not move and the invariant the balance guards is not in question.
     *
     * <p>In one read transaction, so that the goals, what each of them holds and the plan they are
     * judged against all describe the same instant of the ledger.
     */
    @Transactional(readOnly = true)
    @GetMapping("/suggested-reallocation")
    SuggestedReallocationResponse suggestedReallocationOn(@PathVariable long savingsAccountId,
                                                          @RequestParam(required = false)
                                                          Long customerId) {
        vouchFor(savingsAccountId, "suggested reallocation");
        whoseDecisionItIs.insistTheyMayRead(savingsAccountId, customerId,
                "read the moves worth making on");
        return SuggestedReallocationResponse.of(goals.suggestedReallocationOn(savingsAccountId));
    }

    /**
     * Applies every move worth making right now, and answers with what was applied and what the
     * account's money looks like afterwards.
     *
     * <p>A POST with no body, and nothing to send one. What is applied is worked out at the moment of
     * acceptance rather than taken from the client, so a suggestion read yesterday cannot be handed
     * back and replayed against today's money: the customer is saying "do the thing you are showing
     * me", and what this application is showing them is whatever it would show them now.
     *
     * <p>In one transaction with the balance it reads, exactly as a single move is, so that a deposit
     * committing in the middle cannot have half the moves checked against a balance that no longer
     * exists — and so that a refusal on the third move leaves the first two unwritten.
     */
    @Transactional
    @PostMapping("/suggested-reallocation")
    AppliedReallocationResponse acceptTheSuggestedReallocation(
            @PathVariable long savingsAccountId,
            @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "suggested reallocation");
        whoseDecisionItIs.insistTheyMayDecide(savingsAccountId, customerId,
                "move money between the goals of");
        return AppliedReallocationResponse.of(goals.acceptTheSuggestedReallocationOn(
                savingsAccountId, deposits.moneyBalanceOf(savingsAccountId)));
    }

    /** The account's live goals, most important first. */
    @GetMapping
    List<GoalResponse> goalsOn(@PathVariable long savingsAccountId,
                               @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "goals");
        whoseDecisionItIs.insistTheyMayRead(savingsAccountId, customerId, "read the goals of");
        return goals.goalsOn(savingsAccountId).stream().map(GoalResponse::of).toList();
    }

    /**
     * What was given up on, so that a goal closed rather than deleted can still be read back.
     *
     * <p>A path of its own rather than a flag on the list above, so that the ordinary read stays the
     * ordinary read: a page showing what somebody is saving for should not have to say every time
     * that it does not want the things they gave up on.
     */
    @GetMapping("/abandoned")
    List<GoalResponse> abandonedGoalsOn(@PathVariable long savingsAccountId,
                                        @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "abandoned goals");
        whoseDecisionItIs.insistTheyMayRead(savingsAccountId, customerId, "read the goals of");
        return goals.abandonedGoalsOn(savingsAccountId).stream().map(GoalResponse::of).toList();
    }

    /** One goal, whatever state it is in — which is how an abandoned one is read back. */
    @GetMapping("/{goalId}")
    GoalResponse goalOn(@PathVariable long savingsAccountId, @PathVariable long goalId,
                        @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "goal");
        whoseDecisionItIs.insistTheyMayRead(savingsAccountId, customerId, "read the goals of");
        return GoalResponse.of(goals.goalOn(savingsAccountId, goalId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GoalResponse addGoal(@PathVariable long savingsAccountId, @RequestBody NewGoalRequest request,
                         @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "goal");
        whoseDecisionItIs.insistTheyMayDecide(savingsAccountId, customerId, "add a goal to");
        if (request == null || request.target() == null) {
            String reason = "A savings goal needs a name and the amount you are saving up to.";
            log.warn("goal rejected savingsAccountId={} reason={}", savingsAccountId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        RecordedGoal opened = goals.addGoal(
                savingsAccountId,
                request.name(),
                targetIn(savingsAccountId, request.target()),
                // Absent is a goal with no day, which is the ordinary case and not a mistake.
                deadlineIn(savingsAccountId, request.deadline()));
        return GoalResponse.of(opened);
    }

    /**
     * Changes whichever of the three a customer sent. A deadline of {@code ""} removes the one the
     * goal has; a deadline nobody mentioned leaves it alone.
     */
    @PatchMapping("/{goalId}")
    GoalResponse changeGoal(@PathVariable long savingsAccountId, @PathVariable long goalId,
                            @RequestBody ChangeGoalRequest request,
                            @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "goal");
        whoseDecisionItIs.insistTheyMayDecide(savingsAccountId, customerId, "change a goal of");
        if (request == null) {
            String reason = "Say what to change about the goal: its name, its target or its deadline.";
            log.warn("goal change rejected savingsAccountId={} goalId={} reason={}",
                    savingsAccountId, goalId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        boolean theDeadlineIsBeingChanged = request.deadline() != null;
        LocalDate deadline = theDeadlineIsBeingChanged
                && !NO_LONGER_WANTED_BY_A_DAY.equals(request.deadline().trim())
                ? deadlineIn(savingsAccountId, request.deadline())
                : null;
        RecordedGoal changed = goals.changeGoal(
                savingsAccountId, goalId,
                request.name(),
                request.target() == null ? null : targetIn(savingsAccountId, request.target()),
                deadline,
                theDeadlineIsBeingChanged);
        return GoalResponse.of(changed);
    }

    /**
     * The whole order at once. It answers with the account's goals in their new order rather than
     * with nothing, because the order is the thing that changed and a page that had to fetch it again
     * to see what it did is a page with a gap in it.
     */
    @PutMapping("/order")
    List<GoalResponse> reorderGoals(@PathVariable long savingsAccountId,
                                    @RequestBody GoalOrderRequest request,
                                    @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "goal order");
        whoseDecisionItIs.insistTheyMayDecide(savingsAccountId, customerId, "reorder the goals of");
        return goals.reorderGoals(savingsAccountId, request == null ? null : request.goalIds())
                .stream().map(GoalResponse::of).toList();
    }

    /**
     * Pins a weekly amount to the goal in the path: the customer fixing one figure of the plan, which
     * the engine then works the rest of the plan around.
     *
     * <p>A PUT, for the reason the weekly capacity is one: there is at most one pin on a goal, and
     * sending the same figure twice leaves it exactly where it was, which is what a PUT promises and
     * a POST does not. "Change it" and "pin one for the first time" are the same request, so they are
     * the same call.
     *
     * <p>A figure over the account's whole weekly capacity is not refused here or anywhere else. It
     * is a legal thing to say about your own money, and the answer to it is the plan — every goal
     * below given nothing — rather than an objection.
     */
    @PutMapping("/{goalId}/weekly-amount")
    GoalResponse pinWeeklyAmount(@PathVariable long savingsAccountId, @PathVariable long goalId,
                                 @RequestBody PinnedWeeklyAmountRequest request,
                                 @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "goal weekly amount");
        whoseDecisionItIs.insistTheyMayDecide(savingsAccountId, customerId,
                "pin a weekly amount on a goal of");
        if (request == null || request.weeklyAmount() == null) {
            String reason = "Say how much to put into this goal each week, as an amount of money.";
            log.warn("goal weekly amount rejected savingsAccountId={} goalId={} reason={}",
                    savingsAccountId, goalId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return GoalResponse.of(goals.pinWeeklyAmount(savingsAccountId, goalId,
                weeklyAmountIn(savingsAccountId, goalId, request.weeklyAmount())));
    }

    /**
     * Takes the pin off, returning the goal to the engine's ordinary passes.
     *
     * <p>A DELETE, and the one place in this controller where it is the honest verb: the pin really
     * does stop existing, unlike a goal given up on, which is kept so that what somebody was saving
     * for stays readable. Sending it twice is not a mistake — the second one finds nothing pinned
     * and says so in the log rather than refusing.
     */
    @DeleteMapping("/{goalId}/weekly-amount")
    GoalResponse unpinWeeklyAmount(@PathVariable long savingsAccountId, @PathVariable long goalId,
                                   @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "goal weekly amount");
        whoseDecisionItIs.insistTheyMayDecide(savingsAccountId, customerId,
                "take a pinned weekly amount off a goal of");
        return GoalResponse.of(goals.unpinWeeklyAmount(savingsAccountId, goalId));
    }

    /**
     * Gives up on a goal. A POST rather than a DELETE, because nothing is deleted: the goal stays,
     * closed, so that what was being saved for is still readable — and a DELETE that left the thing
     * behind would be lying about what it did.
     */
    @PostMapping("/{goalId}/abandon")
    GoalResponse abandonGoal(@PathVariable long savingsAccountId, @PathVariable long goalId,
                             @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "goal");
        whoseDecisionItIs.insistTheyMayDecide(savingsAccountId, customerId, "give up a goal of");
        return GoalResponse.of(goals.abandonGoal(savingsAccountId, goalId));
    }

    /**
     * Asked before the goals are, so that an account nobody has heard of is refused rather than
     * answered with the empty goal list of an account that simply has none. The sentence is the one
     * Accounts owns, so that four modules saying it are not four wordings one edit away from
     * disagreeing, and it is logged as well as answered because a refusal decided here would
     * otherwise leave no line in the application's log at all.
     */
    private void vouchFor(long savingsAccountId, String whatWasAsked) {
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            String reason = AccountsService.noSuchSavingsAccount(savingsAccountId);
            log.warn("{} rejected savingsAccountId={} reason={}", whatWasAsked, savingsAccountId, reason);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
        }
    }

    /**
     * The target as a figure, or a refusal naming what could not be read as one. Named back to
     * whoever sent it, because a person who typed a comma has to see the comma to see the mistake.
     * Whether the figure is a target this application will keep is Goals' answer, not this one's.
     */
    private BigDecimal targetIn(long savingsAccountId, String target) {
        try {
            return new BigDecimal(target.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + target + "\" is not an amount of money. Write it in digits with a "
                    + "full stop, like 2500.00.";
            log.warn("goal rejected savingsAccountId={} target={} reason={}",
                    savingsAccountId, target, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * The deadline as a day, nothing at all when none was given, or a refusal naming what could not
     * be read as a day. Whether the day is one still to come is Goals' answer, not this one's.
     */
    /**
     * Which way the money is going, or a refusal naming what could not be read as a direction. A
     * question about the request rather than about the money, so it is answered here and the sentence
     * lists the two words that work.
     */
    private AllocationDirection directionIn(long savingsAccountId, long goalId, String direction) {
        try {
            return AllocationDirection.valueOf(String.valueOf(direction).trim().toUpperCase());
        } catch (IllegalArgumentException notADirection) {
            String reason = "Say which way the money is going: \"INTO_THE_GOAL\" or "
                    + "\"OUT_OF_THE_GOAL\". \"" + direction + "\" is neither.";
            log.warn("goal allocation rejected savingsAccountId={} goalId={} direction={} reason={}",
                    savingsAccountId, goalId, direction, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * The amount as a figure, or a refusal naming what could not be read as one — the same answer,
     * in the same shape, a target that is not a number gets. Whether the figure is an amount this
     * application will move is Goals' answer, not this one's.
     */
    private BigDecimal amountIn(long savingsAccountId, long goalId, String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + amount + "\" is not an amount of money. Write it in digits with a "
                    + "full stop, like 50.00.";
            log.warn("goal allocation rejected savingsAccountId={} goalId={} amount={} reason={}",
                    savingsAccountId, goalId, amount, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * The pinned figure as a number, or a refusal naming what could not be read as one — the same
     * answer, in the same shape, a target and a move get. Whether the figure is one this application
     * will pin is Goals' answer, not this one's, and how large it may be is nobody's.
     */
    private BigDecimal weeklyAmountIn(long savingsAccountId, long goalId, String weeklyAmount) {
        try {
            return new BigDecimal(weeklyAmount.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + weeklyAmount + "\" is not an amount of money. Write it in digits "
                    + "with a full stop, like 50.00.";
            log.warn("goal weekly amount rejected savingsAccountId={} goalId={} weeklyAmount={} "
                            + "reason={}",
                    savingsAccountId, goalId, weeklyAmount, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    private LocalDate deadlineIn(long savingsAccountId, String deadline) {
        if (deadline == null || deadline.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(deadline.trim());
        } catch (DateTimeParseException notADay) {
            String reason = "\"" + deadline + "\" is not a day. Write it as a date, like 2026-12-31.";
            log.warn("goal rejected savingsAccountId={} deadline={} reason={}",
                    savingsAccountId, deadline, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }
}
