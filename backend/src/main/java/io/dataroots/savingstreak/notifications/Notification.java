package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A moment a rule decided was worth saying, written down.
 *
 * <p>Stored rather than derived, for the reason a paid loyalty bonus is stored: what happened is a
 * row, and a condition that has passed can no longer be worked out from the present. A balance that
 * reached EUR 1.000 last month and has since been drawn down cannot be asked about the month it was
 * over the rung; only the row it left behind can.
 *
 * <p>Nothing is ever deleted and there is no third state. Two states, unread and read, is one state
 * machine, and a training application whose whole point is showing a rule fire should not offer a
 * button that throws away the evidence that it did.
 *
 * <p>Which fields are filled is decided by the reason, and totally. A balance notification carries
 * the rung as its {@code amount} and no deposit, points or day; a notification about a deposit's
 * anniversary carries the deposit, the points and the day and no amount. The static factories are
 * the only way to make one, so an inconsistent combination — a balance row naming a deposit, say —
 * cannot be constructed. The columns the anniversary reasons need are here already and stay null
 * until the rule that fills them arrives: a column added later against a table SQLite has already
 * created is a schema change, and a null column nobody writes is not.
 *
 * <p>{@code readAt} is a moment rather than a flag, and it is set once. Re-reading an
 * already-read notification leaves the original moment alone, which is what makes marking-read
 * idempotent on the entity itself in the way {@code PointsCredit.expire} is.
 *
 * <p>Package-private, like every other thing in this module except the reason and the projection:
 * a row of this module's record is not something another module gets a handle on.
 */
@Entity
class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long customerId;

    /**
     * Written as its name rather than as its position, so that a row means the same thing after a
     * value is added to the enum — and so that a reviewer reading the table with a SQLite client
     * sees the rule rather than an ordinal to look up.
     */
    @Enumerated(EnumType.STRING)
    private NotificationReason reason;

    /**
     * The savings account the notification is about. Always present: a balance rung belongs to an
     * account, and so does the deposit an anniversary belongs to. It is what lets that account's own
     * page carry the notice that concerns it rather than hiding every warning behind one icon.
     */
    private long savingsAccountId;

    /** The deposit an anniversary belongs to, and null for a balance rung. */
    private Long depositId;

    /** The rung, for a balance reason, and null for an anniversary. */
    private BigDecimal amount;

    /** What an anniversary pays, and null for a balance rung. */
    private Long points;

    /** The day an anniversary falls on, and null for a balance rung. */
    private LocalDate occursOn;

    private Instant raisedAt;

    private Instant readAt;

    protected Notification() {
    }

    private Notification(long customerId, NotificationReason reason, long savingsAccountId,
                         BigDecimal amount, Instant raisedAt) {
        this.customerId = customerId;
        this.reason = reason;
        this.savingsAccountId = savingsAccountId;
        this.amount = amount;
        this.raisedAt = raisedAt;
    }

    /**
     * A savings balance now stands on a rung it was not last known to stand on, and the new rung is
     * the higher one. The amount is the rung it has landed on — one notification however many rungs
     * a single deposit vaulted, because what a customer wants told is where they are.
     */
    static Notification balanceRungReached(long customerId, long savingsAccountId, BigDecimal rung,
                                           Instant raisedAt) {
        return new Notification(
                customerId, NotificationReason.BALANCE_THRESHOLD_REACHED, savingsAccountId, rung,
                raisedAt);
    }

    /**
     * A savings balance no longer reaches a rung it did. The amount is the lowest rung it no longer
     * reaches, which is what makes the row readable backwards into the position the balance is in —
     * {@link NotificationsService} argues that out.
     */
    static Notification balanceRungLost(long customerId, long savingsAccountId, BigDecimal rung,
                                        Instant raisedAt) {
        return new Notification(
                customerId, NotificationReason.BALANCE_THRESHOLD_LOST, savingsAccountId, rung,
                raisedAt);
    }

    Long getId() {
        return id;
    }

    long getCustomerId() {
        return customerId;
    }

    NotificationReason getReason() {
        return reason;
    }

    long getSavingsAccountId() {
        return savingsAccountId;
    }

    Long getDepositId() {
        return depositId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Long getPoints() {
        return points;
    }

    LocalDate getOccursOn() {
        return occursOn;
    }

    Instant getRaisedAt() {
        return raisedAt;
    }

    Instant getReadAt() {
        return readAt;
    }
}
