package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A deposit that happened, as the rest of the application sees it: how much moved, what it earned and
 * how, the rate it was paid at, and when. The stored record stays inside the module; this is a
 * statement about what took place.
 *
 * <p>{@code pointsEarned} is everything the deposit earned however it earned it, which is what it has
 * always meant: before there were streaks every deposit was paid at the ordinary rate, so the figure
 * is unchanged for every deposit made before this scheme existed. {@code basePoints} and
 * {@code streakBonusPoints} are what it is made of, and they always sum to it — a customer looking at
 * nine points against a seven-euro deposit can see where the nine came from.
 *
 * <p>{@code multiplierApplied} is the rate this deposit was in fact paid at, not the rate the account
 * is on today. It was decided at the moment the money moved and is never worked out again, so a
 * deposit keeps explaining itself after the ladder changes and after the run it was paid on has
 * lapsed.
 */
public record RecordedDeposit(Long id, BigDecimal amount, long pointsEarned, long basePoints,
                              long streakBonusPoints, BigDecimal multiplierApplied,
                              Instant depositedAt) {
}
