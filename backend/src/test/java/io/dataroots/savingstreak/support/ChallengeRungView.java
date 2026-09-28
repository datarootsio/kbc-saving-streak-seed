package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One rung of a challenge as the API reports it: which rung, what it asks for, what it pays, and
 * when this customer won it.
 *
 * <p>The threshold is an amount of money on a {@code NEW_SAVINGS} challenge and a count of days,
 * weeks or goals on the kinds that measure those, which is why it is a plain number rather than
 * anything that calls itself an amount. The points are the same whole points a deposit earns.
 *
 * <p>{@code wonAt} is null for a rung this customer has not won on the enrolment the card is about,
 * and that is the claim a test about re-enrolling makes directly: the trophy case still holds the
 * badge from the first round, and the ladder on the second round's card is dark again.
 */
public record ChallengeRungView(String rung, BigDecimal threshold, long points, Instant wonAt) {
}
