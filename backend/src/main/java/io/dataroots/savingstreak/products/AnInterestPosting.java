package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A month of interest as the rest of the application reads it: which period it was, the days it
 * covered, the balance it was worked out on, the rate it was paid at, the version that rate came
 * from, and what it paid.
 *
 * <p><strong>Everything needed to check the arithmetic by hand, on one row.</strong> That is the
 * whole reason this carries six figures rather than two. "You were paid EUR 0,50" is a number a
 * customer has to take on trust; "the 20th of January to the 20th of February, on an average
 * balance of EUR 1.200,00, at 0.50% a year" is a sentence they can redo — divide the rate by
 * twelve, multiply, floor — and a training application whose whole subject is how saving is
 * rewarded should never ask anybody to take a figure on trust.
 *
 * <p><strong>The rate is the one the account was on, not the one its product sells today.</strong>
 * A version published since is a different agreement, and this says what this month was actually
 * paid under. {@link #termsVersion} is the address of it, so a reader can go and look up what else
 * that version said.
 *
 * <p><strong>The lowest balance and the bonus are what explain a month that paid less.</strong>
 * The walk that found the average found the lowest for nothing, and the condition a minimum-balance
 * account has to keep is judged on exactly that figure — so a customer reading a month at the
 * headline rate can see how close they came, on the row that was paid, without anybody re-walking a
 * ledger that has moved since. {@link #annualRatePercent} is the rate that was actually paid rather
 * than the one the product advertises, so the flag and the rate cannot disagree:
 * {@link #bonusEarned} is true exactly when the bonus is inside that figure. It is false on a
 * product with no bonus to offer, which is three of the four, because no bonus was earned there
 * either.
 *
 * <p>The period is a pair of days rather than a month's name, and the second of them is the day the
 * period <em>ended</em> rather than its last day — the half-open reading every other stretch of
 * time in this application uses, so that two consecutive months agree about the day between them. A
 * screen that wants to print "20 Jan – 19 Feb" takes a day off; a screen that wants to put two
 * periods end to end does not have to.
 *
 * <p>Euros and a percentage, never cents and never basis points: the units this module's boundary
 * converts at, the same as every other record that leaves it.
 */
public record AnInterestPosting(

        /** Which of the account's periods this was, counting from one. */
        int periodOrdinal,

        /** The first day it covers. */
        LocalDate from,

        /** The day it ended, which it does not itself cover. */
        LocalDate until,

        /** The average daily balance across it, which the interest was worked out from. */
        BigDecimal averageDailyBalance,

        /** The lowest the balance went on any day of it, which the floor is judged on. */
        BigDecimal lowestDailyBalance,

        /**
         * The rate it was paid at, per year, as a percentage — 0.50 is 0.50%, and the headline rate
         * and the bonus already added together on a month that earned the bonus.
         */
        BigDecimal annualRatePercent,

        /** Whether the bonus rate's condition was kept, and false where there was no bonus. */
        boolean bonusEarned,

        /** Which version of the account's terms that rate came from. */
        int termsVersion,

        /** What the month paid, floored to the cent, and nought for a month that paid nothing. */
        BigDecimal interest,

        /** The moment the sweep judged it, which is the only thing here about when rather than what. */
        Instant postedAt) {
}
