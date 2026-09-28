package io.dataroots.savingstreak.web;

/**
 * What whoever runs the scheme sends when they revoke a voucher: why, and nothing else.
 *
 * <p>Free text, because what goes wrong with a claim is not a closed set — a duplicate, a test,
 * somebody's finger, a reward that could not be fulfilled after all — and a vocabulary would grow
 * a value every time the scheme met a new kind of mistake, with an "other" in it that was this
 * field with extra steps. There is nothing this application could check one against either.
 *
 * <p>It is required all the same, and that is the whole reason this record exists rather than a
 * bare POST. "A mistake can be undone" is only half of what an administrator is promised; the
 * other half is "and explained", and a voucher revoked with no sentence attached is a code that
 * simply stopped working — which is exactly the position the customer and the person at the till
 * were in before any of this existed. Blank counts as not filled in, for the reason the counter's
 * name does: a reason of one space is a field somebody got past rather than a reason.
 *
 * <p>The voucher is in the path rather than in here, like the counter's request next door. It is
 * the thing being acted on, and a code in a URL is how two requests stay obviously about the same
 * voucher.
 */
record CancelVoucherRequest(String reason) {
}
