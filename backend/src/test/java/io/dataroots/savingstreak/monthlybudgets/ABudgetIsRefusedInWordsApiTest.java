package io.dataroots.savingstreak.monthlybudgets;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every budget this application will not keep is refused in a sentence the person who typed it can
 * act on, and the status says what sort of mistake it was.
 *
 * <p>User story 44. A refusal whose only content is a status code is a refusal a customer cannot
 * fix, and the one thing a budget screen can do for somebody who typed a comma is show them the
 * comma. Each assertion here reads the {@code detail} of the RFC 9457 body, because that is the
 * field the frontend prints unchanged.
 *
 * <p><strong>Nothing is written by any of these.</strong> The figure in force is read back after the
 * refusals that could plausibly have half-applied, because a refused declaration that had already
 * superseded the standing row would leave a category with no budget and no way to tell why.
 *
 * <p>The statuses are the reading, and they are asserted deliberately: a category that is not on the
 * account is a 404 because it is about something that is not there; a category that has ended is a
 * 409 because the request was perfectly well formed and it is the state of the category that will
 * not allow it; a figure that is not a figure is a 400 because what the customer does next is type a
 * different one; and a category with nothing to stop is a 404 for the same reason the first one is.
 */
class ABudgetIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private AnAccountWithBudgets account;

    private SpendingCategoryView groceries;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithBudgets(http, "refused-in-words");
        groceries = account.declares("Groceries");
    }

    @Test
    void a_budget_on_an_account_nobody_has_heard_of_is_refused_before_any_rule_about_budgets() {
        long noSuchAccount = account.anIdNoCurrentAccountHas();

        ResponseEntity<JsonNode> refused = account.tryToBudget(noSuchAccount,
                groceries.categoryId(), "250.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused))
                .as("in the words Accounts owns, so that an absent account reads the same way "
                        + "whichever module was asked")
                .contains("current account", String.valueOf(noSuchAccount));
    }

    @Test
    void a_budget_on_a_category_that_is_not_on_this_account_is_refused_naming_the_category() {
        long noSuchCategory = account.anIdNoCategoryHas();

        ResponseEntity<JsonNode> refused = account.tryToBudget(account.id(), noSuchCategory,
                "250.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused))
                .as("the category's own refusal, in the sentence it already uses everywhere a "
                        + "category is named — including for one that belongs to somebody else")
                .contains("category", String.valueOf(noSuchCategory));
    }

    @Test
    void a_budget_on_a_category_from_another_account_is_refused_as_one_that_is_not_there() {
        AnAccountWithBudgets somebodyElse = new AnAccountWithBudgets(http, "not-yours");
        SpendingCategoryView theirs = somebodyElse.declares("Their groceries");

        ResponseEntity<JsonNode> refused = account.tryToBudget(account.id(), theirs.categoryId(),
                "250.00");

        assertThat(refused.getStatusCode())
                .as("telling a customer that a category exists but belongs to another account "
                        + "would be telling them about another account")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(somebodyElse.thisMonth().theCategory(theirs.categoryId()).budgeted())
                .as("and nothing was written on it")
                .isNull();
    }

    @Test
    void a_budget_on_a_category_that_has_ended_is_refused_as_a_conflict() {
        account.budgets(groceries.categoryId(), "250.00");
        account.ends(groceries.categoryId());

        ResponseEntity<JsonNode> refused = account.tryToBudget(account.id(),
                groceries.categoryId(), "300.00");

        assertThat(refused.getStatusCode())
                .as("the request was perfectly well formed and it is the state of the category "
                        + "that will not allow it, so a page telling the customer to fix what they "
                        + "typed would send them to look for a mistake they did not make")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused)).contains("Groceries", "ended");
        assertThat(account.thisMonth().theCategory(groceries.categoryId()).budgeted())
                .as("and the figure that governed the month it was ended in is exactly as it was")
                .isEqualByComparingTo("250.00");
    }

    @Test
    void a_budget_of_nothing_is_refused_rather_than_quietly_accepted() {
        account.budgets(groceries.categoryId(), "250.00");

        ResponseEntity<JsonNode> refused = account.tryToBudget(account.id(),
                groceries.categoryId(), "0.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a category allowed to cost nothing is a category its holder has stopped "
                        + "budgeting, and this application already has a way of saying that")
                .containsIgnoringCase("budget");
        assertThat(account.thisMonth().theCategory(groceries.categoryId()).budgeted())
                .as("nothing was written, so the figure in force is the one the customer left")
                .isEqualByComparingTo("250.00");
    }

    @Test
    void a_budget_quoted_more_finely_than_to_the_cent_is_refused_rather_than_rounded() {
        ResponseEntity<JsonNode> refused = account.tryToBudget(account.id(),
                groceries.categoryId(), "250.005");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("in the words AmountOfMoney owns, so that a third of a cent is refused here "
                        + "exactly as it would be for a deposit or a spend")
                .containsIgnoringCase("two decimal places");
    }

    @Test
    void a_figure_that_is_not_a_number_is_named_back_so_the_mistake_is_visible() {
        ResponseEntity<JsonNode> refused = account.tryToBudget(account.id(),
                groceries.categoryId(), "250,00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a person who typed a comma has to see the comma to see the mistake")
                .contains("250,00");
    }

    @Test
    void a_rollover_rule_this_application_has_never_heard_of_is_named_back_with_the_three_there_are() {
        ResponseEntity<JsonNode> refused = account.tryToBudget(account.id(),
                groceries.categoryId(), "250.00", "CARRY_HALF_OF_IT");

        assertThat(refused.getStatusCode())
                .as("a word the application does not know is a fact about the request, and what "
                        + "the customer does next is send a different one")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("named back, the way a comma in a figure is, and with the three rules there "
                        + "actually are — a refusal that only said no would leave a page guessing")
                .contains("CARRY_HALF_OF_IT", "NOTHING_ROLLS_OVER", "THE_SURPLUS_ROLLS_OVER",
                        "THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER");
        assertThat(account.thisMonth().theCategory(groceries.categoryId()).budgeted())
                .as("and nothing was written: a refused declaration that had already superseded "
                        + "the standing row would leave a category with no figure and no way to "
                        + "tell why")
                .isNull();
    }

    @Test
    void a_budget_with_no_figure_in_it_is_asked_for_one() {
        ResponseEntity<JsonNode> refused = account.tryToBudget(account.id(),
                groceries.categoryId(), null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("\"you did not say what it is allowed to cost\" is a different sentence from "
                        + "\"a budget of nothing is not a budget\"")
                .containsIgnoringCase("allowed to cost");
    }

    @Test
    void a_request_with_no_body_at_all_is_answered_in_this_applications_own_words() {
        ResponseEntity<JsonNode> refused = account.tryToBudgetNothingAtAll(groceries.categoryId());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a body that never arrived is a fact about the request, answered here rather "
                        + "than in Spring's words")
                .containsIgnoringCase("allowed to cost");
    }

    @Test
    void stopping_a_budget_that_is_not_there_is_refused_rather_than_shrugged_at() {
        ResponseEntity<JsonNode> refused = account.tryToStopBudgeting(account.id(),
                groceries.categoryId());

        assertThat(refused.getStatusCode())
                .as("about something that is not there, which is what a 404 says")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused))
                .as("a customer pressing the button twice has a right to know the second press "
                        + "did nothing")
                .contains("Groceries");
    }

    @Test
    void stopping_a_budget_twice_is_refused_the_second_time() {
        account.budgets(groceries.categoryId(), "250.00");
        account.stopsBudgeting(groceries.categoryId());

        ResponseEntity<JsonNode> refused = account.tryToStopBudgeting(account.id(),
                groceries.categoryId());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused)).contains("Groceries");
    }

    @Test
    void a_month_that_is_not_a_month_is_named_back_rather_than_answered_by_the_framework() {
        ResponseEntity<JsonNode> refused = account.tryToReadTheMonthOf(account.id(), "March");

        assertThat(refused.getStatusCode())
                .as("the address exists and it is the month in it that is wrong, so a bad request "
                        + "rather than a missing page")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("this application's own sentence, naming what was typed")
                .contains("March", "2026-03");
    }

    @Test
    void a_month_read_for_an_account_nobody_has_heard_of_is_refused_rather_than_answered_empty() {
        long noSuchAccount = account.anIdNoCurrentAccountHas();

        assertThat(account.tryToReadTheMonthOf(noSuchAccount, null).getStatusCode())
                .as("an empty month would tell somebody that an account they do not hold simply "
                        + "has nothing on it")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(account.tryToReadTheMonthOf(noSuchAccount, "2026-03").getStatusCode())
                .as("and the month already gone answers the same way, because the account is "
                        + "vouched for before the month is read")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** The sentence the customer is shown, which is the field the frontend prints unchanged. */
    private static String detailOf(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody())
                .describedAs("a refusal carries a body, because the whole point of one is that a "
                        + "person reads it")
                .isNotNull();
        return refused.getBody().get("detail").asText();
    }
}
