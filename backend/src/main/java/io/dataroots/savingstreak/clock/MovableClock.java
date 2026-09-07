package io.dataroots.savingstreak.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A clock that runs at the speed of the real one, a chosen number of days further on.
 *
 * <p>It is the whole point of the application reading its moments from a clock it was given: a rule
 * measured in months — points expiring, a loyalty bonus vesting — cannot be reached in a training day
 * against a clock that only moves at the speed of the day. Moved forward a year, every part of the
 * application that dates a record moves with it, because they all read this one object.
 *
 * <p>Forward only, and in whole days. Backwards would let a demonstration produce records dated
 * before ones already written, which is a database nobody can explain rather than a lesson; whole
 * days are the unit the rules being demonstrated are written in.
 *
 * <p>A whole day is a calendar day, in the zone this application's calendar rules are counted in —
 * not a fixed 86400 seconds. The two differ by an hour in any span containing a clock change, and
 * where they differ this clock has to be the calendar: "advance seven days" is asked for in order to
 * reach the next week, and a week is Monday to Sunday in Brussels. Seven times 86400 seconds landing
 * at 23:30 on the Sunday is a trainer pressing the button for the next week and being shown the week
 * they were already in.
 *
 * <p>How far it has moved lives in memory, so that reading the time costs nothing — every deposit
 * and every claim reads it. What survives a restart is written down separately, by
 * {@link ClockService}, and read back into here by {@link ClockOnStartUp} before the application
 * serves anything.
 */
final class MovableClock extends Clock {

    private final Clock realClock;

    /**
     * The zone the whole days this clock moves in are counted in. Not the zone the clock reads in —
     * that is {@link #getZone()}, and every moment this application keeps is a zoneless
     * {@link Instant} — but the calendar a day is a day of.
     */
    private final ZoneId daysAreCountedIn;

    /**
     * Shared with every re-zoned copy of this clock, and atomic because the moving is done by
     * whoever calls the development endpoint while requests are being served off the same object.
     */
    private final AtomicLong movedForwardByDays;

    MovableClock(Clock realClock, ZoneId daysAreCountedIn) {
        this(realClock, daysAreCountedIn, new AtomicLong());
    }

    private MovableClock(Clock realClock, ZoneId daysAreCountedIn, AtomicLong movedForwardByDays) {
        this.realClock = realClock;
        this.daysAreCountedIn = daysAreCountedIn;
        this.movedForwardByDays = movedForwardByDays;
    }

    @Override
    public Instant instant() {
        // Days rather than a stored Duration: the amount moved is a whole number of days everywhere
        // it is asked about, written down or reported, and one representation cannot disagree with
        // itself.
        long days = movedForwardByDays.get();
        Instant realMoment = realClock.instant();
        if (days == 0) {
            // A clock nobody has moved is the real clock, read without touching a calendar at all.
            return realMoment;
        }
        // Through the calendar rather than by adding 86400 seconds a day, so that the moved clock
        // reads the same time of day it really is, on a date that many days on. The span between the
        // two is an hour longer or shorter than a fixed one wherever it contains a clock change, and
        // it is the calendar that the rules being demonstrated — a week, the age of a deposit — are
        // written in.
        //
        // The cost is that the reading is not quite a continuous function of the real one: for the
        // real hour that maps into a clock change, it can repeat or skip an hour. Which is bounded
        // by the hour and cannot move a record to another date, because a clock change happens at
        // 02:00 or 03:00 local and never at midnight — so no record moves into another day, and no
        // week gains or loses a deposit.
        return realMoment.atZone(daysAreCountedIn).plusDays(days).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return realClock.getZone();
    }

    /**
     * The same movable clock in another zone. It shares this one's position rather than copying it,
     * so a caller that re-zoned the clock is not left behind the next time it is moved.
     */
    @Override
    public Clock withZone(ZoneId zone) {
        // The zone it is read in changes; the calendar its days are counted in does not. Two copies
        // of this clock read in different zones still have to agree about what moment it is, and a
        // day counted differently by each of them would break that.
        return new MovableClock(realClock.withZone(zone), daysAreCountedIn, movedForwardByDays);
    }

    /** How far forward of the real clock this one currently reads, in whole days. */
    long movedForwardByDays() {
        return movedForwardByDays.get();
    }

    /**
     * Puts the clock a given number of days ahead of the real one — the total, not a step, because
     * the total is the figure that is written down and reported back.
     */
    void moveForwardTo(long days) {
        movedForwardByDays.set(days);
    }

    /**
     * Says which clock this is and where it is standing, for the start-up line that tells whoever
     * reads a wound-forward log whether the application is on a movable clock at all.
     */
    @Override
    public String toString() {
        return "MovableClock[" + realClock + " moved forward " + movedForwardByDays.get()
                + " calendar days in " + daysAreCountedIn + "]";
    }
}
