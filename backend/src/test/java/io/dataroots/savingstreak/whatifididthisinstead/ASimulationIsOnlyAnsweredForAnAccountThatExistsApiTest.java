package io.dataroots.savingstreak.whatifididthisinstead;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A simulation asked for on an account nobody has heard of is refused, and told why — in the words
 * every other read of a savings account is refused in.
 *
 * <p>Asked before anything is gathered, for the reason the timeline and the deposit history beside
 * it give: an empty present is what an account that has never been paid into has, and answering a
 * made-up identifier with one would tell whoever asked that the account exists. The Simulation
 * module cannot draw that distinction itself — both are a balance of nothing and a list of no
 * deposits — so the question is put to Accounts first, by the one caller that has already been told
 * which account was asked for.
 *
 * <p>The sentence is Accounts' own rather than a second one written for this resource. A customer
 * who mistypes an account number should read the same thing whichever screen they mistyped it on.
 *
 * <p>On the shared application, because nothing here writes anything and nothing here moves a clock.
 */
class ASimulationIsOnlyAnsweredForAnAccountThatExistsApiTest extends ApiIntegrationTest {

    @Test
    void an_account_nobody_has_heard_of_is_refused_with_the_reason() {
        long noSuchAccount = new SeededAccounts(http).anIdNoSavingsAccountHas();

        ResponseEntity<JsonNode> refused = http.postForEntity(
                "/api/savings-accounts/{id}/simulations", Map.of("scenarios", List.of()),
                JsonNode.class, noSuchAccount);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + refused.getBody())
                .isTrue();
        assertThat(refused.getBody().get("detail").asText())
                .as("and names the account that was asked for, so the mistake is visible")
                .contains(String.valueOf(noSuchAccount));
    }
}
