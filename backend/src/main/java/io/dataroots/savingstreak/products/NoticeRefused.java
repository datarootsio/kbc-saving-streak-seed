package io.dataroots.savingstreak.products;

/**
 * Notice this application will not give or will not take back, carrying the reason the customer
 * needs to act on.
 *
 * <p>Its own refusal rather than another kind on {@link ProductRefused}, following the shape
 * Accounts and Budgets already use for a module that does more than one thing: what a product is
 * and what an administrator may publish about it are one subject, and what one customer has given
 * notice on is another. The two refuse for entirely different reasons, are raised by entirely
 * different doors, and a single enum spanning both would make the exhaustive switch that maps them
 * to statuses a place where "a version dated before the one before it" sits next to "that notice
 * has already been cancelled".
 */
public class NoticeRefused extends RuntimeException {

    /**
     * Why notice was not given, or not cancelled.
     *
     * <p>{@code NOT_A_NOTICE_ACCOUNT} is the one worth reading twice. Giving notice on an account
     * whose agreement asks for none is not a harmless no-op: it is somebody acting on a belief
     * about their own money that is wrong, and answering "done" would leave them waiting thirty-two
     * days for something that was already theirs. The sentence says the account has nothing to give
     * notice of, which is the good news it actually is.
     *
     * <p>{@code A_NOTICE_NO_LONGER_STANDING} covers a notice already cancelled and one a withdrawal
     * has already used in full. They are one refusal because what the customer does about them is
     * the same — nothing, the notice is finished — and the sentence says which of the two it was.
     *
     * <p>Adding a kind here breaks the exhaustive switch in {@code RefusalsAsHttp}, which is the
     * point: a refusal reaches the customer with a status somebody chose rather than a default.
     */
    public enum Kind {

        NOT_AN_AMOUNT_OF_MONEY,

        NOT_A_NOTICE_ACCOUNT,

        NO_SUCH_NOTICE,

        A_NOTICE_NO_LONGER_STANDING
    }

    private final Kind kind;

    NoticeRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
