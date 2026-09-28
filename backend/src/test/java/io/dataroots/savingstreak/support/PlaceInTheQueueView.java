package io.dataroots.savingstreak.support;

import java.time.Instant;

/**
 * One place in a queue as the API reports it: who is waiting for what, since when, and how many
 * people are in front of them.
 *
 * <p>{@code position} is one-based and is the backend's count rather than an index a test works
 * out of a list, which is the whole thing worth asserting here: a test that numbered a queue
 * itself would be asserting its own arithmetic.
 *
 * <p>{@code joinedAt} is an {@link Instant} and not a date, because that is what the API sends
 * and because the moment is what the order is decided by — two people who joined on the same
 * day did not join at the same time, and a test that read it as a date would be rounding away
 * the very thing a queue is made of.
 */
public record PlaceInTheQueueView(Long id, long customerId, String offerCode, String title,
                                  int position, Instant joinedAt) {
}
