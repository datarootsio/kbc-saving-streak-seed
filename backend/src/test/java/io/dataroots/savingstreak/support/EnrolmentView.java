package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An enrolment as the API reports it: which challenge, where it stands, the mark it measures from,
 * and when it began and ended.
 *
 * <p>{@code measuringFrom} is the anti-farming rule made visible. It is the most the customer had
 * ever saved at the moment they enrolled, and everything the challenge reads afterwards is measured
 * above it — so a test can assert on the mark itself rather than only on the reading it produces.
 */
public record EnrolmentView(Long id, String challenge, String state, BigDecimal measuringFrom,
                            Instant enrolledAt, Instant endedAt) {
}
