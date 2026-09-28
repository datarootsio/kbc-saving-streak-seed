package io.dataroots.savingstreak.rewards;

/**
 * A voucher the application will not act on, carrying the reason in words the person at the counter
 * can read out to the person in front of them.
 *
 * <p>Separate from {@link RewardRefused} rather than another kind on it, and the split is between
 * two different conversations rather than between two modules. {@code RewardRefused} is the answer
 * to a customer asking for something: it is about a catalogue, a balance, and what somebody has to
 * do before they can have the thing. This is the answer to a counter asking about a code, where
 * every reason is about the voucher's own life and the person reading it is not the person who
 * bought it. One exception with two vocabularies in it would make each exhaustive {@code switch} in
 * the HTTP advice a list of cases the other half never raises.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer.
 */
public class VoucherRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal at a counter can be about. Separate values because what the
     * person holding the screen does next differs: check the code they typed, or tell the customer
     * the thing has already been handed over.
     *
     * <p>Four of them, which is the whole set the spec names and the whole set a voucher can
     * now reach. Each arrived with the slice that could first raise it — a value added at the
     * moment something can produce it, the compiler then stopping that slice in
     * {@code RefusalsAsHttp} until somebody has chosen the status it deserves — and that
     * interruption is the point of the mechanism rather than a cost of it. There is nothing left
     * to predict: a voucher is issued, and then it is used, expired or cancelled, so a fifth kind
     * here would mean a fifth thing had happened to a voucher.
     */
    public enum Kind {

        /** No voucher has ever carried that code. A typo at a till, most often. */
        NO_SUCH_VOUCHER,

        /** It was handed over already, and the sentence says when and where. */
        ALREADY_USED,

        /**
         * It outlived the shelf life its offer gave it, and the sentence says which day that was.
         *
         * <p>Its own kind rather than being folded in with the one above, because the two are
         * different conversations across the counter: "somebody has already had this" is about a
         * thing that was collected, and "this ran out on the 14th" is about a thing that never
         * was. The person at the till reads one of them out loud, and a single "not good" would
         * make them guess which.
         */
        THE_VOUCHER_HAS_EXPIRED,

        /**
         * An administrator revoked it, and the sentence says why in their own words.
         *
         * <p>Its own kind rather than sharing one with the expiry above, and the split matters
         * more here than it does between the first two. Those are both things the customer can
         * be told they did — spent it, or left it too long. This one is the scheme's own mistake:
         * the points have already gone back, nobody at the till can put the voucher right, and
         * the only useful thing to say out loud is the reason somebody typed when they cancelled
         * it. A till told merely "not good" would have to guess between three quite different
         * conversations, and would probably pick the one that blames the person in front of them.
         */
        THE_VOUCHER_WAS_CANCELLED
    }

    private final Kind kind;

    VoucherRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
