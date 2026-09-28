package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One of the six weekly rows as the API sends it — which is exactly as a test reads it.
 *
 * <p>The two days are read rather than worked out, for the reason {@link MonthAheadView}'s window is:
 * a test adding sevens to its own idea of today would be asserting its own arithmetic and would pass
 * against an application whose rows began on some other day.
 *
 * <p>The four figures are read together because the claim about them is a claim about all four at
 * once: {@code leftOver} is {@code arriving - committed - claimedByBudgets}, and a test reading one
 * of them could not say so.
 */
public record WeekAheadView(LocalDate startsOn, LocalDate endsOn, BigDecimal arriving,
                            BigDecimal committed, BigDecimal claimedByBudgets, BigDecimal leftOver,
                            Boolean takesMoreThanItBrings) {
}
