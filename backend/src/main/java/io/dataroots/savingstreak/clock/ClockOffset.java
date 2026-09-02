package io.dataroots.savingstreak.clock;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * How far the clock has been moved forward, written down so that it is still there after a restart.
 *
 * <p>A participant halfway through an exercise has advanced the clock a year to watch a twelve-month
 * rule fire. An application that stopped — a crash, a restart to pick up the code they just wrote —
 * would otherwise rewind them to day zero along with every record they had already dated a year out.
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

    protected ClockOffset() {
        // for JPA
    }

    ClockOffset(long movedForwardByDays) {
        this.id = THE_ONE_ROW;
        this.movedForwardByDays = movedForwardByDays;
    }

    long getMovedForwardByDays() {
        return movedForwardByDays;
    }
}
