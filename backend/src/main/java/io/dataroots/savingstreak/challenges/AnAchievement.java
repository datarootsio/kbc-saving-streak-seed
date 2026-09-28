package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One thing the customer has achieved: which challenge, which rung of it, when they reached it, the
 * reading that won it and the points it paid.
 *
 * <p>A badge, in other words, and there is no separate notion of one. The trophy case is the list of
 * these read newest first, and it only ever grows — nothing in it is revoked, recomputed or expired,
 * whatever becomes of the money or of the points afterwards.
 *
 * <p><strong>The reading and the points are the recorded figures and not to-day's.</strong> They are
 * what was true at the moment the rung was cleared, read back off the award row rather than worked
 * out again from the challenge as it now stands. That is what lets the bank retune a threshold
 * without rewriting anybody's history, and it is what makes an old badge explain itself: a customer
 * looking at a bronze they won last spring can see the figure that won it beside the figure the
 * challenge asks for now.
 *
 * <p>The title is the exception and is fetched from the definition as it now reads, because it is
 * the name of a thing rather than a term of the payment. A challenge that has been renamed should
 * appear under its name, not under the one it had in March.
 */
public record AnAchievement(Long id, String challengeCode, String challengeTitle, Rung rung,
                            Instant awardedAt, BigDecimal reading, long points) {
}
