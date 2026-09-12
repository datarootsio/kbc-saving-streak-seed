package be.kbc.savingstreak.web.dto;

import java.math.BigDecimal;

public record TransferResult(
        TransferView transfer,
        int pointsEarned,
        BigDecimal multiplier,
        boolean streakExtended,
        int streakWeeks,
        OverviewResponse overview) {
}
