package io.dataroots.savingstreak.challenges;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The enrolments this module will not make, each with the status that says what sort of mistake it
 * was and words a person can act on.
 *
 * <p>Two shapes of refusal, and the line between them is the one the rest of this application
 * already draws. A customer or a challenge nobody has heard of is a mistake about <em>what was
 * named</em>, which is a 404. Enrolling in something you are already in, or leaving something you
 * are not in, is a perfectly well-formed request about things that all exist, and it is the state of
 * the enrolment that will not allow it — which is a 409, and not a 400, because there is no typo for
 * the customer to go and find.
 *
 * <p>On the shared application, because no figure here is arithmetic: a refusal is the same refusal
 * whatever anybody else has saved. The two tests that need a state of their own take a customer and
 * a challenge nobody else in this class touches — Bram joins {@code SAVE_TWO_THOUSAND}, nobody ever
 * joins Anke to {@code SAVE_FIVE_HUNDRED} — so neither depends on the order they run in.
 */
class EnrolmentIsRefusedApiTest extends ApiIntegrationTest {

    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";
    private static final String TWO_THOUSAND = "SAVE_TWO_THOUSAND";
    private static final String NOT_A_CHALLENGE = "SAVE_A_PONY";

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * A second enrolment in something already running. Refused rather than quietly answered with the
     * enrolment they already have, because the two are different facts and a list that filled up
     * with duplicates would make a customer's own card unreadable.
     */
    @Test
    void enrolling_twice_in_the_same_challenge_is_a_conflict() {
        assertThat(enrol(BRAM, TWO_THOUSAND).getStatusCode())
                .describedAs("the first enrolment, which this test needs in order to refuse a second")
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> again = enrol(BRAM, TWO_THOUSAND);

        assertRefused(again, HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(again)).contains(TWO_THOUSAND);
    }

    /**
     * Leaving something nobody joined. A conflict rather than a not-found, because both the customer
     * and the challenge are there and it is only the enrolment between them that never was.
     */
    @Test
    void leaving_a_challenge_you_are_not_in_is_a_conflict() {
        ResponseEntity<JsonNode> left = leave(seeded.customerIdOf(ANKE), FIVE_HUNDRED);

        assertRefused(left, HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(left)).contains(FIVE_HUNDRED);
    }

    /**
     * A challenge code this application has never issued, named back so that whoever sent it can see
     * which one it was. A page that has been open since before a season closed is how this happens.
     */
    @Test
    void enrolling_in_a_challenge_that_does_not_exist_is_not_found() {
        ResponseEntity<JsonNode> refused = enrol(BRAM, NOT_A_CHALLENGE);

        assertRefused(refused, HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(refused)).contains(NOT_A_CHALLENGE);
    }

    /** And leaving one, which is the same absence reached through the other verb. */
    @Test
    void leaving_a_challenge_that_does_not_exist_is_not_found() {
        ResponseEntity<JsonNode> refused = leave(seeded.customerIdOf(BRAM), NOT_A_CHALLENGE);

        assertRefused(refused, HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(refused)).contains(NOT_A_CHALLENGE);
    }

    /** Enrolling as somebody this application has never heard of is a mistake about who. */
    @Test
    void enrolling_as_a_customer_that_does_not_exist_is_not_found() {
        assertRefused(enrol(seeded.anIdNoCustomerHas(), FIVE_HUNDRED), HttpStatus.NOT_FOUND);
    }

    /**
     * And reading the challenges of one. Answering with the empty card of somebody who has simply
     * never joined anything would tell the caller their identifier was fine.
     */
    @Test
    void the_challenges_of_a_customer_that_does_not_exist_are_not_found() {
        ResponseEntity<JsonNode> read = http.getForEntity("/api/customers/{id}/challenges",
                JsonNode.class, seeded.anIdNoCustomerHas());

        assertRefused(read, HttpStatus.NOT_FOUND);
    }

    /**
     * A refusal, and words to go with it. The words are asserted to be there rather than to say any
     * particular thing, except where a test names the one thing the person needs back.
     */
    private void assertRefused(ResponseEntity<JsonNode> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(reasonGivenBy(response)).isNotBlank();
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }

    /**
     * Read as unshaped JSON, because a refusal answers with the reason rather than with an
     * enrolment: asking for the response as an enrolment would fail to read it before the status
     * could be looked at.
     */
    private ResponseEntity<JsonNode> enrol(String customerName, String code) {
        return enrol(seeded.customerIdOf(customerName), code);
    }

    private ResponseEntity<JsonNode> enrol(long customerId, String code) {
        return http.postForEntity("/api/customers/{id}/challenges/{code}/enrolments", null,
                JsonNode.class, customerId, code);
    }

    private ResponseEntity<JsonNode> leave(long customerId, String code) {
        return http.exchange("/api/customers/{id}/challenges/{code}/enrolments", HttpMethod.DELETE,
                null, JsonNode.class, customerId, code);
    }
}
