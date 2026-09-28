package io.dataroots.savingstreak.products;

/**
 * A fixed term this application will not break, carrying the reason the customer needs to act on.
 *
 * <p>Its own refusal rather than another kind on {@link ProductRefused} or on
 * {@link NoticeRefused}, following the shape this module has already settled on twice: what an
 * administrator may publish about a product is one subject, what one customer has given notice on
 * is a second, and what one customer may do to the term they are locked into is a third. They
 * refuse for entirely different reasons, are raised by entirely different doors, and a single enum
 * spanning them would make the exhaustive switch that maps them to statuses a place where "a
 * version dated before the one before it" sits beside "that term matured last March".
 *
 * <p><strong>Breaking a term is refused in words and never in silence, which is the whole reason
 * this type exists.</strong> A customer pressing a button that says what it will cost is entitled
 * to be told why it did not happen, and the two things that can be wrong are things they can
 * understand and act on: the account is not on a term at all, or the term is already over. Both are
 * good news dressed as a refusal, and both sentences say so.
 */
public class TermRefused extends RuntimeException {

    /**
     * Why a term was not broken.
     *
     * <p>{@code NOT_A_TERM_ACCOUNT} is also what a term already broken answers with, and that is
     * not a gap — it is the design. Breaking moves the account onto free savings, so an account
     * whose term was broken yesterday is an account with no term, and "a term cannot be broken
     * twice" is a consequence of what breaking does rather than a rule anything has to remember.
     * The sentence is the same either way because what the customer does next is the same: nothing,
     * the money is already theirs.
     *
     * <p>{@code A_TERM_THAT_HAS_ALREADY_MATURED} is told apart from it because the sentences point
     * somewhere different. An account that never had a term has nothing to explain; a term that
     * matured has a date the customer can be told, and knowing their money came free in March is
     * the difference between "why can I not break this" and "I did not need to".
     *
     * <p>Adding a kind here breaks the exhaustive switch in {@code RefusalsAsHttp}, which is the
     * point: a refusal reaches the customer with a status somebody chose rather than a default.
     */
    public enum Kind {

        NOT_A_TERM_ACCOUNT,

        A_TERM_THAT_HAS_ALREADY_MATURED
    }

    private final Kind kind;

    TermRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
