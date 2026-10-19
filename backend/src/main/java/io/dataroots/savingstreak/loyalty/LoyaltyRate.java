package io.dataroots.savingstreak.loyalty;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What an anniversary pays for the money still sitting in a deposit — the rate, and the only place
 * it is written down.
 *
 * <p>A tenth of the deposit's own euros: one point per whole euro, a tenth of that, rounded down. A
 * figure a customer can work out in their head, and one that means the same thing whatever week
 * they paid in — the streak multiplier does not enter it, so the bonus never compounds with a run
 * of weeks. Ten percent of what the euros earned, never ten percent of what the streak paid.
 *
 * <p>Rounded down, because points are whole here as they are everywhere else in this application. A
 * deposit holding under ten euros is therefore worth nothing on its anniversary, in the same way
 * EUR 0.99 has always earned no base point: the rule is visible rather than mysterious, and
 * {@link #THE_LEAST_A_BONUS_IS_PAID_ON} is that threshold worked out from the rate rather than
 * written down beside it.
 *
 * <p>One constant, as the spec insists: no tiering by amount, no second rate for a longer-held
 * deposit. A training exercise that reprices loyalty changes the figure below and nothing else.
 */
final class LoyaltyRate {

    /** A tenth, per whole euro still in the deposit when its anniversary is judged. */
    static final BigDecimal WHAT_AN_ANNIVERSARY_PAYS_ON_THE_EUROS = new BigDecimal("0.10");

    /**
     * The least a deposit can hold and still be worth a single point on its anniversary — ten euros
     * at a tenth.
     *
     * <p>Derived from the rate rather than typed beside it, so that a reprice cannot leave the
     * threshold saying something the arithmetic disagrees with. It decides nothing: what an
     * anniversary pays is {@link #pointsOn} and this is only the figure a log line quotes when that
     * answer is nothing.
     */
    static final BigDecimal THE_LEAST_A_BONUS_IS_PAID_ON =
            BigDecimal.ONE.divide(WHAT_AN_ANNIVERSARY_PAYS_ON_THE_EUROS, 0, RoundingMode.CEILING);

    private LoyaltyRate() {
    }

    /**
     * The whole euros in an amount of money — one point per whole euro, the rule that has always
     * held, so EUR 12.50 is 12 and EUR 0.99 is none.
     *
     * <p>Floored here as well as in the tenth below, which is the same double rounding the ledger
     * does when it prices a deposit at a rate: the euros become points first, and the rate is
     * applied to points rather than to cents. A tenth of EUR 9.99 worked out on the cents would be
     * 0.999 of a point and would still floor to nothing, but a tenth of EUR 99.99 worked out that
     * way would be 9.999 and would floor to 9 — while the deposit's own 99 base points are worth 9
     * as well, so the two readings only diverge where the cents happen to carry the tenth over a
     * whole point. The euros are what the customer was paid on; they are what this is a tenth of.
     */
    static long wholeEurosIn(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /**
     * What an anniversary pays on that many whole euros: a tenth, floored, and nothing at all below
     * {@link #THE_LEAST_A_BONUS_IS_PAID_ON}.
     */
    static long pointsOn(long wholeEuros) {
        return BigDecimal.valueOf(wholeEuros)
                .multiply(WHAT_AN_ANNIVERSARY_PAYS_ON_THE_EUROS)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
    }
}
