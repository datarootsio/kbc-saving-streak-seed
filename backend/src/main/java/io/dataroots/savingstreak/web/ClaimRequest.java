package io.dataroots.savingstreak.web;

/**
 * What a customer sends to claim a reward: which one. Whose points pay for it is the customer in the
 * path, and what it costs is not theirs to say.
 *
 * <p>The reward arrives as the text that was sent rather than as a catalogue entry already matched
 * for us, for the same reason a deposit's amount does: a name that is not in the catalogue deserves
 * an answer about the catalogue, not the default sentence about a request that could not be read.
 */
record ClaimRequest(String reward) {
}
