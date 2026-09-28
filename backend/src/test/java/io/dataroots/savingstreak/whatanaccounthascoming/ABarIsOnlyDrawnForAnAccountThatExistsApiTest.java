package io.dataroots.savingstreak.whatanaccounthascoming;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bar asked for on an account nobody has heard of is refused, and told why.
 *
 * <p>Asked before the deposits are, for the reason the deposit history beside it gives: an empty
 * year is what an account that has never been paid into has, and answering a made-up identifier with
 * one would tell whoever asked that the account exists. The Timeline module cannot draw that
 * distinction itself — both are a list of no deposits — so the question is put to Accounts first, by
 * the one caller that has already been told which account was asked for.
 *
 * <p>On the shared application, because nothing here writes anything and nothing here moves a clock.
 */
class ABarIsOnlyDrawnForAnAccountThatExistsApiTest extends ApiIntegrationTest {

    @Test
    void an_account_nobody_has_heard_of_is_refused_with_the_reason() {
        long noSuchAccount = new SeededAccounts(http).anIdNoSavingsAccountHas();

        ResponseEntity<JsonNode> refused = http.getForEntity(
                "/api/savings-accounts/{id}/timeline", JsonNode.class, noSuchAccount);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + refused.getBody())
                .isTrue();
        assertThat(refused.getBody().get("detail").asText())
                .as("and names the account that was asked for, so the mistake is visible")
                .contains(String.valueOf(noSuchAccount));
    }
}
