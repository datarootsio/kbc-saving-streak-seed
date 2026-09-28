package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

/**
 * One due date that was presented to a current account and refused: which bill, what it was called,
 * the day it was owed, what it asked for, what the account actually held, the night it fell short
 * on, and — if the debt has since been cleared — the moment it was cleared.
 *
 * <p><strong>A record of a failure rather than of a debt.</strong> {@link AnArrear} answers "what do
 * I still owe", and drops a date the moment it is settled; this answers "which dates have ever gone
 * unpaid, and for how long each of them was carried", and a settled one stays in it with its
 * {@code clearedAt} filled in. The two reads are deliberately not one: a still-owed panel that
 * listed debts a customer paid off in March would be useless, and a raiser deciding whether the
 * arrears have <em>crossed</em> a threshold rather than merely stood over it cannot work from a list
 * that forgets everything that has been put right.
 *
 * <p>The name is carried rather than the identifier alone, because the notification built out of
 * this is read by a person: "Rent could not be paid" is something they can act on and "bill 4 could
 * not be paid" is not. It is the name the bill has now, which for a bill renamed since is the name
 * the customer would recognise today.
 *
 * <p>{@code balanceThatFellShort} may be null on a row written before the application recorded it,
 * and such rows are left out of this listing altogether — {@link BillOccurrence} says why.
 */
public record ABillThatCouldNotBePaid(long occurrenceId, long billId, long currentAccountId,
                                      String billName, LocalDate dueOn, BigDecimal amount,
                                      BigDecimal balanceThatFellShort, Instant fellShortAt,
                                      Instant clearedAt) {

    static ABillThatCouldNotBePaid of(BillOccurrence occurrence, String billName) {
        return new ABillThatCouldNotBePaid(
                occurrence.getId(),
                occurrence.getRecurringBillId(),
                occurrence.getCurrentAccountId(),
                billName,
                occurrence.getDueOn(),
                AmountOfMoney.quotedToTheCent(occurrence.getAmount()),
                occurrence.getBalanceThatFellShort() == null
                        ? null
                        : AmountOfMoney.quotedToTheCent(occurrence.getBalanceThatFellShort()),
                occurrence.getFellShortAt(),
                occurrence.getOutcome() == BillOutcome.PAID ? occurrence.getSettledAt() : null);
    }

    /** Whether this hole has since been closed, which is what ends the stretch it was carried for. */
    public boolean isCleared() {
        return clearedAt != null;
    }
}
