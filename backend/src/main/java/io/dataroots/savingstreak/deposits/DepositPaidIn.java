package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A payment into a savings account: which deposit, whose money it was, how much of it went in, what
 * it has earned, and when it landed.
 *
 * <p><strong>What went in rather than what is left of it</strong>, which is the whole reason this is
 * not {@link DepositStillHoldingMoney}. The two are siblings and neither can be widened into the
 * other honestly: a caller asking what somebody has <em>paid in</em> is asking about history, and
 * history does not move when a later withdrawal draws the deposit down. A caller asking what is
 * still theirs is asking about money that is actually there. An account where the two differ is an
 * account money has left, and reporting either figure for both questions would be the difference
 * quietly disappearing off a screen.
 *
 * <p>Every deposit into the account, including deposits drawn down to nothing, for the same reason:
 * a contribution that has since been spent was still a contribution, and the points it earned are
 * still in the pot of whoever made it.
 *
 * <p><strong>The points are asked of the Points module and carried here</strong>, the arrangement
 * {@link MoneyMovement} already settles: the money moving and the points being earned are one event,
 * and one module owning what it earned is what stops the two from ever disagreeing. It is everything
 * the deposit has earned however it earned it — the euros, the run of weeks and every anniversary it
 * has been paid since — and nothing that was not earned by a deposit, so points somebody was given
 * are not in it.
 *
 * <p>The customer is here because whose a deposit was is the question this record exists to answer.
 * It is the one fact that turns a list of payments into an account into a statement about who paid
 * what, which is what a pot shared by two people is read on — and it was already written on every
 * deposit, so nothing new is recorded to answer it.
 *
 * <p><strong>And where it came from</strong>, which is the other fact already on the row and the one
 * a shared pot needs when it ends. Closing a pot returns each member their own remaining euros to
 * the current account their most recent contribution came from, because that is the account they
 * have shown this application they pay from — and asking every member to name one would turn the
 * single act of closing into a form per person. Whose an account is is not reported here and does
 * not have to be: it is the account this very deposit came out of, so it was that customer's at the
 * moment the money moved.
 *
 * <p>The amount is quoted to the cent, so a caller can write it into a log line or add it up without
 * deciding again how many places money has.
 */
public record DepositPaidIn(Long id, long customerId, long fromCurrentAccountId, BigDecimal amount,
                            long pointsEarned, Instant depositedAt) {
}
