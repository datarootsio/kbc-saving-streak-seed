package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

/**
 * One date a bill fell due on, as a ledger of everything that moved reads it: which bill, what it is
 * called, the day it was owed from, the moment it was settled, how late that was, what it asked for
 * and whether it was paid.
 *
 * <p><strong>The third read of the same rows, and it is not either of the other two.</strong>
 * {@link ABillThatFellDue} answers "what became of <em>this</em> bill" one bill at a time, and is
 * read with the bill's name already on the screen above it. {@link AnArrear} answers "what do I
 * still owe" and drops a date the moment it is settled. This answers "where did my money go", across
 * every account a customer holds and whether or not the money moved — so it carries the name, like
 * an arrear does, because a row in a list of rents and phone bills and deposits that said only
 * {@code bill 4} would be a row nobody can read.
 *
 * <p><strong>{@code settledAt} is what this record is ordered by, and that is the whole of its
 * sorting rule.</strong> Every other movement in the ledger happened at a single moment; a bill has
 * two dates, and an arrear owed in March and settled in June belongs at its June settlement rather
 * than back in March where a reader would find it under a balance it never moved. The day it was
 * owed from travels beside it so that the row still reads "rent, due 1 March, taken 14 June" — the
 * history does not rewrite itself, it just sits where the money actually left.
 *
 * <p><strong>An unpaid date is in here too, and that is the point of it.</strong> A ledger that
 * recorded only successes is exactly how a customer ends up confused about a balance, and the answer
 * they need — that the money never moved — is a row rather than a silence. Its {@code settledAt} is
 * the moment it was presented and refused, which is where it belongs in the list: that is the night
 * the customer would otherwise be looking for the missing rent in.
 *
 * <p>{@code amount} is what the date asked for, on both outcomes, for the reason
 * {@link ABillThatFellDue} gives at length: on an unpaid date it is what is still owed, and a nought
 * there would record that a rent of nine hundred euros was presented for nothing.
 *
 * <p>Public, unlike the row it is read from, because it is what the web layer names. The same
 * bargain every other read model in this module strikes.
 */
public record ABillOnTheLedger(long occurrenceId, long billId, long currentAccountId,
                               String billName, LocalDate dueOn, Instant settledAt, long daysLate,
                               BillOutcome outcome, BigDecimal amount) {

    /** The row as the rest of the application reads it. The one place the entity becomes a record. */
    static ABillOnTheLedger of(BillOccurrence presented, String billName) {
        return new ABillOnTheLedger(
                presented.getId(),
                presented.getRecurringBillId(),
                presented.getCurrentAccountId(),
                billName,
                presented.getDueOn(),
                presented.getSettledAt(),
                // The one subtraction, asked about the moment the date was settled at rather than
                // about now: this is a line of history, so how late it was is a fact about the day
                // the money left and not a figure that grows while somebody reads the page.
                AnArrear.daysLateOn(presented.getDueOn(), presented.getSettledAt()),
                presented.getOutcome(),
                // Quoted to the cent here, where the amount leaves the module, for the reason the
                // money-movement ledger quotes its own: SQLite has no decimal type and hands EUR
                // 950.00 back as 950.0, and one list mixing the two reads as two kinds of money.
                AmountOfMoney.quotedToTheCent(presented.getAmount()));
    }
}
