package be.kbc.savingstreak.web.dto;

import java.time.Instant;

public record RedemptionView(
        Long id,
        String rewardTitle,
        int pointsSpent,
        String voucherCode,
        Instant createdAt) {
}
