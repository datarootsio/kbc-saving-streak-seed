package io.dataroots.savingstreak.rewards;

/**
 * Which of the four requests a shared refusal is refusing, so that the WARN line says what the
 * customer actually pressed.
 *
 * <p><strong>This exists because one code path serves four events and the log line named only
 * the first of them.</strong> The order the contract fixes — existence, on sale, window, the
 * customer, who it is for, the limit, stock, points last — is asked once, in
 * {@code theOfferTheyMayAskFor}, and claiming, taking a hold and joining a queue all run through
 * it; giving a hold up and leaving a queue run through the same lookup for the offer's words.
 * One gauntlet is the point: the spec says the order is part of the contract, and a contract
 * written four times is four contracts that quietly come apart. But a refusal raised on it was
 * logged as {@code claim rejected} whatever had been asked, so grepping that phrase in a log
 * found claims, holds and queue joins mixed together — which defeats the two things the project
 * rule asks of a refusal line: that it be greppable, and that it say what happened.
 *
 * <p><strong>The words are the ones this module already uses.</strong> Every refusal a single
 * caller raises for itself already writes one of these four phrases out — {@code hold rejected}
 * on a second hold, {@code place in a queue rejected} on a second place, {@code leaving a queue
 * rejected} on a place that is not there — and the shared path now says the same four words for
 * the same four requests instead of a fifth thing. They are spelled once, here, because a phrase
 * that is grepped for is a phrase two places will eventually disagree about.
 *
 * <p>An enum rather than a string handed in, so that a new caller of the shared gauntlet has to
 * choose which of these it is rather than inventing a fifth wording at the call site, and so
 * that the value can be read back in a test or a debugger as the event it names.
 *
 * <p>Package-private, like everything else in this module that is not a customer's answer: it is
 * a fact about how this module logs and nothing outside it has any business knowing it exists.
 */
enum WhatWasAskedFor {

    /** Somebody pressed claim — including converting a hold, which is a claim that is paid for. */
    A_CLAIM("claim rejected"),

    /**
     * Somebody asked for the last one to be put aside, or asked to give up the one they have.
     *
     * <p>One value for both directions, because that is the distinction the lines already in
     * this module draw: a second hold and a hold that is not there are both {@code hold
     * rejected}, and what was asked is in the sentence and in the kind.
     */
    A_HOLD("hold rejected"),

    /** Somebody asked to join the queue for something that has run out. */
    A_PLACE_IN_A_QUEUE("place in a queue rejected"),

    /**
     * Somebody asked to leave a queue.
     *
     * <p>Its own value rather than a second reading of {@link #A_PLACE_IN_A_QUEUE}, unlike the
     * two directions of a hold, because that is the split the existing lines make and the split
     * somebody reading a log wants: joining is refused for the offer's sake — it has not run
     * out, they are already in it — and leaving is refused for the queue's, and the two answer
     * different complaints.
     */
    LEAVING_A_QUEUE("leaving a queue rejected");

    private final String inTheLog;

    WhatWasAskedFor(String inTheLog) {
        this.inTheLog = inTheLog;
    }

    /**
     * The phrase the WARN line opens with, which is the thing somebody greps a log for.
     *
     * <p>The whole phrase including the word "rejected", rather than a noun the line adds a verb
     * to, so that what is written here is exactly what is searched for and no format string can
     * quietly put a word between them.
     */
    String inTheLog() {
        return inTheLog;
    }
}
