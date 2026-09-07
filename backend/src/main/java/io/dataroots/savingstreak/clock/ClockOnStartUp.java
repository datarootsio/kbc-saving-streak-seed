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
 * <p>One row cannot say what its span was: the one a build before this wrote, which recorded the days
 * alone. That is taken as the fixed 86 400 seconds a day that build's clock was adding — see
 * {@link #whatThoseDaysCameToBeforeSpansWereWrittenDown} — and written into the row, so the position
 * survives the upgrade and no later start has to reason about it again. Refusing such a row would
 * lose the days with it, and the next advance counts from where the clock says it is standing: a
 * record of a year refused and then "advance seven days" lands the clock 358 days behind records
 * already on the ledger, which is the same rewind a hundred times over.
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
     *
     * <p>A row that says how many days but not what they came to is the one thing here that is filled
     * in rather than refused, because it is not a hand edit: it is what the schema update leaves of a
     * record an older build wrote, and that build's span is known exactly — see
     * {@link #whatThoseDaysCameToBeforeSpansWereWrittenDown}. Refusing it would throw the days away
     * with it and let the next advance count from zero.
     */
    private void putTheClockBack(ClockOffset record) {
        long movedForwardByDays = record.getMovedForwardByDays();
        Optional<String> notADayCount = whyThoseAreNotDaysTheClockGoesTo(movedForwardByDays);
        if (notADayCount.isPresent()) {
            log.warn("clock not put back: {}, so it is left at the real moment {}",
                    notADayCount.get(), clock.instant());
            return;
        }
        // The days are checked first because filling a missing span in multiplies them: a day count
        // nobody had looked at yet would make a span of anything at all, including a backwards one.
        Optional<Duration> written = record.getMovedForwardBy();
        Duration movedForwardBy = written.orElseGet(
                () -> whatThoseDaysCameToBeforeSpansWereWrittenDown(movedForwardByDays));
        Optional<String> notASpan = whyThatIsNotASpanThoseDaysCameTo(movedForwardByDays, movedForwardBy);
        if (notASpan.isPresent()) {
            log.warn("clock not put back: {}, so it is left at the real moment {}",
                    notASpan.get(), clock.instant());
            return;
        }
        if (written.isEmpty()) {
            // Written down as well as used, so that the row this application now reads its position
            // out of says what that position is made of — the same reason ClockService writes the
            // span at all. Filled in before the clock is moved and by a call that commits on its
            // own: an application that could not keep the completed row has not moved either, and
            // is therefore still the application the row already describes.
            offsets.save(new ClockOffset(movedForwardByDays, movedForwardBy));
            log.info("clock position recorded before this release given the span its days came to "
                            + "movedForwardByDays={} movedForwardBy={}",
                    movedForwardByDays, movedForwardBy);
        }
        clock.moveForwardTo(movedForwardByDays, movedForwardBy);
        // The span as well as the days, because the span is what the readings are made of and this
        // line is the evidence that the one written down is the one now in use: put back beside an
        // advance of the same days in the log, the two spans read the same.
        log.info("clock put back where it was left movedForwardByDays={} movedForwardBy={} reading={}",
                movedForwardByDays, clock.movedForwardBy(), clock.instant());
    }

    /**
     * What a number of days came to for the build that wrote a record without a span: a fixed
     * 86 400 seconds each, which is exactly what that build's clock added on every reading.
     *
     * <p>Not an approximation of a calendar span, and not meant as one. A row with no span was
     * written before this application counted its days through a calendar at all, by a clock whose
     * reading was the real moment plus this — so this is that application's own position, put back to
     * the second rather than guessed at. Working the calendar span out here instead would be the
     * fresh answer this class exists to avoid: against a real moment on the far side of a clock
     * change it is the hour short that is a rewind.
     *
     * <p>It is also always a span a calendar could have produced for that many days, so the check
     * below has nothing to forgive it: twenty-four hours a day sits between the twenty-three and the
     * twenty-five that bound it.
     */
    private static Duration whatThoseDaysCameToBeforeSpansWereWrittenDown(long days) {
        return Duration.ofDays(days);
    }

    /**
     * Says what is wrong with the number of days in the record, or nothing if it is a number of days
     * this clock can be put forward by. A reason rather than a boolean, because a refusal is only
     * useful to whoever caused it if it says which figure was wrong and what would have been right.
     */
    private static Optional<String> whyThoseAreNotDaysTheClockGoesTo(long movedForwardByDays) {
        if (movedForwardByDays < 0
                || movedForwardByDays > ClockService.MOST_DAYS_THE_CLOCK_CAN_BE_MOVED) {
            return Optional.of("the record says it was moved " + movedForwardByDays
                    + " days, which is not a move forward of at most "
                    + ClockService.MOST_DAYS_THE_CLOCK_CAN_BE_MOVED + " days");
        }
        return Optional.empty();
    }

    /**
     * Says what is wrong with the span, or nothing if it is a span that many days could have come
     * to. The days are known to be in range by the time this is asked, so the pair of them describe
     * a position this clock can be put to.
     */
    private static Optional<String> whyThatIsNotASpanThoseDaysCameTo(
            long movedForwardByDays, Duration by) {
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
