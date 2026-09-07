package io.dataroots.savingstreak.points;

/**
 * Why a batch of points was earned. A deposit earns under both of these: the euros it moved, and the
 * uplift the account's streak paid on top of them.
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
    STREAK_BONUS
}
