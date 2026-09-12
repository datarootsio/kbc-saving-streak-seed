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

/**
 * A batch of points earned at one moment, with the date they lapse. Points are spent from the
 * oldest batch first, so what is left over is always what expires last.
 */
@Entity
@Table(name = "points_lot")
public class PointsLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The customer whose wallet this batch sits in. */
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "points_earned", nullable = false)
    private int pointsEarned;

    @Column(name = "points_remaining", nullable = false)
    private int pointsRemaining;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** The transfer that earned these points, for traceability. */
    @Column(name = "transfer_id")
    private Long transferId;

    /** Whether this batch came from the deposit itself, a loyalty anniversary, or a gift. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private PointsSource source;

    /** The gift that brought these points in, when they were received from someone. */
    @Column(name = "gift_id")
    private Long giftId;

    protected PointsLot() {
        // for JPA
    }

    public PointsLot(Long memberId, int points, Instant earnedAt, Instant expiresAt, Long transferId,
                     PointsSource source) {
        this.memberId = memberId;
        this.pointsEarned = points;
        this.pointsRemaining = points;
        this.earnedAt = earnedAt;
        this.expiresAt = expiresAt;
        this.transferId = transferId;
        this.source = source;
    }

    /** Spends at most {@code wanted} points from this batch and returns how many it gave. */
    public int spendUpTo(int wanted) {
        int given = Math.min(wanted, pointsRemaining);
        pointsRemaining -= given;
        return given;
    }

    public boolean hasExpiredAt(Instant moment) {
        return !moment.isBefore(expiresAt);
    }

    /** Hands part of this batch to another customer, keeping the original expiry date. */
    public PointsLot giveTo(Long otherMemberId, int wanted, Long giftId) {
        int given = spendUpTo(wanted);
        PointsLot gifted = new PointsLot(otherMemberId, given, earnedAt, expiresAt, null,
                PointsSource.GIFT_RECEIVED);
        gifted.giftId = giftId;
        return gifted;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getGiftId() {
        return giftId;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public int getPointsRemaining() {
        return pointsRemaining;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Long getTransferId() {
        return transferId;
    }

    public PointsSource getSource() {
        return source == null ? PointsSource.DEPOSIT : source;
    }
}
