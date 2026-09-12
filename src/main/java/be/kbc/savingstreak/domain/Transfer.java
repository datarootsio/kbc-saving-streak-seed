package be.kbc.savingstreak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "transfer")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_account_id", nullable = false)
    private Long fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private Long toAccountId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    /**
     * The part of this transfer that was new savings, meaning above the savings peak, and so
     * the part that earned points. Nullable so the column can be added to an existing database.
     */
    @Column(name = "new_savings_cents")
    private Long newSavingsCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferDirection direction;

    @Column(name = "points_earned", nullable = false)
    private int pointsEarned;

    @Column(name = "streak_multiplier_bp", nullable = false)
    private int streakMultiplierBasisPoints;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transfer() {
        // for JPA
    }

    public Transfer(Long fromAccountId, Long toAccountId, long amountCents, TransferDirection direction,
                    long newSavingsCents, int pointsEarned, int streakMultiplierBasisPoints,
                    String description, Instant createdAt) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amountCents = amountCents;
        this.direction = direction;
        this.newSavingsCents = newSavingsCents;
        this.pointsEarned = pointsEarned;
        this.streakMultiplierBasisPoints = streakMultiplierBasisPoints;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getNewSavingsCents() {
        return newSavingsCents == null ? 0L : newSavingsCents;
    }

    public TransferDirection getDirection() {
        return direction;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public int getStreakMultiplierBasisPoints() {
        return streakMultiplierBasisPoints;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
