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
     * The other half of the per-reason nullability, and the reason there are two constructors rather
     * than one taking everything: a caller that could pass both an amount and a deposit could
     * construct a row belonging to neither family. Each of these two fills exactly the columns one
     * family has and leaves the other family's null.
     */
    private Notification(long customerId, NotificationReason reason, long savingsAccountId,
                         long depositId, LocalDate occursOn, long points, Instant raisedAt) {
        this.customerId = customerId;
        this.reason = reason;
        this.savingsAccountId = savingsAccountId;
        this.depositId = depositId;
        this.occursOn = occursOn;
        this.points = points;
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

    /**
     * A deposit's next anniversary is near enough to be worth saying, it is worth at least one
     * point, and the deposit stands behind an older one still holding money — so the money the
     * anniversary would pay on is not what the next withdrawal would reach first.
     *
     * <p>The day and the points come from Loyalty exactly as they are: they are the same figures
     * the deposits table already shows, and nothing in this module works out what an anniversary is
     * worth.
     */
    static Notification anniversaryComingFor(long customerId, long savingsAccountId, long depositId,
                                             LocalDate on, long points, Instant raisedAt) {
        return new Notification(
                customerId, NotificationReason.LOYALTY_BONUS_ABOUT_TO_PAY, savingsAccountId,
                depositId, on, points, raisedAt);
    }

    /**
     * The same anniversary on the deposit that is first in line for the next withdrawal, which is
     * the oldest one in the account still holding money — so the euros this anniversary would be
     * paid on are exactly the euros a withdrawal would take.
     */
    static Notification anniversaryAtRiskFor(long customerId, long savingsAccountId, long depositId,
                                             LocalDate on, long points, Instant raisedAt) {
        return new Notification(
                customerId, NotificationReason.LOYALTY_BONUS_AT_RISK, savingsAccountId, depositId,
                on, points, raisedAt);
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

    /**
     * Marks this notification as looked at, as at the given moment, and answers whether that moment
     * is the one it now carries.
     *
     * <p>The notification decides, so that nothing outside can read one twice: one that has already
     * been read answers {@code false} and keeps the moment it was originally read at, which is what
     * makes a second call to mark a customer's notifications read a call that changes nothing. The
     * same shape {@code PointsCredit.expire} has, and for the same reason — a moment set once is a
     * record of when something happened, and overwriting it would turn it into a record of when it
     * was last asked about.
     *
     * <p>Answering whether it changed rather than answering nothing, because the count of what was
     * actually marked is what the caller logs: a line saying nine were marked when eight of them had
     * been read yesterday would be a line a reviewer could not check.
     */
    boolean read(Instant readAt) {
        if (this.readAt != null) {
            return false;
        }
        this.readAt = readAt;
        return true;
    }
}
