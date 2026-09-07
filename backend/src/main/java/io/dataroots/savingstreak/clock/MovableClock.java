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
 * <p>So the calendar is consulted once, by whoever is moving the clock, and what this class is handed
 * and keeps is the span it came to. Between moves a reading is the real clock plus that fixed span,
 * so it runs at exactly the speed of the real one and cannot turn round. Consulting the calendar on
 * every reading would turn it round: a local time landing in the hour a spring clock change skips
 * resolves forward to the far side of the gap and then stops moving until the real clock has crossed
 * it — an hour in which the reading falls back by up to fifty-nine minutes as real time passes, and
 * two deposits made a minute apart are stamped out of the order they happened in.
 *
 * <p>This class holds no opinion about which spans it is given, and that is deliberate: every span it
 * has ever stood at is worked out in one place, {@link ClockService}, which is also the place that
 * writes the span down and the only place that decides a move is allowed. The two ways the span can
 * be set therefore agree by construction — a move computes it and hands the same value to the record
 * and to this clock, and a restart hands back the value that was written down rather than a fresh one
 * ({@link ClockOnStartUp}). A fresh one is the trap, and it is a trap on both paths: the same number
 * of days worked out against a different real moment is an hour shorter on the far side of a clock
 * change, and an hour shorter is a rewind on the restart path and a week that fails to reset on the
 * move path. So a span is never worked out from scratch for a total — a move adds to the span it is
 * standing at what the days asked for come to from the moment it is reading (see
 * {@link #howFarForwardThatManyMoreDaysIs}), and a restart puts back what was written. The one span
 * that comes from neither is the one for a record written before spans were written down, and it is
 * not a fresh calendar answer either — it is the fixed 86 400 seconds a day that the build which
 * wrote that record was adding, so it too is a span some application really stood at. All this class
 * enforces is that a span is not itself backwards.
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
     * For the log, and for the record that has to survive a restart: seven days that came to 169
     * hours are a week containing a clock change, and without the span a reader has to work that out
     * from two readings.
     */
    Duration movedForwardBy() {
        return movedForward.get().by();
    }

    /**
     * The span this clock will stand at once it has moved that many more whole days on: the span it
     * is standing at now, plus what that many days come to counted through the calendar from the
     * moment it is now <em>reading</em>.
     *
     * <p>An increment on where the clock is, and not the whole span worked out afresh for the new
     * total number of days, because the calendar answer depends on the moment it is counted from and
     * that moment moves. Worked out afresh, a move forward is
     * {@code calendar(realNow, total) − calendar(realWhenLastMoved, previousTotal)}, which is the
     * days asked for only while real time has not crossed a clock change since the previous move —
     * and {@link ClockService} writes its position down, so coming back a fortnight later and
     * advancing again is the ordinary way to use this. Across an autumn change that difference is an
     * hour short: "advance seven days" moves the clock 167 hours, and a trainer standing half an hour
     * into a Monday is put at 23:30 on the Sunday of the week they were already in — the week not
     * resetting, which is the one thing counting days through a calendar exists to prevent. Counted
     * from the reading, each move is that many calendar days of the clock's own calendar, whatever
     * real time did in between.
     *
     * <p>Still monotone, which is the other promise: the span only ever grows, because a day counted
     * through this calendar is at least twenty-three hours. And still one span for one move — the
     * calendar is consulted once here, and the answer serves both the record and the clock.
     *
     * <p>Asked here because this is the object that knows the real clock, the span it is standing at
     * and the calendar its days belong to; answered rather than applied, because the span has to be
     * written down before the clock is moved to it — see {@link ClockService}. Reading it and moving
     * to it are two calls so that one value serves both the record and the clock, and a restart
     * therefore has the span the running application was using rather than one it works out again
     * for itself.
     */
    Duration howFarForwardThatManyMoreDaysIs(long days) {
        // One read of the position, so that the span the increment is added to is the same span the
        // reading it is counted from was made of. ClockService is synchronised and is the only
        // mover, so nothing shifts underneath this; taking it once means a later reader does not
        // have to check that.
        HowFarForward standingAt = movedForward.get();
        if (days == 0) {
            return standingAt.by();
        }
        Instant reading = realClock.instant().plus(standingAt.by());
        Instant thatManyDaysOn = reading.atZone(daysAreCountedIn)
                .plusDays(days)
                .toInstant();
        return standingAt.by().plus(Duration.between(reading, thatManyDaysOn));
    }

    /**
     * Puts the clock a given number of days ahead of the real one — the total, not a step, because
     * the total is the figure that is written down and reported back — and the span those days come
     * to, which is what every later reading adds.
     *
     * @throws IllegalArgumentException if that span is backwards, which no reading off this clock
     *         may be. Nothing in the application asks for one: a move computes the span through the
     *         calendar and a restart puts back a span that was checked before it was used. It is
     *         refused here as well because the promise belongs to this class, and a class whose
     *         central promise is only kept by its callers is one a later caller will break.
     */
    void moveForwardTo(long days, Duration by) {
        if (by.isNegative()) {
            throw new IllegalArgumentException("the clock cannot be moved by " + by
                    + ": that is backwards, and this clock only reads forward of the real one");
        }
        movedForward.set(new HowFarForward(days, by));
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
    }
}
