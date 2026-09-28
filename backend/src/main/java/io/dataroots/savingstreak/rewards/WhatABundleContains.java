package io.dataroots.savingstreak.rewards;

/**
 * One thing that is inside a bundle, as everything outside this module reads it: what it is
 * called, how many of it go in, and the code it is known by.
 *
 * <p><strong>The title travels, so that no page has to look a member up.</strong> A customer
 * reading "Family night in" needs to be told it is two cinema tickets and a bag of popcorn, and a
 * card that received only codes would either print {@code CINEMA_TICKET} at somebody or go and
 * fetch the rest of the catalogue to find the words. It is the offer's title as it stands right
 * now rather than one snapshotted anywhere, because a bundle's contents are a fact about the
 * catalogue rather than about any claim — the snapshot argument applies to what somebody bought,
 * and what somebody bought is the bundle.
 *
 * <p>The code is on it as well, because it is the identity and because the page already maps
 * codes to icons — the one place the frontend knows anything about the catalogue, and one that
 * falls back to a generic gift for anything it has not heard of.
 *
 * <p><strong>There is no price here and there will not be one.</strong> A bundle carries its own
 * price, set by whoever composed it, precisely so that the saving is a decision rather than a
 * subtraction; sending the members' prices would invite a page to add them up and quote a
 * discount nobody chose. The spec is explicit that a bundle is priced itself, and the only figure
 * a customer is charged is the one on the bundle.
 *
 * <p><strong>And no count of how many of the member are left.</strong> How many of the
 * <em>bundle</em> can still be handed over already accounts for every member's stock — it is the
 * scarcest member that decides it, and that arithmetic is
 * {@code RewardsService.whatIsLeftOf}'s — so a second figure per line would be the same fact told
 * again at a finer grain, on a card whose reader cannot act on it. What they can act on is "two
 * left", which is beside the bundle.
 *
 * <p>Public, because it hangs off {@link AnOfferAsACustomerReadsIt} and
 * {@link AnOfferAsItStands}, both of which leave this module. The line it is built from does not.
 */
public record WhatABundleContains(String code, String title, int quantity) {
}
