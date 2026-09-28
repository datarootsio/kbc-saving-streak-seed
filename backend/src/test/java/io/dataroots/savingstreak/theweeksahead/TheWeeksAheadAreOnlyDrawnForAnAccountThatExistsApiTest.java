package io.dataroots.savingstreak.theweeksahead;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Six weeks asked for on a current account nobody has heard of is refused, and told why.
 *
 * <p>Asked of Accounts before Budgets is, for the reason the month's spending beside it gives: six
 * weeks with nothing in them is what an account with no income, no bills and no budgets has, and
 * answering a made-up identifier with that would tell whoever asked that the account exists. Budgets
 * cannot draw the distinction itself — both are an empty forecast — so the question is put to the
 * module that owns it, by the one caller that has already been told which account was asked for.
 *
 * <p>Covers user story 44 at the one place this read can refuse anything at all.
 *
 * <p>On the shared application, because nothing here writes anything and nothing here moves a clock.
 */
class TheWeeksAheadAreOnlyDrawnForAnAccountThatExistsApiTest extends ApiIntegrationTest {

    @Test
    void an_account_nobody_has_heard_of_is_refused_with_the_reason() {
        long noSuchAccount = new SeededAccounts(http).anIdNoCurrentAccountHas();

        ResponseEntity<JsonNode> refused = http.getForEntity(
                "/api/current-accounts/{id}/weeks-ahead", JsonNode.class, noSuchAccount);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered "
                        + refused.getBody())
                .isTrue();
        assertThat(refused.getBody().get("detail").asText())
                .as("and names the account that was asked for, in the words Accounts owns, so that "
                        + "four modules saying the same sentence are not four wordings one edit "
                        + "away from disagreeing")
                .contains(String.valueOf(noSuchAccount));
    }
}
