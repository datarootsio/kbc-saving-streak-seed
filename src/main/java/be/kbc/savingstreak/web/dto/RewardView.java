package be.kbc.savingstreak.web.dto;

import be.kbc.savingstreak.domain.RewardCategory;
import java.math.BigDecimal;

public record RewardView(
        Long id,
        String title,
        String partner,
        String description,
        RewardCategory category,
        int pointsCost,
        BigDecimal value,
        String icon,
        boolean affordable,
        int pointsShort,
        int progressPercent) {
}
