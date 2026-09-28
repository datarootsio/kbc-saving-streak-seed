package io.dataroots.savingstreak.products;

/**
 * What rate one month of one account is actually paid at: the headline rate always, plus the bonus
 * rate when the condition the product attaches to it was kept across that month.
 *
 * <p><strong>A class of its own for one addition, and the addition is not why.</strong> What lives
 * here is the <em>judgement</em> — whether a month earned its bonus — and that judgement is the
 * whole of this slice. {@link InterestService} decides which months to pay and writes them down;
 * this decides what each one is worth per year, out of the figures the account's own version of the
 * terms names and the one figure the walk over the ledger produced. Keeping it apart means the
 * sweep's arithmetic ({@code whatAPeriodPays}: an average, a rate, a twelfth, a floor to the cent)
 * is untouched by this feature, and anything else that has to price a stretch of interest at an
 * account's rate — the charge for breaking a term early, a projection — multiplies by the same
 * unchanged method rather than by a method that has quietly grown a condition inside it.
 *
 * <p><strong>The condition is read off the figures, never off the {@link ProductKind}.</strong>
 * That is the decision worth arguing, because the obvious alternative is a switch on the kind: a
 * {@code MINIMUM_BALANCE} account is judged against its floor and the other three are not. It was
 * rejected for two reasons. The sweep holds an agreement and a version of the terms, not a product
 * row, so switching on the kind means loading the catalogue for every account on every night in
 * order to learn something the terms already say. And it would make two columns decide one fact: a
 * product whose kind said minimum balance and whose floor was nought, or the reverse, would have
 * two answers and no rule about which wins. {@code ProductKind} exists because each of its four
 * values asks a different question <em>of a withdrawal</em> — its own javadoc says so — and a bonus
 * is not a question asked of a withdrawal at all.
 *
 * <p><strong>Zero is the absence of the rule here exactly as it is on the terms.</strong> No bonus
 * rate is no bonus to earn, so the month is paid the headline rate and the posting says the bonus
 * was not earned — which is true, and is the reading that keeps the flag worth reading: three
 * products out of four have nothing to earn, and a flag that said {@code true} on every free
 * savings month because a condition nobody set was vacuously met would tell a reader nothing. No
 * floor, on a product that does have a bonus, is nothing to keep and therefore kept: the bonus is
 * paid every month. Nothing in the catalogue is seeded that way, and the reading is written down
 * rather than left to be discovered, because the alternative — treating a missing floor as a
 * condition that can never be met — would publish a bonus rate that could never be paid.
 *
 * <p><strong>At or above the floor, on the lowest day.</strong> The spec's words, and the strictness
 * is the point: a balance that dipped on the 3rd and was back by the 4th went under the floor, and
 * judging the closing balance — or the average — would let a customer take the money out and put it
 * back before the month ended and be paid as though they never had. {@code >=} rather than
 * {@code >} because a balance of exactly five hundred euros has kept a floor of five hundred euros;
 * a floor is a minimum, not something to stay clear of.
 *
 * <p><strong>And it refuses nothing.</strong> Nothing in this class, and nothing that calls it,
 * stands between a customer and their own money. A month that went under the floor is paid the
 * headline rate and keeps every euro; the cost of dipping is exactly one month's bonus, and the
 * month after it is paid in full because each period is judged on its own walk and nothing is
 * carried forward. {@code TheConditionsOnTheWayOut} is where a refusal would have to live and
 * deliberately has no floor in it — a rule that both refused the withdrawal and withheld the bonus
 * could only ever do one of them, since a refused withdrawal never takes a balance under a floor.
 *
 * <p>Pure, static and package-private, like {@link TheDailyBalancesOfAnAccount} beside it and for
 * the same reason: it holds nothing, reads nothing and is a sentence about four numbers. It takes
 * the four as primitives rather than taking a {@code ProductTerms} row, so that the one thing it
 * does is visible in its own signature and cannot quietly come to depend on a tenth column.
 *
 * <p><strong>Public, and widened on purpose rather than by drift.</strong>
 * The what-if simulator asks it once per projected month exactly as the sweep asks it once per
 * real month, so that the bonus judgement — whether a month kept the floor — is made in one place
 * for a year that has happened and for a year that has not.
 * The reason it is safe is the reason {@code LoyaltyRate} gives for the same move: there is nothing
 * of this module's machinery in it — no repository, no entity, no clock, no state, just a rule
 * about numbers. A module that calls it is not reading Products; it is quoting a rule Products
 * wrote down, which is the only way a projection and a sweep can be kept from disagreeing.
 */
public final class TheRateAPeriodIsPaidAt {

    private TheRateAPeriodIsPaidAt() {
    }

    /**
     * What a month earned: the rate in basis points a year that its interest is worked out at, and
     * whether the bonus is part of it.
     *
     * <p>Both, together, because the posting records both and they must agree. A method answering
     * only the rate would leave the caller working out the flag a second way — {@code rate >
     * headline}, say — and a method answering only the flag would leave the caller doing the
     * addition. One judgement, two readings of it, decided once.
     *
     * @param annualRateBasisPoints what the month is paid at, headline plus bonus or headline alone
     * @param bonusEarned           whether the bonus is in that figure, for the posting to record
     */
    public record WhatAPeriodEarned(int annualRateBasisPoints, boolean bonusEarned) {
    }

    /**
     * The rate a period is paid at, given what its version of the terms says and how low the
     * balance went across it.
     *
     * @param headlineRateBasisPoints the rate the agreement pays whatever happens, in basis points
     *                                a year
     * @param bonusRateBasisPoints    what is paid on top for keeping the condition, and nought when
     *                                the product has no bonus to offer
     * @param floorCents              the balance that has to be kept for it, and nought when there
     *                                is no floor to keep
     * @param lowestDailyBalanceCents the lowest the balance went on any day of the period, which is
     *                                what the floor is judged on
     */
    public static WhatAPeriodEarned forAPeriodWhoseLowestBalanceWas(
            int headlineRateBasisPoints, int bonusRateBasisPoints, long floorCents,
            long lowestDailyBalanceCents) {
        boolean earned = bonusRateBasisPoints > 0 && lowestDailyBalanceCents >= floorCents;
        return new WhatAPeriodEarned(
                headlineRateBasisPoints + (earned ? bonusRateBasisPoints : 0), earned);
    }
}
