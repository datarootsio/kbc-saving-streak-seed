package io.dataroots.savingstreak.support;

import java.time.Instant;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * Where a moment lands once the development clock has been moved forward a number of days.
 *
 * <p>Whole days, counted through the calendar of the zone the application counts its days in, which
 * is what the clock itself does: a span containing a clock change is an hour longer or shorter than
 * a fixed seven times 86400 seconds, and a test that asserted the fixed span would be an hour out
 * whenever the real clock happened to be standing that close to the end of March or of October.
 *
 * <p>Shared by every test that says where a moved clock should read, for the same reason as
 * {@link BalancesView}: three copies of this arithmetic would be three chances to disagree about
 * what a day is.
 */
public final class TheMovedClock {

    private TheMovedClock() {
    }

    /** The given real moment, that many calendar days on. */
    public static Instant daysOnFrom(Instant realMoment, long days) {
        return realMoment.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).plusDays(days).toInstant();
    }
}
