package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One thing in a customer's trophy case as the API reports it: which challenge, which rung, when it
 * was won, the reading that won it and the points it paid.
 *
 * <p>{@code reading} and {@code points} are the figures recorded at the moment the rung was cleared,
 * not anything worked out now, which is the claim a test about an award never being recomputed makes
 * directly: the customer keeps saving, the reading on the card climbs, and the figure on the badge
 * stays exactly where it was.
 */
public record AchievementView(Long id, String challenge, String title, String rung,
                              Instant awardedAt, BigDecimal reading, long points) {
}
