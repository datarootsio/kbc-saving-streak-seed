package io.dataroots.savingstreak.giftingpoints;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A list of gifts asked for on behalf of somebody who does not bank here is refused, and told why.
 *
 * <p>Asked before the gifts are, for the reason the money-movement ledger and the claimed-rewards
 * listing both give: an empty list is what a customer who has never given or received anything has,
 * and answering a made-up identifier with one would tell whoever asked that that customer exists.
 * The two answers have to be told apart, because they mean different things to whoever is reading —
 * "you have not given anybody anything yet" and "there is nobody here by that number".
 *
 * <p>The identifier is quoted back in the reason, so that a page which asked for the wrong customer
 * can see which one it asked for. That is the sentence every per-customer read of this application
 * refuses in.
 *
 * <p>On the shared application, because nothing here writes anything.
 */
class AGiftListIsOnlyReadForACustomerWhoExistsApiTest extends ApiIntegrationTest {

    @Test
    void a_customer_nobody_has_heard_of_is_refused_with_the_reason() {
        long nobody = new SeededAccounts(http).anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = http.getForEntity(
                "/api/customers/{id}/gifts", JsonNode.class, nobody);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + refused.getBody())
                .isTrue();
        assertThat(refused.getBody().get("detail").asText()).contains(String.valueOf(nobody));
    }

    /**
     * The other half of the same rule, and what makes the refusal above mean anything: a customer
     * this application has heard of is answered with a list, whatever is or is not in it.
     *
     * <p>How many gifts are in it is not asserted, because the shared database is one file for the
     * whole run and what is in anybody's list there is other tests' doing. That a customer who has
     * been part of no gifts has an <em>empty</em> list is said where it can honestly be said, on an
     * application nothing has ever been given in:
     * {@link BothPartiesReadTheSameGiftFromTheirOwnEndApiTest}.
     */
    @Test
    void a_customer_this_application_has_heard_of_is_answered_with_a_list() {
        SeededAccounts seeded = new SeededAccounts(http);

        ResponseEntity<JsonNode> read = http.getForEntity("/api/customers/{id}/gifts", JsonNode.class,
                seeded.customerIdOf(SeededAccounts.BRAM));

        assertThat(read.getStatusCode())
                .as("somebody who banks here is answered with their gifts and not with a refusal")
                .isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().isArray())
                .as("a list, and this one answered " + read.getBody())
                .isTrue();
    }
}
