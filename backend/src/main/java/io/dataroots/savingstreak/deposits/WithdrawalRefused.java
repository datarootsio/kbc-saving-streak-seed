package io.dataroots.savingstreak.deposits;

/** A withdrawal the application will not make, carrying the reason the customer needs to act on. */
public class WithdrawalRefused extends RuntimeException {

    /**
     * Why a withdrawal was not made.
     *
     * <p>{@code MONEY_IS_SPOKEN_FOR} is the savings goals' claim on the balance: the account holds
     * the money, and a goal has already spoken for it. Told apart from {@code NOT_ENOUGH_MONEY}
     * deliberately, because what the customer does about it is different — there is nothing to pay
     * in, there is a goal to free money from, and the sentence names what is spare so they know how
     * much. Nothing is reduced on their behalf: reallocating a customer's money without being asked
     * is the one thing this feature exists to refuse.
     *
     * <p>{@code AGAINST_THE_AGREEMENT} is the product's own condition: the account holds the
     * money, no goal has spoken for it, and the agreement the account was opened under says it may
     * not leave yet. One kind for all three of those conditions rather than one each, because what
     * the customer does about them is in the sentence and never in the code — a page that switched
     * on a notice kind and a maturity kind would be the second place this application decides what
     * a notice period is, and the first place is the module that keeps the agreement. Told apart
     * from {@code AGAINST_THE_RULES} because that one is about the request being malformed and
     * this one is about a perfectly good request arriving too early.
     *
     * <p>Adding a kind here breaks the exhaustive switch in {@code RefusalsAsHttp}, which is the
     * point: a refusal reaches the customer with a status somebody chose rather than a default.
     */
    public enum Kind {
        NO_SUCH_ACCOUNT, AGAINST_THE_RULES, NOT_ENOUGH_MONEY, MONEY_IS_SPOKEN_FOR,
        AGAINST_THE_AGREEMENT
    }

    WithdrawalRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    private final Kind kind;

    public Kind kind() { return kind; }
}
