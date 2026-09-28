package io.dataroots.savingstreak.automation;

/**
 * A saving rule this application will not leave standing, or a change to one it will not make,
 * carrying the reason in words the person who asked for it can act on.
 *
 * <p>Its own refusal rather than one borrowed from Goals or Accounts, for the reason every other
 * module's is its own: they refuse for their own reasons and will grow apart. What it does share is
 * the wording of anything several of them have to say — a figure that is not an amount of money is
 * {@code AmountOfMoney}'s sentence here as it is there, and an account nobody holds is
 * {@code AccountsService}'s, so a customer who has met one of those objections has met it
 * everywhere.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer, where a new value in {@link Kind} breaks the switch by
 * design — that break is how this codebase makes sure a refusal reaches the customer with a status
 * somebody chose rather than one it quietly inherited.
 *
 * <p><strong>There is no {@code NO_SUCH_ACCOUNT} kind.</strong> This module never asks whether a
 * savings account exists, so it has no way to tell one that does from a number somebody made up:
 * {@code SavingRuleController} vouches for the identifier in the path before a rule is ever asked
 * for, and answers 404 in the words Accounts owns. A kind here would be this module claiming an
 * answer it does not have — the same argument {@code GoalRefused} makes.
 */
public class SavingRuleRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal can be about. They are apart because the person reading one has
     * a different thing to do next: look at which rule they named, accept that the rule they are
     * arguing about is over, or fix what they typed.
     */
    public enum Kind {

        /**
         * No rule with that identifier is on that savings account — including one that is on
         * somebody else's. The two are deliberately the same answer: telling a customer that a rule
         * exists but belongs to another account would be telling them about another account.
         */
        NO_SUCH_RULE,

        /**
         * The rule named has been ended, and an ended rule is a record rather than an instruction.
         *
         * <p>Kept readable and refused to every change, for the reason an abandoned goal is: the
         * whole point of ending a rule rather than deleting it is that the deposits it made stay
         * explained, and a record that could be rewritten afterwards is not a record. A customer who
         * wants the rule back leaves a new one standing, which is the honest way to say it.
         */
        THE_RULE_IS_ENDED,

        /**
         * A split names a goal that is not live on the savings account the rule feeds — one that is
         * not there at all, one that belongs to another account, or one that has been given up on.
         *
         * <p>Its own kind rather than {@link #AGAINST_THE_RULES}, because what the person reading it
         * has to do next is different: everything under that kind is "fix what you typed", and this
         * one is "look at which goal you named". It is the same sentence and the same answer
         * {@code GoalRefused.NO_SUCH_GOAL} gives, in the module that happens to be holding the
         * request.
         *
         * <p>It is refused when the rule is written or changed, and <strong>never when it
         * fires</strong>. By the time a split runs, the money is already in the savings account, and
         * refusing then would mean rolling back a deposit because a goal had been finished or given
         * up on since. A goal abandoned after the split was written therefore takes nothing and its
         * share spills to the next goal — see {@code AutomationService}.
         */
        NO_SUCH_GOAL_IN_THE_SPLIT,

        /**
         * The request does not describe a rule this application will keep: a rule with no name, a
         * current account its holder does not hold, a trigger without the day it needs, a day of the
         * month outside 1 to 31, an amount that is not an amount of money, a floor below nothing,
         * shares that do not add to a hundred, a share that is not a whole percentage of more than
         * nothing, one goal named twice in a split, or one rule too many.
         *
         * <p>One kind for all of them, because they are all the same thing to whoever is reading:
         * fix what you typed and send it again. The sentence says which of them it was.
         */
        AGAINST_THE_RULES
    }

    private final Kind kind;

    SavingRuleRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
