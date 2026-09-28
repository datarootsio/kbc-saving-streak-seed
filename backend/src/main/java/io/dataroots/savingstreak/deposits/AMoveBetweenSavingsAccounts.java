package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A move that happened, as the rest of the application sees it: which two accounts, how much, the
 * two rows it became, what arrived already paid for, and when.
 *
 * <p><strong>One statement about one event, although the ledger holds two rows.</strong> A move
 * writes money out of one savings account and into another, and both rows are needed because a
 * balance at each end is summed from the rows in it — but the customer did one thing, and this is
 * what they did. The two identifiers are in here rather than hidden because a movement is the one
 * thing in this application a person may want to point at afterwards, and the record of money that
 * moved reports the move under the row that left.
 *
 * <p><strong>{@code earnedOnCarriedAcross} is the figure that explains why a move earned
 * nothing.</strong> It is what the rows the money left had already been paid for, plus every euro
 * of interest among them, which the bank added and which has never earned a point here. Written
 * onto the arriving row, it leaves the most the customer has ever saved exactly where it was — so
 * the arriving euros earn nothing they have already earned, and nothing they were never owed. Sent
 * out because a customer whose move earned no points is owed the reason, and this is it.
 *
 * <p><strong>What is deliberately not in here is what the move cost in loyalty.</strong> That is
 * the Loyalty module's figure, it is quoted <em>before</em> the move rather than after it, and a
 * second copy of it assembled here would be this module having an opinion about what an anniversary
 * pays. What this record says about the clock is the moment — which is the day the arriving money's
 * twelve months start from, and the whole of what a move costs.
 *
 * <p>Public because it crosses the boundary out of this module.
 */
public record AMoveBetweenSavingsAccounts(long fromSavingsAccountId, long toSavingsAccountId,
                                          long customerId, BigDecimal amount,
                                          BigDecimal earnedOnCarriedAcross,
                                          long withdrawalId, long depositId, Instant movedAt) {
}
