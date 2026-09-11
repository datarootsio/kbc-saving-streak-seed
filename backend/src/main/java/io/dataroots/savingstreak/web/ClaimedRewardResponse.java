package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.rewards.ClaimedReward;

/**
 * A claim as the customer sees it: what they took, what it cost them at the time, the voucher it
 * produced, and when. The voucher is the thing they actually got, so it travels with the claim
 * rather than being fetched afterwards.
 */
record ClaimedRewardResponse(Long id, String code, String title, long pointsSpent, String voucherCode,
                             Instant claimedAt) {

    static ClaimedRewardResponse of(ClaimedReward claimed) {
        return new ClaimedRewardResponse(
                claimed.id(),
                claimed.reward().code(),
                claimed.reward().title(),
                claimed.pointsSpent(),
                claimed.voucherCode(),
                claimed.claimedAt());
    }
}
