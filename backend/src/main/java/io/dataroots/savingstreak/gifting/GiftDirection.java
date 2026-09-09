package io.dataroots.savingstreak.gifting;

/**
 * Which way a gift went, from the point of view of the customer being told about it.
 *
 * <p>A property of the reader rather than of the gift. One row records one gift, and the same row is
 * a gift sent to the person who gave it and a gift received by the person who got it — so the
 * direction is decided when the gift is reported and never stored. This is the idiom the
 * money-movement ledger already set with its own direction, and it is what lets one customer's
 * sending and receiving be one list that reads chronologically.
 *
 * <p>Two words rather than a boolean or a sign on the points, for the reason the money ledger gives:
 * whoever renders it decides what to call each kind and which way round to draw the arrow, and both
 * are easier to get right from a word.
 */
public enum GiftDirection {

    /** The customer being told about it gave these points away. */
    SENT,

    /** The customer being told about it was given these points by somebody else. */
    RECEIVED
}
