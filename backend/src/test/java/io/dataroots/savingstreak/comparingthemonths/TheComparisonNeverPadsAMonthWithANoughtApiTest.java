package io.dataroots.savingstreak.comparingthemonths;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.CategoryComparedView;
import io.dataroots.savingstreak.support.MonthComparedView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import io.dataroots.savingstreak.support.SpendingHistoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the comparison says about a month it has nothing to say about: that there was no budget, or
 * that there was no category, and never that there was a nought.
 *
 * <p>User story 41 — months before I had a budget should show what I spent and say plainly that
 * there was no budget, so that a blank is never read as a zero — and the two halves of the same
 * argument about a category younger than the window and one already ended.
 *
 * <p><strong>Three ways a month can be empty, and they are three different answers.</strong> A month
 * in which a category existed and nobody had put a figure on it shows the spending with the budget
 * absent. A month before the category was named at all is not a row: there was nothing to spend on
 * and nothing to be measured against, and a nought there would be this application inventing a past.
 * A month after a category was ended is the same absence from the other end, while the months it was
 * live go on reading exactly as they did. Getting any of the three wrong produces a page that looks
 * right, which is why each is asserted on its own.
 *
 * <p><strong>The average is the reason it matters rather than a tidiness argument.</strong> Six
 * padded noughts in front of a category declared last month would give it an average of nought and
 * report its first real month as infinitely above its habit — a figure that is wrong, alarming and
 * entirely manufactured by the padding.
 *
 * <p>Each test opens a household of its own and winds the clock from wherever it finds it, so that
 * they hold whatever order JUnit runs them in: winding cannot be undone, and a test asserting about
 * a window another test had already moved would pass or fail by luck.
 *
 * <p>An application of its own, because months pass by winding the clock.
 */
class TheComparisonNeverPadsAMonthWithANoughtApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-comparison-pads-nothing"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_month_before_the_budget_took_effect_says_there_was_no_budget_rather_than_nought() {
        String customer = app.aCustomerOfItsOwn("spending before a budget");
        SpendingCategoryView goingOut = app.declareACategoryFor(customer, "Going out");
        YearMonth beforeAnyFigure = YearMonth.from(app.theDateTheClockReads());
        app.spendFor(customer, "A round of drinks", "40.00", goingOut.categoryId());

        YearMonth withAFigure = beforeAnyFigure.plusMonths(1);
        windTo(withAFigure.atDay(1));
        app.declareABudgetFor(customer, goingOut.categoryId(), "100.00");
        app.spendFor(customer, "Dinner", "60.00", goingOut.categoryId());

        CategoryComparedView compared =
                app.theLastFewMonthsOf(customer).theCategory(goingOut.categoryId());

        MonthComparedView before = compared.theMonth(beforeAnyFigure.toString());
        assertThat(before.spent())
                .as("the forty euros left the account and the comparison says so: spending is a "
                        + "fact whether or not anybody had put a limit on it")
                .isEqualByComparingTo("40.00");
        assertThat(before.budgeted())
                .as("and there was no budget, said plainly rather than quoted as EUR 0.00 — a "
                        + "blank read as a zero would tell this customer they had overspent a "
                        + "figure they never set, which is exactly the wrong lesson")
                .isNull();
        assertThat(before.rollover())
                .as("the four are absent together or present together: there is no rule on a limit "
                        + "that does not exist")
                .isNull();
        assertThat(before.carriedIn())
                .as("nothing carries into a month nobody was measuring")
                .isNull();
        assertThat(before.allowed()).isNull();
        assertThat(before.left())
                .as("and there is nothing to have left of a limit nobody set")
                .isNull();
        assertThat(before.overspent())
                .as("so the month is not over anything, which is a category being watched rather "
                        + "than policed — a thing a customer is allowed to choose")
                .isFalse();

        MonthComparedView after = compared.theMonth(withAFigure.toString());
        assertThat(after.budgeted())
                .as("while the month the figure took effect in quotes it")
                .isEqualByComparingTo("100.00");
        assertThat(after.left()).isEqualByComparingTo("40.00");
        assertThat(compared.trailingAverage())
                .as("and the month with no budget is still in the average, because the average is "
                        + "over what was spent and every month has a figure for that")
                .isEqualByComparingTo("40.00");
        assertThat(compared.monthsTheAverageIsOver())
                .as("over the one month there is, said out loud so that nobody reads it as three")
                .isEqualTo(1);
        assertThat(compared.comparedWithTheAverage())
                .as("so this month is twenty euros above a habit one month long")
                .isEqualByComparingTo("20.00");
    }

    @Test
    void a_category_younger_than_the_window_reports_only_the_months_it_existed_for() {
        String customer = app.aCustomerOfItsOwn("a young category");
        SpendingCategoryView oldHabit = app.declareACategoryFor(customer, "Groceries");
        app.declareABudgetFor(customer, oldHabit.categoryId(), "100.00");
        YearMonth first = YearMonth.from(app.theDateTheClockReads());
        app.spendFor(customer, "Groceries", "50.00", oldHabit.categoryId());
        windTo(first.plusMonths(1).atDay(1));
        app.spendFor(customer, "Groceries", "50.00", oldHabit.categoryId());
        windTo(first.plusMonths(2).atDay(1));
        app.spendFor(customer, "Groceries", "50.00", oldHabit.categoryId());

        YearMonth named = first.plusMonths(3);
        windTo(named.atDay(1));
        SpendingCategoryView newHabit = app.declareACategoryFor(customer, "Cycling");
        app.declareABudgetFor(customer, newHabit.categoryId(), "80.00");
        app.spendFor(customer, "Inner tubes", "30.00", newHabit.categoryId());

        SpendingHistoryView history = app.theLastFewMonthsOf(customer);
        assertThat(history.months())
                .as("the window is six months whatever the account has been through, so that one "
                        + "set of column headings draws every category on it")
                .hasSize(6);

        CategoryComparedView cycling = history.theCategory(newHabit.categoryId());
        assertThat(cycling.months())
                .as("but Cycling was named this month and has one month of history, not six — the "
                        + "five before it are months in which this customer did not spend nothing "
                        + "on cycling, they are months in which cycling was not one of the things "
                        + "their money went on")
                .hasSize(1);
        assertThat(cycling.months().get(0).month()).isEqualTo(named.toString());
        assertThat(cycling.trailingAverage())
                .as("so there is nothing to compare it against, which is an absence rather than a "
                        + "nought: an average of nought over padded months would report this first "
                        + "month as infinitely above a habit that does not exist")
                .isNull();
        assertThat(cycling.monthsTheAverageIsOver()).isZero();
        assertThat(cycling.comparedWithTheAverage()).isNull();

        CategoryComparedView groceries = history.theCategory(oldHabit.categoryId());
        assertThat(groceries.months())
                .as("while the category that has been there all along has every month of the "
                        + "window it was live in")
                .hasSize(4);
        assertThat(groceries.trailingAverage())
                .as("and an average over the three months behind this one, all of which cost fifty")
                .isEqualByComparingTo("50.00");
        assertThat(groceries.thisMonth().spent())
                .as("this month nothing has gone on groceries yet, which is a real answer about a "
                        + "month still running rather than a month missing from the list")
                .isEqualByComparingTo("0.00");
        assertThat(groceries.comparedWithTheAverage())
                .as("so the customer is fifty euros below their habit, so far")
                .isEqualByComparingTo("-50.00");
    }

    @Test
    void an_ended_category_still_appears_for_the_months_it_was_live() {
        String customer = app.aCustomerOfItsOwn("an ended category");
        SpendingCategoryView seasonTicket = app.declareACategoryFor(customer, "Season ticket");
        app.declareABudgetFor(customer, seasonTicket.categoryId(), "100.00");
        YearMonth first = YearMonth.from(app.theDateTheClockReads());
        app.spendFor(customer, "The first quarter", "90.00", seasonTicket.categoryId());

        YearMonth ended = first.plusMonths(1);
        windTo(ended.atDay(1));
        app.spendFor(customer, "The last of it", "70.00", seasonTicket.categoryId());
        app.endTheCategoryFor(customer, seasonTicket.categoryId());

        YearMonth afterwards = first.plusMonths(2);
        windTo(afterwards.atDay(1));

        CategoryComparedView compared =
                app.theLastFewMonthsOf(customer).theCategory(seasonTicket.categoryId());
        assertThat(compared.categoryState())
                .as("a word its holder has stopped using, and the comparison says so rather than "
                        + "leaving a page to infer it from an absence somewhere else")
                .isEqualTo("ENDED");
        assertThat(compared.months().stream().map(MonthComparedView::month).toList())
                .as("the two months it was live in, and neither the ones before it nor the one "
                        + "after: what was spent under a category is the explanation for money "
                        + "that has already left, and it stays readable after the word is gone")
                .containsExactly(first.toString(), ended.toString());
        assertThat(compared.theMonth(first.toString()).spent()).isEqualByComparingTo("90.00");
        assertThat(compared.theMonth(ended.toString()).budgeted())
                .as("and the month it was ended in still quotes the figure that stood in it, "
                        + "because the spending happened and the standard it was measured against "
                        + "has to be readable")
                .isEqualByComparingTo("100.00");
        assertThat(compared.thisMonth())
                .as("there is no row for this month, which is the honest answer about a category "
                        + "that is not one of the things this money goes on any more — a row of "
                        + "noughts would say they spent nothing on it, which is a different claim")
                .isNull();
        assertThat(compared.comparedWithTheAverage())
                .as("so there is nothing of this month's to quote against the months behind it")
                .isNull();
        assertThat(compared.trailingAverage())
                .as("although the months behind it still average to what they cost, ninety and "
                        + "seventy, which is what makes the record worth keeping")
                .isEqualByComparingTo("80.00");
        assertThat(compared.monthsTheAverageIsOver()).isEqualTo(2);
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
