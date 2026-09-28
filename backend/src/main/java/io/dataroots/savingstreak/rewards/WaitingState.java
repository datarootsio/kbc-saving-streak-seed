package io.dataroots.savingstreak.rewards;

/**
 * Where somebody's place in a queue is in its short life: waiting for the thing to come back, and
 * the two ways that ends.
 *
 * <p><strong>Stored, like {@link HoldState} and unlike almost everything else this module says
 * about an offer.</strong> Whether an offer is open, sold out or on promotion is worked out from
 * its columns and the clock on every read, and the argument for that is on {@link RewardsService}.
 * A place in a queue is the other kind of thing: somebody asked to be told when the thing came
 * back, and then either the sweep handed them a hold or they said they had changed their mind.
 * Both of those are things that happened at a moment, and a thing that happened is not derivable
 * from anything.
 *
 * <p><strong>There is no lapsing here, and that is the whole of the difference from a
 * hold.</strong> A hold has seventy-two hours written on its row and is over the instant the
 * clock passes them, which is why every question about one asks the state <em>and</em> the
 * clock. A place in a queue has no deadline at all: somebody who joined in March is still in
 * line in June, in the same position, unless they leave or their turn comes. So "waiting" is
 * decided by this column alone, no query here reads a clock, and the pair of conditions
 * {@link RewardHoldRepository} insists on has no counterpart in {@link WaitingListRepository}.
 * A queue that expired would be a second deadline nobody was told about, and the one thing this
 * feature keeps saying is that a deadline nobody saw is the thing a customer must never be given.
 *
 * <p><strong>{@link #PROMOTED} is not the same as "they got the thing".</strong> It means the
 * sweep handed them a hold, which is seventy-two hours to decide in and no points spent — the
 * application does not spend somebody's points without being asked. If they let that hold lapse
 * they do not come back here: the row stays {@code PROMOTED}, the queue has done what it
 * promised, and the stock the lapse returns goes to whoever is next in line on the next sweep.
 * Putting them back at the head of the queue was considered and rejected, because it is a
 * customer who has already had their turn taking a second one ahead of somebody who has not had
 * a first.
 *
 * <p>Both endings are terminal, like a hold's and like a voucher's, and for the same reason: a
 * place that could come back to life is a promise to two people about one thing.
 *
 * <p>Package-private, unlike {@link HoldState}, because nothing about it leaves this module.
 * What leaves is {@link APlaceInTheQueue}, which carries a position rather than a state — a
 * customer in a queue wants to know where they stand, and a customer who is not in one is not in
 * one.
 */
enum WaitingState {

    /**
     * They are in the queue and have not been given anything yet. The only state the sweep looks
     * at, and the only one a position is counted over.
     */
    WAITING,

    /** Their turn came and the sweep gave them a hold. What they do with it is the hold's story. */
    PROMOTED,

    /** They changed their mind and said so, which closes the gap behind them at once. */
    LEFT
}
