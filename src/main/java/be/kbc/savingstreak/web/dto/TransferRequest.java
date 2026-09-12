package be.kbc.savingstreak.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TransferRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull @DecimalMin(value = "0.01", message = "Enter an amount of at least \u20ac0.01.")
        @Digits(integer = 9, fraction = 2, message = "An amount can have at most two decimals.")
        BigDecimal amount) {
}
