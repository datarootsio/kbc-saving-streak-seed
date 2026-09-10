package io.dataroots.savingstreak.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Notifications asked for on behalf of somebody who does not bank here are refused, and told why.
 *
 * <p>Asked before the record is, for the reason the money-movement ledger, the claimed rewards and
 * the gifts all give: an empty list is what a customer nothing has ever been said to has, and
 * answering a made-up identifier with one would tell whoever asked that that customer exists. The two
 * answers mean different things to whoever is reading — "nothing has happened yet" and "there is
 * nobody here by that number" — and this feature has a particular reason to keep them apart, because
 * an empty panel is a normal thing to see and is exactly what the page is built to render.
 *
 * <p>The identifier is quoted back in the reason, so that a page which asked for the wrong customer
 * can see which one it asked for. That is the sentence every per-customer read of this application
 * refuses in, and it is Accounts' sentence rather than one this module writes: four modules wording a
 * customer's absence four ways is four sentences one edit away from disagreeing.
 *
 * <p>Both endpoints, because both are customer-scoped and marking read is the one that writes. A
 * refusal that only guarded the read would leave the write to decide for itself what a customer
 * nobody has heard of means, and the honest answer there is not "nothing to mark" either.
 *
 * <p>On the shared application, because a refused request writes nothing.
 */
class NotificationsAreOnlyReadForACustomerWhoExistsApiTest extends ApiIntegrationTest {

    @Test
    void a_customer_nobody_has_heard_of_cannot_have_their_notifications_read() {
        long nobody = new SeededAccounts(http).anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = http.getForEntity(
                "/api/customers/{id}/notifications", JsonNode.class, nobody);

        assertThat(refused.getStatusCode())
                .as("an identifier for somebody who is not there, which is what a 404 says")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + refused.getBody())
                .isTrue();
        assertThat(refused.getBody().get("detail").asText())
                .as("the identifier is quoted back, so a page that asked for the wrong customer "
                        + "can see which one it asked for")
                .contains(String.valueOf(nobody));
    }

    @Test
    void a_customer_nobody_has_heard_of_cannot_mark_notifications_read() {
        long nobody = new SeededAccounts(http).anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = http.postForEntity(
                "/api/customers/{id}/notifications/read", null, JsonNode.class, nobody);

        assertThat(refused.getStatusCode())
                .as("the write is guarded by the same rule as the read, and refuses in the same "
                        + "shape")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + refused.getBody())
                .isTrue();
        assertThat(refused.getBody().get("detail").asText()).contains(String.valueOf(nobody));
    }

    /**
     * The other half of the same rule, and what makes the refusals above mean anything: a customer
     * this application has heard of is answered with a list, whatever is or is not in it.
     *
     * <p>How many notifications are in it is not asserted, because the shared database is one file
     * for the whole run and what is in anybody's record there is other tests' doing. That a customer
     * nothing has been said to has an <em>empty</em> list is said where it can honestly be said, on
     * an application nothing has ever been said in:
     * {@link ACustomerReadsTheirNotificationsNewestFirstApiTest}.
     */
    @Test
    void a_customer_this_application_has_heard_of_is_answered_with_a_list() {
        SeededAccounts seeded = new SeededAccounts(http);

        ResponseEntity<JsonNode> read = http.getForEntity("/api/customers/{id}/notifications",
                JsonNode.class, seeded.customerIdOf(SeededAccounts.BRAM));

        assertThat(read.getStatusCode())
                .as("somebody who banks here is answered with their notifications and not with a "
                        + "refusal")
                .isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().isArray())
                .as("a list, and this one answered " + read.getBody())
                .isTrue();
    }
}
