package io.dataroots.savingstreak.points;

import java.time.LocalDate;

/**
 * The next points a customer stands to lose: how many, and the day their twelve months are up.
 *
 * <p>A day rather than a moment, and that is the whole of the type's opinion. Points reach their
 * anniversary at whatever time of day they were earned and the sweep that acts on it runs overnight,
 * so an exact time would be precision the customer cannot act on — and a figure they could catch the
 * application out on. The day is what is promised and the day is therefore what is answered.
 *
 * <p>Which also settles it for whoever renders this. A moment has to be turned into a calendar day
 * in some particular zone, and a page that did that would be picking the zone of the machine it
 * happened to be running on — a customer in London would be told a deadline a day early. The zone
 * this application counts calendar things in is named once, in the Streaks module, and this is the
 * module that reads it on the customer's behalf.
 *
 * <p>The soonest day rather than every day. A customer deciding whether to claim something today
 * needs to know what today costs them if they do not, and a list of every batch's anniversary is the
 * ledger's shape rather than an answer to that.
 *
 * <p>Every batch whose anniversary falls on that day is counted, not merely the oldest one. Two
 * deposits made on one afternoon are up to four batches expiring on one date, and a figure that
 * named only the first of them would understate what the day costs.
 *
 * <p>The anniversary rather than the run of the sweep that will actually retire the points. The
 * anniversary is the promise made to the customer; that a job gets round to it at three the
 * following morning is how the promise is kept and not part of it.
 */
public record PointsExpiringNext(long points, LocalDate on) {
}
