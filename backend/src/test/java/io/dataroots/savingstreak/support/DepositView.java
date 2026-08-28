package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A deposit as the API reports it: how much moved, what it earned, and when. Shared by every test
 * that reads one back, for the same reason as {@link BalancesView} — three copies of the shape can
 * drift into disagreeing about it, and then one of them is testing a contract nobody serves.
 */
public record DepositView(Long id, BigDecimal amount, long pointsEarned, Instant depositedAt) {
}
