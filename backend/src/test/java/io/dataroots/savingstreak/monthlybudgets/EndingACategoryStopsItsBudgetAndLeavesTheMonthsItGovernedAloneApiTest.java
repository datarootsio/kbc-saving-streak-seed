package io.dataroots.savingstreak.monthlybudgets;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.CategorySpendingView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ending a category stops its budget at once — and "at once" leaves the months already gone exactly
 * as they were, and the month it happened in still measured against the figure that stood.
 *
 * <p>User stories 4, 6 and 9 meeting the budget. A limit on a word nobody spends under any more is a
 * limit nothing will ever be measured against, so the figure has to stop; but a customer who ends
 * Groceries on the twentieth spent three weeks of that month against a figure they had agreed to,
 * and a month read that dropped it would erase the standard they were actually held to. This is
 * deliberately the opposite of what stopping the budget yourself does, which is a decision about the
 * limit rather than about the word.
 *
 * <p>An application of its own, because "the months already gone" only exist once months have gone —
 * and months pass by winding the clock, which cannot be undone.
 *
 * <p>One test, because the clock only goes forward and the narrative is a sequence of months. The
 * claim is asserted after every step instead.
 */
class EndingACategoryStopsItsBudgetAndLeavesTheMonthsItGovernedAloneApiTest
        extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-ending-a-budgeted-category"));
        theCustomer = app.aCustomerOfItsOwn("ending a budgeted category");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_category_ended_in_the_second_month_leaves_the_first_alone_and_the_second_still_measured() {
        SpendingCategoryView groceries = app.declareACategoryFor(theCustomer, "Groceries");
        YearMonth first = YearMonth.from(app.theDateTheClockReads());
        app.declareABudgetFor(theCustomer, groceries.categoryId(), "250.00");
        app.spendFor(theCustomer, "Delhaize", "90.00", groceries.categoryId());

        YearMonth second = first.plusMonths(1);
        windTo(second.atDay(1));
        app.spendFor(theCustomer, "Colruyt", "40.00", groceries.categoryId());

        SpendingCategoryView ended = app.endTheCategoryFor(theCustomer, groceries.categoryId());
        assertThat(ended.state()).isEqualTo("ENDED");

        CategorySpendingView theMonthItEndedIn = app
                .spendingInMonthOf(theCustomer, second.toString())
                .theCategory(groceries.categoryId());
        assertThat(theMonthItEndedIn.categoryState())
                .as("marked ended, so a page can say the word behind the figure is one its holder "
                        + "has stopped using")
                .isEqualTo("ENDED");
        assertThat(theMonthItEndedIn.spent())
                .as("the money left and the record of it explains a balance; ending a word does not "
                        + "unspend it")
                .isEqualByComparingTo("40.00");
        assertThat(theMonthItEndedIn.budgeted())
                .as("and the figure that the spending was really measured against is still quoted, "
                        + "because it really was the standard they were held to")
                .isEqualByComparingTo("250.00");

        CategorySpendingView theMonthBefore = app.spendingInMonthOf(theCustomer, first.toString())
                .theCategory(groceries.categoryId());
        assertThat(theMonthBefore.budgeted())
                .as("a month already gone is untouched: ending a category is a decision about now "
                        + "and never about then")
                .isEqualByComparingTo("250.00");
        assertThat(theMonthBefore.spent()).isEqualByComparingTo("90.00");
        assertThat(theMonthBefore.left()).isEqualByComparingTo("160.00");

        YearMonth third = second.plusMonths(1);
        windTo(third.atDay(1));
        assertThat(app.spendingThisMonthOf(theCustomer).category(groceries.categoryId()))
                .as("and from the next month on the category is gone from the read altogether: "
                        + "nothing can be spent under it, so there is no month for a figure to "
                        + "govern and no row for a page to draw")
                .isEmpty();
        assertThat(app.spendingInMonthOf(theCustomer, first.toString())
                .theCategory(groceries.categoryId()).budgeted())
                .as("while the first month goes on saying exactly what it said, two months later")
                .isEqualByComparingTo("250.00");
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
