package io.dataroots.savingstreak.deposits;

/**
 * A deposit the application will not make, carrying the reason in words the person who asked for it
 * can act on.
 *
 * <p>The message is written to be read by that person rather than by a developer: it is passed
 * through to the screen unchanged, so a reason phrased as a developer's diagnostic would arrive in
 * front of a customer as one.
 *
 * <p>Not a Spring exception, and no status code anywhere near it. Which HTTP status reports a
 * refusal is a question about the API, and it is answered in the web layer.
 */
public class DepositRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal can be about, which is all a caller needs in order to report
     * one. They are told apart because the person reading the refusal has a different thing to do
     * about each: find the right account, correct what they typed, or move less money.
     */
    public enum Kind {
        NO_SUCH_ACCOUNT,
        AGAINST_THE_RULES,
        /**
         * Two real accounts, an amount that is an amount, and no money to move. Kept apart from
         * {@link #AGAINST_THE_RULES} because it is not a rule the deposit broke — the same request
         * on the same accounts goes through once there is enough in the one it comes out of.
         */
        NOT_ENOUGH_MONEY,
        /**
         * Two real accounts, and the person making the deposit is not allowed to make it. The only
         * refusal in this module about <em>who is asking</em> rather than about what they asked
         * for: a shared pot's savings account takes money from its members, with a role that may
         * pay in, and from nobody else.
         *
         * <p>Kept apart from {@link #AGAINST_THE_RULES} because the two say different things about
         * what to do next. A rule broken is a request to change — move the money between your own
         * accounts, type a smaller figure — and this is a request to make from a different
         * position: be invited, or be made a contributor. It is also the one kind here that is not a
         * 400, and telling them apart is what lets the web layer say so.
         */
        NOT_ALLOWED,
        /**
         * Two real accounts, and the savings account belongs to a shared pot an owner has closed.
         *
         * <p>Kept apart from {@link #NOT_ALLOWED} because it is not about who is asking at all: an
         * owner, a contributor and a stranger are each refused it in the same words, and there is no
         * position any of them could be moved to that would let a euro in. What they do next is
         * nothing, which is a different answer from "ask to be invited" — and it is why the web
         * layer reports this one as a conflict rather than as a 403.
         */
        THE_POT_IS_CLOSED,
        /**
         * Two real accounts, and the savings account's own agreement has ended: the customer closed
         * it, and a closed account takes no more money.
         *
         * <p><strong>Not {@link #THE_POT_IS_CLOSED}, although the two are one word apart.</strong>
         * That was the obvious place to put this and it is the wrong one, for the reason those two
         * constants are kept apart from {@link #NOT_ALLOWED}: what is named here is the thing the
         * person ran into, and they ran into two different things. A pot that is closed is somebody
         * else's decision about a shared account — an owner ended it, and a member, a viewer and a
         * stranger are all told the same thing about somewhere that was never only theirs. An
         * account that is closed is this customer's own decision about their own account, taken
         * with one press on a day this refusal names, and undone by opening another account, which
         * is also one press. A reader grepping the log for a pot that turned money away would find
         * every personal account that had been closed as well, and neither sentence could be
         * written from the other's constant.
         *
         * <p>A conflict rather than a bad request, for the reason the pot's is: nothing was typed
         * wrong. The account is real, the amount is an amount, the money is there — the state of
         * the account is what will not have it, and a 400 would send somebody off to look for a
         * mistake they did not make.
         */
        THE_ACCOUNT_IS_CLOSED
    }

    private final Kind kind;

    DepositRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
