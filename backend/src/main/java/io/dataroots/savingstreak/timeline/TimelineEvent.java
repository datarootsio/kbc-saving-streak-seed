package io.dataroots.savingstreak.timeline;

import java.time.LocalDate;

/**
 * One dated thing a savings account has coming: the day, what kind of thing it is, and how many
 * points.
 *
 * <p>A day and a figure, which is all a marker on a bar can carry and all a customer reads off one.
 * Which deposits are behind it is not here: an account's history already answers that a row at a
 * time, and repeating it would be a second answer to disagree with the first.
 *
 * <p>One marker per day per kind. Two deposits paying on one day are one arrival, and points from
 * four lots reaching their twelve months on one day are one departure, because the bar has one
 * position for that day and a customer reads what the day is worth rather than what each lot in it
 * was.
 *
 * <p>Never worth nothing. A day on which no points move is not a thing that happens, whatever a
 * calendar says about it.
 *
 * <p>The day can be one already gone, and that means something exact: the promise fell and the sweep
 * that keeps it runs overnight, so this is owed and is happening tonight. It is answered on the day
 * it was promised on rather than moved forward to the day it will be acted on, because the promise
 * is what was made to the customer.
 *
 * <p>A day rather than a moment, for the reason both modules behind it already give: the day is what
 * is promised, and the zone a moment is read in is the backend's to settle rather than the zone of
 * whichever machine happens to be drawing the screen.
 */
public record TimelineEvent(LocalDate on, TimelineEventKind kind, long points) {
}
