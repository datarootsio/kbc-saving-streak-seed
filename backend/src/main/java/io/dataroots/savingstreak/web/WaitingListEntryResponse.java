package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.rewards.APlaceInTheQueue;

/**
 * One line of a queue as whoever runs the scheme reads it: who is waiting, where they stand,
 * and since when.
 *
 * <p><strong>A second shape beside {@link PlaceInTheQueueResponse} rather than the same one
 * reused, because the two readers want different things.</strong> A customer is answered with
 * their own place and the offer it is in, because they already know who they are and are
 * looking at one card. An administrator is looking at a list about one offer, so the offer is
 * the page and the <em>person</em> is what each row has to say — which is why the name is here
 * and the title is not. "I want to see the waiting list for an offer, so that I know what to
 * restock" is answered by a column of names and a length, and by nothing that repeats the
 * offer forty times.
 *
 * <p><strong>The name is asked of Accounts by the controller.</strong> Who a customer is
 * belongs to Accounts, and Rewards has spent this whole feature not reading other modules for
 * the sake of a screen — the same arrangement the cancelled voucher already uses to put a
 * holder's name beside a refund. It is null when the customer behind a place is no longer on
 * file, which is honest rather than a failure: somebody is still in that queue, and the
 * identifier beside the blank is what an administrator would chase it with.
 *
 * <p>{@code joinedAt} is a moment rather than a day, because the order is what this list is
 * for and two people who joined on the same day did not join at the same time.
 *
 * <p>Like every other administration address, nothing here checks that the caller runs the
 * scheme. The controller says so in the words the sign-in endpoint already uses.
 */
record WaitingListEntryResponse(int position, long customerId, String customerName,
                                Instant joinedAt) {

    static WaitingListEntryResponse of(APlaceInTheQueue place, String customerName) {
        return new WaitingListEntryResponse(place.position(), place.customerId(), customerName,
                place.joinedAt());
    }
}
