package be.kbc.savingstreak.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/** Either bound may be null, which clears that alert. */
public record ThresholdRequest(
        @DecimalMin(value = "0.00", message = "An alert level cannot be negative.")
        @Digits(integer = 9, fraction = 2, message = "An amount can have at most two decimals.")
        BigDecimal below,

        @DecimalMin(value = "0.00", message = "An alert level cannot be negative.")
        @Digits(integer = 9, fraction = 2, message = "An amount can have at most two decimals.")
        BigDecimal above) {
}
