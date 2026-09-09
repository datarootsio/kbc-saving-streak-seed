package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.deposits.RecordedDeposit;
import io.dataroots.savingstreak.loyalty.NextAnniversaryOfADeposit;

/**
 * A deposit as the customer sees it: how much moved, what it earned and how, the rate it was paid
 * at, when it happened, and when it next pays.
 *
 * <p>{@code pointsEarned} is the total credited, which is what it has always been: every deposit
 * before this scheme existed was paid at the ordinary rate, so the figure is unchanged for all of
 * them. {@code basePoints}, {@code streakBonusPoints} and {@code loyaltyBonusPoints} always sum to
 * it, so a customer can check the arithmetic rather than take the total on trust.
 *
 * <p>{@code loyaltyBonusPoints} is every anniversary this deposit has been paid, added up. It is
 * the one figure here that grows after the money moved — a deposit left alone is paid again every
 * twelve months — so the total answers "what has this deposit been worth to me" while the base and
 * the bonus still answer "what did it earn when it landed", unchanged.
 *
 * <p>{@code nextAnniversaryOn} and {@code nextAnniversaryPoints} are the promise rather than the
 * record: the day this deposit next pays, and what that day is worth at what the deposit holds
 * today. The figure falls when the customer withdraws from the deposit, which is what makes the cost
 * of a withdrawal legible after they make one, and it is nothing at all for a deposit holding under
 * ten euros — a tenth of nine euros rounds down, and the rule is shown rather than hidden.
 *
 * <p>Both are null for a deposit that has been emptied, and both together. There is no anniversary
 * left for money that has gone to reach, so "no next anniversary" and "an anniversary worth nothing"
 * are different statements — the same distinction the account's overview draws between having
 * nothing left to expire and having nothing expiring on some particular day.
 *
 * <p>The day travels as a plain date rather than as a moment, for the reason the overview's expiry
 * date gives: which calendar day a moment falls on depends on the zone it is read in, and the
 * Loyalty module has already read it in the one zone this application counts calendars in.
 */
record DepositResponse(Long id, BigDecimal amount, long pointsEarned, long basePoints,
                       long streakBonusPoints, long loyaltyBonusPoints,
                       BigDecimal multiplierApplied, Instant depositedAt,
                       LocalDate nextAnniversaryOn, Long nextAnniversaryPoints) {

    /**
     * One deposit and the anniversary it has coming, which is absent exactly when the deposit holds
     * no money.
     *
     * <p>Assembled here rather than answered by either module, because neither of them can: what a
     * deposit earned is the points ledger's answer through Deposits, and when it next pays is
     * Loyalty's, and the two do not know about each other. Putting them side by side is not a rule
     * and no figure is worked out in this class.
     */
    static DepositResponse of(RecordedDeposit deposit, NextAnniversaryOfADeposit nextAnniversary) {
        return new DepositResponse(
                deposit.id(), deposit.amount(), deposit.pointsEarned(), deposit.basePoints(),
                deposit.streakBonusPoints(), deposit.loyaltyBonusPoints(),
                deposit.multiplierApplied(), deposit.depositedAt(),
                nextAnniversary == null ? null : nextAnniversary.on(),
                nextAnniversary == null ? null : nextAnniversary.points());
    }
}
