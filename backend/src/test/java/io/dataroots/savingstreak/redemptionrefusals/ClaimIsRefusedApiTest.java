package io.dataroots.savingstreak.redemptionrefusals;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A claim the application cannot honour is refused with a reason, and nothing is handed over or
 * taken away.
 *
 * <p>All three are asserted every time, by {@link #assertRefused} and {@link #assertNothingSpent}.
 * There is no reversal path for a claim, so a refusal that spent the points anyway or issued the
 * voucher anyway is not something a later correction could tidy up.
 *
 * <p>Bram is the customer claiming: his one savings account has never been paid into, so he has no
 * points at all, which is exactly the state a refusal for want of points needs. No test here deposits
 * for him, because two other tests assert that nothing ever has.
 */
class ClaimIsRefusedApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * The refusal the whole feature turns on. Somebody who has saved nothing cannot claim anything,
     * and the reason says what the reward costs and what they have, because the person reading it is
     * deciding whether to go and save the difference.
     */
    @Test
    void a_claim_the_customer_cannot_afford_is_refused_and_spends_nothing() {
        Held before = whatIsHeldBy(BRAM);

        ResponseEntity<JsonNode> response = claim(BRAM, "CINEMA_TICKET");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("100");
        assertNothingSpent(BRAM, before);
    }

    /** The cheapest reward in the catalogue is still more than nothing. */
    @Test
    void even_the_cheapest_reward_is_refused_to_a_customer_with_no_points() {
        Held before = whatIsHeldBy(BRAM);

        ResponseEntity<JsonNode> response = claim(BRAM, "CHARITY_DONATION");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingSpent(BRAM, before);
    }

    /**
     * Claiming as somebody this application has never heard of is a mistake about who, not about what
     * they can afford, and it is reported as one.
     *
     * <p>The one refusal here that does not go on to assert nothing was spent, because there is
     * nothing to read: both endpoints that would report it answer not-found for this identifier
     * whatever happened, so the refusal itself has to carry the test.
     */
    @Test
    void a_claim_by_a_customer_that_does_not_exist_is_not_found() {
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/api/customers/{id}/redemptions",
                Map.of("reward", "CHARITY_DONATION"),
                JsonNode.class,
                seeded.anIdNoCustomerHas());

        assertRefused(response, HttpStatus.NOT_FOUND);
    }

    /**
     * A reward the catalogue has never heard of, named back so that whoever sent it can see which
     * one it was. A page that has been open since before a release is the way this happens.
     */
    @Test
    void a_claim_for_something_not_in_the_catalogue_is_refused_and_spends_nothing() {
        Held before = whatIsHeldBy(BRAM);

        ResponseEntity<JsonNode> response = claim(BRAM, "A_PONY");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("A_PONY");
        assertNothingSpent(BRAM, before);
    }

    /**
     * A body that is not a claim at all. Refused rather than allowed to fail somewhere inside: a
     * reward that was never named is not one this application should be reasoning about, and a
     * server error would tell whoever sent it that the fault was here.
     */
    @Test
    void a_claim_naming_no_reward_is_refused_and_spends_nothing() {
        Held before = whatIsHeldBy(BRAM);

        ResponseEntity<JsonNode> response = http.postForEntity(
                "/api/customers/{id}/redemptions", Map.of(), JsonNode.class, seeded.customerIdOf(BRAM));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingSpent(BRAM, before);
    }

    /**
     * Asking what a customer who does not exist has claimed is a mistake, not somebody who has
     * claimed nothing. Answering it with an empty list would tell the caller their identifier was
     * fine.
     */
    @Test
    void the_claims_of_a_customer_that_does_not_exist_are_not_found() {
        // Read as text: a refusal carries an error body rather than a list, and asking for the
        // response as a list would fail to read it before the status could be looked at.
        ResponseEntity<String> response = http.getForEntity(
                "/api/customers/{id}/redemptions", String.class, seeded.anIdNoCustomerHas());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * A refusal, and words to go with it. The words are asserted to be there rather than to say any
     * particular thing, except where a test names the one figure or name the person needs back.
     */
    private void assertRefused(ResponseEntity<JsonNode> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(reasonGivenBy(response)).isNotBlank();
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }

    /** What a refused claim has to leave exactly as it found it: the points, and the claims made. */
    private record Held(long pointsBalance, int claimsMade) {
    }

    private Held whatIsHeldBy(String customerName) {
        ClaimedRewardView[] claimed = http.getForObject("/api/customers/{id}/redemptions",
                ClaimedRewardView[].class, seeded.customerIdOf(customerName));
        return new Held(seeded.pointsBalanceOf(customerName), claimed.length);
    }

    private void assertNothingSpent(String customerName, Held before) {
        Held after = whatIsHeldBy(customerName);
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance());
        assertThat(after.claimsMade()).isEqualTo(before.claimsMade());
    }

    /**
     * Read as unshaped JSON, because a refusal answers with the reason rather than with a claim:
     * asking for the response as a claim would fail to read it before the status could be looked at.
     */
    private ResponseEntity<JsonNode> claim(String customerName, String reward) {
        return http.postForEntity(
                "/api/customers/{id}/redemptions",
                Map.of("reward", reward),
                JsonNode.class,
                seeded.customerIdOf(customerName));
    }
}
