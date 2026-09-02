package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.dataroots.savingstreak.deposits.RecordedWithdrawalAllocation;
import io.dataroots.savingstreak.deposits.RecordedWithdrawal;

/** A withdrawal as the API returns it, including where the money went and when. */
record WithdrawalResponse(Long id, BigDecimal amount, long toCurrentAccountId, Instant withdrawnAt,
                          List<RecordedWithdrawalAllocation> allocations) {

    static WithdrawalResponse of(RecordedWithdrawal withdrawal) {
        return new WithdrawalResponse(withdrawal.id(), withdrawal.amount(), withdrawal.toCurrentAccountId(),
                withdrawal.withdrawnAt(), withdrawal.allocations());
    }
}
