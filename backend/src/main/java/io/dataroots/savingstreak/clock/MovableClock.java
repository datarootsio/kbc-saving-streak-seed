package io.dataroots.savingstreak.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
 * <p>How far it has moved lives in memory, so that reading the time costs nothing — every deposit
 * and every claim reads it. What survives a restart is written down separately, by
 * {@link ClockService}, and read back into here by {@link ClockOnStartUp} before the application
 * serves anything.
 */
final class MovableClock extends Clock {

    private final Clock realClock;

    /**
     * Shared with every re-zoned copy of this clock, and atomic because the moving is done by
     * whoever calls the development endpoint while requests are being served off the same object.
     */
    private final AtomicLong movedForwardByDays;

    MovableClock(Clock realClock) {
        this(realClock, new AtomicLong());
    }

    private MovableClock(Clock realClock, AtomicLong movedForwardByDays) {
        this.realClock = realClock;
        this.movedForwardByDays = movedForwardByDays;
    }

    @Override
    public Instant instant() {
        // Days rather than a stored Duration: the amount moved is a whole number of days everywhere
        // it is asked about, written down or reported, and one representation cannot disagree with
        // itself. Instant counts a day as 86400 seconds, which is what "a year on" means here — this
        // clock has no zone and so has no summer time to be wrong about.
        return realClock.instant().plus(movedForwardByDays.get(), ChronoUnit.DAYS);
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
        return new MovableClock(realClock.withZone(zone), movedForwardByDays);
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
        return "MovableClock[" + realClock + " moved forward " + movedForwardByDays.get() + " days]";
    }
}
