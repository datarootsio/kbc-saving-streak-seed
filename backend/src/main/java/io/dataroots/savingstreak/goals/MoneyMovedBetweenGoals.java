package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * One move of money from one claim on a savings account's balance to another: out of a goal or out
 * of what no goal has claimed, into a goal or back into what no goal has claimed, an amount, and the
 * moment it happened.
 *
 * <p><strong>No money moves.</strong> The savings balance is the sum of what the deposits in the
 * account still hold and nothing here touches it; a row of this table is bookkeeping over a figure
 * Deposits already derives, saying which part of it somebody has spoken for.
 *
 * <p><strong>A goal's allocation is the sum of these rows and is not stored anywhere.</strong> The
 * alternative is a column on the goal, which is a second figure that has to agree with this ledger
 * and would eventually stop agreeing — the argument {@code WeekAndStreakDerivation} makes about
 * weeks and streaks, and the same one the balance itself is derived under. The ledger is also what
 * makes "what did that withdrawal do to my holiday" a thing the account can answer rather than
 * guess: the moves are there to read back.
 *
 * <p><strong>Append-only.</strong> Nothing updates or deletes a row. Money freed from a goal is a
 * new row in the other direction, and a goal given up on while holding money is a new row returning
 * the whole of it — which is why an allocation can go down without any record of what was true
 * before going missing.
 *
 * <p>{@code outOfGoalId} and {@code intoGoalId} are each null to mean <em>unallocated</em>: the part
 * of the balance no goal has claimed. It is not an entity and has no identifier of its own, because
 * it is not a thing — it is what is left, {@code balance − allocated}, derived on every read. Both
 * being null would be a move from nothing to nothing, and {@link GoalsService} refuses it rather
 * than writing a row that means nothing.
 *
 * <p>Package-private, like every other row in this module: what it keeps, and how, is nobody else's
 * business. {@link RecordedGoalMove} is what leaves.
 */
@Entity
class MoneyMovedBetweenGoals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long savingsAccountId;

    /** The goal the money came out of, and null for the part of the balance no goal had claimed. */
    private Long outOfGoalId;

    /** The goal the money went into, and null for it going back to no goal at all. */
    private Long intoGoalId;

    private BigDecimal amount;

    private Instant movedAt;

    protected MoneyMovedBetweenGoals() {
        // for JPA
    }

    MoneyMovedBetweenGoals(long savingsAccountId, Long outOfGoalId, Long intoGoalId,
                           BigDecimal amount, Instant movedAt) {
        this.savingsAccountId = savingsAccountId;
        this.outOfGoalId = outOfGoalId;
        this.intoGoalId = intoGoalId;
        this.amount = amount;
        this.movedAt = movedAt;
    }

    Long getId() {
        return id;
    }

    long getSavingsAccountId() {
        return savingsAccountId;
    }

    Long getOutOfGoalId() {
        return outOfGoalId;
    }

    Long getIntoGoalId() {
        return intoGoalId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Instant getMovedAt() {
        return movedAt;
    }
}
