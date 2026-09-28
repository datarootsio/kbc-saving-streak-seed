package io.dataroots.savingstreak.theweeksahead;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Function;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import io.dataroots.savingstreak.support.WeekAheadView;
import io.dataroots.savingstreak.support.WeeksAheadView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Six weekly rows, beginning on the Monday the current week began on, each saying what arrives in
 * it, what is committed in it, what the budgets claim of it, and what that leaves.
 *
 * <p>User stories 33, 34 and 35, which are the three the whole feature exists to satisfy: the next
 * six weeks week by week, a figure at the end of each row that a customer can check, and a leftover
 * that assumes the budgets are spent in full so that the number is a floor rather than a hope.
 *
 * <p><strong>The clock is wound to a Monday that is the first of a month, and that is the whole of
 * how this test stays arithmetic rather than calendar guesswork.</strong> From there the six rows
 * hold the whole of the current month and part of the next, the salary lands in a row this test can
 * name, and the rent lands in another — so every claim below is about one figure in one row rather
 * than about a total that several things could have made.
 *
 * <p><strong>The spend at the end is the floor being asserted directly.</strong> The current month
 * contributes what is <em>left</em> of its budgets, so a hundred euros of groceries takes exactly a
 * hundred euros out of what the six weeks claim and puts exactly a hundred back into what they
 * leave. The month lies wholly inside the window, which is what makes "exactly" true to the cent:
 * the spread hands a month's whole figure to the weeks that month touches, and all of them are here.
 *
 * <p>An application of its own, because these rows are about what the clock reads and the clock
 * cannot be wound back.
 */
class TheSixWeeksAheadSayWhatEachWeekLeavesApiTest extends ApiIntegrationTest {

    /** The day of the month the salary is declared for: the eleventh, which is a Sunday nowhere. */
    private static final int PAYDAY = 11;

    /** And the rent, far enough past it that the two never land in one row. */
    private static final int RENT_DAY = 21;

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    private static LocalDate theMondayItAllStartsOn;

    private static SpendingCategoryView groceries;

    @BeforeAll
    static void startAnApplicationWoundToAMondayAtTheStartOfAMonth() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-six-weeks-ahead"));
        theCustomer = app.aCustomerOfItsOwn("the six weeks ahead");
        theMondayItAllStartsOn = windToAMondayThatIsTheFirstOfAMonth();
        app.declareIncomeFor(theCustomer, PAYDAY, "2000.00");
        app.declareABillFor(theCustomer, "Rent", RENT_DAY, "800.00");
        groceries = app.declareACategoryFor(theCustomer, "Groceries");
        app.declareABudgetFor(theCustomer, groceries.categoryId(), "310.00");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_card_is_six_weeks_long_and_begins_on_the_monday_the_current_week_began_on() {
        WeeksAheadView ahead = app.weeksAheadOf(theCustomer);

        assertThat(ahead.from())
                .as("the window opens on the Monday this week began on — the week the customer is "
                        + "standing in, counted the way the streak already counts weeks rather "
                        + "than by a second definition of one")
                .isEqualTo(theMondayItAllStartsOn);
        assertThat(ahead.weeks()).hasSize(6);
        for (int row = 0; row < ahead.weeks().size(); row++) {
            WeekAheadView week = ahead.weeks().get(row);
            assertThat(week.startsOn())
                    .as("row " + row + " begins seven days after the one before it, so the six of "
                            + "them are a run of weeks rather than six windows on the same days")
                    .isEqualTo(theMondayItAllStartsOn.plusWeeks(row));
            assertThat(week.endsOn())
                    .as("and runs Monday to Sunday")
                    .isEqualTo(week.startsOn().plusDays(6));
        }
        assertThat(ahead.until())
                .as("so the card reaches six whole weeks out and stops")
                .isEqualTo(theMondayItAllStartsOn.plusWeeks(6).minusDays(1));
    }

    @Test
    void every_row_says_what_it_leaves_and_the_four_figures_add_up_to_the_cent() {
        WeeksAheadView ahead = app.weeksAheadOf(theCustomer);

        for (WeekAheadView week : ahead.weeks()) {
            assertThat(week.leftOver())
                    .as("what a week leaves is what arrives less what is committed less what the "
                            + "budgets claim, worked out once in the application so that the row "
                            + "on the screen is one a customer can check with a pencil: " + week)
                    .isEqualByComparingTo(week.arriving()
                            .subtract(week.committed())
                            .subtract(week.claimedByBudgets()));
        }
        assertThat(ahead.arriving()).isEqualByComparingTo(theTotalOf(ahead, WeekAheadView::arriving));
        assertThat(ahead.committed())
                .isEqualByComparingTo(theTotalOf(ahead, WeekAheadView::committed));
        assertThat(ahead.claimedByBudgets())
                .as("and the card's own totals are the rows added down, rather than a second sum "
                        + "that could disagree with the rows it is drawn above")
                .isEqualByComparingTo(theTotalOf(ahead, WeekAheadView::claimedByBudgets));
        assertThat(ahead.leftOver()).isEqualByComparingTo(theTotalOf(ahead, WeekAheadView::leftOver));
    }

    @Test
    void the_salary_and_the_rent_land_in_the_rows_that_hold_the_days_they_fall_on() {
        WeeksAheadView ahead = app.weeksAheadOf(theCustomer);

        assertThat(rowHolding(ahead, theMondayItAllStartsOn.withDayOfMonth(PAYDAY)).arriving())
                .as("the salary is in the row that holds the day it lands on, out of the very "
                        + "calendar the 01:00 run walks")
                .isEqualByComparingTo("2000.00");
        assertThat(ahead.weeks().get(0).arriving())
                .as("and nothing is in the row before it, because nothing lands in that week")
                .isEqualByComparingTo("0.00");
        assertThat(rowHolding(ahead, theMondayItAllStartsOn.withDayOfMonth(RENT_DAY)).committed())
                .as("and the rent is in the row that holds the day it falls due on, out of the "
                        + "calendar the 02:30 run walks")
                .isEqualByComparingTo("800.00");
        assertThat(ahead.arriving())
                .as("two paydays fall in six weeks and only one rent does, which is the whole "
                        + "reason six weeks is the window: a month would have shown one of each "
                        + "and hidden the second salary this plan is built on")
                .isEqualByComparingTo("4000.00");
        assertThat(ahead.committed()).isEqualByComparingTo("800.00");
    }

    @Test
    void what_could_be_saved_each_week_is_the_six_weeks_leftover_divided_by_six_and_rounded_down() {
        WeeksAheadView ahead = app.weeksAheadOf(theCustomer);

        assertThat(ahead.couldSaveWeekly().multiply(new BigDecimal("6")))
                .as("six weeks of the offer never comes to more than the six weeks leave, because "
                        + "a customer adopting it would then be locking away money that was never "
                        + "going to be there")
                .isLessThanOrEqualTo(ahead.leftOver());
        assertThat(ahead.couldSaveWeekly().add(new BigDecimal("0.01")).multiply(new BigDecimal("6")))
                .as("and a cent more a week would, which is what makes it the division rather "
                        + "than merely a figure under it — rounded down, so the cent that does not "
                        + "divide stays with the customer")
                .isGreaterThan(ahead.leftOver());
        assertThat(ahead.worthOffering())
                .as("an account whose six weeks leave something has an offer worth making, and "
                        + "the comparison is made once here rather than by every screen that draws "
                        + "the figure")
                .isTrue();
    }

    @Test
    void spending_against_a_budget_takes_exactly_that_much_out_of_what_the_weeks_claim() {
        WeeksAheadView before = app.weeksAheadOf(theCustomer);

        app.spendFor(theCustomer, "A week of groceries", "100.00", groceries.categoryId());

        WeeksAheadView after = app.weeksAheadOf(theCustomer);
        assertThat(after.claimedByBudgets())
                .as("the month the clock is in contributes what is left of its budgets, so a "
                        + "hundred euros already spent is a hundred euros the budget can no longer "
                        + "claim — to the cent, because the whole month lies inside these six "
                        + "weeks and the spread hands a month's figure back to the weeks it "
                        + "touches")
                .isEqualByComparingTo(before.claimedByBudgets().subtract(new BigDecimal("100.00")));
        assertThat(after.leftOver())
                .as("and what the six weeks leave rises by exactly the same hundred, because "
                        + "nothing else on this card moved")
                .isEqualByComparingTo(before.leftOver().add(new BigDecimal("100.00")));
        assertThat(after.arriving()).isEqualByComparingTo(before.arriving());
        assertThat(after.committed())
                .as("a spend is money the customer chose to spend and is no part of what they are "
                        + "committed to, which is the distinction the whole module is built on")
                .isEqualByComparingTo(before.committed());
    }

    @Test
    void a_category_nobody_has_put_a_figure_on_claims_nothing_of_any_week() {
        WeeksAheadView before = app.weeksAheadOf(theCustomer);

        SpendingCategoryView watchedOnly = app.declareACategoryFor(theCustomer, "Odds and ends");
        app.spendFor(theCustomer, "A thing", "30.00", watchedOnly.categoryId());

        assertThat(app.weeksAheadOf(theCustomer).claimedByBudgets())
                .as("no amount of spending exceeds a limit nobody set, and no forecast invents one "
                        + "either: a category its holder is watching rather than policing claims "
                        + "nothing of a week, and saying otherwise would hold them to a figure "
                        + "they never gave")
                .isEqualByComparingTo(before.claimedByBudgets());
    }

    /** The row whose seven days hold that day, named by the day rather than by a number. */
    private static WeekAheadView rowHolding(WeeksAheadView ahead, LocalDate day) {
        return ahead.weeks().stream()
                .filter(week -> !day.isBefore(week.startsOn()) && !day.isAfter(week.endsOn()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(day + " is in none of the six rows, which "
                        + "run from " + ahead.from() + " to " + ahead.until()));
    }

    /** One column added down the rows, which is what the card's own total is asserted against. */
    private static BigDecimal theTotalOf(WeeksAheadView ahead,
                                         Function<WeekAheadView, BigDecimal> column) {
        return ahead.weeks().stream().map(column).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Winds the clock on to the next Monday that is also the first of a month, and answers the day
     * it landed on.
     *
     * <p><strong>Both conditions matter and neither is convenience.</strong> A Monday is what makes
     * the window open on today rather than behind it, so this test can name the row a dated thing
     * falls in by counting days rather than by finding the Monday before it. The first of the month
     * is what puts the whole of the current month inside the six weeks, which is what lets the
     * spend above be asserted to the cent instead of to whatever fraction of a month the window
     * happened to hold.
     *
     * <p>Walked a day at a time through the calendar rather than by adding a span, and wound in one
     * move: the clock only goes forward and only in whole days, exactly as a trainer moves it.
     */
    private static LocalDate windToAMondayThatIsTheFirstOfAMonth() {
        LocalDate day = app.theDateTheClockReads();
        long days = 0;
        while (!(day.getDayOfWeek() == java.time.DayOfWeek.MONDAY && day.getDayOfMonth() == 1)) {
            day = day.plusDays(1);
            days++;
        }
        if (days > 0) {
            app.daysPass(days);
        }
        assertThat(app.theDateTheClockReads())
                .describedAs("the clock has to read the day this whole test counts from")
                .isEqualTo(day);
        return day;
    }
}
