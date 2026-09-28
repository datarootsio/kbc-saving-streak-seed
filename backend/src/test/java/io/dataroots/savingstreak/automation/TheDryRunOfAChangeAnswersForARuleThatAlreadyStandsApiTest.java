package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DryRunView;
import io.dataroots.savingstreak.support.GoalShareView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.automation.AnAccountWithRules.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule that already stands has a dry run of its own: what it would move today with a change
 * applied to it, asked the way the change form asks it.
 *
 * <p>The change form on the automatic saving page asks this on every keystroke, and it could not ask
 * the preview for unsaved rules instead. That one refuses a customer who has no room for an eleventh
 * rule, which is the right answer about a rule that would be an eleventh and the wrong answer about
 * one that already exists: a customer holding the ten this application allows would read "You already
 * have 10 saving rules standing" in the preview box while editing a rule they have had for months.
 * That difference is the first test here, and it is asserted against both endpoints in the same test
 * so that the two answers are read side by side rather than taken on trust.
 *
 * <p><strong>A change says only what it is changing.</strong> A change that says nothing about the
 * split is previewed against the split the rule already has, which is what the change itself would
 * leave in place — and that matters most for the one thing no form can show: a rule keeps the line
 * belonging to a goal its holder has since given up on, and a preview that quietly dropped it would
 * be quoting figures for a rule nobody is about to save.
 *
 * <p>Everything else is refused in the sentences the change itself is refused in, because they are
 * the same sentences: a preview that accepted what the save would turn down would be a preview of a
 * change that cannot happen.
 */
class TheDryRunOfAChangeAnswersForARuleThatAlreadyStandsApiTest extends ApiIntegrationTest {

    /** The most this application keeps for one customer, restated here so the test says what it tests. */
    private static final int THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING = 10;

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountWithRulesToChange() {
        account = new AnAccountWithRules(http, "change-dry-run");
    }

    @Test
    void a_customer_with_no_room_for_another_rule_can_still_see_what_a_change_to_one_would_move() {
        for (int rule = 1; rule <= THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING; rule++) {
            account.leaveStanding(account.aFixedAmountEveryWeek("Rule " + rule, "MONDAY", "5.00"));
        }
        SavingRuleView theOneBeingEdited = account.rules().get(0);

        ResponseEntity<JsonNode> asAnEleventh =
                account.tryADryRun(account.aFixedAmountEveryWeek("The same sum", "MONDAY", "9.00"));
        DryRunView asAChange = account.dryRunAChange(theOneBeingEdited.id(),
                Map.of("amount", "9.00"));

        assertThat(asAnEleventh.getStatusCode())
                .as("an unsaved rule really would be an eleventh, so that preview is refused")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(asAnEleventh))
                .contains(String.valueOf(THE_MOST_ONE_CUSTOMER_MAY_LEAVE_STANDING));
        assertThat(asAChange.wouldMove().amount())
                .as("the same figure asked about a rule that already stands is answered, because "
                        + "changing one makes no room and costs the limit nothing")
                .isEqualByComparingTo(new BigDecimal("9.00"));
        assertThat(asAChange.outcome()).isEqualTo("MOVED");
    }

    @Test
    void a_change_that_says_nothing_about_the_split_is_previewed_against_the_split_it_already_has() {
        GoalView holiday = account.opensAGoal("Holiday", "2000.00");
        GoalView bike = account.opensAGoal("New bike", "800.00");
        Map<String, Object> rule = account.aFixedAmountEveryWeek("Sixty a week", "MONDAY", "60.00");
        rule.put("split", RulesAsSomebodyWouldTypeThem.inTurn(
                RulesAsSomebodyWouldTypeThem.aShareFor(holiday.id(), "60"),
                RulesAsSomebodyWouldTypeThem.aShareFor(bike.id(), "40")));
        SavingRuleView standing = account.leaveStanding(rule);

        DryRunView answer = account.dryRunAChange(standing.id(), Map.of("amount", "100.00"));

        assertThat(answer.wouldMove().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(answer.wouldMove().intoGoals())
                .as("the split the rule already has, spread over the new figure — a preview that "
                        + "read a silent split as \"stop spreading it\" would quote a rule nobody "
                        + "is about to save")
                .extracting(GoalShareView::goalId, GoalShareView::share, GoalShareView::amount)
                .containsExactly(
                        Tuple.tuple(holiday.id(), 60, new BigDecimal("60.00")),
                        Tuple.tuple(bike.id(), 40, new BigDecimal("40.00")));
    }

    @Test
    void a_split_a_goal_has_been_given_up_on_since_is_previewed_with_that_goals_line_intact() {
        GoalView holiday = account.opensAGoal("Holiday", "2000.00");
        GoalView bike = account.opensAGoal("New bike", "800.00");
        Map<String, Object> rule = account.aFixedAmountEveryWeek("Sixty a week", "MONDAY", "60.00");
        rule.put("split", RulesAsSomebodyWouldTypeThem.inTurn(
                RulesAsSomebodyWouldTypeThem.aShareFor(holiday.id(), "60"),
                RulesAsSomebodyWouldTypeThem.aShareFor(bike.id(), "40")));
        SavingRuleView standing = account.leaveStanding(rule);
        account.abandons(bike.id());

        DryRunView answer = account.dryRunAChange(standing.id(),
                Map.of("name", "Sixty a week, renamed"));

        assertThat(answer.wouldMove().intoGoals())
                .as("the line belonging to the goal given up on is the one thing a form cannot draw "
                        + "a box for, so it is the one thing a preview built from boxes would lose")
                .extracting(GoalShareView::goalId, GoalShareView::share)
                .containsExactly(Tuple.tuple(holiday.id(), 60), Tuple.tuple(bike.id(), 40));
    }

    @Test
    void a_split_that_is_sent_and_does_not_add_up_is_refused_in_the_sentence_the_change_is_refused_in() {
        GoalView holiday = account.opensAGoal("Holiday", "2000.00");
        SavingRuleView standing = account.leaveStanding(
                account.aFixedAmountEveryWeek("Sixty a week", "MONDAY", "60.00"));
        Map<String, Object> shortOfAHundred = Map.of("split", List.of(
                RulesAsSomebodyWouldTypeThem.aShareFor(holiday.id(), "60")));

        ResponseEntity<JsonNode> previewed =
                account.tryADryRunOfAChange(standing.id(), shortOfAHundred);
        ResponseEntity<JsonNode> saved = account.tryToChange(standing.id(), shortOfAHundred);

        assertThat(previewed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(previewed))
                .as("word for word what the change itself says, so that a customer who previews and "
                        + "then saves is told the same thing twice")
                .isEqualTo(reasonGivenBy(saved))
                .contains("add up to 60");
    }

    @Test
    void a_preview_of_a_change_to_a_rule_that_was_ended_is_refused() {
        SavingRuleView standing = account.leaveStanding(
                account.aFixedAmountEveryWeek("Fifty a week", "MONDAY", "50.00"));
        account.end(standing.id());

        ResponseEntity<JsonNode> response =
                account.tryADryRunOfAChange(standing.id(), Map.of("amount", "9.00"));

        assertThat(response.getStatusCode())
                .as("the same 409 the change itself answers with: an ended rule is over, and that "
                        + "is a state the request conflicts with rather than a badly written one")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(response)).contains("ended");
    }

    @Test
    void a_preview_of_a_change_to_a_rule_that_does_not_exist_is_refused() {
        ResponseEntity<JsonNode> response = account.tryADryRunOfAChange(
                account.anIdNoRuleOnThisAccountHas(), Map.of("amount", "9.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void asking_changes_nothing_about_the_rule() {
        SavingRuleView standing = account.leaveStanding(
                account.aFixedAmountEveryWeek("Fifty a week", "MONDAY", "50.00"));

        account.dryRunAChange(standing.id(), Map.of("name", "Something else", "amount", "9.00"));

        SavingRuleView asItStillReads = account.rules().get(0);
        assertThat(asItStillReads.name())
                .as("a dry run is a question, and a question that renamed the rule would be an "
                        + "answer nobody asked for")
                .isEqualTo("Fifty a week");
        assertThat(asItStillReads.amount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }
}
