package io.dataroots.savingstreak.monthlybudgets;

import java.math.BigDecimal;
import java.time.YearMonth;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.CategorySpendingView;
import io.dataroots.savingstreak.support.MonthOfSpendingView;
import io.dataroots.savingstreak.support.MonthlyBudgetView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer says what one of their categories is allowed to cost each month, changes their mind,
 * and stops budgeting it again — and the figure that is in force is readable at every step.
 *
 * <p>User stories 7, 8 and 10. Until now a category was a word with nothing to be measured against,
 * so "am I spending too much on groceries" was a question the application could restate and not
 * answer. This is the slice that gives the word a figure, and the whole of what makes that figure
 * worth having is that declaring a second one leaves the first readable rather than writing over it.
 *
 * <p>Read back off the month the page draws as well as taken from the answer to the request,
 * because they are two different claims: that the application said yes, and that the figure it said
 * yes to is the one a customer is now being measured against.
 *
 * <p><strong>Nothing here moves money.</strong> The balance is asserted after every declaration,
 * because a budget is a plan for money that will leave rather than money leaving — an application
 * that took the figure would be one nobody could safely change their mind in.
 */
class ABudgetIsDeclaredSupersededAndStoppedApiTest extends ApiIntegrationTest {

    private AnAccountWithBudgets account;

    private SpendingCategoryView groceries;

    private SpendingCategoryView fuel;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithBudgets(http, "declared-superseded-stopped");
        groceries = account.declares("Groceries");
        fuel = account.declares("Fuel");
    }

    @Test
    void a_budget_comes_back_naming_the_category_the_figure_and_the_month_it_takes_effect() {
        MonthlyBudgetView declared = account.budgets(groceries.categoryId(), "250.00");

        assertThat(declared.currentAccountId()).isEqualTo(account.id());
        assertThat(declared.categoryId()).isEqualTo(groceries.categoryId());
        assertThat(declared.categoryName())
                .as("a figure beside an identifier is a figure nobody can act on, so the word "
                        + "travels with it")
                .isEqualTo("Groceries");
        assertThat(declared.amount()).isEqualByComparingTo("250.00");
        assertThat(declared.effectiveFrom())
                .as("a budget takes effect in the month it is declared in, so that a customer who "
                        + "decides on the third is deciding about the month they are in")
                .isEqualTo(YearMonth.now().toString());
        assertThat(declared.stoodThrough())
                .as("a figure in force has no last month, because the customer has not said there "
                        + "is one")
                .isNull();
        assertThat(declared.state()).isEqualTo("STANDING");
        assertThat(declared.rollover())
                .as("a customer who said nothing about rollover has a rule all the same, and it is "
                        + "the one that surprises nobody: this month is this month's and the "
                        + "difference ends with it")
                .isEqualTo("NOTHING_ROLLS_OVER");
        assertThat(declared.declaredAt()).isNotNull();
        assertThat(declared.stoodDownAt()).isNull();
    }

    @Test
    void a_category_with_a_figure_on_it_reports_the_rule_that_figure_carries_under() {
        account.budgets(groceries.categoryId(), "250.00", "THE_SURPLUS_ROLLS_OVER");

        CategorySpendingView row = account.thisMonth().theCategory(groceries.categoryId());
        assertThat(row.rollover())
                .as("the month quotes the rule beside the figure, because a carry nobody can see "
                        + "the rule behind is a carry nobody can check")
                .isEqualTo("THE_SURPLUS_ROLLS_OVER");
        assertThat(row.carriedIn())
                .as("and nothing has arrived in it: this is the first month the figure stands in, "
                        + "so there is no month behind it to have handed anything on")
                .isEqualByComparingTo("0.00");
        assertThat(row.allowed())
                .as("what the month allows is the figure and the carry together, which is the "
                        + "figure the spending is really measured against")
                .isEqualByComparingTo("250.00");
    }

    @Test
    void a_category_nobody_has_budgeted_has_no_rule_and_no_carry_rather_than_a_default_one() {
        account.spends("Delhaize", "40.00", AnAccountWithBudgets.Part.of(fuel.categoryId(),
                "40.00"));

        CategorySpendingView row = account.thisMonth().theCategory(fuel.categoryId());
        assertThat(row.budgeted()).isNull();
        assertThat(row.rollover())
                .as("there is no rule on a limit nobody set, and a page drawing one would be "
                        + "telling somebody what happens to a surplus they cannot have")
                .isNull();
        assertThat(row.carriedIn())
                .as("nothing carries into a month nobody was measuring: absent is a state and not "
                        + "a nought, and the four figures are absent together or present together")
                .isNull();
        assertThat(row.allowed()).isNull();
        assertThat(row.left()).isNull();
        assertThat(row.spent())
                .as("while what it cost is a fact whether or not anybody put a limit on it")
                .isEqualByComparingTo("40.00");
    }

    @Test
    void the_figure_in_force_is_what_this_month_reports_the_category_was_allowed() {
        account.budgets(groceries.categoryId(), "250.00");

        CategorySpendingView row = account.thisMonth().theCategory(groceries.categoryId());

        assertThat(row.budgeted())
                .as("a budget you can declare but not read is not a budget; this is the read")
                .isEqualByComparingTo("250.00");
        assertThat(row.spent()).isEqualByComparingTo("0.00");
        assertThat(row.left())
                .as("and the arithmetic is done here rather than on the page")
                .isEqualByComparingTo("250.00");
    }

    @Test
    void declaring_a_budget_moves_no_money() {
        BigDecimal before = account.balance();

        account.budgets(groceries.categoryId(), "250.00");
        account.budgets(fuel.categoryId(), "120.00");

        assertThat(account.balance())
                .as("a budget is a plan for money that will leave, not money leaving — an "
                        + "application that took the figure would be one nobody could change their "
                        + "mind in")
                .isEqualByComparingTo(before);
    }

    @Test
    void declaring_a_second_figure_supersedes_the_first_rather_than_writing_over_it() {
        MonthlyBudgetView first = account.budgets(groceries.categoryId(), "250.00");

        MonthlyBudgetView second = account.budgets(groceries.categoryId(), "300.00");

        assertThat(second.budgetId())
                .as("a row of its own, because the figure that stood before has months of its own "
                        + "to keep — an amount written over in place would rewrite every one of them")
                .isNotEqualTo(first.budgetId());
        assertThat(second.amount()).isEqualByComparingTo("300.00");
        assertThat(second.state()).isEqualTo("STANDING");
        assertThat(second.effectiveFrom())
                .as("the new figure takes effect this month, which is what the customer means by "
                        + "changing it")
                .isEqualTo(YearMonth.now().toString());

        assertThat(account.thisMonth().theCategory(groceries.categoryId()).budgeted())
                .as("and this month is measured against the figure that is in force now")
                .isEqualByComparingTo("300.00");
    }

    @Test
    void a_budget_can_be_stopped_without_ending_the_category() {
        account.budgets(groceries.categoryId(), "250.00");

        MonthlyBudgetView stopped = account.stopsBudgeting(groceries.categoryId());

        assertThat(stopped.state()).isEqualTo("STOPPED");
        assertThat(stopped.stoodDownAt())
                .as("the record says when the customer stopped holding themselves to it")
                .isNotNull();
        assertThat(stopped.amount())
                .as("the figure is kept rather than cleared, because the months it governed still "
                        + "quote it")
                .isEqualByComparingTo("250.00");

        assertThat(account.thisMonth().categories())
                .as("the category is still one of the words this account's money is described in — "
                        + "stopping a budget is not ending a category")
                .extracting(CategorySpendingView::categoryId)
                .contains(groceries.categoryId());
    }

    @Test
    void a_category_whose_budget_was_stopped_reports_its_spending_and_no_budget_at_all() {
        account.budgets(groceries.categoryId(), "250.00");
        account.spends("Delhaize", "40.00", AnAccountWithBudgets.Part.of(groceries.categoryId(),
                "40.00"));

        account.stopsBudgeting(groceries.categoryId());

        CategorySpendingView row = account.thisMonth().theCategory(groceries.categoryId());
        assertThat(row.spent())
                .as("the spending is a fact whether or not anybody put a limit on it")
                .isEqualByComparingTo("40.00");
        assertThat(row.budgeted())
                .as("a budget absent rather than a nought: a blank drawn as zero would tell "
                        + "somebody they had overspent a figure they never set")
                .isNull();
        assertThat(row.left())
                .as("and there is nothing to have left of a limit that does not exist")
                .isNull();
        assertThat(row.overspent())
                .as("no amount of spending exceeds a limit nobody set")
                .isFalse();
    }

    @Test
    void a_budget_can_be_declared_again_after_it_was_stopped() {
        account.budgets(groceries.categoryId(), "250.00");
        account.stopsBudgeting(groceries.categoryId());

        MonthlyBudgetView again = account.budgets(groceries.categoryId(), "180.00");

        assertThat(again.state()).isEqualTo("STANDING");
        assertThat(account.thisMonth().theCategory(groceries.categoryId()).budgeted())
                .as("stopping is not a door that locks: what a customer cannot do is resurrect the "
                        + "old row, and declaring a figure afresh is always a new one")
                .isEqualByComparingTo("180.00");
    }

    @Test
    void a_category_nobody_has_budgeted_reports_what_it_cost_and_no_figure() {
        account.spends("Shell", "60.00", AnAccountWithBudgets.Part.of(fuel.categoryId(), "60.00"));

        MonthOfSpendingView month = account.thisMonth();

        assertThat(month.theCategory(fuel.categoryId()).budgeted())
                .as("watching a category without policing it is a thing a customer is allowed to "
                        + "choose")
                .isNull();
        assertThat(month.theCategory(fuel.categoryId()).spent()).isEqualByComparingTo("60.00");
        assertThat(month.budgeted())
                .as("and an account with no figure anywhere on it has no total to quote either")
                .isNull();
        assertThat(month.left()).isNull();
        assertThat(month.spent())
                .as("what it cost is still reported, which is the half of the answer that does not "
                        + "depend on anybody having made a plan")
                .isEqualByComparingTo("60.00");
    }

    @Test
    void budgeting_one_category_and_not_another_totals_only_the_one_with_a_figure() {
        account.budgets(groceries.categoryId(), "250.00");
        account.spends("Delhaize", "40.00", AnAccountWithBudgets.Part.of(groceries.categoryId(),
                "40.00"));
        account.spends("Shell", "60.00", AnAccountWithBudgets.Part.of(fuel.categoryId(), "60.00"));

        MonthOfSpendingView month = account.thisMonth();

        assertThat(month.budgeted())
                .as("a limit is only a limit for the categories that have one")
                .isEqualByComparingTo("250.00");
        assertThat(month.spent())
                .as("while what the account spent is everything it spent under a word, budgeted or "
                        + "not")
                .isEqualByComparingTo("100.00");
        assertThat(month.left())
                .as("so the account is over its declared plan by exactly the fuel nobody planned")
                .isEqualByComparingTo("150.00");
    }
}
