package io.dataroots.savingstreak.gifting;

/**
 * A gift the application will not make, carrying the reason in words the person who tried to make it
 * can act on — a mistyped address, a pointless act, a figure that is not a number of points, or a
 * pot with less in it than they meant to give.
 *
 * <p>Gifting's own refusal rather than one borrowed from Rewards, even though both modules spend a
 * customer's points and both can come up short. The two refuse for their own reasons and will grow
 * apart: this one already has kinds about a second person, which nothing about claiming a reward
 * will ever have.
 *
 * <p>These four kinds are the whole of the rule. There is no cap on the size of one gift, no daily
 * total, no cooldown, no minimum and no limit on how many people one customer may give to — a stated
 * absence rather than an oversight. Should a limit ever be wanted it belongs here beside them.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer.
 */
public class GiftRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal can be about. They are different because the person reading it
     * has a different thing to do next: check who they are signed in as, check the address they
     * typed, give to somebody else, type a number, or give away less.
     */
    public enum Kind {

        /** The customer said to be giving is not one this application has heard of. */
        NO_SUCH_CUSTOMER,

        /** Nobody banks under the contact details the gift was addressed to. */
        NO_SUCH_RECIPIENT,

        /**
         * The sender and the recipient are the same person. Refused rather than quietly done: it
         * would move nothing, and a no-op that reports success is worse than a refusal that
         * explains itself.
         */
        TO_YOURSELF,

        /**
         * The figure given is not a positive whole number of points. It arrives as the customer
         * typed it, so a fraction and a word are both answered here rather than coerced into
         * something plausible.
         */
        NOT_A_NUMBER_OF_POINTS,

        /** The sender does not hold that many points, and the reason says how many they do hold. */
        NOT_ENOUGH_POINTS
    }

    private final Kind kind;

    GiftRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
