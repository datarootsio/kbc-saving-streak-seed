package io.dataroots.savingstreak.clock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

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
 * <p>The calendar is consulted once, when the clock is moved, and what is kept is the span it worked
 * out. Between moves the reading is the real clock plus that fixed span, so it runs at exactly the
 * speed of the real one and can never turn round: consulting the calendar on every reading would,
 * because a local time that lands in the hour a spring clock change skips resolves forward to the
 * far side of the gap and then stops moving until the real clock has crossed it — an hour in which
 * the reading falls back by up to fifty-nine minutes as real time passes. Two deposits made a minute
 * apart would be stamped out of the order they happened in.
 *
 * <p>Moving it again cannot turn it round either: a move is refused unless it adds at least one more
 * day (see {@link ClockService}), and one calendar day is at least twenty-three hours, so the span
 * only ever grows.
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
    private final AtomicReference<HowFarForward> movedForward;

    MovableClock(Clock realClock, ZoneId daysAreCountedIn) {
        this(realClock, daysAreCountedIn, new AtomicReference<>(HowFarForward.NOT_MOVED));
    }

    private MovableClock(Clock realClock, ZoneId daysAreCountedIn,
            AtomicReference<HowFarForward> movedForward) {
        this.realClock = realClock;
        this.daysAreCountedIn = daysAreCountedIn;
        this.movedForward = movedForward;
    }

    @Override
    public Instant instant() {
        // The real moment plus a span that was worked out through the calendar when the clock was
        // moved. No calendar arithmetic here: this runs on every deposit, every claim and every read
        // of an account, and — the reason it matters — a reading that is the real one plus a
        // constant is a reading that moves forward exactly as the real clock does.
        return realClock.instant().plus(movedForward.get().by());
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
        return new MovableClock(realClock.withZone(zone), daysAreCountedIn, movedForward);
    }

    /** How far forward of the real clock this one currently reads, in whole days. */
    long movedForwardByDays() {
        return movedForward.get().days();
    }

    /**
     * The span those days came to through the calendar, which is what a reading is actually made of.
     * For the log: seven days that came to 169 hours are a week containing a clock change, and
     * without the span a reader has to work that out from two readings.
     */
    Duration movedForwardBy() {
        return movedForward.get().by();
    }

    /**
     * Puts the clock a given number of days ahead of the real one — the total, not a step, because
     * the total is the figure that is written down and reported back.
     *
     * <p>This is where the calendar is read: those days become the span between the real moment now
     * and the moment that many calendar days on from it, and that span is what every later reading
     * adds.
     */
    void moveForwardTo(long days) {
        movedForward.set(HowFarForward.thatMany(days, realClock.instant(), daysAreCountedIn));
    }

    /**
     * Says which clock this is and where it is standing, for the start-up line that tells whoever
     * reads a wound-forward log whether the application is on a movable clock at all. The span is
     * written out beside the days because it is the figure the readings are actually made of, and
     * the two differ by an hour across a clock change.
     */
    @Override
    public String toString() {
        HowFarForward moved = movedForward.get();
        return "MovableClock[" + realClock + " moved forward " + moved.days()
                + " calendar days in " + daysAreCountedIn + ", which is " + moved.by() + "]";
    }

    /**
     * How far forward the clock stands: the whole days it was asked for, and the fixed span through
     * the calendar that realises them.
     *
     * <p>Both, held together and replaced together, because they are two statements of one position
     * and a reader of the log has to be able to trust that they agree. The days are what is asked
     * for, written down and reported; the span is what a reading is made of.
     */
    private record HowFarForward(long days, Duration by) {

        /** A clock nobody has moved, which is the real clock and touches no calendar at all. */
        static final HowFarForward NOT_MOVED = new HowFarForward(0, Duration.ZERO);

        static HowFarForward thatMany(long days, Instant realMomentOfTheMove, ZoneId daysAreCountedIn) {
            if (days == 0) {
                return NOT_MOVED;
            }
            Instant thatManyDaysOn = realMomentOfTheMove.atZone(daysAreCountedIn)
                    .plusDays(days)
                    .toInstant();
            return new HowFarForward(days, Duration.between(realMomentOfTheMove, thatManyDaysOn));
        }
    }
}
