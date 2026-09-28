package io.dataroots.savingstreak.goals;

/**
 * Whether a goal is still being saved towards, or has been given up on.
 *
 * <p>Two values, and the second is a closing rather than a deletion. A goal that was abandoned is
 * still readable afterwards — its name and its target say what somebody was saving for and how much
 * of it they thought they needed — for the same reason a points batch records its expiry instead of
 * being emptied: what was true is a record, and a row that is gone cannot be asked about.
 *
 * <p>There is deliberately no {@code COMPLETED} here. Whether a goal has arrived is a comparison
 * between what is allocated to it and its target, and both of those are figures that can move; a
 * stored state saying so would be a second copy of an answer the allocations already give, and the
 * two would eventually disagree. Abandoning is different in kind — it is something a customer
 * <em>did</em>, and nothing but this column records that it happened.
 *
 * <p>Public, because {@link RecordedGoal} carries it out of the module: whoever renders a goal has
 * to be able to tell a live one from one that was given up on.
 */
public enum GoalState {

    /** Still being saved towards: it holds a place in the account's order of importance. */
    LIVE,

    /** Given up on. It has left the order and holds no rank, and its name and target remain. */
    ABANDONED
}
