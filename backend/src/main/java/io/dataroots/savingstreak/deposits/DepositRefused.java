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
        NOT_ENOUGH_MONEY
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
