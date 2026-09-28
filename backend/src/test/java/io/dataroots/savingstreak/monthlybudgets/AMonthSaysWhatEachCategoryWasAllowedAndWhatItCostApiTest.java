package io.dataroots.savingstreak.monthlybudgets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.CategorySpendingView;
import io.dataroots.savingstreak.support.MonthOfSpendingView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer reads how this month is going: what each category was allowed to cost, what it cost,
 * and what that leaves — with the arithmetic done for them.
 *
 * <p>User stories 23 and 41. A list of spends says where the money went; this says whether it went
 * there in the quantity the customer decided on before the month, which is the judgement this whole
 * feature exists to train. The figures are asserted against each other as well as against what was
 * recorded, because "budgeted, spent, left" is only worth reading if a person can check it with a
 * pencil.
 *
 * <p><strong>Everything here is derived on the read.</strong> Nothing in this test waits for a job,
 * a month end or a rollup: a spend recorded a second ago is in the next read, and the same read
 * taken twice with nothing in between says the same thing. The class asserts both, because "no
 * stored figure can disagree with the records it was summed from" is the decision the module is
 * shaped by and it is otherwise invisible.
 */
class AMonthSaysWhatEachCategoryWasAllowedAndWhatItCostApiTest extends ApiIntegrationTest {

    private AnAccountWithBudgets account;

    private SpendingCategoryView groceries;

    private SpendingCategoryView goingOut;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithBudgets(http, "the-month-read");
        groceries = account.declares("Groceries");
        goingOut = account.declares("Going out");
    }

    @Test
    void a_month_says_which_month_it_is_and_the_days_it_runs_between() {
        MonthOfSpendingView month = account.thisMonth();

        YearMonth now = YearMonth.now();
        assertThat(month.currentAccountId()).isEqualTo(account.id());
        assertThat(month.month())
                .as("said out loud rather than left to be inferred, because a page working out "
                        + "what month it is would draw a month nobody is in on a wound clock")
                .isEqualTo(now.toString());
        assertThat(month.from()).isEqualTo(LocalDate.of(now.getYear(), now.getMonth(), 1));
        assertThat(month.until()).isEqualTo(now.atEndOfMonth());
    }

    @Test
    void an_account_with_categories_and_nothing_spent_reports_them_all_at_nothing() {
        MonthOfSpendingView month = account.thisMonth();

        assertThat(month.categories())
                .as("a category with nothing spent is a real answer, and drawing it is how a "
                        + "customer sees that they stayed inside it")
                .extracting(CategorySpendingView::categoryId)
                .containsExactly(groceries.categoryId(), goingOut.categoryId());
        assertThat(month.spent()).isEqualByComparingTo("0.00");
        assertThat(month.uncategorised()).isEqualByComparingTo("0.00");
    }

    @Test
    void what_was_spent_under_a_category_is_what_the_month_says_it_cost() {
        account.budgets(groceries.categoryId(), "250.00");
        account.spends("Delhaize", "42.50",
                AnAccountWithBudgets.Part.of(groceries.categoryId(), "42.50"));
        account.spends("Colruyt", "17.50",
                AnAccountWithBudgets.Part.of(groceries.categoryId(), "17.50"));

        CategorySpendingView row = account.thisMonth().theCategory(groceries.categoryId());

        assertThat(row.name()).isEqualTo("Groceries");
        assertThat(row.categoryState()).isEqualTo("STANDING");
        assertThat(row.budgeted()).isEqualByComparingTo("250.00");
        assertThat(row.committed())
                .as("nothing is committed to it: no bill was presented against this category")
                .isEqualByComparingTo("0.00");
        assertThat(row.discretionary()).isEqualByComparingTo("60.00");
        assertThat(row.spent())
                .as("spent is the committed and the discretionary added, to the cent")
                .isEqualByComparingTo("60.00");
        assertThat(row.left())
                .as("and left is the budget less what it cost, worked out here rather than on the "
                        + "page")
                .isEqualByComparingTo("190.00");
        assertThat(row.overspent()).isFalse();
    }

    @Test
    void one_spend_split_across_two_categories_lands_in_both_of_them() {
        account.spends("Supermarket trip", "50.00",
                AnAccountWithBudgets.Part.of(groceries.categoryId(), "30.00"),
                AnAccountWithBudgets.Part.of(goingOut.categoryId(), "20.00"));

        MonthOfSpendingView month = account.thisMonth();

        assertThat(month.theCategory(groceries.categoryId()).discretionary())
                .as("a supermarket trip that was half food and half wine is recorded as what it "
                        + "was, and counted as what it was")
                .isEqualByComparingTo("30.00");
        assertThat(month.theCategory(goingOut.categoryId()).discretionary())
                .isEqualByComparingTo("20.00");
        assertThat(month.spent()).isEqualByComparingTo("50.00");
    }

    @Test
    void money_filed_under_nothing_is_reported_on_its_own_rather_than_disappearing() {
        account.spends("Cash out", "50.00",
                AnAccountWithBudgets.Part.of(groceries.categoryId(), "30.00"),
                AnAccountWithBudgets.Part.unfiled("20.00"));

        MonthOfSpendingView month = account.thisMonth();

        assertThat(month.uncategorised())
                .as("a part with no category is a state a customer chooses, and euros that simply "
                        + "vanished from the page would be the one thing this read must never do")
                .isEqualByComparingTo("20.00");
        assertThat(month.spent())
                .as("kept out of the total below, because that total is the sum of the rows and a "
                        + "total holding money belonging to none of them is a total nobody can check")
                .isEqualByComparingTo("30.00");
    }

    @Test
    void a_category_spent_past_its_budget_reports_a_negative_figure_rather_than_a_nought() {
        account.budgets(goingOut.categoryId(), "100.00");
        account.spends("Round of drinks", "130.00",
                AnAccountWithBudgets.Part.of(goingOut.categoryId(), "130.00"));

        CategorySpendingView row = account.thisMonth().theCategory(goingOut.categoryId());

        assertThat(row.left())
                .as("rounding an overspend up to nothing would hide exactly the month a customer "
                        + "needs to see; nothing refused the spend, because a balance is the only "
                        + "hard constraint here")
                .isEqualByComparingTo("-30.00");
        assertThat(row.overspent()).isTrue();
    }

    @Test
    void a_month_nothing_happened_in_is_an_empty_month_rather_than_a_refusal() {
        account.spends("Delhaize", "20.00",
                AnAccountWithBudgets.Part.of(groceries.categoryId(), "20.00"));

        MonthOfSpendingView monthGone = account.month("2019-01");

        assertThat(monthGone.month()).isEqualTo("2019-01");
        assertThat(monthGone.spent())
                .as("a month before this account existed cost nothing, which is a true statement "
                        + "rather than an error")
                .isEqualByComparingTo("0.00");
        assertThat(monthGone.categories())
                .as("the standing categories are still drawn: they are the words this account "
                        + "describes its money with, and a month with nothing in them says so")
                .extracting(CategorySpendingView::spent)
                .allMatch(spent -> spent.compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void the_same_read_twice_says_the_same_thing_and_a_spend_between_them_changes_it() {
        account.budgets(groceries.categoryId(), "250.00");

        MonthOfSpendingView first = account.thisMonth();
        MonthOfSpendingView again = account.thisMonth();
        assertThat(again.theCategory(groceries.categoryId()).left())
                .as("nothing is stored and nothing is advanced by reading, so two reads with "
                        + "nothing in between are one answer")
                .isEqualByComparingTo(first.theCategory(groceries.categoryId()).left());

        account.spends("Delhaize", "25.00",
                AnAccountWithBudgets.Part.of(groceries.categoryId(), "25.00"));

        assertThat(account.thisMonth().theCategory(groceries.categoryId()).left())
                .as("and a spend recorded a second ago is in the next read, because every figure "
                        + "here is derived from the records rather than kept beside them")
                .isEqualByComparingTo("225.00");
    }

    @Test
    void ending_a_category_leaves_this_month_reporting_what_was_spent_against_the_figure_that_stood() {
        account.budgets(groceries.categoryId(), "250.00");
        account.spends("Delhaize", "80.00",
                AnAccountWithBudgets.Part.of(groceries.categoryId(), "80.00"));

        account.ends(groceries.categoryId());

        CategorySpendingView row = account.thisMonth().theCategory(groceries.categoryId());
        assertThat(row.categoryState())
                .as("marked ended, so that a page can say the word behind the figure is one its "
                        + "holder has stopped using")
                .isEqualTo("ENDED");
        assertThat(row.spent())
                .as("the money left and the record of it explains a balance; ending a word does "
                        + "not unspend it")
                .isEqualByComparingTo("80.00");
        assertThat(row.budgeted())
                .as("and the figure the spending was actually measured against is still quoted — a "
                        + "month read that dropped it would erase the standard somebody was held to")
                .isEqualByComparingTo("250.00");
        assertThat(row.left()).isEqualByComparingTo("170.00");
    }

    @Test
    void an_ended_category_with_nothing_in_a_month_is_not_in_that_month_at_all() {
        account.ends(goingOut.categoryId());

        MonthOfSpendingView month = account.thisMonth();

        assertThat(month.category(goingOut.categoryId()))
                .as("an ended category is a record of months already gone, and every month from "
                        + "now on would otherwise carry a list of words nobody uses")
                .isEmpty();
        assertThat(month.categories())
                .extracting(CategorySpendingView::categoryId)
                .containsExactly(groceries.categoryId());
    }

    @Test
    void a_budget_stopped_by_ending_its_category_governs_nothing_after_this_month() {
        account.budgets(groceries.categoryId(), "250.00");

        account.ends(groceries.categoryId());

        assertThat(account.month(YearMonth.now().plusMonths(1).toString())
                .category(groceries.categoryId()))
                .as("a limit on a word nobody spends under any more is a limit nothing will ever "
                        + "be measured against")
                .isEmpty();
    }

    @Test
    void stopping_a_budget_leaves_the_category_spendable_and_still_reported() {
        account.budgets(groceries.categoryId(), "250.00");
        account.stopsBudgeting(groceries.categoryId());

        account.spends("Delhaize", "30.00",
                AnAccountWithBudgets.Part.of(groceries.categoryId(), "30.00"));

        CategorySpendingView row = account.thisMonth().theCategory(groceries.categoryId());
        assertThat(row.categoryState())
                .as("stopping a budget is a decision about the figure, not about the word")
                .isEqualTo("STANDING");
        assertThat(row.spent()).isEqualByComparingTo("30.00");
        assertThat(row.budgeted()).isNull();
    }
}
