package be.kbc.savingstreak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The part of one deposit that is sitting still in a savings account, on its own recurring
 * 12-month clock. Every anniversary it survives pays a loyalty bonus; a withdrawal eats into
 * the principal oldest first, and whatever is left keeps the original clock.
 */
@Entity
@Table(name = "savings_position")
public class SavingsPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The deposit that opened this position. */
    @Column(name = "transfer_id", nullable = false)
    private Long transferId;

    /** The savings account the money is sitting in; a rebalance moves this. */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "principal_opened_cents", nullable = false)
    private long principalOpenedCents;

    @Column(name = "principal_left_cents", nullable = false)
    private long principalLeftCents;

    /** When the money landed. The anniversaries are counted from here, and never reset. */
    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "anniversaries_paid", nullable = false)
    private int anniversariesPaid;

    @Column(name = "loyalty_points_paid", nullable = false)
    private int loyaltyPointsPaid;

    protected SavingsPosition() {
        // for JPA
    }

    public SavingsPosition(Long transferId, Long accountId, long principalCents, Instant openedAt) {
        this(transferId, accountId, principalCents, openedAt, 0, 0);
    }

    private SavingsPosition(Long transferId, Long accountId, long principalCents, Instant openedAt,
                            int anniversariesPaid, int loyaltyPointsPaid) {
        this.transferId = transferId;
        this.accountId = accountId;
        this.principalOpenedCents = principalCents;
        this.principalLeftCents = principalCents;
        this.openedAt = openedAt;
        this.anniversariesPaid = anniversariesPaid;
        this.loyaltyPointsPaid = loyaltyPointsPaid;
    }

    /**
     * Takes up to {@code wanted} out of this position and returns what it gave. The clock is
     * untouched: what stays behind has still been there since {@link #getOpenedAt()}.
     */
    public long takeUpTo(long wanted) {
        long given = Math.min(wanted, principalLeftCents);
        principalLeftCents -= given;
        return given;
    }

    /** Splits off part of this position, keeping its clock, for a move to another account. */
    public SavingsPosition splitOff(long cents, Long newAccountId) {
        long moved = takeUpTo(cents);
        SavingsPosition part = new SavingsPosition(transferId, newAccountId, moved, openedAt,
                anniversariesPaid, 0);
        part.principalOpenedCents = moved;
        return part;
    }

    public void recordAnniversary(int points) {
        anniversariesPaid++;
        loyaltyPointsPaid += points;
    }

    public void moveTo(Long newAccountId) {
        this.accountId = newAccountId;
    }

    public boolean isOpen() {
        return principalLeftCents > 0;
    }

    public Long getId() {
        return id;
    }

    public Long getTransferId() {
        return transferId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public long getPrincipalOpenedCents() {
        return principalOpenedCents;
    }

    public long getPrincipalLeftCents() {
        return principalLeftCents;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public int getAnniversariesPaid() {
        return anniversariesPaid;
    }

    public int getLoyaltyPointsPaid() {
        return loyaltyPointsPaid;
    }
}
