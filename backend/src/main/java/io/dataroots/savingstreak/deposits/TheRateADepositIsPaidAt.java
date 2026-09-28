package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;

import io.dataroots.savingstreak.streaks.StreakMultiplier;

/**
 * The two factors a deposit is paid at, put together: what the run of weeks pays and what the
 * product pays, multiplied into the one rate the ledger prices with.
 *
 * <p><strong>They multiply rather than adding, and the order matters.</strong> Whole euros first,
 * then the streak, then the product, floored once at the end — which is the same double flooring
 * the ledger has always done, with one more factor in the middle of it. Multiplying is what makes a
 * run of weeks worth more in a product that pays more; adding would make the product's benefit
 * worth the same to somebody on their twentieth consecutive week as to somebody on their first,
 * which is the opposite of what both schemes are for. Flooring once is what stops the two rounding
 * losses from stacking: {@code floor(floor(7 × 1.10) × 1.25)} is 8 and {@code floor(7 × 1.10 ×
 * 1.25)} is 9, and the customer is entitled to the second.
 *
 * <p>Nothing here does the flooring. The combined rate is handed to {@code PointsService} exactly
 * as a streak rate always was, and that ledger floors the euros, multiplies, and floors again —
 * once each, which is the whole of the rule. A second flooring in this class would be the second
 * place a deposit is priced.
 *
 * <p><strong>A rate is written to two places, and to every further place the arithmetic actually
 * produced.</strong> That is the one decision this class makes and it is a decision about honesty
 * rather than about presentation. A streak rate has two places and a product's multiple has up to
 * four, so their product has up to six; rounding it to two would turn a half-percent product uplift
 * into no uplift at all — which is exactly the argument {@code BasisPoints} makes for holding a
 * multiple to four places in the first place — and rounding it to four could still drop a place the
 * ladder itself put there. So nothing is rounded: the trailing zeros are stripped and two places
 * are restored, which leaves {@code 1.00 × 1.0000} reading as {@code 1.00} exactly as it always
 * did, and {@code 1.10 × 1.2500} reading as {@code 1.375}, which is what the deposit was in fact
 * paid.
 *
 * <p>The restoring of two places is not cosmetic either. It is here for the reason
 * {@link StreakMultiplier#asARate} gives about the same figure: a rate that has been through SQLite
 * comes back carrying whatever scale a float kept, so 1.50 arrives as 1.5, and the rate a deposit
 * reports in its history has to read the way it read in the answer to the deposit itself.
 *
 * <p>A class of its own rather than three lines inside the one method that prices a deposit,
 * because the rule is read from two ends — the deposit is priced here and its history quotes the
 * rate back — and two spellings of "how a rate is written down" is how the two ends start
 * disagreeing. There is nothing to construct and nothing to inject.
 *
 * <p><strong>Public, and widened on purpose rather than by drift.</strong> It started
 * package-private, because the only thing that priced a deposit was the service next to it. The
 * savings products comparison screen is the second: it says what a named amount would earn in each
 * product, and what it would earn in points is a deposit's rate applied to a deposit's euros. A
 * projection that multiplied the two factors itself would be the second place a deposit is priced,
 * and the paragraphs above — that they multiply rather than add, in that order, rounded that way —
 * would then be true in one place and copied in another. The reason it is safe to widen is the
 * reason {@code AmountOfMoney} and {@code LoyaltyRate} give for the same move: there is nothing of
 * Deposits in it. No repository, no entity, no state, nothing but two {@link BigDecimal}s and a
 * rule about how they compose. A module that calls this is not reading Deposits; it is quoting a
 * rule.
 */
public final class TheRateADepositIsPaidAt {

    /**
     * The multiple that changes nothing: one, per whole euro, which is what a deposit earned before
     * there was anything to multiply it by.
     *
     * <p><strong>Not the scheme's ordinary rate, and the difference is the whole reason this
     * constant exists here rather than being read off {@code StreakMultiplier}.</strong> It is what a
     * row of this ledger reports when nothing was written on it: a deposit recorded before rates were
     * stored at all, and one recorded before there were products to contribute a second factor.
     * Those deposits earned one point per whole euro — the figure is a fact about what the ledger
     * did, not a reading of what the ladder says. Reading the scheme's first rung for it would make
     * a history row move the day the bank published a scheme whose first rung is 1,05×, quietly
     * restating what somebody was paid in 2024 as something they were not paid, which is precisely
     * the promise this application makes about a deposit that has landed.
     *
     * <p>It is also the neutral element of {@link #combining}, which is what makes it the honest
     * answer for the product's share of a rate: a deposit not priced under any product was not priced
     * under a product at a rate nobody wrote down, and {@code 1.00} is what "no product entered this"
     * means when it is multiplied by. {@code LoyaltyRate.THE_TENTH_EVERY_ANNIVERSARY_USED_TO_PAY} is
     * the same shape of figure one module along: a default somebody reaches for deliberately, never
     * a rate anything is paid at by omission.
     */
    public static final BigDecimal THE_MULTIPLE_THAT_CHANGES_NOTHING = new BigDecimal("1.00");

    /** How a rate is written at its shortest: 1,10× rather than 1,1×. */
    private static final int THE_PLACES_A_RATE_IS_WRITTEN_TO_AT_LEAST = 2;

    private TheRateADepositIsPaidAt() {
    }

    /**
     * The one rate a deposit is priced at: what the run of weeks pays, times what the product pays.
     *
     * @param streak  what the run of weeks the deposit has just been counted into pays per whole
     *                euro, which is {@link StreakMultiplier}'s answer
     * @param product what a euro saved into this account is worth, as a multiple of one, which is
     *                {@link WhatAEuroSavedIntoAnAccountIsWorth}'s answer
     */
    public static BigDecimal combining(BigDecimal streak, BigDecimal product) {
        return quoted(streak.multiply(product));
    }

    /**
     * A rate written the way this application writes one: two places at least, and every further
     * place the figure actually carries.
     *
     * <p>Exact by construction. Stripping trailing zeros throws nothing away — {@code 1.2500} and
     * {@code 1.25} are one rate — and setting the scale back up only ever pads, so no rounding mode
     * is reached for and none could be. A rate this application never wrote down cannot arrive
     * here.
     */
    static BigDecimal quoted(BigDecimal rate) {
        BigDecimal withoutItsPadding = rate.stripTrailingZeros();
        return withoutItsPadding.scale() < THE_PLACES_A_RATE_IS_WRITTEN_TO_AT_LEAST
                ? withoutItsPadding.setScale(THE_PLACES_A_RATE_IS_WRITTEN_TO_AT_LEAST)
                : withoutItsPadding;
    }
}
