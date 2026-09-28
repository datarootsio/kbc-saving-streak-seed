package io.dataroots.savingstreak.automation;

import java.util.Map;

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
 * A rule's name, what makes it move, the day it moves on, its amount and its floor can each be
 * changed, without deleting it and starting again.
 *
 * <p>Each field is changed on its own, and the assertion that matters in every one of these tests is
 * the second one: that nothing else about the rule moved. A change that quietly reset a day while
 * the customer was editing an amount is the failure this feature can least afford, because nothing
 * on the page would say it had happened.
 *
 * <p>What is judged is the rule <em>as it would then read</em> rather than the fields that arrived,
 * which is why turning a weekly rule into a monthly one without naming a day of the month is refused
 * as the half a sentence it is — and why the rule is left exactly as it was when it is.
 *
 * <p>Saying something the rule contradicts is refused for the same reason saying too little is: a
 * change names its fields on purpose, so a floor sent to a rule that moves a fixed amount is the one
 * thing its sender came to say, and dropping it while answering 200 would report a change that was
 * never made. Sending the kind that uses the figure along with it is how it is meant to be said, and
 * is asserted here beside the refusals so that the rule reads as being about the pairing rather than
 * about the field.
 */
class ARuleIsChangedApiTest extends ApiIntegrationTest {

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountWithARuleToChange() {
        account = new AnAccountWithRules(http, "changed");
    }

    @Test
    void a_rules_name_is_changed_and_nothing_else_about_it_moves() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Hoiday fund", "MONDAY", "50.00"));

        SavingRuleView changed = account.change(rule.id(), Map.of("name", "Holiday fund"));

        assertThat(changed.name()).isEqualTo("Holiday fund");
        assertThat(changed.trigger()).isEqualTo("WEEKLY");
        assertThat(changed.dayOfWeek()).isEqualTo("MONDAY");
        assertThat(changed.amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void a_weekly_rules_day_is_changed() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Every Monday", "MONDAY", "50.00"));

        SavingRuleView changed = account.change(rule.id(), Map.of("dayOfWeek", "FRIDAY"));

        assertThat(changed.dayOfWeek()).isEqualTo("FRIDAY");
        assertThat(changed.dayOfMonth()).isNull();
    }

    @Test
    void a_monthly_rules_day_is_changed() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryMonth("The first", "1", "300.00"));

        SavingRuleView changed = account.change(rule.id(), Map.of("dayOfMonth", "28"));

        assertThat(changed.dayOfMonth()).isEqualTo(28);
    }

    @Test
    void a_rules_amount_is_changed() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Fifty a week", "MONDAY", "50.00"));

        SavingRuleView changed = account.change(rule.id(), Map.of("amount", "75.50"));

        assertThat(changed.amount()).isEqualByComparingTo("75.50");
        assertThat(changed.dayOfWeek()).isEqualTo("MONDAY");
    }

    @Test
    void a_sweeps_floor_is_changed() {
        SavingRuleView rule = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Whatever is left", "800.00"));

        SavingRuleView changed = account.change(rule.id(), Map.of("floor", "950.00"));

        assertThat(changed.floor()).isEqualByComparingTo("950.00");
        assertThat(changed.howMuchMoves()).isEqualTo("EVERYTHING_ABOVE");
        assertThat(changed.amount()).isNull();
    }

    /**
     * Changing what makes a rule move means saying the day the new trigger needs in the same breath,
     * and the old trigger's day is dropped rather than left lying about: a monthly rule carrying the
     * Monday it used to move on is a row nobody could read.
     */
    @Test
    void what_makes_a_rule_move_is_changed_along_with_the_day_the_new_trigger_needs() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Every Monday", "MONDAY", "50.00"));

        SavingRuleView changed =
                account.change(rule.id(), Map.of("trigger", "MONTHLY", "dayOfMonth", "15"));

        assertThat(changed.trigger()).isEqualTo("MONTHLY");
        assertThat(changed.dayOfMonth()).isEqualTo(15);
        assertThat(changed.dayOfWeek()).isNull();
    }

    /**
     * And the figure the new kind has no use for goes with it, for the same reason: a sweep still
     * carrying the fifty euros it used to move would be two answers to "how much moves".
     */
    @Test
    void a_fixed_amount_rule_is_turned_into_a_sweep_and_drops_its_amount() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Fifty a week", "MONDAY", "50.00"));

        SavingRuleView changed = account.change(rule.id(),
                Map.of("howMuchMoves", "EVERYTHING_ABOVE", "floor", "800.00"));

        assertThat(changed.howMuchMoves()).isEqualTo("EVERYTHING_ABOVE");
        assertThat(changed.floor()).isEqualByComparingTo("800.00");
        assertThat(changed.amount()).isNull();
    }

    /**
     * A change that would leave a trigger without its day is refused, and — the half that matters —
     * the rule is left exactly as its holder last said it. A half-applied change is a rule nobody
     * asked for standing against somebody's money.
     */
    @Test
    void a_change_that_would_leave_a_trigger_without_its_day_is_refused_and_nothing_moves() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Every Monday", "MONDAY", "50.00"));

        ResponseEntity<JsonNode> response =
                account.tryToChange(rule.id(), Map.of("trigger", "MONTHLY"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("day of the month");
        assertThat(account.rules()).singleElement().satisfies(unchanged -> {
            assertThat(unchanged.trigger()).isEqualTo("WEEKLY");
            assertThat(unchanged.dayOfWeek()).isEqualTo("MONDAY");
        });
    }

    /** The same the other way round, so that neither direction can half-happen. */
    @Test
    void a_change_that_would_leave_a_kind_of_amount_without_its_figure_is_refused() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Fifty a week", "MONDAY", "50.00"));

        ResponseEntity<JsonNode> response =
                account.tryToChange(rule.id(), Map.of("howMuchMoves", "EVERYTHING_ABOVE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("floor");
        assertThat(account.rules()).singleElement().satisfies(unchanged -> {
            assertThat(unchanged.howMuchMoves()).isEqualTo("A_FIXED_AMOUNT");
            assertThat(unchanged.amount()).isEqualByComparingTo("50.00");
        });
    }

    /**
     * A change naming a figure the rule does not carry is refused rather than swallowed. Dropping it
     * and answering 200 would tell a page the edit went through; the sweep's floor would still be
     * what it was, and nothing on the wire would say so.
     */
    @Test
    void a_change_naming_a_figure_the_rule_has_no_use_for_is_refused_rather_than_ignored() {
        SavingRuleView rule = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Whatever is left", "800.00"));

        ResponseEntity<JsonNode> response =
                account.tryToChange(rule.id(), Map.of("amount", "75.50"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("everything above a floor", "A_FIXED_AMOUNT");
        assertThat(account.rules()).singleElement().satisfies(unchanged -> {
            assertThat(unchanged.floor()).isEqualByComparingTo("800.00");
            assertThat(unchanged.amount()).isNull();
        });
    }

    /** And the same the other way round, so that neither figure can be sent to the wrong kind. */
    @Test
    void a_change_naming_a_floor_on_a_fixed_amount_rule_is_refused() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Fifty a week", "MONDAY", "50.00"));

        ResponseEntity<JsonNode> response =
                account.tryToChange(rule.id(), Map.of("floor", "800.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("fixed amount", "EVERYTHING_ABOVE");
        assertThat(account.rules()).singleElement()
                .extracting(SavingRuleView::amount)
                .satisfies(still -> assertThat(still).isEqualByComparingTo("50.00"));
    }

    /** A day the trigger has no use for is the same objection about the other half of a rule. */
    @Test
    void a_change_naming_a_day_of_the_week_on_a_monthly_rule_is_refused() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryMonth("The fifteenth", "15", "50.00"));

        ResponseEntity<JsonNode> response =
                account.tryToChange(rule.id(), Map.of("dayOfWeek", "FRIDAY"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("every month", "WEEKLY");
        assertThat(account.rules()).singleElement().satisfies(unchanged -> {
            assertThat(unchanged.dayOfMonth()).isEqualTo(15);
            assertThat(unchanged.dayOfWeek()).isNull();
        });
    }

    /**
     * A payday rule is told where its day actually comes from, because that is the one a customer
     * could reasonably think they can set: the answer says to move the declaration instead.
     */
    @Test
    void a_change_naming_a_day_of_the_month_on_a_payday_rule_is_refused_and_says_where_its_day_comes_from() {
        account.declaresAnIncome("25", "3000.00");
        SavingRuleView rule = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Sweep on payday", "800.00"));

        ResponseEntity<JsonNode> response =
                account.tryToChange(rule.id(), Map.of("dayOfMonth", "15"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("payday", "declared", "MONTHLY");
        assertThat(account.rules()).singleElement()
                .extracting(SavingRuleView::dayOfMonth).isEqualTo(25);
    }

    /**
     * Sending both halves at once is the way to say it, and it still works: the refusals above are
     * about a field arriving on its own, not about the field.
     */
    @Test
    void the_same_figure_sent_with_the_kind_that_uses_it_is_accepted() {
        SavingRuleView rule = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Whatever is left", "800.00"));

        SavingRuleView changed = account.change(rule.id(),
                Map.of("howMuchMoves", "A_FIXED_AMOUNT", "amount", "75.50"));

        assertThat(changed.howMuchMoves()).isEqualTo("A_FIXED_AMOUNT");
        assertThat(changed.amount()).isEqualByComparingTo("75.50");
        assertThat(changed.floor()).isNull();
    }

    /** A change that asks for nothing is a sentence with no verb in it, and is told so. */
    @Test
    void a_change_that_says_nothing_at_all_is_refused_rather_than_answered_with_the_rule() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Fifty a week", "MONDAY", "50.00"));

        ResponseEntity<JsonNode> response = account.tryToChange(rule.id(), Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("Say what to change");
        assertThat(account.rules()).singleElement()
                .extracting(SavingRuleView::name).isEqualTo("Fifty a week");
    }

    /** A change read back off the list rather than off what the change itself answered. */
    @Test
    void the_change_is_what_the_account_says_afterwards_and_not_only_what_the_call_answered() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryMonth("Rent leftovers", "1", "100.00"));

        account.change(rule.id(), Map.of("name", "Rainy day", "amount", "125.00"));

        assertThat(account.rules()).singleElement().satisfies(listed -> {
            assertThat(listed.name()).isEqualTo("Rainy day");
            assertThat(listed.amount()).isEqualByComparingTo("125.00");
            assertThat(listed.dayOfMonth()).isEqualTo(1);
        });
    }
}
