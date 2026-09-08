package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One entry in the money-movement ledger as a test reads it, which is exactly as the API answers it.
 *
 * <p>The direction is read as the word the API sends rather than mapped onto an enum of the test's
 * own, so that a rename in the backend fails a test instead of being quietly translated back.
 */
public record MoneyMovementView(String direction, long id, long savingsAccountId, long currentAccountId,
                                BigDecimal amount, long pointsEarned, Instant movedAt) {
}
