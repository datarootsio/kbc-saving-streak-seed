package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the API answers when a customer breaks a fixed term: the day, the maturity they gave up,
 * what it cost, and the agreement the account is on now.
 *
 * <p>Shared here rather than copied into each test that reads one, for the reason every other view
 * in this package gives.
 *
 * <p><strong>{@code nowOn} is {@link AnAgreementView}, the same shape the account's own page hands
 * out.</strong> That is the point of it being in this answer: a test can assert that the maturity
 * date is gone and that the account is on free savings without reading the account again, and the
 * shape it asserts against is the shape every other reading of an agreement uses. A second record
 * meaning almost the same thing would be a second contract to keep in step.
 */
public record ATermBrokenView(long savingsAccountId, LocalDate brokenOn,
                              LocalDate wouldHaveMaturedOn, int termMonths,
                              int earlyExitPenaltyDays, BigDecimal balanceItWasChargedOn,
                              BigDecimal charge, AnAgreementView nowOn) {
}
