package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Money that left a savings account, as much of it as somebody counting a stretch of time needs: how
 * much was taken out, and when.
 *
 * <p>{@link DepositLanded} in the other direction, and deliberately the same shape. A week of saving
 * is what went in less what came back out, and a caller counting one has to be able to count the
 * other the same way.
 *
 * <p>The amount is quoted to the cent, so a caller can add these up or write one out without
 * deciding again how many places money has.
 *
 * <p>Which deposits it drew down is not here, and that is the point of the shape: a week is judged
 * on money leaving, not on where in the ledger it was taken from. Whoever needs the allocations asks
 * {@link WithdrawalsService#withdrawalsFrom}.
 */
public record WithdrawalMade(Long id, BigDecimal amount, Instant withdrawnAt) {
}
