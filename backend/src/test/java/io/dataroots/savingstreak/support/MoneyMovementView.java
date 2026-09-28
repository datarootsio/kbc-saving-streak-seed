package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One entry in the money-movement ledger as a test reads it, which is exactly as the API answers it.
 *
 * <p>The direction is read as the word the API sends rather than mapped onto an enum of the test's
 * own, so that a rename in the backend fails a test instead of being quietly translated back. The
 * same goes for the outcome on a bill row.
 *
 * <p>{@code automatic} is the API's own answer to "did a saving rule make this, or did somebody
 * press a button" — not something a test works out from the amounts, which would be the test
 * agreeing with itself rather than reading what the page reads.
 *
 * <p><strong>A month's interest is a fifth kind and the one with no current account at all.</strong>
 * Nothing was debited to pay it, so {@code currentAccountId} is null on it and on nothing else —
 * which is why it is read as a boxed value here. A test asserting that interest is in the ledger is
 * asserting that the euros the bank added are in the record of euros, beside the ones the customer
 * moved and told apart from them by the direction.
 *
 * <p><strong>One record for all five kinds, with the components the other kinds do not have left
 * null.</strong> A bill row carries a name, the day it was owed from, whether it was paid and how
 * late it was, and carries no savings account at all — money leaving a current account for the rent
 * never goes near one. A spend carries its own name, the split it is filed under and, if it has been
 * corrected since, the moment that happened. A deposit and a withdrawal carry none of it. Reading
 * them through one record is what lets a test assert that the four arrive in one list, which is the
 * claim the ledger makes.
 *
 * <p><strong>A move between two of one customer's own savings accounts is a sixth kind, and the
 * only one naming two savings accounts.</strong> It is one entry rather than two although the
 * backend writes a row at each end, which is exactly what a test reading this list is asserting:
 * the customer did one thing. {@code savingsAccountId} is where the euros left from and
 * {@code toSavingsAccountId} is where they arrived, and {@code currentAccountId} is null on it
 * because no everyday account was touched at either end.
 *
 * <p>{@code correctedAt} is null on a spend nobody has corrected, and that null is the whole of what
 * a test asserting the marker is asserting: the ledger says plainly which spends were not right the
 * first time rather than quietly showing something different from what it showed yesterday.
 */
public record MoneyMovementView(String direction, long id, Long savingsAccountId, Long currentAccountId,
                                BigDecimal amount, long pointsEarned, Instant movedAt,
                                boolean automatic, String billName, LocalDate dueOn, String outcome,
                                Long daysLate, String spendName, Instant correctedAt,
                                List<SpendPartView> parts, Long toSavingsAccountId) {
}
