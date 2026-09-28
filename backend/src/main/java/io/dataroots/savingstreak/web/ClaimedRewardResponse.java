package io.dataroots.savingstreak.web;

import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.rewards.ClaimedReward;
import io.dataroots.savingstreak.rewards.VoucherState;

/**
 * A claim as the customer sees it: what they took, what it cost them at the time, the voucher it
 * produced, when, and where that voucher has got to since. The voucher is the thing they actually
 * got, so it travels with the claim rather than being fetched afterwards.
 *
 * <p>The state travels for the same reason the voucher code does. A customer's list of vouchers
 * used to be a list of things that were all equally good, because the application could not tell
 * them apart; now that a counter can hand one over, a code that has been spent has to read as
 * spent, with the day on it, or the page is inviting somebody to carry a dead code to a till.
 *
 * <p>The day the voucher runs out travels on every claim, not only on the ones that have run out,
 * and that is the point of it: a customer who is told the date once the deadline has passed was
 * never told the date. Null when the offer it came from set no shelf life, which is what all four
 * seeded offers do — so the field is present and empty on almost every claim this application has
 * ever made, and a page reads that absence as "this one does not run out".
 *
 * <p><strong>The reason a voucher was cancelled travels, and the day it was.</strong> This is the
 * one thing that happens to a claim which is nobody's doing but the scheme's, and a list that
 * greyed the row out and said nothing else would be the application telling a customer their
 * voucher stopped working. The words are whoever ran the scheme's own, passed through unchanged:
 * a paraphrase here would be a second account of a mistake that already has one. Both are null
 * for anything but a cancelled voucher, like the pair above them.
 *
 * <p>What came back is deliberately not a field. A cancellation refunds exactly what the claim
 * cost, which is {@code pointsSpent}, already on this record and already the figure the customer
 * recognises from the day they claimed; a second number could only agree with it or be wrong.
 *
 * <p>The fields are added at the end rather than woven in among the others, which is not a style
 * decision: this shape is the one the existing reward tests read back, and appending keeps every
 * field they name exactly where it was.
 */
record ClaimedRewardResponse(Long id, String code, String title, long pointsSpent, String voucherCode,
                             Instant claimedAt, VoucherState state, Instant usedAt,
                             String usedByCounter, LocalDate expiresOn, Instant cancelledAt,
                             String cancelledBecause) {

    static ClaimedRewardResponse of(ClaimedReward claimed) {
        return new ClaimedRewardResponse(
                claimed.id(),
                claimed.rewardCode(),
                claimed.title(),
                claimed.pointsSpent(),
                claimed.voucherCode(),
                claimed.claimedAt(),
                claimed.state(),
                claimed.usedAt(),
                claimed.usedByCounter(),
                claimed.expiresOn(),
                claimed.cancelledAt(),
                claimed.cancelledBecause());
    }
}
