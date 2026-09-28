package io.dataroots.savingstreak.support;

import java.time.Instant;

/**
 * One line of a queue as whoever runs the scheme reads it: who is waiting, where they stand and
 * since when.
 *
 * <p>A second shape beside {@link PlaceInTheQueueView} because the API sends a second shape,
 * and the difference is the point of both: a customer is answered with their own place and the
 * offer it is in, while an administrator reading one offer's list wants the <em>person</em> on
 * every row. A test asserting the name is asserting the thing that makes the administration
 * read worth having at all.
 */
public record WaitingListEntryView(int position, long customerId, String customerName,
                                   Instant joinedAt) {
}
