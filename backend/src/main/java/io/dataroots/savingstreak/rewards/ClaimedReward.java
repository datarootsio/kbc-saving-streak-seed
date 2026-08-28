package io.dataroots.savingstreak.rewards;

import java.time.Instant;

/**
 * A reward that has been handed over, as the rest of the application sees it: what it was, what it
 * cost at the time, the voucher it produced, and when.
 *
 * <p>The stored record stays inside the module; this is a statement about something that happened.
 * There is no way to undo one, because redemption is final — the voucher exists the moment this does.
 */
public record ClaimedReward(Long id, Reward reward, long pointsSpent, String voucherCode, Instant claimedAt) {
}
