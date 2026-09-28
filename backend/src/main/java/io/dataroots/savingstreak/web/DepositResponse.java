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
 * <p>{@code newSavings} is how much of the amount was new saving, and so how much of it earned. It
 * is the whole amount for anybody who has never taken money back out of savings, which is nearly
 * every deposit there is. It is less when this deposit is filling a gap an earlier withdrawal left:
 * those euros earned their points the first time they were saved, and a euro saved twice is one
 * euro. It is sent on every deposit rather than only on the short ones, so that a screen can say why
 * a deposit earned what it earned without working anything out for itself.
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
 * <p>The day is the one the deposit next pays on rather than the next one its calendar reaches, and
 * the two differ for the hours between an anniversary falling and the overnight sweep paying it. A
 * deposit whose anniversary was this lunchtime reports that day, still to be paid, rather than the
 * same day next year: a date in the past here means a bonus is owed and coming.
 *
 * <p>Both are null for a deposit that has been emptied, and both together. There is no anniversary
 * left for money that has gone to reach, so "no next anniversary" and "an anniversary worth nothing"
 * are different statements — the same distinction the account's overview draws between having
 * nothing left to expire and having nothing expiring on some particular day.
 *
 * <p>{@code productMultiplierApplied} is the part of that rate the account's savings product
 * accounts for, on its own: {@code 1.00} where the product changes nothing, which is what free
 * savings pays and what every deposit made before there were products was paid. The two are sent
 * apart so that a customer reading a row can see which of the two schemes earned them what — a run
 * of weeks is something they did, and a product is something they chose — and because neither
 * figure can be worked out from the other: 1.375 is 1.10 times 1.25 and equally 1.25 times 1.10.
 * Sent on every deposit rather than only where it is interesting, for the reason {@code newSavings}
 * is: a history whose rows change shape is a history a page has to render defensively.
 *
 * <p>Neither rate says which points came from which factor, and no field here does. The base points
 * are the euros and the streak bonus is the whole of the uplift over them, exactly as before, because
 * two factors that multiply have no shares to divide an uplift into. The rates say what each factor
 * was, which is a fact; the split of an uplift between them would be an invention.
 *
 * <p>{@code termsVersion} is which version of the account's savings product terms this deposit
 * landed under. It is the same kind of statement as {@code multiplierApplied} beside it: what was
 * in force when the money arrived, not what is in force now. The day an account takes its product's
 * newer terms, every row already in this history goes on naming the agreement it was actually
 * priced under, which is the whole reason the version is written on the deposit rather than read
 * off the account.
 *
 * <p>It is null for a deposit nothing has stamped — one recorded before there were agreements and
 * not yet reached by the start-up migration. Null rather than a one, because a version nobody
 * recorded is not a version: reporting "version 1" would be inventing the agreement the money was
 * priced under, which is a different kind of claim from reporting the ordinary rate for a deposit
 * that really was paid at it.
 *
 * <p>The day travels as a plain date rather than as a moment, for the reason the overview's expiry
 * date gives: which calendar day a moment falls on depends on the zone it is read in, and the
 * Loyalty module has already read it in the one zone this application counts calendars in.
 */
record DepositResponse(Long id, BigDecimal amount, BigDecimal newSavings, long pointsEarned,
                       long basePoints, long streakBonusPoints, long loyaltyBonusPoints,
                       BigDecimal multiplierApplied, BigDecimal productMultiplierApplied,
                       Integer termsVersion, Instant depositedAt,
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
                deposit.id(), deposit.amount(), deposit.newSavings(), deposit.pointsEarned(),
                deposit.basePoints(),
                deposit.streakBonusPoints(), deposit.loyaltyBonusPoints(),
                deposit.multiplierApplied(), deposit.productMultiplierApplied(),
                deposit.termsVersion(), deposit.depositedAt(),
                nextAnniversary == null ? null : nextAnniversary.on(),
                nextAnniversary == null ? null : nextAnniversary.points());
    }
}
