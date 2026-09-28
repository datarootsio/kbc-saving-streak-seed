package io.dataroots.savingstreak.timeline;

import java.time.LocalDate;
import java.util.List;

/**
 * The year a savings account has ahead of it: the day the window opens, the day it closes, and every
 * dated thing the deposits in that account have coming in between.
 *
 * <p>The window travels with the markers because a bar cannot be drawn without it, and because
 * working out what day it is, is not the caller's to do. This application's clock can be wound a
 * year forward; a screen positioning these against the machine's own date would draw a year nobody
 * is in. It is settled here, once, in the zone the application counts calendars in.
 *
 * <p>Complete rather than a sample, and that is the whole reason the window is twelve months —
 * {@link TimelineHorizon} carries the argument. Nothing is beyond the right-hand edge, so whoever
 * draws this can say so.
 *
 * <p>Ordered by day, and two markers on one day come back in the order the day runs in.
 *
 * <p>Empty for an account nobody has paid into, with the window all the same. "Nothing is coming" is
 * an answer, and it is a different one from an account that could not be read.
 *
 * <p>Nothing is added up across the markers. No total of what the year costs and no net of the two
 * kinds: a point can be paid on an anniversary inside this window and expire inside it too, so a net
 * would count one point twice in opposite directions — and a customer reading a bar is asking about
 * days rather than about a year's balance.
 */
public record AccountTimeline(LocalDate from, LocalDate until, List<TimelineEvent> events) {
}
