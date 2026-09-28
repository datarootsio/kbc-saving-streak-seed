package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * One date a bill fell due on and was not paid, and still has not been: which bill, the day it was
 * owed from, how late that is by now, and what is still owed for it.
 *
 * <p>The half of {@link ABillThatFellDue} that is still open, read as a claim on the account rather
 * than as a line of history. A bill's history answers "what became of my rent in March"; this
 * answers "what do I owe, and how bad is it", which is the one panel this feature exists to put in
 * front of a customer.
 *
 * <p><strong>{@code daysLate} is counted from the day it was owed to now, not to when it was last
 * presented.</strong> That is the difference between this record and the history's, and it is
 * deliberate: a date that was presented and refused in March carries a {@code settledAt} of the
 * morning it was refused, so lateness measured against it would freeze at nought and an arrear
 * carried for two months would read as no later than one carried overnight. What a customer needs to
 * know is how long they have been carrying it, which is a figure about today.
 *
 * <p><strong>{@code amount} is what was originally due and never a cent more.</strong> No interest,
 * no fee, no charge of any kind is ever added to it — the accumulation of arrears is the whole of
 * the consequence, and a fee would make the lesson about banks instead of about the customer's own
 * choices. A rent that went up in April does not make March's arrear bigger either: the figure is
 * the one the row was written with.
 *
 * <p>{@code billName} is carried rather than left to be looked up, because a list of what is owed
 * with nothing but identifiers on it is a list nobody can act on. It is the bill's name as it reads
 * now, which is what the customer recognises.
 *
 * <p>Public, unlike the row it is read from: it is what the web layer names. The same bargain
 * {@link ADeclaredBill} and {@link ABillThatFellDue} strike.
 */
public record AnArrear(Long id, long billId, long currentAccountId, String billName,
                       LocalDate dueOn, long daysLate, BigDecimal amount) {

    /** The row as the rest of the application reads it, aged against the moment it is read at. */
    static AnArrear of(BillOccurrence unpaid, String billName, BigDecimal amountToTheCent,
                       Instant asAt) {
        return new AnArrear(
                unpaid.getId(),
                unpaid.getRecurringBillId(),
                unpaid.getCurrentAccountId(),
                billName,
                unpaid.getDueOn(),
                daysLateOn(unpaid.getDueOn(), asAt),
                amountToTheCent);
    }

    /**
     * How many whole days passed between the day the money was owed and the given moment, and never
     * less than nothing.
     *
     * <p>The one place this application subtracts a due date from a moment. {@link ABillThatFellDue}
     * asks it about the moment a date was settled at and this record asks it about now, and a second
     * copy of the same subtraction would be a second answer to "how late is this" — which is exactly
     * the figure a customer is reading off the screen.
     *
     * <p>Counted in whole days through the calendar of the zone this application counts every
     * calendar thing in, so that a rent owed on the first and looked at on the second is one day
     * late rather than nought or two depending on the hour.
     */
    static long daysLateOn(LocalDate dueOn, Instant asAt) {
        LocalDate on = asAt.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
        return Math.max(0, ChronoUnit.DAYS.between(dueOn, on));
    }
}
