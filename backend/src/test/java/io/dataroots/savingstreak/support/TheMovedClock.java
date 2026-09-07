package io.dataroots.savingstreak.support;

import java.time.Instant;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * The window a reading off the development clock can fall in, for a test that read the real clock
 * either side of taking it.
 *
 * <p>Whole days, counted through the calendar of the zone the application counts its days in, which
 * is what the clock itself does when it is moved: a span containing a clock change is an hour longer
 * or shorter than a fixed seven times 86400 seconds, and a test that asserted the fixed span would
 * be an hour out whenever the real clock happened to be standing that close to the end of March or
 * of October.
 *
 * <p>A window rather than a pair of bounds in the order they were read, because the calendar move is
 * not quite monotone in the real moment it starts from: a local time that lands in the hour a spring
 * clock change skips resolves forward past the gap, so counting from a moment a millisecond later
 * can land an hour earlier. Once a year, for a millisecond, "before" and "after" would come out the
 * wrong way round and a bracket built from them would be unsatisfiable. Taken as the earlier and the
 * later of the two, it is a bracket either way — and an hour's slack once a year is a far better
 * trade than a test that fails for reasons no reader could reconstruct.
 *
 * <p>Shared by every test that says where a moved clock should read, for the same reason as
 * {@link BalancesView}: three copies of this arithmetic would be three chances to disagree about
 * what a day is.
 */
public final class TheMovedClock {

    private TheMovedClock() {
    }

    /**
     * The earliest a clock standing that many days on can read, for real moments read either side of
     * the reading.
     */
    public static Instant earliestReadingOf(Instant realMomentBefore, Instant realMomentAfter, long days) {
        Instant fromBefore = daysOnFrom(realMomentBefore, days);
        Instant fromAfter = daysOnFrom(realMomentAfter, days);
        return fromBefore.isBefore(fromAfter) ? fromBefore : fromAfter;
    }

    /** The latest it can read, over the same pair. */
    public static Instant latestReadingOf(Instant realMomentBefore, Instant realMomentAfter, long days) {
        Instant fromBefore = daysOnFrom(realMomentBefore, days);
        Instant fromAfter = daysOnFrom(realMomentAfter, days);
        return fromBefore.isAfter(fromAfter) ? fromBefore : fromAfter;
    }

    /** The given real moment, that many calendar days on. */
    private static Instant daysOnFrom(Instant realMoment, long days) {
        return realMoment.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).plusDays(days).toInstant();
    }
}
