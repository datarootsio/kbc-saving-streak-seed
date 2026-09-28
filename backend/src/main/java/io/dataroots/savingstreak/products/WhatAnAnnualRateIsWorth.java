package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What an annual rate comes to over part of a year: a month of it, or a stated number of days of
 * it, on an amount in cents, floored to the cent.
 *
 * <p><strong>One place a rate is priced, because a second place is where two rates start to
 * disagree.</strong> Two things in this module turn a rate in basis points into euros — the monthly
 * interest sweep, and the price of breaking a fixed term early — and they are the same arithmetic
 * over different slices of a year. Written twice, the day somebody changes how the flooring works,
 * or moves to a 360-day convention, or reprices to quarterly interest, exactly one of them would
 * change; and the symptom would be a customer charged at a rate they were never paid at.
 *
 * <p><strong>Cents and basis points all the way through, which is what makes the floor the only
 * rounding in it.</strong> A percentage would have to be divided by a hundred first, and where that
 * division rounded would be a second decision nobody wrote down. The spec says the module holds
 * rates as integer basis points and money as long cents for exactly this reason, and this class is
 * the one place both units are multiplied together.
 *
 * <p><strong>Floored downwards, always.</strong> On the way in, because a bank that rounded a
 * half-cent up on every account every month would be paying money nobody had earned. On the way
 * out, because a charge rounded up is a customer paying a cent more than the sentence they were
 * shown promised — and the sentence is shown before they confirm, so the two have to agree exactly.
 * One direction for both means the reading quoted and the euros taken are the same number by
 * construction rather than by care.
 *
 * <p><strong>A year is three hundred and sixty-five days here, whatever the calendar did.</strong>
 * The penalty is a stated price — "ninety days of interest at the headline rate" — rather than a
 * stretch of time anybody lived through, and a charge that came out a cent different for a term
 * broken in a leap year would be two prices for one sentence. The alternative, counting the actual
 * days of the year the break falls in, would also make the quoted figure depend on which side of a
 * new year the customer pressed the button. A fixed divisor is the reading a customer can check.
 *
 * <p>Package-private and static, like every other piece of arithmetic in this module that has no
 * state: what a rate is worth is a function of three numbers, and a class with a constructor would
 * invite somebody to inject a fourth.
 *
 * <p><strong>Public, and widened on purpose rather than by drift.</strong>
 * The what-if simulator folds a year of nights over one account and has to post the interest
 * each projected month would pay. That is this arithmetic and no other: a fold working out its own
 * twelfth would be the second place a rate is turned into euros, and a customer would be promised
 * one figure by the projection and paid another by the sweep.
 * The reason it is safe is the reason {@code LoyaltyRate} gives for the same move: there is nothing
 * of this module's machinery in it — no repository, no entity, no clock, no state, just a rule
 * about numbers. A module that calls it is not reading Products; it is quoting a rule Products
 * wrote down, which is the only way a projection and a sweep can be kept from disagreeing.
 */
public final class WhatAnAnnualRateIsWorth {

    /**
     * How many basis points a whole year is, times the months in it — the one divisor that turns an
     * annual rate in basis points into a month's share of it.
     *
     * <p>Written as the two figures multiplied rather than as 120 000, so that the reader can see
     * which of them is the unit and which is the twelfth: a repricing to quarterly interest changes
     * the second number and nothing else.
     */
    private static final BigDecimal A_YEARS_BASIS_POINTS_TIMES_THE_MONTHS_IN_IT =
            BigDecimal.valueOf(10_000L * 12L);

    /**
     * The same divisor cut into days instead of months, and the same argument about reading it as
     * two figures: the unit, and how many of them a year is divided into.
     */
    private static final BigDecimal A_YEARS_BASIS_POINTS_TIMES_THE_DAYS_IN_IT =
            BigDecimal.valueOf(10_000L * 365L);

    private WhatAnAnnualRateIsWorth() {
        // arithmetic, not a thing
    }

    /**
     * A month's share of an annual rate on an amount, floored to the cent — what a monthly interest
     * period pays.
     */
    public static long overAMonth(long cents, int annualRateBasisPoints) {
        return BigDecimal.valueOf(cents)
                .multiply(BigDecimal.valueOf(annualRateBasisPoints))
                .divide(A_YEARS_BASIS_POINTS_TIMES_THE_MONTHS_IN_IT, 0, RoundingMode.FLOOR)
                .longValueExact();
    }

    /**
     * A stated number of days' share of an annual rate on an amount, floored to the cent — what
     * breaking a fixed term early costs.
     *
     * <p>The days are multiplied in before the division rather than after, so the whole sum is one
     * exact multiplication and one flooring. Working out a day's interest first and multiplying it
     * by ninety would floor ninety times over, and the difference — up to eighty-nine cents on a
     * charge of a few euros — would be a discount nobody decided to give.
     */
    static long overDays(long cents, int annualRateBasisPoints, int days) {
        return BigDecimal.valueOf(cents)
                .multiply(BigDecimal.valueOf(annualRateBasisPoints))
                .multiply(BigDecimal.valueOf(days))
                .divide(A_YEARS_BASIS_POINTS_TIMES_THE_DAYS_IN_IT, 0, RoundingMode.FLOOR)
                .longValueExact();
    }
}
