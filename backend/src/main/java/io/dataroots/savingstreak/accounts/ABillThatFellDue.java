package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One date a recurring bill fell due, as the rest of the application sees it: the day it was owed,
 * the moment it was settled, how late that was, what was asked for and whether it was paid.
 *
 * <p>Public, unlike the row it is read from, because it is what the web layer names. The row itself
 * stays inside this module; a module that hands out its entities to be read elsewhere has no
 * boundary left to speak of. The same bargain {@link ADeclaredBill} strikes for the declaration.
 *
 * <p><strong>{@code dueOn} and {@code settledAt} are two different facts and both are here.</strong>
 * The first is the day the money was owed, the second is when the application got round to it, and
 * on a wound clock or after downtime they are different days. A customer coming back to an
 * application that was down is owed the gap between them rather than a balance that is simply
 * different from the one they remember.
 *
 * <p><strong>{@code daysLate} is that gap, said rather than left to be worked out.</strong> It is
 * derived here from the other two rather than stored beside them, because a third copy of one
 * subtraction is a third thing that can disagree — the reading {@code RecordedOccurrence} gives of
 * its own. Counted in whole days through the calendar of the zone this application counts every
 * calendar thing in, so that a rent owed on the first and taken on the second is one day late rather
 * than nought or two depending on the hour the job ran at.
 *
 * <p><strong>{@code amount} is what the date asked for and not what moved</strong>, and it is the
 * same figure on both outcomes. On a paid date the two are the same number; on an unpaid one the
 * amount is what the customer still owes, which is the whole reason the row is worth keeping — a
 * nought there would record that a bill of nine hundred euros was presented for nothing.
 *
 * <p>The amount is quoted to the cent on the way out, for the reason {@code RecordedOccurrence}
 * gives: it has been through SQLite, which has no decimal type and holds an amount as a float, so
 * 900.00 comes back as 900.0 and would reach a page as a number rather than as money.
 */
public record ABillThatFellDue(Long id, long billId, long currentAccountId, LocalDate dueOn,
                               Instant settledAt, long daysLate, BillOutcome outcome,
                               BigDecimal amount) {

    /** The row as the rest of the application reads it. The one place the entity becomes a record. */
    static ABillThatFellDue of(BillOccurrence occurrence, BigDecimal amountToTheCent) {
        return new ABillThatFellDue(
                occurrence.getId(),
                occurrence.getRecurringBillId(),
                occurrence.getCurrentAccountId(),
                occurrence.getDueOn(),
                occurrence.getSettledAt(),
                howLate(occurrence.getDueOn(), occurrence.getSettledAt()),
                occurrence.getOutcome(),
                amountToTheCent);
    }

    /**
     * How many whole days passed between the day the money was owed and the day it was settled on,
     * and never less than nothing: a date settled on the morning it fell due is nought days late,
     * and a clock nobody could wind backwards makes the negative case unreachable.
     *
     * <p>The subtraction itself is {@link AnArrear#daysLateOn}, asked here about the moment the date
     * was settled at and asked there about now. One subtraction rather than two, because two places
     * deciding how late something is are two answers to the one figure a customer reads.
     */
    private static long howLate(LocalDate dueOn, Instant settledAt) {
        return AnArrear.daysLateOn(dueOn, settledAt);
    }
}
