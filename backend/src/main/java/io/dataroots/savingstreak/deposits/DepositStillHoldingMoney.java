package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A deposit that still has money in it: which deposit, whose it is, which account it is sitting in,
 * how much of it is left, and when it landed.
 *
 * <p>A fact about a deposit and nothing more. Whoever asks for these has a rule that turns on the
 * age of somebody's money and on how much of it is still there — the loyalty bonus is the first —
 * and Deposits answers the fact while the rule stays where it belongs. This module's opinion about
 * points is, as ever, none.
 *
 * <p>A sibling of {@link DepositLanded} rather than a change to it, because the two answer different
 * questions and neither can be widened into the other honestly. {@code DepositLanded} carries what
 * was paid in and says nothing about whose it was, which is right for counting a week of saving; a
 * caller judging money that has stayed put needs the customer whose pot a reward would go into and
 * the amount that is actually still there.
 *
 * <p>The remaining amount is quoted to the cent, so a caller can write it into a log line or work a
 * figure out from it without deciding again how many places money has.
 *
 * <p><strong>The savings account is here because a rule about money that has stayed put is a rule
 * about where it stayed.</strong> An anniversary is paid at the rate of the product the money is
 * sitting in, and the account is the only address a rule outside this module can ask that question
 * at — the customer is not, because one customer can hold two accounts on two products, and the
 * deposit's own identifier is not, because nothing outside Deposits can turn one into an account.
 * It sits beside the customer rather than replacing them: what an anniversary pays is decided by
 * the account and credited to the person, and both ends of that sentence are needed at once.
 */
public record DepositStillHoldingMoney(Long id, long customerId, long savingsAccountId,
                                       BigDecimal remainingAmount, Instant depositedAt) {
}
