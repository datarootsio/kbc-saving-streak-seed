package io.dataroots.savingstreak.automation;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.automation.AnAccountWithRules.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A split this application will not spread money by is refused with a reason somebody can act on,
 * and no rule is left standing.
 *
 * <p>User story 9's "shares that do not add to a hundred", and the one refusal this feature has that
 * is about a goal rather than about a rule.
 *
 * <p><strong>Every refusal here is at the moment the rule is written, and never at the moment it
 * fires.</strong> That division is the feature's own argument: before anything has moved, a split
 * naming a goal that is not being saved towards is a mistake somebody can fix; by the time it fires,
 * the money is in the savings account and refusing would mean rolling back a deposit. What happens to
 * a goal abandoned <em>after</em> the split was written is a different test's subject, and its answer
 * is that the share spills rather than the transfer failing.
 *
 * <p>Each refusal is asserted three times over — the status, words in {@code detail} naming what was
 * objected to, and that no rule was left standing — for the reason {@code ARuleIsRefusedInWordsApiTest}
 * gives: a refusal that came back as a bare status leaves the person who caused it guessing.
 *
 * <p>The shares-that-do-not-add-up sentence is read for the figure they <em>do</em> add up to,
 * because that is the whole of what the customer has to be told: somebody looking at three boxes
 * reading 60, 30 and 5 cannot see the 95, and a refusal that only said "they must add to a hundred"
 * would send them back to add the boxes up themselves.
 */
class ASplitIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private AnAccountWithRules account;
    private GoalView house;
    private GoalView car;

    @BeforeEach
    void anAccountWithGoalsToSplitAcross() {
        account = new AnAccountWithRules(http, "split-refused");
        house = account.opensAGoal("House", "5000.00");
        car = account.opensAGoal("Car", "5000.00");
    }

    @Test
    void shares_that_do_not_add_up_to_a_hundred_are_refused_saying_what_they_do_add_up_to() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(aRuleSplit(
                RulesAsSomebodyWouldTypeThem.aShareFor(house.id(), "60"),
                RulesAsSomebodyWouldTypeThem.aShareFor(car.id(), "35")));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .as("the sentence says what they add up to, which is the figure the customer "
                        + "cannot see by looking at their own boxes")
                .contains("add up to 95");
    }

    @Test
    void shares_that_add_up_to_more_than_a_hundred_are_refused_the_same_way() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(aRuleSplit(
                RulesAsSomebodyWouldTypeThem.aShareFor(house.id(), "60"),
                RulesAsSomebodyWouldTypeThem.aShareFor(car.id(), "60")));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("add up to 120");
    }

    @Test
    void a_split_naming_a_goal_that_is_not_on_this_account_is_refused_as_no_such_goal() {
        long noSuchGoal = Math.max(house.id(), car.id()) + 1;

        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(aRuleSplit(
                RulesAsSomebodyWouldTypeThem.aShareFor(house.id(), "60"),
                RulesAsSomebodyWouldTypeThem.aShareFor(noSuchGoal, "40")));

        assertRefused(response, HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(response))
                .as("the goal that is not there is named back, so the customer knows which line of "
                        + "their split to look at")
                .contains(String.valueOf(noSuchGoal), "no goal");
    }

    @Test
    void a_split_naming_a_goal_its_holder_has_given_up_on_is_refused_as_no_such_goal() {
        account.abandons(car.id());

        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(aRuleSplit(
                RulesAsSomebodyWouldTypeThem.aShareFor(house.id(), "60"),
                RulesAsSomebodyWouldTypeThem.aShareFor(car.id(), "40")));

        assertRefused(response, HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(response))
                .as("a goal that was given up on is not being saved towards, so a rule cannot be "
                        + "left standing to put money into it")
                .contains(String.valueOf(car.id()));
    }

    @Test
    void a_share_of_nothing_is_refused_rather_than_kept_as_a_goal_that_gets_nothing() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(aRuleSplit(
                RulesAsSomebodyWouldTypeThem.aShareFor(house.id(), "100"),
                RulesAsSomebodyWouldTypeThem.aShareFor(car.id(), "0")));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .as("a goal that is named, offered nothing and takes nothing is a line that says "
                        + "nothing; leaving it out is how a customer gives it nothing")
                .contains("whole percentage");
    }

    @Test
    void one_goal_named_twice_in_a_split_is_refused() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(aRuleSplit(
                RulesAsSomebodyWouldTypeThem.aShareFor(house.id(), "60"),
                RulesAsSomebodyWouldTypeThem.aShareFor(house.id(), "40")));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains(String.valueOf(house.id()), "twice");
    }

    @Test
    void a_share_that_is_not_a_number_at_all_is_refused_about_the_share() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(aRuleSplit(
                RulesAsSomebodyWouldTypeThem.aShareFor(house.id(), "sixty"),
                RulesAsSomebodyWouldTypeThem.aShareFor(car.id(), "40")));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .as("named back to whoever sent it, because a person who typed a word has to see "
                        + "the word to see the mistake")
                .contains("sixty");
    }

    @SafeVarargs
    private Map<String, Object> aRuleSplit(Map<String, Object>... shares) {
        return RulesAsSomebodyWouldTypeThem.spreadAcross(
                account.aFixedAmountEveryWeek("Spread it", "MONDAY", "50.00"),
                RulesAsSomebodyWouldTypeThem.inTurn(shares));
    }

    /**
     * Every refusal leaves the account exactly as it was. Asserted on every one of them rather than
     * on a chosen few, because a rule left standing with half a split written is the failure nothing
     * on the page would show.
     */
    private void assertRefused(ResponseEntity<JsonNode> response, HttpStatus status) {
        assertThat(response.getStatusCode())
                .describedAs("the refusal's status: " + response.getBody())
                .isEqualTo(status);
        List<SavingRuleView> standing = account.rules();
        assertThat(standing)
                .describedAs("a refused rule is not left standing")
                .isEmpty();
    }
}
