package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The rung a customer is climbing towards, and what it still asks for.
 *
 * <p>The one figure on the card that answers "should I pay in today". A reading on its own says how
 * far somebody has come; this says how far is left, which is the question a person standing in front
 * of their banking app is actually asking.
 *
 * <p>The next rung is the first one the reading has <em>not</em> cleared, and clearing means reaching
 * the threshold rather than passing it: a challenge that asks for EUR 500 is finished by the
 * five-hundredth euro. There is no next rung once gold is cleared, and that absence is reported as
 * an absence rather than as a rung asking for nothing — a card with a bar that can never fill is a
 * card that reads as unfinished work.
 *
 * <p>Derived on every read from the reading and the challenge's rungs, and stored nowhere. A
 * threshold the bank retunes therefore changes what everybody's card says next, which is the point
 * of the thresholds being rows; what a customer was already <em>paid</em> is a different sort of fact
 * and is recorded rather than recomputed, in the slice that first pays one.
 */
public record TheNextRungUp(ChallengeRung rung, BigDecimal stillNeeded) {

    /**
     * The first rung the reading has not reached, with the difference, or nothing at all when every
     * rung is behind them.
     *
     * @param reading where the enrolment stands
     * @param rungs   the challenge's ladder, cheapest first, which is the order it is declared in
     */
    static Optional<TheNextRungUp> above(BigDecimal reading, List<ChallengeRung> rungs) {
        return rungs.stream()
                .filter(rung -> reading.compareTo(rung.threshold()) < 0)
                .findFirst()
                .map(rung -> new TheNextRungUp(rung, rung.threshold().subtract(reading)));
    }
}
