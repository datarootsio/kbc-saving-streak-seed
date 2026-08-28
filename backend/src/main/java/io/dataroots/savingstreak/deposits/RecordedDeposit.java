package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A deposit that happened, as the rest of the application sees it: how much moved, what it earned,
 * and when. The stored record stays inside the module; this is a statement about what took place.
 */
public record RecordedDeposit(Long id, BigDecimal amount, long pointsEarned, Instant depositedAt) {
}
