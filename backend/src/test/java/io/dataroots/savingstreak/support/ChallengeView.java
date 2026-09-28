package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * One challenge on a customer's card as the API reports it: what it is, what it asks of them, and
 * where they stand in it.
 *
 * <p>Everything from {@code state} onwards is null for a challenge nobody has enrolled in, which is
 * the claim a test about enrolling being a decision can make directly: a challenge counts nothing
 * until it is joined. The reading is null again once the enrolment is over, because a reading is a
 * statement about a live enrolment and this application stores no progress that could be frozen.
 *
 * <p>{@code season} is null on an evergreen challenge, which belongs to no campaign and is always
 * open, and carries the window on one that belongs to a season. That null is what a test about an
 * evergreen challenge asserts on directly: it is not a season with a very long window, it is no
 * season at all.
 */
public record ChallengeView(String code, String title, String words, String kind, boolean repeatable,
                            List<ChallengeRungView> rungs, boolean enrolled, String state,
                            BigDecimal measuringFrom, BigDecimal reading, String nextRung,
                            BigDecimal stillNeeded, SeasonView season) {
}
