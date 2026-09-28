package io.dataroots.savingstreak.simulation;

/**
 * What kind of dated thing a branch of a future has in it — the seven sentences a customer is
 * actually deciding between, as opposed to the twelve rows they are reading down.
 *
 * <p><strong>Declared in the order a day runs in, and that is load-bearing rather than
 * tidy.</strong> Events come back ordered by day, and two things sharing a day come back in the
 * order the night does them, which is the promise {@code TimelineEventKind} already makes for the
 * same reason on the same kind of bar: a bonus credited this morning and a batch expiring this
 * morning are a different day's worth of points depending on which went first, and a list that
 * showed them the other way round would be describing a night this application does not have.
 * Comparing two of these is how that order is kept, so the order below is the schedule — the rules
 * at two, the expiry sweep at three, the loyalty sweep at half past — followed by the things that
 * belong to the day rather than to the night, and the week's own verdict last of all, because a week
 * closes when its Sunday is over.
 *
 * <p><strong>A name rather than a flag.</strong> Two of these have no producer in the fold yet and
 * they are declared all the same, because the enum is one place and a client that learned about a
 * kind a slice at a time would have to be changed once per slice. Each says below which ticket fills
 * it.
 *
 * <p>Every one of them is worth something. An anniversary that pays nothing is not a bonus, a week
 * nobody was on a run in is not a week lost, and a day on which nothing moves is not a thing that
 * happens — the same reading {@code TimelineEvent} gives about its own bar, and for the same reason:
 * a marker a customer cannot act on is noise on a list they are trying to decide from.
 */
public enum AKindOfThingThatHappens {

    /**
     * Money arriving in the branch and earning not one point, because {@code TheMostEverSaved} has
     * seen those euros before.
     *
     * <p><strong>The kind the whole feature's problem statement is about.</strong> A branch that
     * takes five hundred out and pays it back in shows points that simply fail to rise, and a figure
     * that does not move explains nothing — a customer reads it as the simulator being broken rather
     * than as the rule it is. So the day is a dated event carrying the amount that earned nothing,
     * and the screen can say why.
     *
     * <p>It fires without any adjustment at all for a customer sitting below their own mark: anybody
     * who has taken money out and not put it back is filling a gap the ledger has already paid for,
     * and the next rule that fires earns them nothing. A withdrawal <em>inside</em> a branch is what
     * ticket 06 adds, and it needs nothing new here.
     *
     * <p>Two in the morning, because a rule firing is what makes money arrive.
     */
    MONEY_ARRIVES_AND_EARNS_NOTHING,

    /** Points reaching their twelve months and going. Three in the morning. */
    POINTS_EXPIRE,

    /**
     * A deposit's anniversary paying a tenth of the whole euros still in it. Half past three.
     *
     * <p>Never worth nothing: an anniversary that pays no points writes no row and puts no marker on
     * the account's own bar, so it is not a thing that happens here either.
     */
    A_BONUS_IS_PAID,

    /**
     * A withdrawal in a branch that could not take all it was asked for, carrying what it did take.
     *
     * <p><strong>No producer yet: ticket 06 fills it.</strong> Taking money out is one of the four
     * adjustments and asking for more than the branch holds is an outcome rather than a refusal —
     * the fold takes what there is, records that the withdrawal was short, and carries on, because
     * refusing would make the most interesting scenario in the set unaskable.
     *
     * <p>During the day rather than in the night. A withdrawal is a thing a customer does at a
     * cashpoint, so it lands after everything the night did and before the week is judged on what
     * went into it — a Sunday withdrawal is part of that Sunday's week.
     */
    A_WITHDRAWAL_FALLS_SHORT,

    /**
     * A goal arriving: the Monday the money is all there by, carrying the goal's target.
     *
     * <p>The Monday {@code WhenAGoalWillBeReached} already names, which is the Monday the money is
     * there <em>by</em> rather than the Monday the last week of saving starts on. Naming the second
     * of those would be naming a day the goal has not arrived on, which is the one thing a
     * projection must never do.
     */
    A_GOAL_IS_REACHED,

    /**
     * A day a goal was wanted by, passed in this branch without the goal arriving, carrying the
     * goal's target.
     *
     * <p>Once, on the deadline, and never again. A goal that is late is late for the rest of the
     * year, and a marker on every day after the deadline would be three hundred markers saying one
     * thing.
     */
    A_DEADLINE_IS_MISSED,

    /**
     * A Sunday that closed without enough going in, ending a run of secured weeks — carrying the run
     * that ended.
     *
     * <p>Only when there was a run to lose. A customer who has not secured a week does not lose one
     * every Sunday, and fifty-two of those would bury the events a decision turns on.
     *
     * <p>Last in the night, because a week is judged on everything that happened in it and the last
     * of those things is whatever happened on its Sunday.
     */
    A_WEEK_IS_LOST
}
