package io.dataroots.savingstreak.notifications;

import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * How near an anniversary has to be before it is worth saying anything about — the window, and the
 * only place the reading of it is written down.
 *
 * <p><strong>The length arrives as an argument, from the scheme in force on the night the sweep
 * runs.</strong> Thirty days is what version 1 of the scheme is seeded at and what this rule ran on
 * before the scheme had versions. Ninety was rejected as unread wallpaper on a twelve-month cycle: a
 * warning that arrives a quarter of the way through the year is a warning a customer has forgotten
 * by the time the money is actually exposed, and one that would be true for a third of every
 * deposit's life. Seven was rejected as too late to act on — a customer who wants to leave the money
 * where it is for one more week has to hear about it before they have already moved it. That is an
 * argument for what the bank should publish rather than for a figure written down here, which is why
 * the bank now publishes it.
 *
 * <p><strong>Its own published figure, not the maturity window's.</strong> The scheme carries two
 * separate counts of days, and the separation is the point: the two windows are the same length
 * today by coincidence of judgement rather than by rule, and one column for both would mean that
 * shortening this warning silently shortened the notice a customer gets before a year of their money
 * is rolled into another year of it. {@link AMaturityComingSoon} sits beside this class so that the
 * coincidence is visible to anybody changing either.
 *
 * <p><strong>The constant and the forms that read it are gone, and their going is a decision rather
 * than a tidy-up.</strong> They measured the window against a length written down here rather than
 * against the one the scheme published, and a bridge nobody removes is how two windows start
 * disagreeing — it would have gone on warning thirty days out after the bank published fourteen,
 * with nothing anywhere objecting, because thirty days is a perfectly good window. The warning is
 * now about the argument and is sharper for it: a caller that added its own days to a date would be
 * the second place this window is decided.
 *
 * <p>Judged in {@code Europe/Brussels}, the zone every calendar rule in this application is decided
 * in, because an anniversary is a day rather than a moment: the deposit's anniversary day comes out
 * of Loyalty as a day in that zone, and comparing it against a day worked out in any other zone
 * would put the boundary a few hours out of step with the rule that pays.
 *
 * <p>One boundary and not two. An anniversary the sweep has not yet been able to say anything about
 * is <em>owed</em> — the loyalty sweep pays at half past three and this one runs at four, so an
 * anniversary still outstanding when this looks is one whose money is exposed right now — and
 * saying nothing about it because the day has technically gone would be the one silence a customer
 * would call a bug. So the only reason the window passes a deposit over is that its anniversary is
 * further off than the window reaches, which is exactly the reason the sweep logs.
 *
 * <p>A window and nothing else. What an anniversary falls on and what it is worth are Loyalty's
 * answers and are never worked out again here.
 */
final class AnAnniversaryComingSoon {

    private AnAnniversaryComingSoon() {
    }

    /**
     * Whether an anniversary falling on this day is near enough to be worth saying, as at the moment
     * the sweep was told about, with the window the stated number of days long.
     *
     * <p>The moment comes from the caller, as every moment in this application does: a sweep run
     * against a clock a trainer has wound forward has to judge its window against the day the
     * application thinks it is rather than the day the machine is having. The length comes from the
     * caller for the same kind of reason — a window is a figure the bank publishes, not one this
     * class knows.
     *
     * <p>One boundary and not two, for the reason argued above: an anniversary the sweep has not yet
     * been able to say anything about is owed, whatever the window's length. Nothing about the rule
     * changed with the figure — only where the figure comes from.
     *
     * <p>Days rather than a month, so that the window is the same length in February as in July: a
     * customer told a month ahead in one part of the year and twenty-eight days ahead in another
     * would be reading a rule that shifted under them. That argument is about the unit and it
     * survives the length becoming a published figure: the scheme carries a count of days for this
     * window, and never a number of months.
     *
     * @param daysBeforeItIsWorthSaying how many days ahead the window reaches, from the scheme in
     *                                  force on the night the sweep runs; days and never months,
     *                                  which is the unit the scheme publishes it in
     */
    static boolean isWorthSayingAsAt(LocalDate anniversary, Instant now,
                                     int daysBeforeItIsWorthSaying) {
        return !anniversary.isAfter(theLastDayWorthSayingAsAt(now, daysBeforeItIsWorthSaying));
    }

    /**
     * The furthest-off anniversary day still worth saying something about, as at that moment, with
     * the window the stated number of days long — the window's one boundary, named so that the sweep
     * can put it in the line explaining a deposit it passed over.
     *
     * <p>Inclusive: an anniversary exactly that many days out is that many days out and not one
     * more. Counted in {@code Europe/Brussels}, the zone every calendar rule in this application is
     * decided in, and counted in days so that the window is the same length in February as in July.
     *
     * @param daysBeforeItIsWorthSaying how many days ahead the window reaches, from the scheme in
     *                                  force on the night the sweep runs
     */
    static LocalDate theLastDayWorthSayingAsAt(Instant now, int daysBeforeItIsWorthSaying) {
        return now.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate()
                .plusDays(daysBeforeItIsWorthSaying);
    }
}
