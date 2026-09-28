package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.goals.ASuggestedMove;

/**
 * One move worth making as the API reports it: out of that goal, into this one, how much, and why.
 *
 * <p>Both ends carry a name beside their identifier, for the reason {@link GoalMoveResponse}'s do: a
 * page saying "out of Holiday, into House" should not have to fetch every goal on the account to look
 * two numbers up. Unlike a move in the ledger, neither end is ever the part of the balance no goal has
 * claimed — this suggestion only ever moves money between goals — so neither identifier is ever null.
 *
 * <p>{@code reason} is the sentence that names the goal the move helps and where that goal stands,
 * and it travels on the row rather than on the list because that is where the question is asked: a
 * customer looking at "500.00 out of Holiday" wants to know why on that line.
 *
 * <p>It carries no identifier of its own, because there is no row to have one. A suggestion is derived
 * on the read that answers with it and stored nowhere, and accepting works it out again — see
 * {@link SuggestedReallocationResponse}.
 */
record SuggestedMoveResponse(Long outOfGoalId, String outOfGoalName, Long intoGoalId,
                             String intoGoalName, BigDecimal amount, String reason) {

    static SuggestedMoveResponse of(ASuggestedMove move) {
        return new SuggestedMoveResponse(move.outOfGoalId(), move.outOfGoalName(), move.intoGoalId(),
                move.intoGoalName(), move.amount(), move.reason());
    }
}
