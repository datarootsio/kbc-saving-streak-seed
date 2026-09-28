package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One movement of money across the boundary between an everyday account and savings, as a ledger
 * reports it: which way it went, how much, between which two accounts, when, and what it earned.
 *
 * <p>One shape for both kinds, which is the point of it. A deposit and a withdrawal are the same
 * event seen from opposite sides — an amount, two accounts, a moment — and a customer reading back
 * over what they have done with their money is reading one story rather than two lists they have to
 * interleave by eye. What differs between the two is carried in {@link #direction} rather than in
 * the fields, so nothing reading this list has to know which kind it is holding in order to render
 * it.
 *
 * <p>{@code pointsEarned} is zero for a withdrawal, which is what a withdrawal earns rather than a
 * gap in the record. It is zero on a month's interest too, and for a stronger reason: a euro the
 * bank added has never earned a point in this application and never will.
 *
 * <p>{@code currentAccountId} is the other end of the movement, and it is <em>nothing at all</em>
 * on a month's interest — the one null in this record, and a deliberate one. Interest has no other
 * end: no account was debited to pay it, so an identifier put there to keep the column full would
 * be a row claiming a current account it never touched, and a page drawing "from → to" would print
 * a lie. A reader tells the case by the direction, which is what the direction is for.
 *
 * <p>The identifier is the deposit's or the withdrawal's, and the two are numbered separately: it is
 * unique within a direction and not across the ledger, which is why anything keying rows off it has
 * to key off the pair.
 *
 * <p>What was put in rather than what is left of it. A deposit drawn down by a later withdrawal
 * still moved the amount it moved, and both movements are in the list — showing the remainder here
 * would be the same euros subtracted twice.
 *
 * <p><strong>{@code toSavingsAccountId} is the second savings account, and it is nothing at all on
 * every kind but one.</strong> A move between two of a customer's own savings accounts is the only
 * movement in this application with savings at both ends, and it is reported as <em>one</em> row
 * rather than as a withdrawal here and a deposit next door — because it is one thing the customer
 * did, and a reader handed two halves would have to pair them up by amount and moment to see that.
 * So the row names where the euros left from in {@link #savingsAccountId} and where they arrived
 * in here, and a page draws the arrow from the pair without consulting anything else. On every
 * other kind it is null, in the way {@link #currentAccountId} is null on a month's interest:
 * an identifier put there to keep the component full would be a row claiming an account it never
 * touched.
 */
public record MoneyMovement(MoneyMovementDirection direction, long id, long savingsAccountId,
                            Long currentAccountId, BigDecimal amount, long pointsEarned,
                            Instant movedAt, Long toSavingsAccountId) {

    /**
     * A movement with one savings account at one end of it, which is every kind but a move.
     *
     * <p>A factory rather than a null written out at four call sites, so that the one component
     * that does not apply to these kinds is absent by construction rather than by everybody
     * remembering. The move builds itself through {@link #between} and names both accounts.
     */
    static MoneyMovement of(MoneyMovementDirection direction, long id, long savingsAccountId,
                            Long currentAccountId, BigDecimal amount, long pointsEarned,
                            Instant movedAt) {
        return new MoneyMovement(direction, id, savingsAccountId, currentAccountId, amount,
                pointsEarned, movedAt, null);
    }

    /**
     * One move of money from one of a customer's savings accounts to another of their own.
     *
     * <p>No current account at either end and nothing earned: the euros never crossed the boundary
     * this ledger is about, and a euro that has been earned on once is not earned on again for
     * changing which of its holder's accounts it sits in.
     */
    static MoneyMovement between(long id, long fromSavingsAccountId, long toSavingsAccountId,
                                 BigDecimal amount, Instant movedAt) {
        return new MoneyMovement(MoneyMovementDirection.BETWEEN_SAVINGS_ACCOUNTS, id,
                fromSavingsAccountId, null, amount, 0, movedAt, toSavingsAccountId);
    }
}
