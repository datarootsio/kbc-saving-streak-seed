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
 * is unchanged for every deposit made before this scheme existed. {@code basePoints},
 * {@code streakBonusPoints} and {@code loyaltyBonusPoints} are what it is made of, and the three
 * always sum to it — a customer looking at nine points against a seven-euro deposit can see where
 * the nine came from.
 *
 * <p>{@code loyaltyBonusPoints} is every anniversary this deposit has ever been paid, added up, and
 * it is the one figure here that grows after the money has moved: a deposit left alone is paid again
 * every twelve months, so what it has been worth to the customer altogether goes up while what it
 * earned <em>when it landed</em> stays exactly what it was. That is what a recurring reward means,
 * and it is why the total is asked of the points ledger on every read rather than fixed at the
 * moment the euros moved.
 *
 * <p>{@code multiplierApplied} is the rate this deposit was in fact paid at, not the rate the account
 * is on today. It was decided at the moment the money moved and is never worked out again, so a
 * deposit keeps explaining itself after the ladder changes and after the run it was paid on has
 * lapsed.
 */
public record RecordedDeposit(Long id, BigDecimal amount, long pointsEarned, long basePoints,
                              long streakBonusPoints, long loyaltyBonusPoints,
                              BigDecimal multiplierApplied, Instant depositedAt) {
}
