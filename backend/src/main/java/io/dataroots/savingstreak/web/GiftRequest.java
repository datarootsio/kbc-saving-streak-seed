package io.dataroots.savingstreak.web;

/**
 * What a customer sends to give points away: who is to get them, and how many. Whose points they are
 * is the customer in the path.
 *
 * <p>The recipient is named by the contact details they bank under, which is the same thing their
 * owner types to sign in. It is the human-facing half of this contract on purpose: a page may offer
 * a picker over the people who bank here as a convenience, and the request underneath still carries
 * the address.
 *
 * <p>The points arrive as the text that was typed rather than as a number already read for us, for
 * the reason a deposit's amount does: "2.5" is a mistake somebody makes and deserves an answer about
 * points, not about the request being unreadable, and text is the only form that still has the
 * characters in it.
 */
record GiftRequest(String recipientContactDetails, String points) {
}
