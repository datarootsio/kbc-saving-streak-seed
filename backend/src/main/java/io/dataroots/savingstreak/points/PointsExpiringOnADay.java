package io.dataroots.savingstreak.points;

import java.time.LocalDate;

/**
 * One day some of a customer's points go, and how many go on it.
 *
 * <p>{@link PointsExpiringNext} answered for the soonest such day and nothing else, which is the
 * right answer to "what does today cost me if I claim nothing". This is the same fact repeated for
 * every day there is one, which is the right answer to "what has this pot got coming" — and the two
 * are deliberately the same shape, because they are the same statement about a different number of
 * days.
 *
 * <p>A day and a figure, and still nothing about batches. Every point going on one day is in the
 * figure however many lots it came from: points earned at different moments of one afternoon are up
 * to four batches reaching their twelve months at four moments of one day, and a customer reads
 * those as one date. Which is also why this cannot be read backwards into how the ledger stores
 * things — a day here is not a batch, and on a busy afternoon it never was.
 *
 * <p>What is left in them rather than what was credited to them. A batch a reward has been partly
 * paid out of takes only its remainder to its anniversary, so this is what the customer would
 * actually lose by leaving it there — the figure the day costs, not the figure the day was once
 * worth.
 *
 * <p>A day rather than a moment, for the reason {@link PointsExpiringNext} gives at length: the day
 * is what is promised, the sweep that acts on it runs overnight, and the zone a moment is read in is
 * this module's to decide rather than the caller's.
 *
 * <p>The day can be one already gone. A batch whose anniversary fell this lunchtime is still here
 * until the sweep runs at three the following morning, and it is reported on the day it was promised
 * on rather than moved forward to the day it will actually be taken — a date in the past here means
 * points that are going tonight.
 */
public record PointsExpiringOnADay(LocalDate on, long points) {
}
