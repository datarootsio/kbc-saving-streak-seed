package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.gifting.GiftGiven;

/**
 * A gift as the API reports one: which gift, which way it reads for the customer being told about
 * it, both people by identifier and by name, how many points, and when it happened.
 *
 * <p>One shape for a gift just made and for a gift read back out of a list, and the direction is
 * what differs between the two ends of the same gift — {@code SENT} to the person who made it,
 * {@code RECEIVED} in the other person's list. It travels as its own word rather than as a sign on
 * the points, exactly as the money-movement ledger's direction does: a page decides what to call
 * each kind and which way round to draw the arrow, and both are easier to get right from a word.
 *
 * <p>Both people rather than only the other one, so a row is self-describing whoever fetched it.
 *
 * <p>The moment comes off the application's clock, so a gift made against a wound-forward clock
 * reads where the trainer wound it to.
 */
record GiftResponse(Long id, String direction, Long senderId, String senderName, Long recipientId,
                    String recipientName, long points, Instant givenAt) {

    static GiftResponse of(GiftGiven gift) {
        return new GiftResponse(gift.id(), gift.direction().name(), gift.senderId(), gift.senderName(),
                gift.recipientId(), gift.recipientName(), gift.points(), gift.givenAt());
    }
}
