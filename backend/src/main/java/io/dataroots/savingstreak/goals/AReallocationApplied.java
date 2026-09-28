package io.dataroots.savingstreak.goals;

/**
 * What accepting a suggestion did: the moves that were actually applied, and the account's money as
 * it stands afterwards.
 *
 * <p>The two travel together because the second is the answer to the first. A customer who has just
 * taken the advice wants to see what their goals now hold, and a page that had to fetch the account
 * again to find out is a page with a gap in it where the money moved.
 *
 * <p><strong>{@code applied} is what was applied, and not what was read.</strong> The suggestion is
 * worked out again at the moment of acceptance, so a customer who read one figure yesterday and
 * pressed the button today gets today's moves — and if nothing is worth moving any more,
 * {@code applied.worthSuggesting()} is false, no move was made, and the sentence says why. That is the
 * honest answer to a stale plan: not a refusal, and not yesterday's moves applied to today's money.
 */
public record AReallocationApplied(ASuggestedReallocation applied, AllocationsOnAnAccount allocations) {
}
