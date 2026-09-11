package io.dataroots.savingstreak.clock;

import java.time.Instant;

/**
 * Where the application's clock is standing: how many days forward of the real one it has been
 * moved, and what it therefore reads now.
 *
 * <p>Both, because either on its own leaves somebody mid-exercise guessing. The days say how much of
 * the demonstration's time has been spent; the moment is what a deposit made next will be dated.
 */
public record HowFarTheClockHasMoved(long movedForwardByDays, Instant now) {
}
