package io.dataroots.savingstreak.redemptionrefusals;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
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
 * <p>Bram's savings account is the one claimed against: it has never been paid into, so it has no
 * points at all, which is exactly the state a refusal for want of points needs. No test here deposits
 * into it, because two other tests assert that nothing ever has.
 */
class ClaimIsRefusedApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * The refusal the whole feature turns on. An account that has saved nothing cannot claim
     * anything, and the reason says what the reward costs and what the account has, because the
     * person reading it is deciding whether to go and save the difference.
     */
    @Test
    void a_claim_an_account_cannot_afford_is_refused_and_spends_nothing() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(savingsAccount);

        ResponseEntity<JsonNode> response = claim(savingsAccount, "CINEMA_TICKET");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("100");
        assertNothingSpent(savingsAccount, before);
    }

    /** The cheapest reward in the catalogue is still more than nothing. */
    @Test
    void even_the_cheapest_reward_is_refused_to_an_account_with_no_points() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(savingsAccount);

        ResponseEntity<JsonNode> response = claim(savingsAccount, "CHARITY_DONATION");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingSpent(savingsAccount, before);
    }

    /**
     * Claiming against an account that does not exist is a mistake about which account, not about
     * what it can afford, and it is reported as one.
     *
     * <p>The one refusal here that does not go on to assert nothing was spent, because there is
     * nothing to read: both endpoints that would report it answer not-found for this identifier
     * whatever happened, so the refusal itself has to carry the test.
     */
    @Test
    void a_claim_against_a_savings_account_that_does_not_exist_is_not_found() {
        ResponseEntity<JsonNode> response = claim(seeded.anIdNoSavingsAccountHas(), "CHARITY_DONATION");

        assertRefused(response, HttpStatus.NOT_FOUND);
    }

    /**
     * A reward the catalogue has never heard of, named back so that whoever sent it can see which
     * one it was. A page that has been open since before a release is the way this happens.
     */
    @Test
    void a_claim_for_something_not_in_the_catalogue_is_refused_and_spends_nothing() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(savingsAccount);

        ResponseEntity<JsonNode> response = claim(savingsAccount, "A_PONY");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("A_PONY");
        assertNothingSpent(savingsAccount, before);
    }

    /**
     * A body that is not a claim at all. Refused rather than allowed to fail somewhere inside: a
     * reward that was never named is not one this application should be reasoning about, and a
     * server error would tell whoever sent it that the fault was here.
     */
    @Test
    void a_claim_naming_no_reward_is_refused_and_spends_nothing() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(savingsAccount);

        ResponseEntity<JsonNode> response = http.postForEntity(
                "/api/savings-accounts/{id}/redemptions", Map.of(), JsonNode.class, savingsAccount);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingSpent(savingsAccount, before);
    }

    /**
     * Asking what an account that does not exist has claimed is a mistake, not an account that has
     * claimed nothing. Answering it with an empty list would tell the caller their identifier was
     * fine.
     */
    @Test
    void the_claims_of_a_savings_account_that_does_not_exist_are_not_found() {
        // Read as text: a refusal carries an error body rather than a list, and asking for the
        // response as a list would fail to read it before the status could be looked at.
        ResponseEntity<String> response = http.getForEntity(
                "/api/savings-accounts/{id}/redemptions", String.class, seeded.anIdNoSavingsAccountHas());

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

    private Held whatIsHeldIn(long savingsAccountId) {
        BalancesView balances =
                http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
        ClaimedRewardView[] claimed = http.getForObject(
                "/api/savings-accounts/{id}/redemptions", ClaimedRewardView[].class, savingsAccountId);
        return new Held(balances.pointsBalance(), claimed.length);
    }

    private void assertNothingSpent(long savingsAccountId, Held before) {
        Held after = whatIsHeldIn(savingsAccountId);
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance());
        assertThat(after.claimsMade()).isEqualTo(before.claimsMade());
    }

    /**
     * Read as unshaped JSON, because a refusal answers with the reason rather than with a claim:
     * asking for the response as a claim would fail to read it before the status could be looked at.
     */
    private ResponseEntity<JsonNode> claim(long savingsAccountId, String reward) {
        return http.postForEntity(
                "/api/savings-accounts/{id}/redemptions",
                Map.of("reward", reward),
                JsonNode.class,
                savingsAccountId);
    }
}
