package io.dataroots.savingstreak.points;

/**
 * Why a batch of points was earned. A deposit earns under three of these: the euros it moved, the
 * uplift the account's streak paid on top of them, and a tenth of the euros again for every year
 * they stayed where they were put. The fourth is not earned by a deposit at all — it is points
 * somebody else gave away.
 *
 * <p>Public, unlike the batch it is written on: what a deposit earned is reported broken down by
 * reason, so the reasons are part of what this module says rather than part of how it stores
 * things. A caller still cannot learn that points are kept as dated batches — only that a figure it
 * was handed was earned for this reason and not that one.
 */
public enum PointsReason {

    /** One point per whole euro moved into savings, which every deposit earns. */
    BASE_ACCRUAL,

    /**
     * What a run of consecutive secured weeks paid on top of the euros, which is the whole of the
     * difference between the base accrual and what the deposit was actually worth.
     *
     * <p>Its own reason and its own batch rather than a larger base accrual, so that a customer
     * looking at nine points against a seven-euro deposit can see where the nine came from. It also
     * keeps "one point per whole euro" meaning what it has always meant: the figure the deposit
     * history has reported since before there were streaks does not quietly come to mean something
     * else.
     */
    STREAK_BONUS,

    /**
     * A tenth of the whole euros a deposit still held on one of its anniversaries, paid for the
     * money having stayed put for another twelve months.
     *
     * <p>Its own reason, and one deposit earns under it once per anniversary rather than once
     * altogether: a deposit left alone for three years has three of these batches, each dated at
     * the anniversary that paid it and each with twelve months of its own to run. That is what makes
     * "what has this deposit been worth to me" a figure that grows while "what did it earn when it
     * landed" stays exactly what it was.
     *
     * <p>When an anniversary falls and what it pays are the Loyalty module's rule and this ledger
     * has no opinion about either. What arrives here is a number of points, a deposit, and the
     * moment they were earned at — the same three things a deposit's own credit arrives with.
     */
    LOYALTY_BONUS,

    /**
     * A slice of somebody else's pot, handed over by them. The points were not earned by whoever
     * holds them now: they were earned by the customer who gave them away, and the batch that
     * arrives carries the moment <em>that</em> customer earned them rather than the moment of the
     * gift. A pot can therefore hold points older than any deposit its owner ever made, which is
     * what having been given something second-hand looks like.
     *
     * <p>Pointedly not one of the reasons a deposit can have earned under, and that absence is the
     * whole of what keeps this reason out of every existing figure: a deposit's breakdown asks for
     * the reasons a deposit earns under and this is not one of them, so no breakdown grows a field
     * and nothing a deposit says it earned changes.
     *
     * <p>Who gave them, to whom, and when is the Gifting module's record. What arrives here is a
     * number of points, the gift that caused them, and the moment they were originally earned —
     * the same three things a loyalty bonus arrives with.
     */
    GIFT_RECEIVED,

    /**
     * What a rung of a challenge paid for being reached. The customer took something on, their
     * saving carried them past a threshold that challenge named, and the points the threshold is
     * worth were credited once and for good.
     *
     * <p>Pointedly not one of the reasons a deposit can have earned under, and that absence is the
     * whole of what keeps this reason out of every existing figure — the same argument {@link
     * #GIFT_RECEIVED} makes, for a closely related reason. A rung is not paid for by a deposit: it
     * is paid for by a reading over the whole of somebody's saving clearing a mark, and one deposit
     * may clear three rungs at once while the next clears none. So no deposit's breakdown grows a
     * field, nothing a deposit says it earned changes, and a batch credited under this reason
     * references an award rather than a deposit.
     *
     * <p>Which rung of which challenge, when, and what reading won it is the Challenges module's
     * record. The award is written down before these points are credited and is never revoked,
     * never re-priced and never deleted afterwards, whatever becomes of the money or of the batch
     * this reason wrote. What arrives here is a number of points, the award that caused them, and
     * the moment they were earned — the same three things a loyalty bonus and a gift arrive with.
     *
     * <p>And nothing else about them is special, which is the whole point of crediting them this
     * way. They are an ordinary dated batch: gone twelve months after the moment given, spent
     * oldest-first alongside every other batch, counted in what the customer is told goes next,
     * giftable, and good for anything in the catalogue. A challenge pays in a currency the customer
     * already understands, and this ledger did not have to learn a second kind of point in order to
     * let it.
     */
    CHALLENGE_REWARD,

    /**
     * Points handed back because an administrator revoked the voucher they had paid for.
     *
     * <p><strong>A fresh batch under a new reason, and never the batches that were spent put
     * back.</strong> That is the whole decision this reason exists to record, and it is worth
     * reading as a refusal of the obvious alternative. Spending goes oldest-first, so the points
     * a claim took came out of the batches nearest their own twelve months; by the time somebody
     * notices a claim should never have been made, some of those batches may have expired, and
     * topping a dead batch back up would put points somewhere they can never be spent from while
     * reporting a balance that says they can. A refund that cannot be spent is worse than no
     * refund, because only one of the two is visible. So the points arrive the way every other
     * kind of point has ever arrived in this ledger — through the one door, as a dated batch,
     * under a reason of its own — which is the pattern {@link #GIFT_RECEIVED} and
     * {@link #CHALLENGE_REWARD} already set.
     *
     * <p><strong>The moment is the cancellation and not the earning behind it</strong>, which is
     * the one place this reason differs from a gift. A gift inherits its date so that passing
     * points back and forth cannot keep them alive for ever; a refund has nobody to pass to and
     * nothing to keep alive. What it has is a customer who is being given back something they
     * never got the use of, and dating the batch at the mistake rather than at their original
     * deposit is what makes the refund worth what a refund should be worth: twelve months of its
     * own, from today.
     *
     * <p>Pointedly not one of the reasons a deposit can have earned under, like the two reasons
     * above it and for the same argument: no deposit earned these, so no deposit's breakdown
     * grows a field and nothing a deposit says it earned changes. The reference is the claim that
     * was cancelled — the only thing that says which voucher these points came back from.
     *
     * <p>An expiry is not here and never will be. A voucher that outlived its shelf life refunds
     * nothing, because a deadline somebody is refunded for is not a deadline; that the two ends
     * of a voucher's life are different states is exactly so that only one of them can reach this
     * reason.
     */
    REDEMPTION_CANCELLED
}
