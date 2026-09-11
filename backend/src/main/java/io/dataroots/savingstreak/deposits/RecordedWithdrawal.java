package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A withdrawal as callers can read it: amount, destination and the moment it happened. */
public record RecordedWithdrawal(Long id, BigDecimal amount, long toCurrentAccountId, Instant withdrawnAt,
                                 List<RecordedWithdrawalAllocation> allocations) {
}
