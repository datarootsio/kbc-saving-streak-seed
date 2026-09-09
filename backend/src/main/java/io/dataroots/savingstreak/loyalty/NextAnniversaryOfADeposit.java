package io.dataroots.savingstreak.loyalty;

import java.time.LocalDate;

/**
 * When a deposit next pays and what that anniversary is worth at what the deposit holds today:
 * which deposit, the day, and the points.
 *
 * <p>The point of the whole scheme made visible. A customer looking at their history can see what
 * leaving the money alone is going to pay them, and — because the figure is worked out from what is
 * still in the deposit rather than from what landed in it — what taking it out would cost. Withdraw
 * half and this figure halves; that is the forfeit, reported before it happens rather than
 * explained afterwards.
 *
 * <p>A promise about money that is there. A deposit holding nothing has none of these at all rather
 * than a date with nothing on it: the money has gone, and there is no anniversary left for it to
 * reach. That is the caller's reading of an absence, not a field in here.
 *
 * <p>Points of nothing is a different statement and is said out loud. A deposit holding nine euros
 * has an anniversary coming and will be paid nothing on it, because a tenth of nine euros rounds
 * down — so the rounding is a rule the customer can see rather than a bug they suspect.
 *
 * <p>A day rather than a moment, for the reason {@link LoyaltyAnniversary#dayOf} gives: the day is
 * what is promised, and the zone it is read in is this module's to decide rather than the caller's.
 *
 * <p>What this deposit has <em>already</em> been paid is deliberately not here. That is a number of
 * points in a customer's pot, the points ledger is where those live, and it is reported beside this
 * from there — so a deposit's loyalty figures cannot come from two places that disagree.
 */
public record NextAnniversaryOfADeposit(long depositId, LocalDate on, long points) {
}
