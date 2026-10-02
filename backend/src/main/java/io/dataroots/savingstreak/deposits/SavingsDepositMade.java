package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

/** A successful deposit, published inside the transaction that records it. */
public record SavingsDepositMade(long savingsAccountId, long customerId, BigDecimal amount,
                                 Instant depositedAt) {
}
