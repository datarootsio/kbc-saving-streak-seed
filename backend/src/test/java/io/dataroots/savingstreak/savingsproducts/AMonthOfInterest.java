package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a month of interest comes to, worked out the way the specification says it and not the way
 * the application does it.
 *
 * <p><strong>A restatement on purpose, and the only one in these tests.</strong> Everything else
 * here is read back through the API and compared with a figure the test put in. The interest cannot
 * be: it is an arithmetic answer, and a test that read the amount off the posting and compared it
 * with the amount on the balance would be asking the application to agree with itself. So the rule
 * is written out once, here, in the words the spec uses — the average daily balance at a twelfth of
 * the annual rate, floored to the cent — and the tests compare what the application paid with what
 * that sentence says.
 *
 * <p>It works from the percentage rather than from basis points, because the percentage is what
 * leaves the application: a test that spoke the module's internal unit would be a test that could
 * only be written by somebody who had read the module. The rate is read off the version the account
 * is actually on, never written into a test, because free savings has published two of them and an
 * account is paid at the one it was opened under.
 *
 * <p>Shared by the tests here rather than copied into each of them, for the reason the shared views
 * are shared: four copies of one division are four chances for one of them to round differently and
 * for the test that did to be the one everybody believes.
 */
final class AMonthOfInterest {

    /**
     * A year of percentage points, as the divisor that turns one into a month's share: a hundred to
     * take the percentage down to a fraction, and twelve for the months in a year.
     */
    private static final BigDecimal A_HUNDRED_TIMES_THE_MONTHS_IN_A_YEAR = BigDecimal.valueOf(1200);

    private AMonthOfInterest() {
    }

    /**
     * The interest a period pays on that average daily balance at that annual rate.
     *
     * <p>Floored to the cent, downwards, which is the rule and is also what makes the figure worth
     * asserting: a month at 0.50% on EUR 1.200,00 is exactly fifty cents, and a month on a balance
     * a cent either side of it is still exactly fifty cents. A test comparing a rounded figure
     * would pass against an implementation that rounded the other way.
     */
    static BigDecimal onABalanceOf(BigDecimal averageDailyBalance, BigDecimal annualRatePercent) {
        return averageDailyBalance.multiply(annualRatePercent)
                .divide(A_HUNDRED_TIMES_THE_MONTHS_IN_A_YEAR, 2, RoundingMode.FLOOR);
    }
}
