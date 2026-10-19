package io.dataroots.savingstreak.points;

/**
 * Why a batch of points was earned. A deposit earns under all of these: the euros it moved, the
 * uplift the account's streak paid on top of them, and a tenth of the euros again for every year
 * they stayed where they were put.
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
    LOYALTY_BONUS
}
