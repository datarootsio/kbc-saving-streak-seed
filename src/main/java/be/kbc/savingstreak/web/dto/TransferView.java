package be.kbc.savingstreak.web.dto;

import be.kbc.savingstreak.domain.TransferDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransferView(
        Long id,
        String description,
        String fromAccountName,
        String toAccountName,
        BigDecimal amount,
        TransferDirection direction,
        int pointsEarned,
        /** When the points this transfer earned lapse, null when it earned none. */
        LocalDate pointsExpireOn,
        /** How much of those points is still unspent and still valid. */
        int pointsLeft,
        boolean pointsHaveExpired,
        /** Loyalty bonus already paid on this deposit's anniversaries. */
        int loyaltyPaid,
        /** What the next anniversary would pay, given what is left of the deposit. */
        int loyaltyNextPoints,
        /** When that is, or null once the deposit has been withdrawn. */
        LocalDate loyaltyNextOn,
        BigDecimal loyaltyStillSaved,
        Instant createdAt) {
}
