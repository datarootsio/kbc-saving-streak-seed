package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;

/**
 * One rung of one challenge: which step it is, what the reading has to clear to reach it, and what
 * it pays for reaching it.
 *
 * <p>The threshold is a plain number rather than an amount of money, because only some kinds measure
 * in euros. On a {@link ChallengeKind#NEW_SAVINGS} challenge it is euros; on the kinds the spec has
 * waiting behind it, it is a count of weeks, of days or of goals. One type for all of them is what
 * keeps the comparison in {@link TheNextRungUp} the one comparison rather than five, and what a
 * threshold is counted in is the kind's business.
 *
 * <p>The points are whole points, and they are the same points a deposit earns: the paying of them
 * is a later slice, but what a rung is worth has to be on the card from the first one, because a
 * customer judging whether a challenge is worth their while is judging exactly this figure.
 *
 * <p>A statement about a challenge rather than a row of its own. The three rungs are columns on the
 * definition, because every challenge has exactly three and a table of at-most-three-rows-per-parent
 * would be a join that can express shapes this application would then have to refuse.
 */
public record ChallengeRung(Rung rung, BigDecimal threshold, long points) {
}
