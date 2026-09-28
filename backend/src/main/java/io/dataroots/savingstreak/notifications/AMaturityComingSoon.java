package io.dataroots.savingstreak.notifications;

import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * How near a term's maturity has to be before it is worth saying anything about — the window, and
 * the only place the reading of it is written down.
 *
 * <p><strong>The length arrives as an argument, from the scheme in force on the night the sweep
 * runs.</strong> Thirty days is the spec's own figure and what version 1 of the scheme is seeded at;
 * it is no longer written down here, and neither is the window {@link AnAnniversaryComingSoon} opens
 * on an anniversary. <strong>The deliberate duplication survives the move, and that is the part
 * worth checking</strong>: the scheme publishes two separate counts of days, one for a maturity and
 * one for an anniversary, so that shortening the loyalty warning still cannot silently shorten the
 * notice a customer gets before a year of their money is rolled into another year of it. Two columns
 * rather than one, for the reason there were two constants. The two windows are the same length
 * today by coincidence of judgement rather than by rule, and the two classes sit beside each other
 * so that the coincidence is visible to anybody changing either.
 *
 * <p><strong>The constant and the forms that read it are gone, and their going is a decision rather
 * than a tidy-up.</strong> They measured the window against a length written down here rather than
 * against the one the scheme published, and a bridge nobody removes is how two windows start
 * disagreeing — it would have gone on warning thirty days out after the bank published fourteen,
 * with nothing anywhere objecting, because thirty days is a perfectly good window. The warning is
 * now about the argument and is sharper for it: a caller that added its own days to a date would be
 * the second place this window is decided.
 *
 * <p><strong>Two boundaries, and this is where it parts company with the anniversary
 * window.</strong> An anniversary still outstanding when the sweep looks is <em>owed</em> — the
 * loyalty sweep pays at half past three and this one runs at four — so that window has a far edge
 * and no near one. A maturity is the opposite: {@code MaturitiesService} settles every maturity that
 * has arrived at a quarter past three, by the ending the account agreed to at the beginning, and by
 * four o'clock the decision has been taken. Telling a customer their term matures on a day that has
 * gone would be a letter about something already done, and on a term left waiting — which keeps its
 * passed maturity date for the rest of its life — it would be that letter every night for ever. So
 * the day itself is outside the window as well as every day before it.
 *
 * <p>Judged in {@code Europe/Brussels}, the zone every calendar rule in this application is decided
 * in, because a maturity is a day rather than a moment: the date comes out of
 * {@code TheTermAnAccountIsLockedInto} as a day in that zone, and comparing it against a day worked
 * out in any other zone would put the boundary a few hours out of step with the sweep that settles.
 *
 * <p>A window and nothing else. When a term is up is Products' answer and is never worked out again
 * here.
 */
final class AMaturityComingSoon {

    private AMaturityComingSoon() {
    }

    /**
     * Whether a term maturing on this day is near enough, and still far enough off, to be worth
     * saying something about as at the moment the sweep was told about, with the window the stated
     * number of days long.
     *
     * <p>The moment comes from the caller, as every moment in this application does: a sweep run
     * against a clock a trainer has wound forward has to judge its window against the day the
     * application thinks it is rather than the day the machine is having. The length comes from the
     * caller for the same kind of reason — a window is a figure the bank publishes, not one this
     * class knows.
     *
     * <p>Both boundaries, for the reason argued above, and whatever the window's length: the day
     * itself is outside it as well as every day before it, because by four o'clock a maturity that
     * has arrived has already been settled and a letter about it would be a letter about something
     * already done. Nothing about the rule changed with the figure — only where the figure comes
     * from.
     *
     * <p>Days rather than a month, so that the window is the same length in February as in July: a
     * customer warned a month ahead in one part of the year and twenty-eight days ahead in another
     * would be reading a rule that shifted under them. That argument is about the unit and it
     * survives the length becoming a published figure — the scheme carries a count of days for this
     * window and never a number of months.
     *
     * @param daysBeforeItIsWorthSaying how many days ahead the window reaches, from the scheme in
     *                                  force on the night the sweep runs; days and never months,
     *                                  which is the unit the scheme publishes it in
     */
    static boolean isWorthSayingAsAt(LocalDate maturesOn, Instant now,
                                     int daysBeforeItIsWorthSaying) {
        LocalDate today = theDayItIs(now);
        return maturesOn.isAfter(today)
                && !maturesOn.isAfter(theLastDayWorthSayingAsAt(now, daysBeforeItIsWorthSaying));
    }

    /**
     * The furthest-off maturity still worth saying something about, as at that moment, with the
     * window the stated number of days long — the far edge, named so that the sweep can put it in
     * the line explaining a term it passed over.
     *
     * <p>Inclusive: a term exactly that many days off is that many days off and not one more.
     * Counted in {@code Europe/Brussels}, the zone every calendar rule in this application is
     * decided in, and counted in days so that the window is the same length in February as in July.
     *
     * @param daysBeforeItIsWorthSaying how many days ahead the window reaches, from the scheme in
     *                                  force on the night the sweep runs
     */
    static LocalDate theLastDayWorthSayingAsAt(Instant now, int daysBeforeItIsWorthSaying) {
        return theDayItIs(now).plusDays(daysBeforeItIsWorthSaying);
    }

    /**
     * The day the application is standing on, in the one zone it counts its calendars in — the near
     * edge of the window as well as the thing the far edge is measured from, which is why it is
     * named rather than inlined twice.
     */
    static LocalDate theDayItIs(Instant now) {
        return now.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }
}
