package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.goals.RecordedGoalMove;

/**
 * One move in a goal's history as the API reports it: where the money came from, where it went, how
 * much, and when.
 *
 * <p>Both ends carry a name beside their identifier so that a page can say "out of Holiday, into
 * House" without fetching every goal on the account to look the numbers up. A null identifier and a
 * null name mean the part of the balance no goal has claimed — it is derived and has no row to have
 * a name in, and a page decides what to call it.
 */
record GoalMoveResponse(Long id, Long savingsAccountId, Long outOfGoalId, String outOfGoalName,
                        Long intoGoalId, String intoGoalName, BigDecimal amount, Instant movedAt) {

    static GoalMoveResponse of(RecordedGoalMove move) {
        return new GoalMoveResponse(move.id(), move.savingsAccountId(), move.outOfGoalId(),
                move.outOfGoalName(), move.intoGoalId(), move.intoGoalName(), move.amount(),
                move.movedAt());
    }
}
