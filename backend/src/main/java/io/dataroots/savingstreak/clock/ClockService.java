package io.dataroots.savingstreak.clock;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Moves the application's clock forward, and says where it is standing.
 *
 * <p>A lab affordance, not a feature: it exists in the development profile alone, so an application
 * started without that profile has no way to move time and nothing in it that writes down where time
 * was moved to. Nothing a customer can see reaches this class.
 *
 * <p>Moving the clock is two things — the record that survives a restart, and the clock the running
 * application actually reads — and this is the only place that knows both. It is therefore also the
 * one place the calendar is consulted about what a number of days comes to, so that the record and
 * the clock are given the same answer rather than each working one out.
 */
@Service
@Profile("dev")
public class ClockService {

    private static final Logger log = LoggerFactory.getLogger(ClockService.class);

    /**
     * As far as the clock will go, counted from where it really is.
     *
     * <p>A hundred years is past every rule this application will ever be asked to demonstrate, and
     * short of the arithmetic a moment cannot survive. The cap is on the total rather than on one
     * move, because ten moves of a year are the same journey as one move of a decade.
     */
    static final long MOST_DAYS_THE_CLOCK_CAN_BE_MOVED = 36500;

    private final MovableClock clock;
    private final ClockOffsetRepository offsets;

    ClockService(MovableClock clock, ClockOffsetRepository offsets) {
        this.clock = clock;
        this.offsets = offsets;
    }

    /**
     * Moves the clock a number of days further on than it already is, and reports where that leaves
     * it.
     *
     * <p>Synchronised, so that reading where the clock is, writing where it will be and putting it
     * there are one step. Two moves arriving together would otherwise both start from the same
     * reading, and one of them would be silently lost — cheap to rule out, and this is called by a
     * person with curl rather than by anything hot.
     *
     * @throws ClockRefused if that is not a move forward this clock can make
     */
    public synchronized HowFarTheClockHasMoved advanceBy(long days) {
        long alreadyMovedBy = clock.movedForwardByDays();
        Duration alreadyMovedByASpanOf = clock.movedForwardBy();
        Instant wasReading = clock.instant();
        // The figures the decision below is made of, so that a reader of the log can work out the
        // new position by hand rather than take the reported one on trust. The span as well as the
        // days, because the days asked for are counted on from the reading and the reading is the
        // real moment plus that span.
        log.debug("clock asked to advance days={} alreadyMovedByDays={} alreadyMovedBy={} reading={}",
                days, alreadyMovedBy, alreadyMovedByASpanOf, wasReading);
        refuseUnlessAMoveForward(days, alreadyMovedBy);

        long movedForwardByDays = alreadyMovedBy + days;
        // The calendar is read once, here, and the one span it gives goes to both the record and the
        // clock. Two reads would be two answers, because a calendar answer depends on the moment it
        // is counted from — and the record's job is to hand a restart the span this application is
        // about to start using, not a plausible one.
        //
        // What is counted is the days asked for now, on from where the clock is standing, and the
        // answer is added to the span it is standing on. Not the new total counted from the real
        // moment: those differ once real time has crossed a clock change since the previous move,
        // which a position that survives a restart makes ordinary — advance in October, come back in
        // November, advance again, and the total worked out afresh is an hour short of seven days on
        // from the reading. An hour short is the trainer pressing the button for the next week and
        // being shown the week they were already in, which is the whole reason these days go through
        // a calendar at all.
        Duration movedForwardBy = clock.howFarForwardThatManyMoreDaysIs(days);
        // What the calendar said, on its own rather than buried in a total: this is the span the
        // days asked for came to from where the clock was reading, and it is the figure that says
        // whether this move really was that many calendar days. A reader who only had the totals
        // would have to subtract two of them, and the whole defect this guards against is a total
        // that is not the previous total plus a calendar span.
        log.debug("clock move counted through the calendar from the reading days={} reading={} "
                        + "thisMoveAdds={} alreadyMovedBy={} movedForwardBy={}",
                days, wasReading, movedForwardBy.minus(alreadyMovedByASpanOf),
                alreadyMovedByASpanOf, movedForwardBy);
        // Written down before the running clock is moved, and by a call that commits on its own: if
        // the record cannot be kept, the clock has not moved, and an application that came back up
        // would not disagree with the one that went down. The other order would leave a restart
        // rewinding a demonstration that had already reported itself moved.
        offsets.save(new ClockOffset(movedForwardByDays, movedForwardBy));
        clock.moveForwardTo(movedForwardByDays, movedForwardBy);

        Instant now = clock.instant();
        // The span as well as the days, because they are not the same statement: seven days are 169
        // hours in the week the clocks go back, and this is where a reader sees which week they are
        // in without subtracting two readings by hand.
        log.info("clock advanced byDays={} movedForwardByDays={} movedForwardBy={} wasReading={} "
                        + "nowReading={}",
                days, movedForwardByDays, clock.movedForwardBy(), wasReading, now);
        return new HowFarTheClockHasMoved(movedForwardByDays, now);
    }

    /** Where the clock is standing, for somebody mid-exercise who needs to know where in time they are. */
    public HowFarTheClockHasMoved howFarItHasMoved() {
        HowFarTheClockHasMoved moved = new HowFarTheClockHasMoved(clock.movedForwardByDays(), clock.instant());
        log.debug("clock asked where it is standing movedForwardByDays={} movedForwardBy={} reading={}",
                moved.movedForwardByDays(), clock.movedForwardBy(), moved.now());
        return moved;
    }

    /**
     * Refuses anything that is not a move forward this clock can make, before a single figure is
     * written down or a clock is touched.
     *
     * <p>Backwards is refused rather than allowed and warned about: records already written carry
     * moments off this clock, and winding it back would date the next ones before them, which is a
     * history nobody can read rather than a demonstration.
     */
    private void refuseUnlessAMoveForward(long days, long alreadyMovedBy) {
        if (days <= 0) {
            throw refusing("The clock only moves forward. Ask for at least one day, not " + days + ".");
        }
        // Both terms checked against the cap rather than only their sum, so that a number large
        // enough to overflow the addition is refused by the same sentence as one that is merely too
        // far.
        if (days > MOST_DAYS_THE_CLOCK_CAN_BE_MOVED
                || alreadyMovedBy + days > MOST_DAYS_THE_CLOCK_CAN_BE_MOVED) {
            throw refusing("The clock moves at most " + MOST_DAYS_THE_CLOCK_CAN_BE_MOVED
                    + " days (a hundred years) past the real one, and it has already moved "
                    + alreadyMovedBy + ", so " + days + " more is too far.");
        }
    }

    /** Every refusal says why in the log as well as to the caller, because only one of them is kept. */
    private ClockRefused refusing(String reason) {
        log.warn("clock not advanced: {}", reason);
        return new ClockRefused(reason);
    }
}
