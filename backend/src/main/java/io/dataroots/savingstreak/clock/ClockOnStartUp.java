package io.dataroots.savingstreak.clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Puts the clock back where it was left, before the application serves anything.
 *
 * <p>The running clock keeps its position in memory, so an application that has just started is
 * standing at the real moment until it is told otherwise. Told here, from the figure
 * {@link ClockService} wrote down — which is what makes a restart in the middle of an exercise a
 * pause rather than a rewind.
 *
 * <p>Runs once every bean exists and before the context finishes refreshing, which is before the web
 * server binds its port. A {@code CommandLineRunner} would run after the application was already
 * answering requests, and a deposit made in that window would be dated at the real moment while
 * everything around it was a year on.
 */
@Component
@Profile("dev")
class ClockOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ClockOnStartUp.class);

    private final MovableClock clock;
    private final ClockOffsetRepository offsets;

    ClockOnStartUp(MovableClock clock, ClockOffsetRepository offsets) {
        this.clock = clock;
        this.offsets = offsets;
    }

    @Override
    public void afterSingletonsInstantiated() {
        offsets.findById(ClockOffset.THE_ONE_ROW).ifPresentOrElse(
                offset -> putTheClockBack(offset.getMovedForwardByDays()),
                // The ordinary case, and worth a line all the same: it says the question was asked,
                // so a clock reading today is not blamed on a step nobody can see.
                () -> log.debug("clock was never moved movedForwardByDays=0 reading={}", clock.instant()));
    }

    /**
     * Moves the clock to where the record says, unless the record says somewhere the clock does not
     * go.
     *
     * <p>Only {@link ClockService} writes that row and it refuses anything but a move forward within
     * range, so a figure outside it means the file was edited by hand. Read back without asking, it
     * would start the application behind the real moment — the one thing moving the clock is not
     * allowed to do — and every rule about the age of a record would be quietly wrong for the rest of
     * the session. Refused here instead, out loud, and the application comes up at the real moment.
     */
    private void putTheClockBack(long movedForwardByDays) {
        if (movedForwardByDays < 0
                || movedForwardByDays > ClockService.MOST_DAYS_THE_CLOCK_CAN_BE_MOVED) {
            log.warn("clock not put back: the record says it was moved {} days, which is not a move "
                            + "forward of at most {} days, so it is left at the real moment {}",
                    movedForwardByDays, ClockService.MOST_DAYS_THE_CLOCK_CAN_BE_MOVED, clock.instant());
            return;
        }
        clock.moveForwardTo(movedForwardByDays);
        // With the span the days came to through the calendar, worked out afresh against the real
        // moment this application started at: the same number of days is an hour more or less
        // depending on which clock changes it now spans.
        log.info("clock put back where it was left movedForwardByDays={} movedForwardBy={} reading={}",
                movedForwardByDays, clock.movedForwardBy(), clock.instant());
    }
}
