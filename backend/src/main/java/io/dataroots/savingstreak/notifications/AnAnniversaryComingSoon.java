package io.dataroots.savingstreak.notifications;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * How near an anniversary has to be before it is worth saying anything about — the window, and the
 * only place its length is written down.
 *
 * <p>Thirty days. Ninety was rejected as unread wallpaper on a twelve-month cycle: a warning that
 * arrives a quarter of the way through the year is a warning a customer has forgotten by the time
 * the money is actually exposed, and one that would be true for a third of every deposit's life.
 * Seven was rejected as too late to act on — a customer who wants to leave the money where it is
 * for one more week has to hear about it before they have already moved it.
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
 * further off than thirty days, which is exactly the reason the sweep logs.
 *
 * <p>A window and nothing else. What an anniversary falls on and what it is worth are Loyalty's
 * answers and are never worked out again here.
 */
final class AnAnniversaryComingSoon {

    /**
     * How long before an anniversary it becomes worth telling the customer about it.
     *
     * <p>Days rather than a month, so that the window is the same length in February as in July: a
     * customer told a month ahead in one part of the year and twenty-eight days ahead in another
     * would be reading a rule that shifted under them.
     */
    static final Period HOW_LONG_BEFORE_AN_ANNIVERSARY_IS_WORTH_SAYING = Period.ofDays(30);

    private AnAnniversaryComingSoon() {
    }

    /**
     * Whether an anniversary falling on this day is near enough to be worth saying, as at the moment
     * the sweep was told about.
     *
     * <p>The moment comes from the caller, as every moment in this application does: a sweep run
     * against a clock a trainer has wound forward has to judge its window against the day the
     * application thinks it is rather than the day the machine is having.
     */
    static boolean isWorthSayingAsAt(LocalDate anniversary, Instant now) {
        return !anniversary.isAfter(theLastDayWorthSayingAsAt(now));
    }

    /**
     * The furthest-off anniversary day still worth saying something about, as at that moment — the
     * window's one boundary, named so that the sweep can put it in the line explaining a deposit it
     * passed over.
     *
     * <p>Inclusive: an anniversary exactly thirty days out is thirty days out and not thirty-one.
     */
    static LocalDate theLastDayWorthSayingAsAt(Instant now) {
        return now.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate()
                .plus(HOW_LONG_BEFORE_AN_ANNIVERSARY_IS_WORTH_SAYING);
    }
}
