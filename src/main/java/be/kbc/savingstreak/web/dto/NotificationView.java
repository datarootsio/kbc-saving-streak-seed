package be.kbc.savingstreak.web.dto;

import be.kbc.savingstreak.domain.NotificationKind;
import java.time.Instant;

public record NotificationView(
        Long id,
        NotificationKind kind,
        String title,
        String body,
        int points,
        boolean unread,
        Instant createdAt) {
}
