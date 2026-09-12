package be.kbc.savingstreak.web.dto;

import java.time.Instant;

public record GiftView(
        Long id,
        /** SENT when the signed-in customer gave the points away, RECEIVED when they got them. */
        String direction,
        String counterpartName,
        String counterpartInitials,
        int points,
        String message,
        Instant createdAt) {
}
