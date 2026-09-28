package io.dataroots.savingstreak.challenges;

/**
 * A challenge the application will not enrol somebody in, or an enrolment it will not end, carrying
 * the reason in words the person who asked can act on.
 *
 * <p>This module's own, rather than shared with the modules it reads. Challenges refuse for reasons
 * that have nothing to do with a deposit or a reward — being already in something, not being in it
 * at all — and a shared exception would tie each module's vocabulary to the others' and make every
 * one of them grow a kind the rest have no use for.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer.
 */
public class ChallengeRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal can be about. They are apart because the person reading one has
     * a different thing to do next, and because the web layer answers them with different statuses:
     * two are about something that is not there, and the rest are about the state of something that
     * is.
     *
     * <p>All seven the spec names are here, and the two about a season are the two whose answer is
     * a date rather than an action: every other kind ends with "so do something else", and those
     * two end with "so come back" and "so you are too late". The sentence each carries says which
     * day, because a refusal that withheld the date would leave a customer with nothing to do but
     * guess.
     */
    public enum Kind {

        /** Nobody banks here under that identifier. */
        NO_SUCH_CUSTOMER,

        /** The bank offers no challenge under that code. */
        NO_SUCH_CHALLENGE,

        /** They are in it already, and a second enrolment would be a duplicate on their own card. */
        ALREADY_ENROLLED,

        /** There is nothing of theirs to leave. */
        NOT_ENROLLED,

        /**
         * The challenge belongs to a season whose first day has not arrived yet.
         *
         * <p>Not a {@link #NO_SUCH_CHALLENGE}, although hiding a season until it opens would be one
         * way to do it: a campaign a customer can see coming is the reason they come back in
         * January, which is the whole point of running one. So the card is there to read, the
         * refusal is about the window rather than about the challenge, and the sentence says the
         * day it opens.
         */
        THE_SEASON_HAS_NOT_OPENED,

        /**
         * The challenge belongs to a season whose last day has gone by.
         *
         * <p>Apart from {@link #THE_SEASON_HAS_NOT_OPENED} although both are the same window read
         * from different ends, because the two say opposite things to the person reading them: one
         * of them ends with "so come back on the third" and this one ends with "so that one is
         * over". Apart from {@link #ALREADY_DONE_AND_NOT_REPEATABLE} too — that is a promise about
         * a badge somebody has won, and this is a window that ran out whether or not they ever
         * joined.
         */
        THE_SEASON_HAS_CLOSED,

        /**
         * They have finished this one, and the bank offers it once in a lifetime.
         *
         * <p>Apart from {@link #ALREADY_ENROLLED}, which is about something still running, because
         * the two say different things to the person reading them: one of them ends with "so wait",
         * and this one ends with "so never". A repeatable challenge somebody has finished refuses
         * nothing at all — a fresh enrolment takes a fresh mark and asks for genuinely new money,
         * which is what makes a repeat safe to allow and a one-off worth declaring.
         */
        ALREADY_DONE_AND_NOT_REPEATABLE
    }

    private final Kind kind;

    ChallengeRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
