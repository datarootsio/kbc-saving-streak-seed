package io.dataroots.savingstreak.web;

/**
 * What a customer sends to get into the queue for something that has run out: which offer.
 *
 * <p>The same shape as {@link ClaimRequest} and {@link HoldRequest}, named by the same field,
 * deliberately. Claiming, holding and waiting are three urgencies of one sentence — "I want this
 * one" — and a page that had to remember which of the three says something other than
 * {@code reward} would be a page with a bug waiting in it.
 *
 * <p>Nothing about a position is sendable, and nothing about being told. Where somebody ends up
 * in the line is the order they joined in, which is the backend's to decide and the one thing a
 * queue means; and a customer whose turn comes is told because the scheme tells them, not
 * because they asked to be.
 */
record JoinTheQueueRequest(String reward) {
}
