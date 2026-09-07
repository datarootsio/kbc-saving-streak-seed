package io.dataroots.savingstreak.clock;

import java.time.Duration;
import java.util.Optional;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * How far the clock has been moved forward, written down so that it is still there after a restart.
 *
 * <p>A participant halfway through an exercise has advanced the clock a year to watch a twelve-month
 * rule fire. An application that stopped — a crash, a restart to pick up the code they just wrote —
 * would otherwise rewind them to day zero along with every record they had already dated a year out.
 *
 * <p>Two figures, because a position is two figures: the whole days that were asked for, and the
 * span through the calendar those days came to. The days are what a participant is told and what the
 * next move is counted from; the span is what the clock actually adds to the real moment, and it is
 * not derivable from the days afterwards. Seven days are 168 hours in most weeks and 169 in the week
 * the clocks go back, so an application that put the days back and worked the span out again against
 * the real moment it happened to restart at could come up an hour behind the one that went down —
 * a rewind, which is the one thing this record exists to prevent.
 *
 * <p>The span is kept in whole seconds, which loses nothing: it is the gap between two moments that
 * share a nano-of-second — a real moment and that same moment a number of calendar days on — and
 * zone offsets are whole minutes, so it never has a fraction of a second in it.
 *
 * <p>One row, under a fixed identifier: there is one clock, and a table that could hold two answers
 * about where it is would eventually hold two. Written only by {@link ClockService}, which exists in
 * the development profile alone, so an application without that profile never writes here and reads
 * nothing back.
 */
@Entity
class ClockOffset {

    /** The only row this table ever has, named rather than generated so it can be overwritten. */
    static final long THE_ONE_ROW = 1L;

    @Id
    private Long id;

    private long movedForwardByDays;

    /**
     * Nullable, for one case: a database written before this column existed has it added empty by
     * the schema update, and such a row says how many days but not what they came to. Read back as
     * nothing rather than as zero, because zero is a real position — the clock standing where the
     * real one does — and a row that does not say is a row {@link ClockOnStartUp} has to refuse
     * rather than believe.
     */
    private Long movedForwardBySeconds;

    protected ClockOffset() {
        // for JPA
    }

    ClockOffset(long movedForwardByDays, Duration movedForwardBy) {
        this.id = THE_ONE_ROW;
        this.movedForwardByDays = movedForwardByDays;
        this.movedForwardBySeconds = movedForwardBy.toSeconds();
    }

    long getMovedForwardByDays() {
        return movedForwardByDays;
    }

    /** The span the days came to, or nothing if the row does not say what it was. */
    Optional<Duration> getMovedForwardBy() {
        return Optional.ofNullable(movedForwardBySeconds).map(Duration::ofSeconds);
    }
}
