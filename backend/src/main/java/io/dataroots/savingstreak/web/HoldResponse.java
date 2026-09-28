package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.rewards.AHoldOnAnOffer;
import io.dataroots.savingstreak.rewards.HoldState;

/**
 * One hold as the API reports it: which offer is being kept, until when, and what became of it.
 *
 * <p>The answer to taking one, to giving one up, and nothing else — converting a hold answers
 * with the claim it became, because what the customer has at that point is a voucher and the
 * hold is over.
 *
 * <p>{@code lapsesAt} is a moment and not a day, which is the one thing about this response
 * worth arguing. Every other deadline this API sends is a calendar date, because every other
 * deadline is one: a voucher's shelf life, an offer's window, a promotion's last day. A hold is
 * seventy-two hours from the moment it was taken, so a date would be a rounding of the promise
 * rather than the promise, and the page counts down to it. The argument is written out on the
 * module's own shelf-life class.
 *
 * <p>{@code state} travels as the enum's own name, for the reason a voucher's does: the page
 * styles and phrases by it, and a string it had to know the spellings of would be the same
 * knowledge with nothing checking it.
 *
 * <p>{@code costInPoints} is what the offer costs <em>today</em> and is deliberately not a price
 * this hold has locked in. Converting spends the points at the moment of conversion at the price
 * in force then, so a hold taken before a promotion and converted during one pays the sale
 * price. The figure is here so that a page can show what the customer is about to spend without
 * going back to the catalogue for it, and it is as true as any price on any card: true today.
 *
 * <p>{@code endedAt} is null while the hold is live and is the moment it actually stopped being
 * live otherwise, which is never the same question as {@code lapsesAt} — one is when it was due
 * to end and the other is when it did.
 */
record HoldResponse(Long id, long customerId, String offerCode, String title, long costInPoints,
                    Instant takenAt, Instant lapsesAt, HoldState state, Instant endedAt) {

    static HoldResponse of(AHoldOnAnOffer hold) {
        return new HoldResponse(hold.id(), hold.customerId(), hold.offerCode(), hold.title(),
                hold.costInPoints(), hold.takenAt(), hold.lapsesAt(), hold.state(),
                hold.endedAt());
    }
}
