package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What a current account says about the income its holder has declared, as the API reports it.
 * Shared by every test that asks, for the same reason as {@link BalancesView} — copies of a shape
 * drift into disagreeing about it.
 *
 * <p>{@code declared} is the field that tells an account nobody has said anything about from one
 * whose holder declared a figure, and it is why a test can assert on absence without reading a null
 * and guessing what it meant.
 */
public record MonthlyIncomeView(long currentAccountId, boolean declared, Integer dayOfMonth,
                                BigDecimal amount, LocalDate nextPayday, Instant declaredAt) {
}
