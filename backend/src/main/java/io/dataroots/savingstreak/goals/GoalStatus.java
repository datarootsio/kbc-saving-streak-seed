package io.dataroots.savingstreak.goals;

/**
 * Where a goal stands: still being saved towards, arriving in time or late, never arriving at all,
 * arrived, or given up on.
 *
 * <p><strong>Derived on every read, and stored nowhere.</strong> That is the whole reason it is not
 * a value in {@link GoalState}: {@code GoalState} is a column, and every answer here is a comparison
 * between figures that all move — what is allocated against the target, and a projected date against
 * a deadline. A column saying {@code COMPLETED} would be a second copy of an answer the ledger
 * already gives, and a goal whose target was raised the next morning would still be carrying it.
 * Abandoning is different in kind and is stored, because it is something a customer <em>did</em>.
 *
 * <p><strong>Four of these are one comparison, made in {@link WhenAGoalWillBeReached}</strong>: the
 * projected completion date against the day the goal is wanted by. That is why they are one field
 * rather than a status and a separate verdict — there is only one thing being asked, which is
 * whether this goal arrives, and when.
 *
 * <p><strong>There is deliberately no {@code AT_RISK}.</strong> It needs a threshold nobody can
 * defend, and a projection three days past a deadline already says exactly what it is.
 *
 * <p>{@link #COMPLETED} is a closed goal as far as money is concerned: it takes no more, and asking
 * it to is refused as {@code THE_GOAL_IS_CLOSED}, the same answer an abandoned goal gives. Money can
 * still be freed <em>out</em> of one, which is what stops a customer who over-saved having to abandon
 * the goal to get their money back.
 */
public enum GoalStatus {

    /**
     * Live, not arrived, and nothing more can be said: nobody has declared what they can put away in
     * a week, so there is no rate to project a date from and nothing to compare against a deadline.
     *
     * <p>Not a verdict, and deliberately not {@link #UNREACHABLE}. A customer who has said nothing
     * has not said they can save nothing, and answering a question they were never asked would be
     * this application putting a plan in their mouth.
     */
    STILL_SAVING,

    /** The allocation has reached the target. It takes no more money; it can still give some back. */
    COMPLETED,

    /** There is a deadline, and the projection lands on or before it. */
    ON_TRACK,

    /** There is a deadline, and the projection lands after it. */
    OFF_TRACK,

    /** The plan gives this goal nothing, or so little that it never arrives at all. */
    UNREACHABLE,

    /** There is no deadline: a projection, and nothing to be late for. */
    NO_DEADLINE,

    /** Given up on. It holds no money — abandoning returns the whole of it — and holds no rank. */
    ABANDONED
}
