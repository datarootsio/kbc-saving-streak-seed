package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.goals.AReallocationApplied;

/**
 * What accepting a suggestion did, as the API reports it: the moves that were applied, and the
 * account's money as it stands afterwards.
 *
 * <p>The two travel together because the second is the answer to the first. Somebody who has just
 * taken the advice wants to see what their goals now hold and what the account has left unclaimed, and
 * a page that had to fetch the account again to find out is a page with a gap in it where the money
 * moved. {@code allocated} plus {@code unallocated} still equals {@code balance} on the way out, which
 * is the one sum anybody reading this is checking.
 *
 * <p>{@code applied} is what was applied and not what was read a moment ago. The suggestion is worked
 * out again at the instant of acceptance, so a stale one quietly becomes the smaller one that is true
 * now — or none at all, in which case {@code applied.worthSuggesting()} is false, no money moved, and
 * the sentence says why. That is a 200 and not a refusal: nothing was wrong with the request, there
 * was simply nothing left to do.
 */
record AppliedReallocationResponse(SuggestedReallocationResponse applied,
                                   AllocationsResponse allocations) {

    static AppliedReallocationResponse of(AReallocationApplied accepted) {
        return new AppliedReallocationResponse(
                SuggestedReallocationResponse.of(accepted.applied()),
                AllocationsResponse.of(accepted.allocations()));
    }
}
