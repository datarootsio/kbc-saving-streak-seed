package io.dataroots.savingstreak.rewards;

import java.time.Instant;

/**
 * One place in one queue as the rest of the application reads it: who is waiting for what, since
 * when, and how many people are in front of them.
 *
 * <p>Public, like {@link AHoldOnAnOffer} and {@link ClaimedReward}, because it is what leaves
 * this module. The row it is read off and the repository that finds it stay behind
 * {@link RewardsService}.
 *
 * <p><strong>{@code position} is the whole of what a customer wants from this, and it is
 * counted rather than stored.</strong> "I want to be told where I am in the queue, so that
 * waiting is something I can reason about" is the user story, and a queue that only said "you
 * are in it" would answer none of it. It is one-based, because it is read out loud — first in
 * line is first, not nought — and it is counted over the order people joined at the moment of
 * the read, so somebody in front of them who leaves moves them up with nothing having been
 * written. The argument against a column is on {@link WaitingListPlace}.
 *
 * <p><strong>There is no state on it.</strong> A place that leaves this module is a place
 * somebody is waiting in: joining answers with one, leaving answers with the one that has just
 * ended, and the administrator's reading is the people still in line. What became of a place
 * afterwards is the module's own bookkeeping, and a page given a state would have to decide for
 * itself whether {@code PROMOTED} meant "you have it" — which it does not, it means a hold is
 * waiting, and the hold says that for itself on the very same card.
 *
 * <p><strong>The title is read off the catalogue as it stands and is not snapshotted</strong>,
 * for the reason {@link AHoldOnAnOffer} gives about its own and more so: a place in a queue can
 * outlast a hold many times over, and somebody who corrects a typo in an offer's title has
 * corrected the title of the thing forty people are waiting for. There is nothing here worth
 * freezing, because nothing here is a record of a purchase.
 *
 * <p>There is no price either, deliberately. Waiting locks nothing in: when their turn comes
 * they are handed a hold, and converting that hold pays whatever is in force then — the
 * argument written out on {@link AHoldOnAnOffer}, arrived at one step earlier.
 *
 * @param position one-based, counted over everybody still waiting for this offer, so that "you
 *        are third" is a sentence somebody can act on
 */
public record APlaceInTheQueue(Long id, long customerId, String offerCode, String title,
                               int position, Instant joinedAt) {
}
