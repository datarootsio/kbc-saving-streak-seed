package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.rewards.APlaceInTheQueue;

/**
 * One place in one queue as the API reports it: who is waiting for what, since when, and how
 * many people are in front of them.
 *
 * <p>The answer to joining and to leaving, and to nothing else. A customer whose turn comes is
 * not answered with this at all — their place is gone and what they have is a hold, which their
 * card and their notification both say for themselves.
 *
 * <p><strong>{@code position} is the whole of what this response is for.</strong> "You are in
 * the queue" answers nothing anybody asked; "you are third" is a thing somebody can reason
 * about, which is the user story word for word. It is one-based because it is read out loud,
 * and it is the backend's count rather than an index the page works out of a list — the page
 * never sees the list, and a queue is precisely the thing a customer may not be shown other
 * people's names in.
 *
 * <p>It is a position at the moment of the answer and not a promise. Somebody in front may
 * leave, which moves them up, and nothing in this application pretends otherwise — the next
 * reading of the catalogue carries the number as it then stands.
 *
 * <p>There is no state on it, and no price. What became of a place is the module's own
 * bookkeeping — the argument is on {@code APlaceInTheQueue} — and waiting locks no price in,
 * because what a turn buys is a hold and converting a hold pays whatever is in force then.
 *
 * <p>{@code joinedAt} is a moment rather than a day, like a hold's deadline and unlike every
 * other date this API sends, because it is what the order is decided by: two people who joined
 * on the same day did not join at the same time, and a date would make the one thing a queue
 * is made of unreadable.
 */
record PlaceInTheQueueResponse(Long id, long customerId, String offerCode, String title,
                               int position, Instant joinedAt) {

    static PlaceInTheQueueResponse of(APlaceInTheQueue place) {
        return new PlaceInTheQueueResponse(place.id(), place.customerId(), place.offerCode(),
                place.title(), place.position(), place.joinedAt());
    }
}
