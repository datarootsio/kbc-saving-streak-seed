package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A deposit as the API reports it: how much moved, what it earned and how, the rate it was paid at,
 * and when. Shared by every test that reads one back, for the same reason as {@link BalancesView} —
 * three copies of the shape can drift into disagreeing about it, and then one of them is testing a
 * contract nobody serves.
 *
 * <p>{@code pointsEarned} is the total credited and {@code basePoints} plus {@code streakBonusPoints}
 * is what it is made of; the two always sum to it.
 */
public record DepositView(Long id, BigDecimal amount, long pointsEarned, long basePoints,
                          long streakBonusPoints, BigDecimal multiplierApplied, Instant depositedAt) {
}
