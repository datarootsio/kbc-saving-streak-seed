package io.dataroots.savingstreak.goals;

/**
 * Which way money is being moved with respect to one goal: into it, or out of it.
 *
 * <p>Here so that no amount anywhere in this feature is ever negative. "Free 50.00 from the holiday"
 * and "put 50.00 towards the holiday" are two different instructions, and the difference between
 * them travels as a word rather than as a minus sign — a sign is one typo away from meaning the
 * opposite of what somebody meant, and a refusal quoting {@code -50.00} teaches them nothing. It is
 * the same argument {@code MoneyMovementDirection} makes about deposits and withdrawals.
 *
 * <p>The ledger underneath does not use this: a row names the goal it came out of and the goal it
 * went into, and direction is what a <em>request</em> about one goal needs in order to say which end
 * that goal is. {@link GoalsService} takes the two ends.
 */
public enum AllocationDirection {

    /** The named goal is the end the money arrives at. */
    INTO_THE_GOAL,

    /** The named goal is the end the money leaves. */
    OUT_OF_THE_GOAL
}
