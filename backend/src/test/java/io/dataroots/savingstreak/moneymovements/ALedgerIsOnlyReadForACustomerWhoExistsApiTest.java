package io.dataroots.savingstreak.moneymovements;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A ledger asked for on behalf of somebody who does not bank here is refused, and told why.
 *
 * <p>Asked before the movements are, for the reason the claimed-rewards listing gives: an empty
 * ledger is what somebody who has never moved anything has, and answering a made-up identifier with
 * one would tell whoever asked that the customer exists.
 *
 * <p>On the shared application, because nothing here writes anything.
 */
class ALedgerIsOnlyReadForACustomerWhoExistsApiTest extends ApiIntegrationTest {

    @Test
    void a_customer_nobody_has_heard_of_is_refused_with_the_reason() {
        long nobody = new SeededAccounts(http).anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = http.getForEntity(
                "/api/customers/{id}/money-movements", JsonNode.class, nobody);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + refused.getBody())
                .isTrue();
        assertThat(refused.getBody().get("detail").asText()).contains(String.valueOf(nobody));
    }
}
