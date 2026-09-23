package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A deposit as the API reports it: how much moved, what it earned and how, the rate it was paid at,
 * when, and when it next pays. Shared by every test that reads one back, for the same reason as
 * {@link BalancesView} — three copies of the shape can drift into disagreeing about it, and then one
 * of them is testing a contract nobody serves.
 *
 * <p>{@code pointsEarned} is the total credited and {@code basePoints} plus {@code streakBonusPoints}
 * plus {@code loyaltyBonusPoints} is what it is made of; the three always sum to it. The total
 * therefore grows on each of the deposit's anniversaries, which is what a recurring reward means,
 * while what the deposit earned when it landed — the base points and the streak bonus — never
 * changes again.
 *
 * <p>{@code nextAnniversaryOn} and {@code nextAnniversaryPoints} are what the deposit is going to
 * pay rather than what it has paid: the day of its next anniversary and what that day is worth at
 * what the deposit holds now. Both are null together for a deposit that has been emptied, because
 * there is no anniversary left for money that has gone to reach; a deposit holding under ten euros
 * has a date with nothing to be earned on it, which is a different statement and is said as one.
 * The day can be one just gone: an anniversary that has fallen and is waiting on the overnight sweep
 * is the one still reported as next, because it is the one that pays next.
 */
public record DepositView(Long id, BigDecimal amount, long pointsEarned, long basePoints,
                          long streakBonusPoints, long loyaltyBonusPoints,
                          BigDecimal multiplierApplied, Instant depositedAt,
                          LocalDate nextAnniversaryOn, Long nextAnniversaryPoints) {
}
