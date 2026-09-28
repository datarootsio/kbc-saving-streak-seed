package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.challenges.AnEnrolment;

/**
 * An enrolment as the customer sees it: which challenge, where it stands, the mark it will measure
 * from, and when it began and ended.
 *
 * <p>The mark travels back with the enrolment rather than being fetched afterwards, because it is
 * what explains every reading that follows: a customer whose progress is nothing the instant after
 * they joined is owed the figure their challenge started counting above, and the moment to give it
 * to them is the moment they joined.
 */
record EnrolmentResponse(Long id, String challenge, String state, BigDecimal measuringFrom,
                         Instant enrolledAt, Instant endedAt) {

    static EnrolmentResponse of(AnEnrolment enrolment) {
        return new EnrolmentResponse(
                enrolment.id(),
                enrolment.challengeCode(),
                enrolment.state().name(),
                enrolment.measuringFrom(),
                enrolment.enrolledAt(),
                enrolment.endedAt());
    }
}
