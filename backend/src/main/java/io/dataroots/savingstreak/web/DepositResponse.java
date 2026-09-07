package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.deposits.RecordedDeposit;

/**
 * A deposit as the customer sees it: how much moved, what it earned and how, the rate it was paid
 * at, and when it happened.
 *
 * <p>{@code pointsEarned} is the total credited, which is what it has always been: every deposit
 * before this scheme existed was paid at the ordinary rate, so the figure is unchanged for all of
 * them. {@code basePoints} and {@code streakBonusPoints} always sum to it, so a customer can check
 * the multiplication rather than take the total on trust.
 */
record DepositResponse(Long id, BigDecimal amount, long pointsEarned, long basePoints,
                       long streakBonusPoints, BigDecimal multiplierApplied, Instant depositedAt) {

    static DepositResponse of(RecordedDeposit deposit) {
        return new DepositResponse(
                deposit.id(), deposit.amount(), deposit.pointsEarned(), deposit.basePoints(),
                deposit.streakBonusPoints(), deposit.multiplierApplied(), deposit.depositedAt());
    }
}
