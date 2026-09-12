package be.kbc.savingstreak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "redemption")
public class Redemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reward_id", nullable = false)
    private Long rewardId;

    @Column(name = "reward_title", nullable = false)
    private String rewardTitle;

    @Column(name = "points_spent", nullable = false)
    private int pointsSpent;

    @Column(name = "voucher_code", nullable = false, unique = true)
    private String voucherCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Redemption() {
        // for JPA
    }

    public Redemption(Long rewardId, String rewardTitle, int pointsSpent, String voucherCode, Instant createdAt) {
        this.rewardId = rewardId;
        this.rewardTitle = rewardTitle;
        this.pointsSpent = pointsSpent;
        this.voucherCode = voucherCode;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRewardId() {
        return rewardId;
    }

    public String getRewardTitle() {
        return rewardTitle;
    }

    public int getPointsSpent() {
        return pointsSpent;
    }

    public String getVoucherCode() {
        return voucherCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
