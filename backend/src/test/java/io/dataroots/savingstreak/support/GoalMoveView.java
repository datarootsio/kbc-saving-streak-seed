package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One move in a goal's history, as the API reports it: where the money came from, where it went, how
 * much, and when.
 *
 * <p>Both ends are boxed, and null on either is the part of the balance no goal has claimed. A test
 * asserting that money went back to being spare has to be able to see that absence.
 */
public record GoalMoveView(Long id, Long savingsAccountId, Long outOfGoalId, String outOfGoalName,
                           Long intoGoalId, String intoGoalName, BigDecimal amount, Instant movedAt) {
}
