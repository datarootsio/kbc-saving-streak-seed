package be.kbc.savingstreak.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GiftRequest(
        @NotNull Long toMemberId,
        @NotNull @Min(value = 1, message = "Send at least one point.") Integer points,
        @Size(max = 140, message = "Keep the message under 140 characters.") String message) {
}
