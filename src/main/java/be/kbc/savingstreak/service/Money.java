package be.kbc.savingstreak.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conversions between the euro amounts of the API and the eurocents of the database. */
public final class Money {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static long toCents(BigDecimal euros) {
        if (euros.scale() > 2) {
            throw new BusinessRuleException("An amount can have at most two decimals.");
        }
        return euros.multiply(HUNDRED).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    public static BigDecimal toEuros(long cents) {
        return BigDecimal.valueOf(cents).divide(HUNDRED).setScale(2, RoundingMode.UNNECESSARY);
    }
}
