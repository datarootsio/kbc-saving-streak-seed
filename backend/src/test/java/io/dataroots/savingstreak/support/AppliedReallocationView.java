package io.dataroots.savingstreak.support;

/**
 * What accepting a suggestion did, as the API reports it: the moves that were applied, and the
 * account's money as it stands afterwards.
 *
 * <p>Both halves matter to a test. The first says what was applied <em>now</em> rather than what was
 * read a moment ago, which is what makes a stale suggestion assertable; the second is where
 * {@code allocated + unallocated == balance} is checked after the money moved.
 */
public record AppliedReallocationView(SuggestedReallocationView applied, AllocationsView allocations) {
}
