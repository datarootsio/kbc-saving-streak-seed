package io.dataroots.savingstreak.monthlybudgets;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.CategorySpendingView;
import io.dataroots.savingstreak.support.MonthlyBudgetView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Changing what a category is allowed to cost applies from now rather than backwards: the months
 * already gone go on quoting the figure that stood in them.
 *
 * <p>User stories 8 and 9, and the reason a budget is a row rather than a column. A customer who
 * raises their grocery budget in the third month is saying what the third month is allowed to cost;
 * they are not saying the first two were allowed to cost it. An amount written over in place would
 * rewrite every month anybody ever looks back at — their history would become what they currently
 * intend rather than what happened — and the read would quietly start judging a month against a
 * standard nobody was held to at the time.
 *
 * <p><strong>Three months and two changes of mind</strong>, because two figures only prove that the
 * first was kept and three prove that the mechanism is a chain rather than a special case for the
 * first supersession. The spending in each month is different as well, so that a month quoting the
 * wrong figure and a month quoting the wrong spending are two failures rather than one.
 *
 * <p>An application of its own, because months pass by winding the clock and that cannot be undone.
 *
 * <p>One test, because the clock only goes forward and the narrative is a sequence of months. The
 * claim is asserted after every step instead, which is what a customer changing their mind actually
 * looks like.
 */
class ABudgetSupersededInJuneLeavesAprilAndMayQuotingWhatStoodApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-budget-superseded"));
        theCustomer = app.aCustomerOfItsOwn("a budget superseded");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void two_changes_of_mind_leave_every_month_before_them_quoting_the_figure_that_stood() {
        SpendingCategoryView groceries = app.declareACategoryFor(theCustomer, "Groceries");
        YearMonth first = YearMonth.from(app.theDateTheClockReads());

        MonthlyBudgetView stoodFirst = app.declareABudgetFor(theCustomer, groceries.categoryId(),
                "250.00");
        app.spendFor(theCustomer, "Delhaize", "80.00", groceries.categoryId());
        assertThat(stoodFirst.effectiveFrom()).isEqualTo(first.toString());
        assertThat(stoodFirst.stoodThrough())
                .as("a figure in force has no last month")
                .isNull();
        assertThatTheMonthReads(first, "250.00", "80.00", "170.00");

        YearMonth second = first.plusMonths(1);
        windTo(second.atDay(1));
        app.spendFor(theCustomer, "Colruyt", "240.00", groceries.categoryId());
        assertThatTheMonthReads(second, "250.00", "240.00", "10.00");
        assertThatTheMonthReads(first, "250.00", "80.00", "170.00");

        YearMonth third = second.plusMonths(1);
        windTo(third.atDay(1));
        MonthlyBudgetView stoodSecond = app.declareABudgetFor(theCustomer, groceries.categoryId(),
                "300.00");
        app.spendFor(theCustomer, "Delhaize", "310.00", groceries.categoryId());

        assertThat(stoodSecond.effectiveFrom())
                .as("the new figure takes effect in the month it was named in, which is what the "
                        + "customer means by changing it")
                .isEqualTo(third.toString());
        assertThatTheMonthReads(third, "300.00", "310.00", "-10.00");
        assertThat(app.spendingInMonthOf(theCustomer, third.toString())
                .theCategory(groceries.categoryId()).overspent())
                .as("and the month they raised it in is still over it, because they spent more "
                        + "than the figure they raised it to")
                .isTrue();
        assertThatTheMonthReads(first, "250.00", "80.00", "170.00");
        assertThatTheMonthReads(second, "250.00", "240.00", "10.00");

        YearMonth fourth = third.plusMonths(1);
        windTo(fourth.atDay(1));
        MonthlyBudgetView stoodThird = app.declareABudgetFor(theCustomer, groceries.categoryId(),
                "400.00");
        assertThat(stoodThird.effectiveFrom()).isEqualTo(fourth.toString());

        assertThatTheMonthReads(fourth, "400.00", "0.00", "400.00");
        assertThat(app.spendingThisMonthOf(theCustomer).month())
                .as("and \"this month\" is the month the clock is in, which is why no page ever has "
                        + "to work it out for itself")
                .isEqualTo(fourth.toString());
        assertThatTheMonthReads(third, "300.00", "310.00", "-10.00");
        assertThatTheMonthReads(second, "250.00", "240.00", "10.00");
        assertThatTheMonthReads(first, "250.00", "80.00", "170.00");
    }

    @Test
    void stopping_a_budget_leaves_the_months_it_governed_quoting_it_and_this_month_with_none() {
        SpendingCategoryView fuel = app.declareACategoryFor(theCustomer, "Fuel");
        YearMonth theMonthItStoodIn = YearMonth.from(app.theDateTheClockReads());
        app.declareABudgetFor(theCustomer, fuel.categoryId(), "120.00");
        app.spendFor(theCustomer, "Shell", "45.00", fuel.categoryId());

        MonthlyBudgetView stopped = app.stopBudgetingFor(theCustomer, fuel.categoryId());

        assertThat(stopped.state()).isEqualTo("STOPPED");
        assertThat(stopped.stoodThrough())
                .as("a customer disowning a limit should not go on being measured against it in "
                        + "the very month they disowned it, so its run ends before this one")
                .isEqualTo(theMonthItStoodIn.minusMonths(1).toString());

        CategorySpendingView row = app.spendingInMonthOf(theCustomer, theMonthItStoodIn.toString())
                .theCategory(fuel.categoryId());
        assertThat(row.spent())
                .as("what was spent under it is a fact whether or not anybody is policing it")
                .isEqualByComparingTo("45.00");
        assertThat(row.budgeted())
                .as("and the figure is gone from this month: absent rather than a nought, which is "
                        + "the difference between watching a category and policing one")
                .isNull();
    }

    /**
     * The three figures a month is worth checking, asserted together, because "budgeted, spent,
     * left" is only worth reading if the three of them agree — a helper that checked one at a time
     * would let a month pass with a budget from one row and a subtraction from another.
     */
    private static void assertThatTheMonthReads(YearMonth month, String budgeted, String spent,
                                                String left) {
        CategorySpendingView row = app.spendingInMonthOf(theCustomer, month.toString())
                .categories().stream()
                .filter(one -> one.name().equals("Groceries"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Groceries is not in " + month + " at all"));
        assertThat(row.budgeted())
                .as(month + " was allowed EUR " + budgeted)
                .isEqualByComparingTo(budgeted);
        assertThat(row.spent()).as(month + " cost EUR " + spent).isEqualByComparingTo(spent);
        assertThat(row.left())
                .as(month + " left EUR " + left + ", which is the budget less what it cost")
                .isEqualByComparingTo(left);
    }

    /**
     * Moves the clock to a day, counted through the calendar rather than by a fixed span. The clock
     * only goes forward and only in whole days, which is exactly the way a trainer moves it — and a
     * fixed thirty would land in the wrong month twice a year.
     */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days).describedAs("the clock only goes forward").isPositive();
        app.daysPass(days);
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
    }
}
