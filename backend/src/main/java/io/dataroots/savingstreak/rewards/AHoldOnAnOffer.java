package io.dataroots.savingstreak.rewards;

import java.time.Instant;

/**
 * One hold as the rest of the application reads it: which offer is being kept for whom, until
 * when, and what became of it.
 *
 * <p>Public, like {@link ClaimedReward} and {@link AnOfferAsACustomerReadsIt}, because it is what
 * leaves this module. The row it is read off and the repository that finds it stay behind
 * {@link RewardsService}.
 *
 * <p><strong>{@code lapsesAt} is the whole of what a customer wants from this.</strong> A hold
 * that did not say when it runs out would be a deadline nobody saw coming, which is the one thing
 * the spec says a hold must never be. It is an instant rather than a day for the reason argued on
 * {@link TheShelfLifeOfAHold}: seventy-two hours is seventy-two hours, and the page counts down
 * to it.
 *
 * <p><strong>The title is read off the catalogue as it stands, and is not snapshotted.</strong>
 * That is the opposite of what a claim does, and the difference is worth stating because the two
 * look alike. A claim is a record of something that happened and has to go on reading correctly
 * after the catalogue has been renamed or withdrawn — the argument is written out on
 * {@code Redemption}. A hold is not a record of anything; it is a live thing with at most
 * seventy-two hours to run, and an administrator who corrects a title while somebody is holding
 * one has corrected the title of the thing that person is holding. Nothing to preserve, so
 * nothing stored twice.
 *
 * <p>{@code costInPoints} is what the offer costs <em>today</em>, and it is deliberately not a
 * promise. Converting spends the points at the moment of conversion, at the price in force then,
 * which is how a hold taken before a promotion and converted during one pays the sale price. A
 * figure frozen onto the hold would be this application quoting a price it had decided not to
 * charge.
 *
 * <p>{@code endedAt} is null while the hold is live and is the moment it stopped being live
 * otherwise — the moment they converted it, the moment they gave it up, or the moment the sweep
 * wrote down that it had run out. It is not the same as {@code lapsesAt}: one is when it was due
 * to end and the other is when it actually did.
 */
public record AHoldOnAnOffer(Long id, long customerId, String offerCode, String title,
                             long costInPoints, Instant takenAt, Instant lapsesAt,
                             HoldState state, Instant endedAt) {
}
