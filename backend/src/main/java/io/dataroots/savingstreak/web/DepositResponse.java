package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.deposits.RecordedDeposit;

/** A deposit as the customer sees it: how much moved, what it earned, and when it happened. */
record DepositResponse(Long id, BigDecimal amount, long pointsEarned, Instant depositedAt) {

    static DepositResponse of(RecordedDeposit deposit) {
        return new DepositResponse(
                deposit.id(), deposit.amount(), deposit.pointsEarned(), deposit.depositedAt());
    }
}
