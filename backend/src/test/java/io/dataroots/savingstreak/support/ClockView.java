package io.dataroots.savingstreak.support;

import java.time.Instant;

/**
 * The application's clock as the development API reports it: how far forward of the real one it has
 * been moved, and what it reads now. Shared by every test that asks, for the same reason as
 * {@link BalancesView} — copies of a shape drift into disagreeing about it.
 */
public record ClockView(long movedForwardByDays, Instant now) {
}
