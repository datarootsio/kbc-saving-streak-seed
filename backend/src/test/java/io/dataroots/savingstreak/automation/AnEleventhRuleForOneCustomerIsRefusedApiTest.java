package io.dataroots.savingstreak.automation;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.automation.AnAccountWithRules.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ten rules stand; the eleventh is refused, and the sentence quotes the limit.
 *
 * <p>The limit keeps the page readable and bounds the catch-up: a wound clock turns every standing
 * weekly rule into thousands of occurrences to work through, and the number of rules is the
 * multiplier on that. A customer who hits it needs to be told the figure rather than left to find it
 * by trying, which is why the refusal names it.
 *
 * <p>Ended rules do not count, and that is asserted rather than assumed: they are not on the page and
 * they fire nothing, so they cost neither of the things the limit protects — and a customer who
 * tidied up would otherwise still be locked out.
 *
 * <p>This class opens a customer of its own for the reason the limit itself gives: the count is per
 * customer, so ten rules left standing on a shared account would refuse the eleventh rule of
 * whichever class ran next.
 */
class AnEleventhRuleForOneCustomerIsRefusedApiTest extends ApiIntegrationTest {

    /** The most this application keeps for one customer, restated here so the test says what it tests. */
    private static final int THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING = 10;

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountToFillUp() {
        account = new AnAccountWithRules(http, "the limit");
    }

    @Test
    void ten_rules_stand_and_the_eleventh_is_refused_quoting_the_limit() {
        for (int rule = 1; rule <= THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING; rule++) {
            account.leaveStanding(account.aFixedAmountEveryWeek("Rule " + rule, "MONDAY", "5.00"));
        }

        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(
                account.aFixedAmountEveryWeek("One too many", "MONDAY", "5.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .contains(String.valueOf(THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING));
        assertThat(account.rules()).hasSize(THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING);
        assertThat(AnAccountWithRules.namesOf(account.rules())).doesNotContain("One too many");
    }

    @Test
    void ending_one_makes_room_for_another() {
        for (int rule = 1; rule <= THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING; rule++) {
            account.leaveStanding(account.aFixedAmountEveryWeek("Rule " + rule, "MONDAY", "5.00"));
        }
        SavingRuleView theOneNoLongerWanted = account.rules().get(0);

        account.end(theOneNoLongerWanted.id());
        SavingRuleView theEleventh =
                account.leaveStanding(account.aFixedAmountEveryWeek("Room at last", "FRIDAY", "5.00"));

        assertThat(theEleventh.name()).isEqualTo("Room at last");
        assertThat(account.rules()).hasSize(THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING);
        assertThat(account.endedRules()).singleElement()
                .extracting(SavingRuleView::id).isEqualTo(theOneNoLongerWanted.id());
    }
}
