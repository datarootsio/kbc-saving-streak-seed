package io.dataroots.savingstreak.web;

/**
 * What a customer sends to put the last of something aside: which offer.
 *
 * <p>The same shape as {@link ClaimRequest} and named by the same field, deliberately. Taking a
 * hold and claiming are the same question asked with different urgency — "I want this one" —
 * and a page that had to remember that one of them says {@code reward} and the other says
 * something else would be a page with a bug waiting in it.
 *
 * <p>Nothing about how long it lasts is sendable. Seventy-two hours is the scheme's rule rather
 * than a preference, and an application that took a duration from the browser would be letting
 * whoever asked decide how long they kept the last one.
 */
record HoldRequest(String reward) {
}
