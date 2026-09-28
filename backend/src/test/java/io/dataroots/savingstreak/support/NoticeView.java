package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One notice as the API sends it, mirroring {@code NoticeResponse} field for field.
 *
 * <p>Shared here rather than nested in the test that happens to need it first, for the reason every
 * other view in this package is: two copies of a response's shape are two chances to disagree about
 * what the API actually sends, and the copy inside the test that is not run today is the one that
 * drifts.
 *
 * <p>{@code ready} and {@code daysLeft} are both read back and both asserted on, because the whole
 * of what this ticket is about is that neither is stored — a test that only checked the date would
 * pass against an implementation that wrote a flag on the row and forgot to keep it truthful.
 */
public record NoticeView(Long id, Long savingsAccountId, BigDecimal amount,
                         BigDecimal stillStanding, LocalDate givenOn, LocalDate readyOn,
                         boolean ready, int daysLeft) {
}
