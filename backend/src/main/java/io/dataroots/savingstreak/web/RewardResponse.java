package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.rewards.Reward;

/**
 * One thing in the catalogue: what to call it back, what to show, and what it costs.
 *
 * <p>The words are the backend's, so a page renders the catalogue without knowing anything about what
 * is in it. The code is what a claim names — sent out here and sent back unchanged — so the frontend
 * never spells a reward's name out for itself.
 */
record RewardResponse(String code, String title, String description, long costInPoints) {

    static RewardResponse of(Reward reward) {
        return new RewardResponse(
                reward.code(), reward.title(), reward.description(), reward.costInPoints());
    }
}
