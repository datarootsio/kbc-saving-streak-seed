package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.clock.HowFarTheClockHasMoved;

/**
 * Where the application's clock is standing, as the development API reports it: how far it has been
 * moved, and what it now reads.
 */
record ClockResponse(long movedForwardByDays, Instant now) {

    static ClockResponse of(HowFarTheClockHasMoved moved) {
        return new ClockResponse(moved.movedForwardByDays(), moved.now());
    }
}
