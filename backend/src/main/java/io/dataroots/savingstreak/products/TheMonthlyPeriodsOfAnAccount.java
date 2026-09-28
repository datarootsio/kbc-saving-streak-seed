package io.dataroots.savingstreak.products;

import java.time.LocalDate;
import java.time.Period;

/**
 * An account's monthly interest periods: when the <em>n</em>-th one begins and ends, and how many
 * of them have gone by.
 *
 * <p>The only thing in this module that answers "which month is this account in". A period is named
 * once here, in the same way twelve months is named once in {@code LoyaltyAnniversary}, so a
 * training exercise that paid interest quarterly would have one figure to change.
 *
 * <p><strong>Counted from the day the account was opened rather than from the calendar
 * month.</strong> An account opened on the 20th is paid for the 20th to the 20th, not for whatever
 * remains of the month it happened to be opened in. A calendar month would make everybody's first
 * period a stub of a few days paying a few cents, which is a thing to explain rather than a thing
 * to read — and it would make the period an account is in a fact about the calendar rather than
 * about the agreement, which is the opposite of what this whole module is for.
 *
 * <p><strong>One month multiplied by <em>n</em> and added once, never added <em>n</em> times, and
 * this is the rule {@code LoyaltyAnniversary} already states rather than a second statement of
 * it.</strong> February clamping is not associative: the 31st of January plus one month is the 28th
 * of February, and the 28th plus one month is the 28th of March — so adding a month twelve times
 * walks an account opened on the 31st permanently back to the 28th and keeps it there. Multiplied
 * out from the original day, the clamp applies to the one addition and undoes itself the moment the
 * next long month comes round: the 31st of January plus two months is the 31st of March, which is
 * the answer somebody reading a calendar would give.
 *
 * <p>Days rather than moments, because a period is a stretch of days and the balance it is worked
 * out on is a balance per day. The zone a day is read in is settled once, where a moment becomes a
 * day, and never here.
 *
 * <p>A class of its own rather than methods on the sweep, for {@link TheTermsOnOfferToday}'s
 * reason: it is a pure function over a date and a number, its interesting cases are calendar cases
 * that an API test would need years of clock-winding to reach, and there is nothing to construct
 * and nothing to inject.
 *
 * <p><strong>Public, and widened on purpose rather than by drift.</strong>
 * The what-if simulator has to know which day of its walk each projected month closes on, and
 * a fold counting months of its own would be the second place this application decides when an
 * account is paid — so a projection and a sweep would fall a day apart in February.
 * The reason it is safe is the reason {@code LoyaltyRate} gives for the same move: there is nothing
 * of this module's machinery in it — no repository, no entity, no clock, no state, just a rule
 * about numbers. A module that calls it is not reading Products; it is quoting a rule Products
 * wrote down, which is the only way a projection and a sweep can be kept from disagreeing.
 */
public final class TheMonthlyPeriodsOfAnAccount {

    /** How often an account is paid, in one place. */
    private static final Period HOW_OFTEN_AN_ACCOUNT_IS_PAID = Period.ofMonths(1);

    private TheMonthlyPeriodsOfAnAccount() {
    }

    /**
     * The day the <em>n</em>-th period begins, which is the day the one before it ended.
     *
     * <p>The first period begins on the day the account was opened, so that a deposit made that
     * morning is in the average from the first day rather than from the second.
     *
     * @throws IllegalArgumentException if asked for a period before the first, which nobody types
     *                                  and so can only be a mistake in the caller
     */
    public static LocalDate beginningOf(LocalDate openedOn, int ordinal) {
        if (ordinal < 1) {
            throw new IllegalArgumentException(
                    "an account's first interest period is its first, and this one was asked for "
                            + ordinal);
        }
        return openedOn.plus(HOW_OFTEN_AN_ACCOUNT_IS_PAID.multipliedBy(ordinal - 1));
    }

    /**
     * The day the <em>n</em>-th period ends, which is the day after its last day.
     *
     * <p>Half-open, like every other stretch of time in this application: the period covers its
     * first day and every day up to but not including this one, so two adjacent periods agree about
     * which of them owns the day between them and no day's balance is counted twice or not at all.
     */
    public static LocalDate endOf(LocalDate openedOn, int ordinal) {
        return beginningOf(openedOn, ordinal + 1);
    }

    /**
     * How many whole periods have gone by on a given day — the ordinal of the last one that has
     * ended, and none at all for an account still inside its first month.
     *
     * <p>A period that ends exactly today has gone by, which is what makes an account opened on the
     * 20th of January payable for its first month on the 20th of February rather than on the 21st.
     * The day it ends is the first day of the next period, so the account is never paid twice for
     * it.
     *
     * <p>Counted by walking the periods rather than by counting the months between two dates,
     * because the count is wrong in exactly the case the clamping exists for: the 31st of January
     * to the 28th of February is twenty-eight days and no whole month to any month-counting
     * arithmetic, while the period that ends that day is plainly the account's first and has
     * plainly ended. The walk asks {@link #endOf}, so there is one answer to "when does a period
     * end" and this is a question asked of that answer. It runs once per account per sweep and
     * takes one step per month the account has been open.
     *
     * <p>None at all for an account dated after the day being asked about, which a trainer who
     * wound the clock forward, opened an account and wound it back has left behind.
     */
    public static int periodsGoneBy(LocalDate openedOn, LocalDate on) {
        int gone = 0;
        while (!endOf(openedOn, gone + 1).isAfter(on)) {
            gone++;
        }
        return gone;
    }
}
