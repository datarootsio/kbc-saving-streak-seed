package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.rewards.ARewardOnOffer;

/**
 * One thing in the catalogue: what to call it back, what to show, and what it costs.
 *
 * <p>The words are the backend's, so a page renders the catalogue without knowing anything about what
 * is in it. The code is what a claim names — sent out here and sent back unchanged — so the frontend
 * never spells a reward's name out for itself.
 *
 * <p>Built from what the Rewards module says is on offer rather than from a constant it hands over.
 * This class used to take the enum, which meant the web layer held a compile-time list of the
 * catalogue and could not have been shown a fifth reward without being rebuilt. It is a mapping from
 * one record to another now, which is all a response object should ever have been.
 */
record RewardResponse(String code, String title, String description, long costInPoints) {

    static RewardResponse of(ARewardOnOffer offer) {
        return new RewardResponse(
                offer.code(), offer.title(), offer.description(), offer.costInPoints());
    }
}
