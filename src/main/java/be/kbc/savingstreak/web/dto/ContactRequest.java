package be.kbc.savingstreak.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank(message = "Fill in a first name.")
        @Size(max = 40, message = "Keep the first name under 40 characters.")
        String firstName,

        @NotBlank(message = "Fill in a last name.")
        @Size(max = 40, message = "Keep the last name under 40 characters.")
        String lastName) {
}
