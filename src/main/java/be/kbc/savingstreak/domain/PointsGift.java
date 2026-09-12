package be.kbc.savingstreak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One customer giving points to another. Kept as its own record so every gift is auditable:
 * who gave what to whom, and when.
 */
@Entity
@Table(name = "points_gift")
public class PointsGift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_member_id", nullable = false)
    private Long fromMemberId;

    @Column(name = "to_member_id", nullable = false)
    private Long toMemberId;

    @Column(nullable = false)
    private int points;

    @Column(length = 140)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PointsGift() {
        // for JPA
    }

    public PointsGift(Long fromMemberId, Long toMemberId, int points, String message, Instant createdAt) {
        this.fromMemberId = fromMemberId;
        this.toMemberId = toMemberId;
        this.points = points;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getFromMemberId() {
        return fromMemberId;
    }

    public Long getToMemberId() {
        return toMemberId;
    }

    public int getPoints() {
        return points;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
