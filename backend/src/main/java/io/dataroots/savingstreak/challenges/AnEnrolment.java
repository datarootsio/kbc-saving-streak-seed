package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A customer's enrolment in a challenge, as the rest of the application sees it: which challenge,
 * where it stands, the mark it measures from, and when it began and ended.
 *
 * <p>The stored row stays inside the module; this is a statement about something somebody did. The
 * mark travels with it because it is the thing that explains the reading — a customer whose progress
 * is EUR 40 when they have saved EUR 400 this year is owed the reason, and the reason is the figure
 * their challenge started counting above.
 *
 * <p>There is no progress on it. Progress is derived on every read and belongs to
 * {@link AChallengeAsItStands}, which is the card; this is the commitment underneath the card.
 */
public record AnEnrolment(Long id, String challengeCode, EnrolmentState state,
                          BigDecimal measuringFrom, Instant enrolledAt, Instant endedAt) {
}
