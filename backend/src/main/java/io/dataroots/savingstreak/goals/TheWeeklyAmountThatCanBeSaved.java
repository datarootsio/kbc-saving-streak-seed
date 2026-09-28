package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * The most a customer says they can put away in a week, on one savings account.
 *
 * <p><strong>Declared, never derived.</strong> It would be easy to average the last six weeks of
 * deposits and call that a capacity, and every figure the planning engine went on to quote would
 * then be a function of what the customer happened to do recently — unexplainable to them, and
 * untestable without staging six weeks of deposits first. This is a sentence the customer said about
 * themselves. Suggesting one from their history is a good later feature and would be a suggestion,
 * not this row.
 *
 * <p><strong>One row per savings account, and none until one is declared.</strong> Absence is the
 * honest state and it is kept as absence rather than as a zero: a zero is a customer saying they can
 * save nothing, which is a different sentence from their not having said anything, and the planning
 * outputs report that capacity is not set rather than quoting a number nobody gave them. The unique
 * constraint is what makes "one row" a fact about the table rather than a habit of the service.
 *
 * <p>Changing it replaces the figure rather than appending to a history. Unlike
 * {@link MoneyMovedBetweenGoals}, nothing is derived by summing this: what matters is what the
 * customer can save now, and a ledger of what they used to think would be a second thing to read
 * with no question that asks for it. {@code declaredAt} is when the figure that is in force was
 * declared, so that a plan can say how old the assumption behind it is.
 *
 * <p>Package-private, like every row in this module. {@link SavingCapacityOnAnAccount} is what
 * leaves.
 */
@Entity
class TheWeeklyAmountThatCanBeSaved {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The account this is the capacity of, at most once.
     *
     * <p>Unique because two rows for one account would be two answers to a question that has one,
     * and whichever the read happened to return would be the capacity — a race nobody would see
     * until a plan quoted the wrong figure.
     */
    @Column(unique = true)
    private long savingsAccountId;

    private BigDecimal weeklyAmount;

    /** When the figure now in force was declared, replaced along with it. */
    private Instant declaredAt;

    protected TheWeeklyAmountThatCanBeSaved() {
        // for JPA
    }

    TheWeeklyAmountThatCanBeSaved(long savingsAccountId, BigDecimal weeklyAmount, Instant declaredAt) {
        this.savingsAccountId = savingsAccountId;
        this.weeklyAmount = weeklyAmount;
        this.declaredAt = declaredAt;
    }

    long getSavingsAccountId() {
        return savingsAccountId;
    }

    BigDecimal getWeeklyAmount() {
        return weeklyAmount;
    }

    Instant getDeclaredAt() {
        return declaredAt;
    }

    /**
     * Says it again, differently: the new figure replaces the old one and takes the moment with it.
     *
     * <p>Both together, never one without the other. A figure whose moment was left behind would
     * read as an assumption made weeks ago that the customer has in fact just confirmed.
     */
    void redeclare(BigDecimal weeklyAmount, Instant declaredAt) {
        this.weeklyAmount = weeklyAmount;
        this.declaredAt = declaredAt;
    }
}
