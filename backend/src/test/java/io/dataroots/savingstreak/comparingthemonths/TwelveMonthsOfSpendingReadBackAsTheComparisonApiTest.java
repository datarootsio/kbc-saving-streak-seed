package io.dataroots.savingstreak.comparingthemonths;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.CategoryComparedView;
import io.dataroots.savingstreak.support.MonthComparedView;
import io.dataroots.savingstreak.support.SpendView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import io.dataroots.savingstreak.support.SpendingHistoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A year of a household's spending, wound a month at a time, read back as the comparison the budget
 * screen draws.
 *
 * <p>User stories 39 and 40. A customer wants to tell a bad month from a habit, and they want a
 * months-long average to compare against so that one unusual month does not look like a trend.
 *
 * <p><strong>Twelve months of actual clock advance, and nothing simulated.</strong> Every figure
 * below was put there by a request a customer could have made: a category named, a figure declared,
 * and one spend a month taken out of a real balance. The comparison is then asserted against what
 * those runs actually did rather than against a fixture — which is the only way to find out whether
 * the derived read and the months it derives from agree, and the only way to catch a window that
 * quietly slid by a month somewhere in the year.
 *
 * <p><strong>Twelve rather than six, deliberately.</strong> Six months of history would leave the
 * window and the account's whole life the same stretch, and a read that ignored its bound entirely
 * would pass. With a year behind it the comparison has to stop at six and say which six, and the
 * six oldest months have to be absent from it although they are perfectly readable one at a time.
 *
 * <p><strong>Nothing is stored and no job is run.</strong> The clock is wound and nothing else
 * happens: there is no monthly close in this feature, no rollup to fill and no cursor to advance, so
 * a comparison read at the end of the year is a fold over records the application has been holding
 * all along. The correction at the end is what proves it — a split changed in a month already gone
 * moves that month's row and the average taken over it, with no job between the change and the read.
 *
 * <p>An application of its own, because months pass by winding the clock and that cannot be undone.
 */
class TwelveMonthsOfSpendingReadBackAsTheComparisonApiTest extends ApiIntegrationTest {

    /**
     * What the household spent on groceries in each of its twelve months, oldest first.
     *
     * <p>Six steady months, then a quiet one, then a climb to an expensive one and back down. The
     * shape matters: the six inside the window have to be different from each other so that a row
     * quoted against the wrong month is visible, and the three the average is taken over — fifty, a
     * hundred and twenty, sixty — average to a figure that does not divide by three, so that a read
     * rounding its average anywhere but at the cent is caught.
     */
    private static final List<String> MONTH_BY_MONTH = List.of(
            "60.00", "60.00", "60.00", "60.00", "60.00", "60.00",
            "30.00", "40.00", "50.00", "120.00", "60.00", "90.00");

    /** What the figure on Groceries was every one of those months, never superseded. */
    private static final String THE_FIGURE = "100.00";

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    private static SpendingCategoryView groceries;

    private static SpendingCategoryView car;

    /**
     * A second household living exactly the same year, whose only purpose is to have its past
     * corrected.
     *
     * <p><strong>A customer of its own because a correction changes the months every other test
     * here reads.</strong> These test methods run in an order JUnit decides rather than the one they
     * are written in, so a correction applied to the household above would leave three tests
     * asserting a year that had already been rewritten — passing or failing depending on which
     * method ran first, which is the worst kind of test there is. The clock cannot be wound again
     * either, because every assertion in this class is about the window the year of winding
     * produced, so the second household lives its year beside the first rather than after it.
     */
    private static String theCorrector;

    private static SpendingCategoryView correctedGroceries;

    private static SpendingCategoryView correctedCar;

    /** The month each of the twelve spends was recorded in, oldest first, as the clock read it. */
    private static final List<YearMonth> MONTHS_LIVED = new ArrayList<>();

    /**
     * The second household's spends as they were recorded, oldest first, so that one of them can be
     * said again later.
     */
    private static final List<SpendView> SPENDS_TO_CORRECT = new ArrayList<>();

    @BeforeAll
    static void liveAYearOfMonthsOnAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-twelve-months-compared"));
        theCustomer = app.aCustomerOfItsOwn("twelve months compared");
        groceries = app.declareACategoryFor(theCustomer, "Groceries");
        // A second word the money goes on, named on the first day and never budgeted, so that the
        // comparison has to report a standing category whose budget is absent in every month of it
        // rather than nought in all six — and so that a correction has somewhere to move a hundred
        // and twenty euros to.
        car = app.declareACategoryFor(theCustomer, "Car");
        app.declareABudgetFor(theCustomer, groceries.categoryId(), THE_FIGURE);

        theCorrector = app.aCustomerOfItsOwn("twelve months corrected");
        correctedGroceries = app.declareACategoryFor(theCorrector, "Groceries");
        correctedCar = app.declareACategoryFor(theCorrector, "Car");
        app.declareABudgetFor(theCorrector, correctedGroceries.categoryId(), THE_FIGURE);

        YearMonth first = YearMonth.from(app.theDateTheClockReads());
        for (int month = 0; month < MONTH_BY_MONTH.size(); month++) {
            if (month > 0) {
                windTo(first.plusMonths(month).atDay(1));
            }
            MONTHS_LIVED.add(YearMonth.from(app.theDateTheClockReads()));
            String spent = MONTH_BY_MONTH.get(month);
            String what = "Groceries in " + MONTHS_LIVED.get(month);
            app.spendFor(theCustomer, what, spent, groceries.categoryId());
            // The same year, euro for euro, on the household whose past gets rewritten — so that
            // the figures the correction moves are figures this test has already asserted are right
            // before it moves them.
            SPENDS_TO_CORRECT.add(app.spendFor(theCorrector, what, spent,
                    correctedGroceries.categoryId()));
        }
        assertThat(MONTHS_LIVED)
                .describedAs("twelve months actually passed on the clock, each of them distinct")
                .doesNotHaveDuplicates()
                .hasSize(MONTH_BY_MONTH.size());
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_comparison_covers_the_last_six_months_and_stops_there() {
        SpendingHistoryView history = app.theLastFewMonthsOf(theCustomer);

        assertThat(history.month())
                .as("the comparison ends with the month the application's clock is in, which after "
                        + "a year of winding is not the month any wall clock reads")
                .isEqualTo(theLast().toString());
        assertThat(history.months())
                .as("six months, oldest first, and the same window for every category on the "
                        + "account so that one set of column headings draws all of them")
                .containsExactly(monthsLived(6, 7, 8, 9, 10, 11));
        assertThat(history.earliest()).isEqualTo(MONTHS_LIVED.get(6).toString());

        CategoryComparedView compared = history.theCategory(groceries.categoryId());
        assertThat(compared.months().stream().map(MonthComparedView::month).toList())
                .as("and the category's own rows are those six months, in one call rather than six")
                .containsExactly(monthsLived(6, 7, 8, 9, 10, 11));
        assertThat(compared.month(MONTHS_LIVED.get(5).toString()))
                .as("the six months before them are readable one at a time and are deliberately "
                        + "not in the comparison: a window that quietly grew with the account would "
                        + "be a row nobody could read across")
                .isEmpty();
        assertThat(app.spendingInMonthOf(theCustomer, MONTHS_LIVED.get(5).toString())
                .theCategory(groceries.categoryId()).spent())
                .as("although that month is still there, and still says what it cost")
                .isEqualByComparingTo(MONTH_BY_MONTH.get(5));
    }

    @Test
    void every_month_in_it_says_what_was_budgeted_what_was_spent_and_the_difference() {
        CategoryComparedView compared =
                app.theLastFewMonthsOf(theCustomer).theCategory(groceries.categoryId());

        for (int month = 6; month < MONTH_BY_MONTH.size(); month++) {
            MonthComparedView row = compared.theMonth(MONTHS_LIVED.get(month).toString());
            String spent = MONTH_BY_MONTH.get(month);
            assertThat(row.budgeted())
                    .as("the figure that stood over " + MONTHS_LIVED.get(month))
                    .isEqualByComparingTo(THE_FIGURE);
            assertThat(row.spent())
                    .as("what " + MONTHS_LIVED.get(month) + " actually cost, which is what the "
                            + "spend recorded in it took out of the balance")
                    .isEqualByComparingTo(spent);
            assertThat(row.left())
                    .as("and the difference, worked out once by the application rather than by "
                            + "whoever is drawing it")
                    .isEqualByComparingTo(row.allowed().subtract(row.spent()));
            assertThat(row.spent())
                    .as("the comparison and the month read are two readings of one answer about "
                            + MONTHS_LIVED.get(month))
                    .isEqualByComparingTo(app
                            .spendingInMonthOf(theCustomer, MONTHS_LIVED.get(month).toString())
                            .theCategory(groceries.categoryId()).spent());
        }
    }

    @Test
    void a_three_month_average_accompanies_it_and_this_month_is_quoted_against_it() {
        CategoryComparedView compared =
                app.theLastFewMonthsOf(theCustomer).theCategory(groceries.categoryId());

        assertThat(compared.monthsTheAverageIsOver())
                .as("three months, which is short enough to notice a change of circumstances and "
                        + "long enough that one unusual month does not become the trend")
                .isEqualTo(3);
        assertThat(compared.trailingAverage())
                .as("fifty, a hundred and twenty and sixty, averaged to the cent — the three months "
                        + "behind this one and not this one, because a month compared with itself "
                        + "compares to nothing")
                .isEqualByComparingTo("76.67");
        assertThat(compared.thisMonth().spent())
                .as("and this month, which is the one being judged")
                .isEqualByComparingTo(MONTH_BY_MONTH.get(11));
        assertThat(compared.comparedWithTheAverage())
                .as("so the customer is told they are thirteen euros and thirty-three cents above "
                        + "what the last three months cost, rather than being left to subtract it")
                .isEqualByComparingTo("13.33");
    }

    @Test
    void a_standing_category_nobody_budgeted_reports_its_months_with_no_budget_at_all() {
        CategoryComparedView compared =
                app.theLastFewMonthsOf(theCustomer).theCategory(car.categoryId());

        assertThat(compared.months())
                .as("Car was named on the first day and has been one of the things this money goes "
                        + "on ever since, so every month of the window holds it")
                .hasSize(6);
        assertThat(compared.months())
                .allSatisfy(row -> {
                    assertThat(row.budgeted())
                            .as("and nobody ever said what it was allowed to cost, which is a "
                                    + "state rather than a nought: a blank drawn as 0.00 would "
                                    + "tell this customer they had overspent a figure they never "
                                    + "set")
                            .isNull();
                    assertThat(row.rollover()).isNull();
                    assertThat(row.carriedIn()).isNull();
                    assertThat(row.allowed()).isNull();
                    assertThat(row.left()).isNull();
                    assertThat(row.overspent())
                            .as("and no amount of spending exceeds a limit nobody set")
                            .isFalse();
                });
    }

    /**
     * On a household of its own, because it rewrites the year the tests above are reading.
     *
     * <p>That household lived exactly the same twelve months, so the figures this moves are the
     * figures already asserted to be right, and what changes between the two reads below is the
     * correction and nothing else.
     */
    @Test
    void a_split_corrected_in_a_month_already_gone_moves_that_month_and_the_average_over_it() {
        YearMonth expensive = MONTHS_LIVED.get(9);
        SpendView theExpensiveOne = SPENDS_TO_CORRECT.get(9);
        CategoryComparedView before =
                app.theLastFewMonthsOf(theCorrector).theCategory(correctedGroceries.categoryId());
        assertThat(before.theMonth(expensive.toString()).spent())
                .as("a hundred and twenty euros filed under Groceries, two months ago")
                .isEqualByComparingTo("120.00");
        assertThat(before.trailingAverage())
                .as("and an average that a third of it is standing in")
                .isEqualByComparingTo("76.67");

        app.correctTheSplitFor(theCorrector, theExpensiveOne.spendId(), correctedCar.categoryId(),
                "120.00");

        SpendingHistoryView history = app.theLastFewMonthsOf(theCorrector);
        CategoryComparedView after = history.theCategory(correctedGroceries.categoryId());
        assertThat(after.theMonth(expensive.toString()).spent())
                .as("the customer says it was the car, and the month it happened in changes — "
                        + "nothing was stored, nothing was closed and no job was run between the "
                        + "correction and this read")
                .isEqualByComparingTo("0.00");
        assertThat(after.trailingAverage())
                .as("so the average taken over that month changes with it: fifty, nothing and "
                        + "sixty rather than fifty, a hundred and twenty and sixty")
                .isEqualByComparingTo("36.67");
        assertThat(after.comparedWithTheAverage())
                .as("and this month, unchanged at ninety euros, is now a long way above a habit "
                        + "that was never as expensive as it looked")
                .isEqualByComparingTo("53.33");
        assertThat(history.theCategory(correctedCar.categoryId()).theMonth(expensive.toString())
                .spent())
                .as("while the hundred and twenty arrives where the customer says it belongs, in "
                        + "the month it actually left the account rather than in the month they "
                        + "noticed")
                .isEqualByComparingTo("120.00");
    }

    /** The month the clock is in, which is the last of the twelve this test lived through. */
    private static YearMonth theLast() {
        return MONTHS_LIVED.get(MONTHS_LIVED.size() - 1);
    }

    /** Some of the months lived through, as the API writes them, for a containsExactly. */
    private static String[] monthsLived(int... which) {
        String[] named = new String[which.length];
        for (int at = 0; at < which.length; at++) {
            named[at] = MONTHS_LIVED.get(which[at]).toString();
        }
        return named;
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
