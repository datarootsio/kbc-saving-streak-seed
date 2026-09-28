package io.dataroots.savingstreak.support;

import java.time.Instant;

/**
 * A hold as the API reports it: which offer is being kept for whom, until when, and what became
 * of it.
 *
 * <p>{@code lapsesAt} is an {@link Instant} and not a date, because that is what the API sends
 * and it is the one deadline in this application that is not a calendar day: a hold is
 * seventy-two hours from the moment it was taken. A test that read it as a date would be
 * rounding the very thing it is asserting about.
 *
 * <p>{@code state} is text rather than the backend's enum, for the reason {@link OfferView}'s
 * state is: a test asserting {@code "HELD"} is asserting what actually goes over the wire, which
 * is what a page reads, and sharing the enum would let a value be renamed on both sides at once
 * with every test still passing.
 *
 * <p>{@code endedAt} is null while the hold is live and is the moment it stopped being live
 * otherwise — never the same question as {@code lapsesAt}, which is when it was due to end.
 */
public record HoldView(Long id, long customerId, String offerCode, String title,
                       long costInPoints, Instant takenAt, Instant lapsesAt, String state,
                       Instant endedAt) {
}
