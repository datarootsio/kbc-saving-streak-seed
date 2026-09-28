package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.deposits.AMoveBetweenSavingsAccounts;
import io.dataroots.savingstreak.deposits.MovesService;
import io.dataroots.savingstreak.loyalty.LoyaltyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The two doors a customer moving money between two of their own savings accounts needs: read what
 * moving would cost, and move it.
 *
 * <p><strong>Two doors rather than one, and that is the feature.</strong> The reading quotes what
 * the move would cost in loyalty <em>before</em> anything is confirmed; the press is a separate
 * request that pays exactly that. A single door that moved the money and then reported the forfeit
 * would be charging a price nobody had been shown — which is the same argument the fixed term's two
 * doors beside this one make, and it applies with more force here, because the whole reason moving
 * exists as an operation is that the alternative punished somebody without telling them.
 *
 * <p><strong>Under the account the money leaves</strong>, because that is the account whose
 * conditions decide whether the move may happen at all: notice, a term and a floor are all facts
 * about the source, and a customer refused is refused by the account they are moving out of. The
 * other account is in the body, which is the shape a deposit and a withdrawal already take — one
 * account in the path, the other named by whoever is asking.
 *
 * <p>A controller of its own rather than two more methods on {@code SavingsAccountController},
 * following the notice and term controllers beside it: that class already answers for balances,
 * deposits, withdrawals and the timeline and takes eight services to do it, and a move is a
 * separate subject with a service of its own.
 *
 * <p><strong>It judges nothing about the move.</strong> Whether the two accounts are one
 * customer's, whether either belongs to a shared pot, whether the destination still takes money,
 * whether the source holds it, whether the agreement will let it go and whether a goal has spoken
 * for it are all rules the domain keeps — and every one of them comes back as a sentence this layer
 * carries untouched. A controller that checked first would be a second copy of a rule, and the
 * second copy is always the one that is out of date. The only thing read here is whether the
 * request has the two things a move needs in it at all, and whether what was typed is a number.
 */
@RestController
@RequestMapping("/api/savings-accounts/{savingsAccountId}/moves")
class SavingsAccountMoveController {

    private static final Logger log = LoggerFactory.getLogger(SavingsAccountMoveController.class);

    private final MovesService moves;
    private final LoyaltyService loyalty;

    SavingsAccountMoveController(MovesService moves, LoyaltyService loyalty) {
        this.moves = moves;
        this.loyalty = loyalty;
    }

    /**
     * What moving this much to that account would cost in loyalty, with nothing moved.
     *
     * <p>A POST although it changes nothing, which is the shape the saving rule's own preview
     * already set in this API: the question carries an amount that has to reach the domain as the
     * customer typed it, and a figure in a query string is a figure something has already decided
     * how to parse. Nothing is written and nothing is reserved.
     *
     * <p>It refuses everything the move itself would refuse, in the same sentences. A price quoted
     * for a move that was never going to be allowed is worse than no price: somebody would weigh a
     * loyalty clock against a notice period they could not get past anyway.
     */
    @PostMapping("/preview")
    WhatMovingWouldCostResponse whatItWouldCost(@PathVariable long savingsAccountId,
                                                @RequestBody MoveRequest request) {
        BigDecimal amount = theAmountIn(savingsAccountId, request, "what moving money would cost");
        return WhatMovingWouldCostResponse.of(loyalty.whatMovingWouldCost(
                savingsAccountId, request.toSavingsAccountId(), amount));
    }

    /**
     * Moves the money, in one operation, and answers with what happened to it.
     *
     * <p>201 rather than 200, because something was created that the customer now holds: a deposit
     * in the other account, with a clock of its own running on it. That row is the move as far as
     * the account it arrived in is concerned, and its identifier is in the answer.
     *
     * <p>The anniversary the arriving money has just started counting towards is read off the
     * Loyalty module here rather than assembled by the domain, exactly as a deposit's is and for
     * that endpoint's reason: a move just made and the same money looked at tomorrow have to say
     * the same thing, and one code path is what makes that true instead of hoped for.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AMoveBetweenSavingsAccountsResponse move(@PathVariable long savingsAccountId,
                                             @RequestBody MoveRequest request) {
        BigDecimal amount = theAmountIn(savingsAccountId, request, "a move");
        AMoveBetweenSavingsAccounts moved =
                moves.move(savingsAccountId, request.toSavingsAccountId(), amount);
        return AMoveBetweenSavingsAccountsResponse.of(moved,
                loyalty.whenTheDepositsInAnAccountNextPay(moved.toSavingsAccountId())
                        .get(moved.depositId()));
    }

    /**
     * The two things a move needs, read off the request, or a refusal naming what could not be read.
     *
     * <p>Reading the request, not judging it. Whether a figure is a number at all is a question
     * about the characters that arrived; whether the number is an amount this application will move
     * is a rule, and it belongs to the domain, which refuses on its own and in its own words.
     *
     * <p>What was typed is named back, because a person who wrote a comma has to see the comma to
     * see the mistake. It is logged with the sentence rather than a summary of it, so that what a
     * reviewer reads and what the person at the keyboard was told are the same words — this is the
     * one refusal on this path decided before the domain is called at all, and it would otherwise
     * be the single refusal nobody could find in the log.
     */
    private BigDecimal theAmountIn(long savingsAccountId, MoveRequest request, String what) {
        if (request == null || request.amount() == null || request.toSavingsAccountId() == null) {
            String reason = "A move needs an amount and the savings account it is going to.";
            log.warn("{} rejected savingsAccountId={} reason={}", what, savingsAccountId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        try {
            return new BigDecimal(request.amount().trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + request.amount() + "\" is not an amount of money. Write it in "
                    + "digits with a full stop, like 25.00.";
            log.warn("{} rejected savingsAccountId={} toSavingsAccountId={} amount={} reason={}",
                    what, savingsAccountId, request.toSavingsAccountId(), request.amount(), reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }
}
