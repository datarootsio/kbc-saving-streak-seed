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
 * Pausing or resuming a rule that has been ended is refused, in a sentence saying why.
 *
 * <p><strong>This is the one place the two buttons are not idempotent, and the difference is the
 * point.</strong> Pausing a paused rule and resuming a live one are accepted quietly, because the
 * customer is asking for a state the rule is already in. An ended rule is not a state anybody can
 * ask for: it is a record of an instruction that stopped existing, and pausing a record is not a
 * thing. The honest way back is to leave a new rule standing, and the sentence says so.
 *
 * <p>A conflict rather than a bad request, the same distinction an abandoned goal is drawn at: the
 * request was perfectly well formed, and it is the state of the rule that will not allow it, so a
 * page telling the customer to correct what they typed would send them looking for a mistake they
 * did not make.
 *
 * <p>A rule that is not on the account at all is the other half of the pair, and answers as a rule
 * that is not there rather than as one that is somebody else's.
 */
class PausingOrResumingAnEndedRuleIsRefusedApiTest extends ApiIntegrationTest {

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountWithARuleToEndAndThenArgueAbout() {
        account = new AnAccountWithRules(http, "pausing-an-ended-rule");
    }

    @Test
    void pausing_an_ended_rule_is_refused_as_a_conflict_saying_the_rule_is_over() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Over and done with", "MONDAY", "20.00"));
        account.end(rule.id());

        ResponseEntity<JsonNode> refused = account.tryToPause(rule.id());

        assertThat(refused.getStatusCode())
                .as("the request was well formed and it is the rule's state that will not allow "
                        + "it, which is a conflict rather than a form to correct")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("and it says which rule, what it cannot be, and what to do instead")
                .isEqualTo("\"Over and done with\" was ended and cannot be paused. Leave a new rule "
                        + "standing if you want to save this way again.");
        assertThat(account.endedRules())
                .singleElement()
                .satisfies(kept -> assertThat(kept.state())
                        .as("and the refusal left it exactly as it was: an ended rule that a "
                                + "refused pause had quietly moved to PAUSED would be a rule back "
                                + "from the dead with a gap in the middle of its record")
                        .isEqualTo("ENDED"));
    }

    @Test
    void resuming_an_ended_rule_is_refused_in_the_same_words() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryMonth("Finished with this", "15", "75.00"));
        account.end(rule.id());

        ResponseEntity<JsonNode> refused = account.tryToResume(rule.id());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .isEqualTo("\"Finished with this\" was ended and cannot be resumed. Leave a new "
                        + "rule standing if you want to save this way again.");
        assertThat(account.endedRules())
                .singleElement()
                .satisfies(kept -> assertThat(kept.state()).isEqualTo("ENDED"));
    }

    @Test
    void a_rule_that_was_paused_and_then_ended_is_refused_a_resume_just_the_same() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Stopped, then stopped for good", "FRIDAY", "60.00"));
        account.pause(rule.id());
        SavingRuleView ended = account.end(rule.id());

        assertThat(ended.state())
                .as("a paused rule can be ended: its holder stopped it for a while and then decided "
                        + "they were done with it, which is a thing somebody actually does")
                .isEqualTo("ENDED");

        ResponseEntity<JsonNode> refused = account.tryToResume(rule.id());

        assertThat(refused.getStatusCode())
                .as("and once it is ended there is no resuming it, however it got there")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .contains("was ended and cannot be resumed");
    }

    @Test
    void pausing_a_rule_that_is_not_on_this_account_says_there_is_no_such_rule() {
        long noSuchRule = account.anIdNoRuleOnThisAccountHas();

        ResponseEntity<JsonNode> refused = account.tryToPause(noSuchRule);

        assertThat(refused.getStatusCode())
                .as("a rule that is not there is a thing somebody named that is not there")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(refused))
                .isEqualTo("There is no saving rule " + noSuchRule + " on savings account "
                        + account.id() + ".");
    }
}
