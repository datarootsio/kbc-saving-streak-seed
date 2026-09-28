package io.dataroots.savingstreak.products;

import java.math.BigDecimal;

import io.dataroots.savingstreak.deposits.TheRateADepositIsPaidAt;
import io.dataroots.savingstreak.loyalty.LoyaltyRate;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.products.TheRateAPeriodIsPaidAt.WhatAPeriodEarned;

/**
 * What a named amount would be worth after twelve months in a product: the euros of interest it
 * would be paid, and the points it would earn on the way in and on its first anniversary.
 *
 * <p><strong>Every figure here is a rule this application already has, quoted.</strong> That is the
 * whole of what this class is for, and it is the one decision in it worth arguing. A comparison
 * screen is the second place a rate gets priced, and the second place a rate is priced is the first
 * place two rates disagree — a customer shown EUR 30.24 on a card and paid EUR 30.19 by the sweep
 * has been lied to by a screen nobody thought of as arithmetic. So not one figure below is worked
 * out here:
 *
 * <ul>
 *   <li>what rate a month is paid at, headline or headline-plus-bonus, is
 *       {@link TheRateAPeriodIsPaidAt}'s judgement, asked once per projected month exactly as the
 *       sweep asks it once per real month;</li>
 *   <li>what that rate comes to over a month on a balance is {@link WhatAnAnnualRateIsWorth}, the
 *       same static {@code InterestService.whatAPeriodPays} delegates to, down to the flooring;</li>
 *   <li>what a euro saved here is worth in points is {@link TheRateADepositIsPaidAt#combining}
 *       composed with {@link PointsService#pointsEarnedOn}, which is literally the pair of calls a
 *       deposit is priced by;</li>
 *   <li>what an anniversary pays is {@link LoyaltyRate#pointsOn(long, BigDecimal)} on
 *       {@link LoyaltyRate#wholeEurosIn}, at the rate the product's own version names.</li>
 * </ul>
 *
 * <p>Two of those four were widened for this class, and both say so in their own javadoc rather
 * than here. {@code LoyaltyRate} was already public for exactly this reason and its javadoc argues
 * it at length; this class is the second caller that argument was written for.
 *
 * <p><strong>Monthly, compounding, twelve times — because that is what the sweep does.</strong>
 * Interest is paid into the account that earned it and is therefore in the next month's average, so
 * a projection that took a twelfth of a year's simple interest would be under by a few cents on a
 * good rate and would be wrong in a way that only showed up after a year of clock-winding. The
 * projected balance is stepped forward by each month's payment, each month is floored to the cent
 * on its own, and the twelve floorings are the same twelve the account would actually suffer. A
 * single flooring at the end would be a figure a cent or two above what the bank would really pay.
 *
 * <p><strong>The projection supposes the money is put in and left alone, and says nothing about
 * anybody's streak.</strong> The catalogue is the same answer for everybody — that is what makes a
 * rate something a customer can weigh before they hold anything — so the points on the way in are
 * priced at the ordinary rate, which is what a deposit outside any run of weeks is paid. Quoting the
 * customer's own streak would make the shelf read differently for two people standing in front of
 * the same poster, and would promise a run of weeks to somebody who is about to break one. The
 * product's multiple is the part of that figure the product decides, and it is the part this screen
 * is comparing.
 *
 * <p><strong>And that ordinary rate comes off the scheme in force today, not out of a constant.</strong>
 * It used to be read from {@code StreakMultiplier}, which is where the ladder's first rung was
 * written down; it is now handed in, because the shelf belongs to nobody and "nobody" is somebody
 * with no run of weeks behind them — which is precisely the run the ladder's first rung prices,
 * under whatever version of the scheme the bank is selling under today. A projection that went on
 * quoting 1,00× after the bank published a first rung of 1,05× would promise a customer less than
 * the account they are about to open would actually pay them, which is the second-place-a-rate-is-
 * priced failure this class exists to prevent, arriving by the back door. It is deliberately
 * <em>not</em> {@link TheRateADepositIsPaidAt#THE_MULTIPLE_THAT_CHANGES_NOTHING}: that figure means
 * "no product entered this" and is a fact about what the ledger did, where this one is a rate the
 * bank is currently offering.
 *
 * <p><strong>One anniversary falls inside twelve months, and exactly one.</strong> A deposit's first
 * anniversary is a year after it landed and {@code LoyaltyAnniversary} counts an anniversary as
 * passed on the day itself, so a projection over twelve months carries one and never two. The
 * interest paid along the way earns no anniversary of its own — the loyalty sweep reads the
 * customer's own deposits and interest is not one of them — so the euros the anniversary is paid on
 * are the euros that were typed, not the balance they grew into.
 *
 * <p>Pure, static and package-private, like {@link TheRateAPeriodIsPaidAt} and
 * {@link WhatAnAnnualRateIsWorth} beside it. It holds nothing and reads nothing: a projection is a
 * function of an amount and a row of the catalogue, and a class with a constructor would invite
 * somebody to inject a clock into it.
 */
final class WhatAYearInAProductWouldPay {

    /** Twelve months, which is the span the comparison screen is about and the only one it offers. */
    static final int MONTHS_IN_THE_PROJECTION = 12;

    /**
     * The lowest balance a month is supposed to have held when the supposition is that the floor was
     * <em>not</em> kept: nothing at all.
     *
     * <p>Nought rather than a cent under the floor, because "a cent under" is a figure somebody
     * would later have to justify and because nought is the only value that means the same thing
     * whatever the floor happens to be. It is handed to {@link TheRateAPeriodIsPaidAt}, which then
     * makes the judgement rather than this class asserting the answer — and on a product with a
     * bonus and no floor to keep that rule answers that the bonus <em>was</em> earned, which is
     * correct and is why the two readings collapse into one figure there.
     */
    private static final long NOTHING_WAS_KEPT = 0L;

    private WhatAYearInAProductWouldPay() {
        // arithmetic, not a thing
    }

    /**
     * What twelve months of interest came to, what the balance grew to, and whether the bonus was in
     * every month of it.
     *
     * <p>The three together, because they are one walk and a caller that asked for them separately
     * would walk the year three times and could be handed answers from three different suppositions.
     */
    record AYearOfInterest(long interestCents, long balanceCents, boolean bonusEarnedEveryMonth) {
    }

    /**
     * Twelve months of interest on an amount, at whatever rate each of those months would be paid
     * at.
     *
     * @param amountCents               what is put in on the first day and never touched again
     * @param headlineRateBasisPoints   what the agreement pays whatever happens
     * @param bonusRateBasisPoints      what is paid on top for keeping the condition, and nought
     *                                  where the product has no bonus to offer
     * @param floorCents                the balance that has to be kept for it, and nought where
     *                                  there is no floor
     * @param supposingTheFloorIsKept   whether to suppose the balance never went under the floor —
     *                                  true for the honest reading of money left alone, in which
     *                                  the lowest the balance ever goes is the amount itself, and
     *                                  false for the second reading a floored product needs, in
     *                                  which every month went under
     */
    static AYearOfInterest on(long amountCents, int headlineRateBasisPoints,
                              int bonusRateBasisPoints, long floorCents,
                              boolean supposingTheFloorIsKept) {
        long balanceCents = amountCents;
        long interestCents = 0;
        boolean bonusEarnedEveryMonth = true;
        for (int month = 1; month <= MONTHS_IN_THE_PROJECTION; month++) {
            // The balance itself when the money is left alone, because a balance that only ever
            // grows is at its lowest on the first day of the month. That is the same figure the
            // walk over a real ledger would produce for an account nothing moved in or out of, and
            // it is what makes an amount under the floor honestly read as a year with no bonus in
            // it rather than as a promise nobody could keep.
            long lowestCents = supposingTheFloorIsKept ? balanceCents : NOTHING_WAS_KEPT;
            WhatAPeriodEarned earned = TheRateAPeriodIsPaidAt.forAPeriodWhoseLowestBalanceWas(
                    headlineRateBasisPoints, bonusRateBasisPoints, floorCents, lowestCents);
            long paid = WhatAnAnnualRateIsWorth.overAMonth(balanceCents,
                    earned.annualRateBasisPoints());
            interestCents += paid;
            balanceCents += paid;
            bonusEarnedEveryMonth &= earned.bonusEarned();
        }
        return new AYearOfInterest(interestCents, balanceCents, bonusEarnedEveryMonth);
    }

    /**
     * What the amount earns in points the moment it lands in an account on this product.
     *
     * <p>At the ordinary rate for the run of weeks, for the reason the class javadoc gives: the
     * shelf belongs to nobody. What is left varying is the product's own multiple, which is the
     * figure the screen is putting side by side.
     *
     * <p>The two are multiplied by {@link TheRateADepositIsPaidAt#combining}, which is the same call
     * a real deposit is priced by — so a published first rung quoted to four places composes with a
     * product's multiple here exactly as it would in the ledger.
     *
     * @param theOrdinaryRate  what the first week of a run pays per whole euro, off the version of
     *                         the scheme in force today — never a figure this class wrote down, for
     *                         the reason the class javadoc argues at length
     * @param pointsMultiplier what a euro saved into this product is worth, as a multiple of one,
     *                         which is what {@code ASetOfTerms.pointsMultiplier} carries
     */
    static long pointsWhenTheMoneyLands(BigDecimal amount, BigDecimal theOrdinaryRate,
                                        BigDecimal pointsMultiplier) {
        return PointsService.pointsEarnedOn(amount,
                TheRateADepositIsPaidAt.combining(theOrdinaryRate, pointsMultiplier));
    }

    /**
     * What the first anniversary of that money pays in an account on this product.
     *
     * <p>On the euros that were typed rather than on the balance they grew into, because an
     * anniversary is paid on what is still sitting in <em>that deposit</em> and the interest along
     * the way lands as deposits of its own which earn no anniversary at all.
     *
     * @param anniversaryRatePerWholeEuro the fraction of a point each whole euro earns, which is
     *                                    what {@code ProductTerms.anniversaryRatePerWholeEuro}
     *                                    hands over and never the percentage beside it
     */
    static long pointsOnItsFirstAnniversary(BigDecimal amount,
                                            BigDecimal anniversaryRatePerWholeEuro) {
        return LoyaltyRate.pointsOn(LoyaltyRate.wholeEurosIn(amount), anniversaryRatePerWholeEuro);
    }
}
