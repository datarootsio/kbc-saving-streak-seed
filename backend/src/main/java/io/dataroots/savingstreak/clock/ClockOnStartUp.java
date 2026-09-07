package io.dataroots.savingstreak.clock;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Puts the clock back where it was left, before the application serves anything.
 *
 * <p>The running clock keeps its position in memory, so an application that has just started is
 * standing at the real moment until it is told otherwise. Told here, from the record
 * {@link ClockService} wrote down — which is what makes a restart in the middle of an exercise a
 * pause rather than a rewind.
 *
 * <p>A pause because the span in that record is put back as it was written, not worked out again.
 * The days alone would not do it: the same seven days are 169 hours in the week the clocks go back
 * and 168 either side of it, so an application restarting on the far side of a clock change from the
 * move would work out a span an hour shorter than the one the stopped application had been adding,
 * and would come up an hour behind it. Records written before the restart would then be dated after
 * ones written after it, and a trainer who had advanced into a new week would be back in the week
 * they had left — the rewind this whole record exists to prevent, arrived at by restarting instead of
 * by moving the clock backwards.
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

    /**
     * The shortest and the longest a calendar day gets, which is what a written-down span is checked
     * against. A day is twenty-four hours except where a clock change makes it twenty-three or
     * twenty-five, so a span outside this range for that many days is not one any calendar produced —
     * and since only this pair bounds how far a record can put the clock, the cap in days is what
     * bounds the span too.
     */
    private static final Duration SHORTEST_A_CALENDAR_DAY_GETS = Duration.ofHours(23);
    private static final Duration LONGEST_A_CALENDAR_DAY_GETS = Duration.ofHours(25);

    private final MovableClock clock;
    private final ClockOffsetRepository offsets;

    ClockOnStartUp(MovableClock clock, ClockOffsetRepository offsets) {
        this.clock = clock;
        this.offsets = offsets;
    }

    @Override
    public void afterSingletonsInstantiated() {
        offsets.findById(ClockOffset.THE_ONE_ROW).ifPresentOrElse(
                this::putTheClockBack,
                // The ordinary case, and worth a line all the same: it says the question was asked,
                // so a clock reading today is not blamed on a step nobody can see.
                () -> log.debug("clock was never moved movedForwardByDays=0 reading={}", clock.instant()));
    }

    /**
     * Moves the clock to where the record says, unless the record says somewhere the clock does not
     * go.
     *
     * <p>Only {@link ClockService} writes that row and it refuses anything but a move forward within
     * range, so a record outside it means the file was edited by hand — which on a training laptop is
     * a thing that happens. Read back without asking, it would start the application behind the real
     * moment, or further ahead than the days it reports, and every rule about the age of a record
     * would be quietly wrong for the rest of the session. Refused here instead, out loud, and the
     * application comes up at the real moment.
     */
    private void putTheClockBack(ClockOffset record) {
        long movedForwardByDays = record.getMovedForwardByDays();
        Optional<Duration> movedForwardBy = record.getMovedForwardBy();
        Optional<String> notAPosition = whyThatIsNotAPositionTheClockGoesTo(movedForwardByDays, movedForwardBy);
        if (notAPosition.isPresent()) {
            log.warn("clock not put back: {}, so it is left at the real moment {}",
                    notAPosition.get(), clock.instant());
            return;
        }
        clock.moveForwardTo(movedForwardByDays, movedForwardBy.get());
        // The span as well as the days, because the span is what the readings are made of and this
        // line is the evidence that the one written down is the one now in use: put back beside an
        // advance of the same days in the log, the two spans read the same.
        log.info("clock put back where it was left movedForwardByDays={} movedForwardBy={} reading={}",
                movedForwardByDays, clock.movedForwardBy(), clock.instant());
    }

    /**
     * Says what is wrong with the record, or nothing if it describes a position this clock can be put
     * to. A reason rather than a boolean, because a refusal is only useful to whoever caused it if it
     * says which figure was wrong and what would have been right.
     */
    private static Optional<String> whyThatIsNotAPositionTheClockGoesTo(
            long movedForwardByDays, Optional<Duration> movedForwardBy) {
        if (movedForwardByDays < 0
                || movedForwardByDays > ClockService.MOST_DAYS_THE_CLOCK_CAN_BE_MOVED) {
            return Optional.of("the record says it was moved " + movedForwardByDays
                    + " days, which is not a move forward of at most "
                    + ClockService.MOST_DAYS_THE_CLOCK_CAN_BE_MOVED + " days");
        }
        if (movedForwardBy.isEmpty()) {
            return Optional.of("the record says it was moved " + movedForwardByDays
                    + " days but not what span those days came to, and this application cannot work "
                    + "that out for itself without risking an hour less than the one that wrote it");
        }
        Duration by = movedForwardBy.get();
        Duration shortest = SHORTEST_A_CALENDAR_DAY_GETS.multipliedBy(movedForwardByDays);
        Duration longest = LONGEST_A_CALENDAR_DAY_GETS.multipliedBy(movedForwardByDays);
        if (by.compareTo(shortest) < 0 || by.compareTo(longest) > 0) {
            return Optional.of("the record says " + movedForwardByDays + " days came to " + by
                    + ", and no calendar makes that many days anything but " + shortest + " to "
                    + longest);
        }
        return Optional.empty();
    }
}
