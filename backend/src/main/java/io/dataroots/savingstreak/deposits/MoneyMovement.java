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
 * gap in the record. Nothing here is null.
 *
 * <p>The identifier is the deposit's or the withdrawal's, and the two are numbered separately: it is
 * unique within a direction and not across the ledger, which is why anything keying rows off it has
 * to key off the pair.
 *
 * <p>What was put in rather than what is left of it. A deposit drawn down by a later withdrawal
 * still moved the amount it moved, and both movements are in the list — showing the remainder here
 * would be the same euros subtracted twice.
 */
public record MoneyMovement(MoneyMovementDirection direction, long id, long savingsAccountId,
                            long currentAccountId, BigDecimal amount, long pointsEarned,
                            Instant movedAt) {
}
