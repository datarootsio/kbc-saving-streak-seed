package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One date a bill is going to fall due on, as the year-ahead preview reports it — which is exactly
 * as a test reads it.
 *
 * <p>{@link BillOccurrenceView} in the future tense, and a test reads this against that one: the
 * whole claim of the forecast is that the dates quoted here are the dates the nightly run presents
 * once the clock has reached them, which is checkable by winding the clock twelve months and
 * comparing the two lists.
 *
 * <p>{@code owedRatherThanStillToCome} is read rather than worked out, for the reason
 * {@link OccurrenceToComeView}'s is: it is the boundary the backend drew against its own clock, and
 * a test comparing each date against its own idea of today would be asserting its own arithmetic.
 */
public record BillToComeView(long billId, long currentAccountId, String billName, LocalDate dueOn,
                             BigDecimal amount, boolean owedRatherThanStillToCome) {
}
