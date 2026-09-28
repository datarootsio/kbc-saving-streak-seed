package io.dataroots.savingstreak.automation;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.automation.AnAccountWithRules.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dry run refuses the same things a saved rule would, in the same sentences.
 *
 * <p>A dry run that answered happily for a rule the save would then turn down would be a preview of
 * a rule that cannot exist — which is worse than no preview, because it is the answer somebody acts
 * on before filling the form in.
 *
 * <p><strong>Every case is asserted against the save's own answer rather than against a sentence
 * written out here.</strong> That is the whole point: a string copied into this test would go on
 * passing after somebody reworded one of the two endpoints, which is exactly the drift the criterion
 * is about. So each bad rule is sent to both, and the status and the {@code detail} have to match.
 *
 * <p>The status is compared as well as the words, because the two refusals are not all one status:
 * a split naming a goal that is not on the account is a 404 and the rest are 400, and a dry run that
 * answered 400 for the first of them would be telling a page to draw the wrong kind of message.
 *
 * <p>Every rule here is one the save genuinely refuses, so nothing this test sends to the save is
 * ever written — which is why it can share the application the rest of the run uses. The limit on
 * how many rules one customer may leave standing is the one case that needs rules written first, and
 * it has an account of its own for that reason.
 */
class TheDryRunRefusesWhatASaveWouldInTheSameSentencesApiTest extends ApiIntegrationTest {

    /** As many rules as one customer may leave standing, which the eleventh is then refused for. */
    private static final int THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING = 10;

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountToBeRefusedOn() {
        account = new AnAccountWithRules(http, "a dry run is refused as a save is");
    }

    @Test
    void a_rule_the_save_would_refuse_is_refused_by_the_dry_run_in_the_same_words() {
        GoalView bike = account.opensAGoal("Bike", "1000.00");

        for (Map<String, Object> notARule : everyRuleThisApplicationWillNotKeep(bike)) {
            bothRefuseItIdentically(notARule);
        }
    }

    /**
     * The eleventh rule, which is refused for being an eleventh rule rather than for anything it
     * says — and is therefore the one refusal a customer could only find out about by trying to save.
     * Being told at the preview is the point of asking.
     */
    @Test
    void an_eleventh_rule_is_refused_by_the_dry_run_exactly_as_the_save_refuses_it() {
        AnAccountWithRules full = new AnAccountWithRules(http, "a dry run is refused for an eleventh");
        for (int i = 1; i <= THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING; i++) {
            full.leaveStanding(full.aFixedAmountEveryWeek("Rule " + i, "MONDAY", "10.00"));
        }
        Map<String, Object> anEleventh = full.aFixedAmountEveryWeek("One too many", "MONDAY", "10.00");

        ResponseEntity<JsonNode> dryRun = full.tryADryRun(anEleventh);
        ResponseEntity<JsonNode> save = full.tryToLeaveStanding(anEleventh);

        assertThat(dryRun.getStatusCode())
                .as("the dry run of an eleventh rule is turned down exactly as saving it is: " + save)
                .isEqualTo(save.getStatusCode());
        assertThat(dryRun.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(dryRun))
                .as("and in the same sentence, which names the figure the customer has to act on")
                .isEqualTo(reasonGivenBy(save))
                .contains(String.valueOf(THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING));
        assertThat(full.rules())
                .as("and neither request left anything behind")
                .hasSize(THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING);
    }

    private void bothRefuseItIdentically(Map<String, Object> notARule) {
        ResponseEntity<JsonNode> dryRun = account.tryADryRun(notARule);
        ResponseEntity<JsonNode> save = account.tryToLeaveStanding(notARule);

        assertThat(save.getStatusCode())
                .as("this test is only worth anything for rules the save really does refuse, and "
                        + notARule + " has to be one of them")
                .isNotEqualTo(HttpStatus.CREATED);
        assertThat(dryRun.getStatusCode())
                .as("the dry run of " + notARule + " answers with the status the save answers with")
                .isEqualTo(save.getStatusCode());
        assertThat(reasonGivenBy(dryRun))
                .as("and with the sentence the save gives, word for word, so that neither can be "
                        + "reworded without the other")
                .isEqualTo(reasonGivenBy(save))
                .isNotBlank();
    }

    /**
     * One of each objection this module makes to a rule: a missing name, a missing trigger, the day
     * a trigger needs left out, the figure a kind of amount needs left out, a figure that is not an
     * amount of money, a day of the month no month has, a split that does not add to a hundred, a
     * split naming a goal that is not on the account, and a current account somebody else holds.
     */
    private List<Map<String, Object>> everyRuleThisApplicationWillNotKeep(GoalView bike) {
        return List.of(
                withoutTheField(account.aFixedAmountEveryWeek("x", "MONDAY", "50.00"), "name"),
                withoutTheField(account.aFixedAmountEveryWeek("No trigger", "MONDAY", "50.00"),
                        "trigger"),
                withoutTheField(account.aFixedAmountEveryWeek("No day", "MONDAY", "50.00"),
                        "dayOfWeek"),
                withoutTheField(account.aFixedAmountEveryWeek("No figure", "MONDAY", "50.00"),
                        "amount"),
                account.aFixedAmountEveryWeek("Nothing at all", "MONDAY", "0.00"),
                account.aFixedAmountEveryWeek("Sub-cent", "MONDAY", "50.001"),
                account.aFixedAmountEveryMonth("No such day", "32", "50.00"),
                account.everythingAboveAFloorEveryWeek("Below nothing", "MONDAY", "-1.00"),
                RulesAsSomebodyWouldTypeThem.spreadAcross(
                        account.aFixedAmountEveryWeek("Ninety per cent", "MONDAY", "50.00"),
                        RulesAsSomebodyWouldTypeThem.inTurn(
                                RulesAsSomebodyWouldTypeThem.aShareFor(bike.id(), "90"))),
                RulesAsSomebodyWouldTypeThem.spreadAcross(
                        account.aFixedAmountEveryWeek("No such goal", "MONDAY", "50.00"),
                        RulesAsSomebodyWouldTypeThem.inTurn(
                                RulesAsSomebodyWouldTypeThem.aShareFor(
                                        bike.id() + 100_000, "100"))),
                somebodyElsesCurrentAccount());
    }

    /**
     * The same rule drawn from a current account this customer does not hold. The identifier is one
     * past every account there is, which is the case the pairing answers {@code NO_SUCH_CURRENT_ACCOUNT}
     * for — a sentence Accounts owns and both endpoints have to borrow identically.
     */
    private Map<String, Object> somebodyElsesCurrentAccount() {
        Map<String, Object> rule = account.aFixedAmountEveryWeek("Not my account", "MONDAY", "50.00");
        rule.put("fromCurrentAccountId", account.currentAccountId() + 100_000);
        return rule;
    }

    private static Map<String, Object> withoutTheField(Map<String, Object> rule, String field) {
        rule.remove(field);
        return rule;
    }
}
