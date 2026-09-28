package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What a savings account says about the most its holder can put away in a week, as the API reports
 * it and so as a test reads it.
 *
 * <p>{@code declared} and {@code weeklyCapacity} are read together on purpose. The claim a test
 * about an account nobody has declared for is making is that the figure is absent rather than zero,
 * and only the boxed {@link BigDecimal} can tell those apart — a primitive would read a missing
 * figure back as 0.00 and pass the very test it exists to fail.
 *
 * <p>{@code aWeekIsNotSecuredAtThisRate} is the warning a capacity under the weekly minimum carries.
 * It is a warning and not a refusal: the figure below it was accepted, which is what the test either
 * side of the minimum is about.
 */
public record SavingCapacityView(Long savingsAccountId, Boolean declared, BigDecimal weeklyCapacity,
                                 BigDecimal weeklyMinimum, Boolean aWeekIsNotSecuredAtThisRate,
                                 Instant declaredAt) {
}
