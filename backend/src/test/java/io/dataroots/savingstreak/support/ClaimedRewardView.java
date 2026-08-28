package io.dataroots.savingstreak.support;

import java.time.Instant;

/** A claimed reward as the API reports it: what it was, what it cost, the voucher, and when. */
public record ClaimedRewardView(Long id, String code, String title, long pointsSpent, String voucherCode,
                                Instant claimedAt) {
}
