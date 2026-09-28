package io.dataroots.savingstreak.loyalty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * What an anniversary pays for the money still sitting in a deposit — the arithmetic, and the only
 * place it is written down.
 *
 * <p>A fraction of the deposit's own euros: one point per whole euro, that fraction of it, rounded
 * down. A figure a customer can work out in their head, and one that means the same thing whatever
 * week they paid in — the streak multiplier does not enter it, so the bonus never compounds with a
 * run of weeks. Ten percent of what the euros earned, never ten percent of what the streak paid.
 *
 * <p><strong>The rate used to be a constant here and is now an argument.</strong> Every account was
 * paid a tenth because every account was on the same unwritten agreement; accounts are now on
 * savings products, and a product says what its own anniversary pays. So the caller hands the rate
 * in, having asked {@link WhatAnAnniversaryPaysHere} about the account the money is sitting in, and
 * what stayed behind is this: the flooring, the threshold, and the insistence that the rate is
 * applied to points rather than to cents. {@link #THE_TENTH_EVERY_ANNIVERSARY_USED_TO_PAY} is still
 * written down, because it is what every product in this application is seeded at and what a caller
 * with no account to ask about is owed.
 *
 * <p><strong>The rate is a fraction per whole euro and nothing else.</strong> The module that
 * stores it holds the same figure three ways — 1 000 basis points, 10.00 percent, the fraction
 * 0.1000 — and two of those, handed in here, would pay ten thousand and a hundred times too much
 * without anything throwing. There is no unit to check at runtime, because a {@link BigDecimal} of
 * 1 000 is a perfectly good rate for a product that pays a thousand points a euro; the defence is
 * that {@link WhatAnAnniversaryPaysHere} names the unit in its own signature and the module that
 * owns the storage does the conversion on its own side of it.
 *
 * <p>Rounded down, because points are whole here as they are everywhere else in this application. A
 * deposit holding under ten euros is therefore worth nothing on its anniversary at the tenth, in
 * the same way EUR 0.99 has always earned no base point: the rule is visible rather than mysterious,
 * and {@link #theLeastABonusIsPaidOn} is that threshold worked out from the rate that was handed in
 * rather than from a constant beside it — which is the whole point of it now that the rate varies.
 *
 * <p>One rule, as the spec insists: no tiering by amount, no second rate for a longer-held deposit.
 * A product that reprices loyalty publishes a version; nothing below changes.
 *
 * <p><strong>Public for the simulator's fold</strong>, which replays a year of nights and has to say
 * what each anniversary inside it would pay. That is the same figure this rule already decides for
 * the sweep, and a fold working out its own tenth would be the second place loyalty is priced — so
 * a repricing would move the bonus a customer is actually paid and leave the projection promising
 * the old one. A rate quoted is a rate that cannot drift; a rate copied is a bug waiting for
 * somebody to change one of the copies. <strong>That argument is now about the rate as well as
 * about the arithmetic</strong>: the fold is handed the account's own rate, off the agreement on
 * the snapshot it was folded from, and there is no longer any way to ask what an anniversary pays
 * without naming the account it is paid in.
 *
 * <p><strong>The flat-rate overload is gone, and its going is a decision rather than a
 * tidy-up.</strong> {@code pointsOn(long)} existed for exactly one slice, as a bridge for the fold
 * while the fold had no account to ask about; it read the tenth out of the constant below and
 * paid it to everybody. A bridge nobody removes is how two rates start disagreeing — it would have
 * gone on quietly promising a tenth to a fixed-term customer whose product pays fifteen percent,
 * and nothing anywhere would have objected, because a tenth is a perfectly good rate. So the rate
 * is now an argument with no default and the compiler is what makes every caller name one.
 */
public final class LoyaltyRate {

    /**
     * A tenth, per whole euro still in the deposit when its anniversary is judged: what every
     * anniversary in this application paid before accounts were on products.
     *
     * <p>Not the rule any more, and kept for two honest uses. It is what every product the
     * catalogue seeds pays, so an account on free savings is paid exactly what it was paid the day
     * before accounts were on products. And it is what an account with no agreement on record is
     * paid, which is the window before the start-up migration has run — the reading both
     * {@link WhatAnAnniversaryPaysHere} and the what-if simulator's snapshot fall back on. It is a
     * default somebody has to reach for deliberately and never a rate anything is paid at by
     * omission.
     */
    public static final BigDecimal THE_TENTH_EVERY_ANNIVERSARY_USED_TO_PAY = new BigDecimal("0.10");

    private LoyaltyRate() {
    }

    /**
     * The whole euros in an amount of money — one point per whole euro, the rule that has always
     * held, so EUR 12.50 is 12 and EUR 0.99 is none.
     *
     * <p>Floored here as well as in the rate below, which is the same double rounding the ledger
     * does when it prices a deposit at a rate: the euros become points first, and the rate is
     * applied to points rather than to cents. A tenth of EUR 9.99 worked out on the cents would be
     * 0.999 of a point and would still floor to nothing, but a tenth of EUR 99.99 worked out that
     * way would be 9.999 and would floor to 9 — while the deposit's own 99 base points are worth 9
     * as well, so the two readings only diverge where the cents happen to carry the rate over a
     * whole point. The euros are what the customer was paid on; they are what this is a fraction of.
     */
    public static long wholeEurosIn(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /**
     * What an anniversary pays on that many whole euros at the rate the account's product names: the
     * rate times the euros, floored, and nothing at all below {@link #theLeastABonusIsPaidOn}.
     *
     * <p>Nothing at all, always, at a rate of nought — which is a product that pays nothing for
     * money staying put, and a real agreement somebody may publish. No special case is written for
     * it because none is needed: nought times anything floors to nothing, and the threshold below
     * says out loud that there is no amount which would clear it.
     *
     * @param wholeEuros       the whole euros still sitting in the deposit, from
     *                         {@link #wholeEurosIn}
     * @param ratePerWholeEuro the fraction of a point each of them earns, which is
     *                         {@link WhatAnAnniversaryPaysHere}'s answer about the account the money
     *                         is sitting in — {@code 0.1000} for the tenth, never 10.00 and never
     *                         1 000
     */
    public static long pointsOn(long wholeEuros, BigDecimal ratePerWholeEuro) {
        return BigDecimal.valueOf(wholeEuros)
                .multiply(ratePerWholeEuro)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
    }

    /**
     * The least a deposit can hold and still be worth a single point on its anniversary at that
     * rate — ten euros at a tenth, nine at twelve percent, seven at fifteen.
     *
     * <p>Derived from the rate that was handed in rather than typed beside it, so that an account on
     * a product paying more is not told about a threshold belonging to a product paying less. It
     * decides nothing: what an anniversary pays is {@link #pointsOn} and this is only the figure a
     * log line quotes when that answer is nothing.
     *
     * <p><strong>Nothing at all when the rate is nought.</strong> A product that pays nothing for
     * money staying put has no amount that would earn a point — not ten euros, not ten million —
     * and one divided by nought is not a large threshold, it is the absence of one. Answered as the
     * empty every other absent reading in this application answers with, rather than as a figure so
     * enormous that a log line would read like a rule somebody could clear.
     *
     * @param ratePerWholeEuro the fraction of a point each whole euro earns, never less than nothing
     */
    public static Optional<BigDecimal> theLeastABonusIsPaidOn(BigDecimal ratePerWholeEuro) {
        if (ratePerWholeEuro.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.ONE.divide(ratePerWholeEuro, 0, RoundingMode.CEILING));
    }
}
