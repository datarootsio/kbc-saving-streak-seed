package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A deposit that landed in a savings account, as much of it as somebody counting a stretch of time
 * needs: how much was paid in, and when.
 *
 * <p>The amount is quoted to the cent, so a caller can add these up or write one out without
 * deciding again how many places money has.
 *
 * <p>How much was paid in, never how much of it is still there. A withdrawal draws a deposit down
 * and does not un-happen it, so the two figures answer different questions and a caller asking what
 * landed in a week is asking this one. Whoever wants the money that is actually in the account asks
 * {@link DepositsService#moneyBalanceOf} instead.
 *
 * <p>Without what it earned, which is the difference between this and {@link RecordedDeposit}: a
 * caller working out what a stretch of time took in has no use for the points and would cost a
 * lookup in the points ledger to be handed them.
 */
public record DepositLanded(Long id, BigDecimal amount, Instant depositedAt) {
}
