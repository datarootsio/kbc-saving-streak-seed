package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.goals.GoalsService;
import io.dataroots.savingstreak.sharedpots.WhatAPotIsSavingForIsItsOwnersDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The most a savings account's holder says they can put away in a week.
 *
 * <p>A resource of its own beside the goals rather than a path under them, because it is not a goal
 * and does not need one: an account with no goals at all can carry a capacity, and an account with
 * five goals carries one capacity between them. It is the figure the planning engine spends across
 * whatever goals are there.
 *
 * <p>A PUT rather than a POST. There is at most one capacity per account and sending the same figure
 * twice leaves the account exactly where it was — which is what a PUT promises and a POST does not —
 * and "change it" and "set it for the first time" are the same request, so they are the same call.
 *
 * <p><strong>This class vouches for the savings account</strong>, the way
 * {@code SavingsGoalController} does and for the same reason: the Goals module reads no other
 * module, so it cannot tell an account that exists from a number somebody made up, and an account
 * nobody has heard of has to be refused rather than answered with the "not declared yet" of an
 * account that simply has no figure.
 *
 * <p>Beyond that it judges nothing. Whether the characters that arrived are a number at all is a
 * question about the request and is answered here; whether the figure is one this application will
 * keep, and whether it clears the weekly minimum, are rules, and they belong to Goals.
 *
 * <p><strong>And it asks whose capacity this is.</strong> A shared pot has a weekly saving capacity
 * the way any account does, because it is an account — and the projections on a pot's goals are
 * worthless without one, which is why declaring it is story 45 and not an afterthought. Who may
 * declare it is the one new rule: the owner, because it is the assumption every figure on every
 * shared goal is then quoted against, and a contributor who could halve it would be rewriting what
 * the group has been told it can do. Every member may read it. The question is put to
 * {@link WhatAPotIsSavingForIsItsOwnersDecision}, which says nothing at all about an account a
 * customer holds.
 *
 * <p>Who is asking travels as {@code ?customerId=} on both paths, for the reasons
 * {@code SavingsGoalController} sets out at length: these are a personal account's paths, a GET has
 * no body to name anybody in, and one question asked two ways on one screen is a question that will
 * eventually be asked wrong. It is optional and is read only when a pot holds the account, so a
 * customer declaring their own capacity sends exactly what they always sent.
 */
@RestController
@RequestMapping("/api/savings-accounts/{savingsAccountId}/saving-capacity")
class SavingCapacityController {

    private static final Logger log = LoggerFactory.getLogger(SavingCapacityController.class);

    private final AccountsService accounts;
    private final GoalsService goals;

    /** Asked, for a pot's account and for no other, whether the person named in the request may. */
    private final WhatAPotIsSavingForIsItsOwnersDecision whoseDecisionItIs;

    SavingCapacityController(AccountsService accounts, GoalsService goals,
                             WhatAPotIsSavingForIsItsOwnersDecision whoseDecisionItIs) {
        this.accounts = accounts;
        this.goals = goals;
        this.whoseDecisionItIs = whoseDecisionItIs;
    }

    /**
     * What the account's holder has said they can save each week, or that they have said nothing.
     *
     * <p>Answered rather than left to a 404, because "nobody has declared one" is an answer about an
     * account that exists and a page has something to draw for it: the box to type one into.
     */
    @GetMapping
    SavingCapacityResponse savingCapacityOn(@PathVariable long savingsAccountId,
                                            @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "saving capacity");
        whoseDecisionItIs.insistTheyMayRead(savingsAccountId, customerId,
                "read the weekly saving capacity of");
        return SavingCapacityResponse.of(goals.savingCapacityOn(savingsAccountId));
    }

    /** Declares it, or declares it again, and answers with the figure as the account now reports it. */
    @PutMapping
    SavingCapacityResponse declareSavingCapacity(@PathVariable long savingsAccountId,
                                                 @RequestBody SavingCapacityRequest request,
                                                 @RequestParam(required = false) Long customerId) {
        vouchFor(savingsAccountId, "saving capacity");
        whoseDecisionItIs.insistTheyMayDecide(savingsAccountId, customerId,
                "declare the weekly saving capacity of");
        if (request == null || request.weeklyCapacity() == null) {
            String reason = "Say the most you can put away in a week, as an amount of money.";
            log.warn("saving capacity rejected savingsAccountId={} reason={}", savingsAccountId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return SavingCapacityResponse.of(goals.declareSavingCapacity(
                savingsAccountId, weeklyCapacityIn(savingsAccountId, request.weeklyCapacity())));
    }

    /**
     * Asked before Goals is, in the words Accounts owns, so that four modules saying the same
     * sentence are not four wordings one edit away from disagreeing. Logged as well as answered,
     * because a refusal decided here would otherwise leave no line in the application's log at all.
     */
    private void vouchFor(long savingsAccountId, String whatWasAsked) {
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            String reason = AccountsService.noSuchSavingsAccount(savingsAccountId);
            log.warn("{} rejected savingsAccountId={} reason={}", whatWasAsked, savingsAccountId, reason);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
        }
    }

    /**
     * The figure as a number, or a refusal naming what could not be read as one — the same answer, in
     * the same shape, a goal's target gets. Named back to whoever sent it, because a person who typed
     * a comma has to see the comma to see the mistake. Whether the figure is a capacity this
     * application will keep is Goals' answer, not this one's.
     */
    private BigDecimal weeklyCapacityIn(long savingsAccountId, String weeklyCapacity) {
        try {
            return new BigDecimal(weeklyCapacity.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + weeklyCapacity + "\" is not an amount of money. Write it in digits "
                    + "with a full stop, like 75.00.";
            log.warn("saving capacity rejected savingsAccountId={} weeklyCapacity={} reason={}",
                    savingsAccountId, weeklyCapacity, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }
}
