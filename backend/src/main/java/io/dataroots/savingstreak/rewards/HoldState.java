package io.dataroots.savingstreak.rewards;

/**
 * Where a hold is in its short life: the last one set aside for somebody, and the three ways that
 * ends.
 *
 * <p><strong>Stored rather than derived, unlike almost everything else this module says about an
 * offer.</strong> Whether an offer is open, sold out or on promotion is worked out from its
 * columns and the clock every time it is read, and the argument for that is written out on
 * {@link RewardsService}. A hold is the other kind of thing: somebody asked for one, and then
 * somebody either converted it, gave it up, or did neither until the time ran out. Two of those
 * three are things a person did at a moment, and a thing a person did is not derivable from
 * anything.
 *
 * <p><strong>{@link #LAPSED} is the odd one, and it is the reason this enum needs saying
 * carefully.</strong> A hold whose seventy-two hours have gone by is over whether or not anything
 * has written it down: {@code lapsesAt} is on the row, the clock is injected, and every question
 * this module asks about a live hold asks it of both. So the sweep that writes {@code LAPSED}
 * is not what ends a hold — it is what records that it ended, so that the row and the clock agree
 * and so that the step which promotes whoever is next in line has something to read. The
 * alternative was to let the sweep be the thing that ends a hold, and it was rejected: a customer
 * converting at hour seventy-five, hours before the sweep ran, would have got the thing they were
 * told they had lost, and the seventy-two hours on their screen would have meant nothing.
 *
 * <p>All three endings are terminal, like a voucher's, and for the same reason: a hold that could
 * be revived is a promise to two people at once.
 *
 * <p>Public because it leaves the module on {@link AHoldOnAnOffer}; the row it lives on does not.
 */
public enum HoldState {

    /**
     * The customer has it set aside and has not yet decided. The only state in which a hold takes
     * stock away from anybody else, and only while its moment is still ahead.
     */
    HELD,

    /** They claimed it. The stock went out as a voucher and the hold is spent. */
    CONVERTED,

    /** They changed their mind and said so, which puts the thing back in the window at once. */
    GIVEN_UP,

    /**
     * Nobody said anything and the seventy-two hours went by. The stock was back the moment the
     * clock passed; this is the sweep writing that down.
     */
    LAPSED
}
