package io.dataroots.savingstreak.goals;

/**
 * A change to somebody's goals the application will not make, carrying the reason in words the
 * person who asked for it can act on.
 *
 * <p>Its own refusal rather than one borrowed from Deposits, for the reason every other module's is
 * its own: the two refuse for their own reasons and will grow apart. What it does share is the
 * wording of anything both have to say — a figure that is not an amount of money is
 * {@code AmountOfMoney}'s sentence here as it is there, so a customer who has met both objections
 * has met one objection.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer, where a new value here breaks the switch by design.
 *
 * <p><strong>There is no {@code NO_SUCH_ACCOUNT} kind.</strong> This module reads no other module,
 * so it has no way to tell a savings account that exists from a number somebody made up: whoever
 * owns accounts vouches for the identifier before a goal is ever asked for, and says so in the words
 * {@code AccountsService} owns. A kind here would be this module claiming an answer it does not have.
 */
public class GoalRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal can be about. They are apart because the person reading one has
     * a different thing to do next: look at which goal they named, fix what they typed, or accept
     * that the goal they are aiming at is closed.
     */
    public enum Kind {

        /**
         * No goal with that identifier is on that savings account — including one that is on
         * somebody else's. The two are deliberately the same answer: telling a customer that a goal
         * exists but belongs to another account would be telling them about another account.
         */
        NO_SUCH_GOAL,

        /**
         * The goal named is closed to what was asked. Two goals are closed, for two different
         * reasons, and they are one kind here because they are one thing to whoever asked: this goal
         * is not taking that.
         *
         * <p>An abandoned goal is kept so that what somebody was saving for stays readable, and a
         * record that could be rewritten afterwards is not a record — so it can be neither changed
         * nor given money. A completed goal is closed to money only: what is allocated to it has
         * reached its target, and a target that could be exceeded would stop meaning anything, since
         * "still needed" and every projection built on it depend on a goal having a finite need. A
         * customer who wants to save more raises the target, which is the honest way to say it.
         *
         * <p>Money can still be freed out of a completed goal. It is closed to money arriving, not
         * sealed: a customer who over-allocated would otherwise have to give the goal up to get
         * their own money back.
         */
        THE_GOAL_IS_CLOSED,

        /**
         * Allocating more of the balance than the account has spare, quoting what is spare.
         *
         * <p>This is the feature's one invariant refusing: the allocations on an account never add
         * up to more than the account holds. It is about the account rather than about the goal —
         * there is nothing wrong with what was typed and nothing wrong with the goal, there is
         * simply less money unspoken-for than was asked to be spoken for — and the sentence says how
         * much there is, because a customer who has to free money from another goal first needs to
         * know how much to free.
         */
        NOT_ENOUGH_UNALLOCATED,

        /**
         * Allocating past a goal's target, quoting what it still needs.
         *
         * <p>Apart from {@link #NOT_ENOUGH_UNALLOCATED} because the two send the person who caused
         * them somewhere different: one means "the account has not got it", the other means "this
         * goal does not want it, put the rest somewhere else". Over-funding is refused rather than
         * allowed or quietly trimmed, for the reason {@link #THE_GOAL_IS_CLOSED} gives about a
         * target that could be exceeded.
         */
        MORE_THAN_THE_GOAL_NEEDS,

        /**
         * The change does not describe a goal this application will keep, or a move it will make: a
         * goal with no name, a target of nothing or less, a target quoted more finely than money is,
         * a deadline that has already passed, an order that is not a permutation of the account's
         * live goals, an amount that is not an amount of money, a move with the same thing at both
         * ends, freeing more out of a goal than it is holding, or a weekly saving capacity that is
         * not an amount of money.
         *
         * <p>One kind for all of them, because they are all the same thing to whoever is reading:
         * fix what you typed and send it again. The sentence says which of them it was.
         */
        AGAINST_THE_RULES
    }

    private final Kind kind;

    GoalRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
