package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.products.AnInterestPosting;

/**
 * One month of interest as the screen reads it: which period it was, the days it covered, the
 * balance it was worked out on, the rate it was paid at, the version that rate came from, and what
 * it paid.
 *
 * <p><strong>Everything needed to check the arithmetic, because that is what this panel is
 * for.</strong> A figure on its own is a number the customer has to take on trust; the period, the
 * average balance and the rate together are a sentence they can redo by hand — divide the rate by
 * twelve, multiply by the balance, floor it. In a training application about how saving is
 * rewarded, a reward nobody can check is the one thing worth not shipping.
 *
 * <p><strong>The rate is the account's own and not the product's.</strong> An account opened before
 * free savings was repriced goes on being paid at the rate it was written with, so this figure and
 * the one on the catalogue's card can differ — which is the whole feature rather than a bug in it.
 * {@code termsVersion} is on the row for exactly that reason: it is the address of the agreement
 * this month was paid under, and it lets a reader put the payment beside the version that decided
 * it.
 *
 * <p><strong>{@code lowestDailyBalance} and {@code bonusEarned} are reported before anything pays
 * for them.</strong> The walk that found the average found the lowest for nothing, and it is the
 * figure a floor is judged on; showing it now means a customer on a minimum-balance product can
 * already see how close they came. {@code bonusEarned} is false on every row until the slice that
 * pays for keeping a floor lands, because there is no bonus on offer to earn.
 *
 * <p>The period travels as two plain days, and the second is the day the period <em>ended</em>
 * rather than its last day — the half-open reading the backend uses everywhere, so two consecutive
 * months meet rather than overlap. A screen printing "20 Jan – 19 Feb" takes a day off; a screen
 * putting two periods end to end does not have to.
 *
 * <p>Days rather than moments, for the reason every other date on these responses gives: which
 * calendar day a moment falls on depends on the zone it is read in, and the backend has already
 * read it in the one zone this application counts its calendars in.
 */
record InterestPostingResponse(int periodOrdinal, LocalDate from, LocalDate until,
                               BigDecimal averageDailyBalance, BigDecimal lowestDailyBalance,
                               BigDecimal annualRatePercent, boolean bonusEarned, int termsVersion,
                               BigDecimal interest, Instant postedAt) {

    /** The module's own reading, turned into the one the screen gets, figure for figure. */
    static InterestPostingResponse of(AnInterestPosting posting) {
        return new InterestPostingResponse(posting.periodOrdinal(), posting.from(), posting.until(),
                posting.averageDailyBalance(), posting.lowestDailyBalance(),
                posting.annualRatePercent(), posting.bonusEarned(), posting.termsVersion(),
                posting.interest(), posting.postedAt());
    }
}
