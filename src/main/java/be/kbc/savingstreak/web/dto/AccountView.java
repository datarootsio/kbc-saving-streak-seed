package be.kbc.savingstreak.web.dto;

import be.kbc.savingstreak.domain.AccountType;
import java.math.BigDecimal;

public record AccountView(
        Long id,
        String name,
        String iban,
        AccountType type,
        String subtitle,
        BigDecimal balance,
        BigDecimal goal,
        Integer goalProgressPercent,
        BigDecimal interestRate,
        /** Warn below and tell above, both optional. */
        BigDecimal alertBelow,
        BigDecimal alertAbove,
        /** Null for the current account; savings accounts carry their points timeline. */
        AccountTimeline timeline) {
}
