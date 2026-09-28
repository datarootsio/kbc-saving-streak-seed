package io.dataroots.savingstreak.timeline;

/**
 * What kind of thing a marker on an account's bar is. Two of them today, and they are opposites: one
 * is points arriving and one is points going.
 *
 * <p>A name rather than a flag, because a third dated rule is the sort of thing this application
 * keeps growing. A boolean saying whether a marker is good news would have to be widened into
 * something like this the first time a date on the bar was neither.
 *
 * <p>Declared in the order a day runs in. The expiry sweep is scheduled before the loyalty sweep, so
 * on a day that does both the points go and then the bonus arrives — and that is the order
 * {@link TimelineService} puts two markers sharing a day in, by comparing these.
 */
public enum TimelineEventKind {

    /**
     * Points reaching their twelve months. Every point going on that day is in the figure, however
     * many lots of it there are and whichever of this account's deposits earned them.
     */
    POINTS_EXPIRE,

    /**
     * A deposit's anniversary paying for the money that stayed put, at a tenth of the whole euros
     * still in it.
     *
     * <p>Never worth nothing. A deposit holding under ten euros has an anniversary and is paid
     * nothing on it, and that is a fact about the deposit rather than a thing that happens — it is
     * on the deposit in the history and it is not a marker here.
     */
    LOYALTY_BONUS
}
