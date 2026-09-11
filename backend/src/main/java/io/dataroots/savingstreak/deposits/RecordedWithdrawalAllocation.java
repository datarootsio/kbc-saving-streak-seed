package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;

/** The amount a withdrawal took from one deposit, retained for the later loyalty-bonus rule. */
public record RecordedWithdrawalAllocation(long depositId, BigDecimal amount) {
}
