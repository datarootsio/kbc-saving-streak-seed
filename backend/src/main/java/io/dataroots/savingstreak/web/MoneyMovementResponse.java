package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.deposits.MoneyMovement;

/**
 * One entry in the money-movement ledger as the API reports it.
 *
 * <p>The direction travels as its own name — {@code INTO_SAVINGS} or {@code OUT_OF_SAVINGS} — rather
 * than as a sign on the amount or as a boolean. A page rendering the ledger decides what to call each
 * kind and which way round to draw the arrow, and both of those are easier to get right from a word
 * than from a minus sign.
 */
record MoneyMovementResponse(String direction, long id, long savingsAccountId, long currentAccountId,
                             BigDecimal amount, long pointsEarned, Instant movedAt) {

    static MoneyMovementResponse of(MoneyMovement movement) {
        return new MoneyMovementResponse(movement.direction().name(), movement.id(),
                movement.savingsAccountId(), movement.currentAccountId(), movement.amount(),
                movement.pointsEarned(), movement.movedAt());
    }
}
