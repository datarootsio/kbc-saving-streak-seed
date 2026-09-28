package io.dataroots.savingstreak.monthlybudgets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BillOccurrenceView;
import io.dataroots.savingstreak.support.CategorySpendingView;
import io.dataroots.savingstreak.support.MonthOfSpendingView;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a household is committed to and what it chooses are described in the same words and told
 * apart in the same month: one category holds a rent that went out on a standing instruction and a
 * plumber the customer decided to call, and the read shows each of them separately.
 *
 * <p>User stories 21 and 22. This is the slice where the two halves of the feature meet. A category
 * whose month said only "you spent nine hundred and fifty euros" would be no help at all: nine
 * hundred of it was a rent the customer agreed to a year ago and fifty was a decision they made last
 * Tuesday, and only the second is something they can do anything about. The split is the whole
 * reason the read is worth reading.
 *
 * <p><strong>Committed and discretionary are told apart by the movement and never by the
 * category.</strong> There is no "bills category" and no "spending category" — there are categories,
 * and a bill occurrence and a spend part can land in the same one on the same day.
 *
 * <p><strong>A date that could not be paid contributes nothing and is read anyway.</strong> Nothing
 * was taken, so nothing is counted; the row is walked all the same, so that "what a month cost" is
 * one rule with one exception written down in one place instead of a silence a later reader would
 * have to reconstruct. This test reads the bill's own history beside the category's month, because
 * that is the only way to see from outside that a date really was presented.
 *
 * <p>An application of its own, because a bill occurrence only exists once a nightly run has
 * presented one — and a run needs a clock this test may wind, which cannot be undone.
 *
 * <p>One test, because the clock only goes forward and the narrative is a sequence of months. The
 * claim is asserted after every step instead.
 */
class ACategoryHoldsBothACommittedBillAndAChosenSpendApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-committed-and-chosen"));
        theCustomer = app.aCustomerOfItsOwn("committed and chosen");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_rent_taken_and_a_plumber_paid_land_in_one_category_and_are_reported_apart() {
        SpendingCategoryView housing = app.declareACategoryFor(theCustomer, "Housing");
        SpendingCategoryView groceries = app.declareACategoryFor(theCustomer, "Groceries");
        SpendingCategoryView transport = app.declareACategoryFor(theCustomer, "Transport");
        RecurringBillView rent = app.declareABillFor(theCustomer, "Rent", 1, "900.00");
        app.putBillInCategoryFor(theCustomer, rent.billId(), housing.categoryId());
        app.declareABudgetFor(theCustomer, housing.categoryId(), "1000.00");
        app.declareABudgetFor(theCustomer, groceries.categoryId(), "300.00");
        app.declareABudgetFor(theCustomer, transport.categoryId(), "2500.00");

        YearMonth theMonthItStartedIn = YearMonth.from(app.theDateTheClockReads());
        app.spendFor(theCustomer, "Delhaize", "120.00", groceries.categoryId());

        MonthOfSpendingView thisMonth = app.spendingThisMonthOf(theCustomer);
        assertThat(thisMonth.theCategory(housing.categoryId()).committed())
                .as("the rent has not fallen due yet, and a budget counts money that left rather "
                        + "than money that is going to")
                .isEqualByComparingTo("0.00");
        assertThat(thisMonth.theCategory(groceries.categoryId()).discretionary())
                .isEqualByComparingTo("120.00");

        // The first of next month, where the rent falls due. The clock moves in whole days, so the
        // days are counted through the calendar rather than guessed at — a fixed thirty would land
        // in the wrong month twice a year.
        windTo(theFirstOf(theMonthItStartedIn.plusMonths(1)));
        app.runJob(THE_BILLS_JOB);
        YearMonth theMonthTheRentWasTakenIn = YearMonth.from(app.theDateTheClockReads());
        app.spendFor(theCustomer, "Plumber", "50.00", housing.categoryId());

        MonthOfSpendingView rentMonth = app.spendingThisMonthOf(theCustomer);
        CategorySpendingView itsHousing = rentMonth.theCategory(housing.categoryId());
        assertThat(itsHousing.committed())
                .as("money that left on a standing instruction the customer made once")
                .isEqualByComparingTo("900.00");
        assertThat(itsHousing.discretionary())
                .as("and money they chose to spend last Tuesday, in the same category and told "
                        + "apart by the movement rather than by the word")
                .isEqualByComparingTo("50.00");
        assertThat(itsHousing.spent())
                .as("the two added, to the cent, so a customer can check the row with a pencil")
                .isEqualByComparingTo("950.00");
        assertThat(itsHousing.budgeted()).isEqualByComparingTo("1000.00");
        assertThat(itsHousing.left())
                .as("which is what makes the figure worth reading: fifty euros of room, and only "
                        + "the fifty already spent was ever theirs to do anything about")
                .isEqualByComparingTo("50.00");

        assertThat(app.spendingInMonthOf(theCustomer, theMonthItStartedIn.toString())
                .theCategory(groceries.categoryId()).discretionary())
                .as("and the month before it is exactly as it was: a spend counts in the month it "
                        + "was recorded in, and nothing moved it")
                .isEqualByComparingTo("120.00");
        assertThat(rentMonth.theCategory(groceries.categoryId()).discretionary())
                .as("while this month has none of it, because the groceries were last month's")
                .isEqualByComparingTo("0.00");

        // A bill this account plainly cannot cover, on the day after the one the clock is standing
        // on, so that the next run presents it and takes nothing.
        LocalDate tomorrow = app.theDateTheClockReads().plusDays(1);
        RecurringBillView seasonTicket = app.declareABillFor(theCustomer, "Season ticket",
                tomorrow.getDayOfMonth(), "5000.00");
        app.putBillInCategoryFor(theCustomer, seasonTicket.billId(), transport.categoryId());
        BigDecimal before = app.currentAccountBalanceOf(theCustomer);

        app.daysPass(1);
        app.runJob(THE_BILLS_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("nothing was taken: there is no partial payment in this application, so a bill "
                        + "the account cannot cover leaves the balance exactly where it was")
                .isEqualByComparingTo(before);
        assertThat(app.historyOfBill(theCustomer, seasonTicket.billId()))
                .as("but the date really was presented, which is the half of the record a balance "
                        + "that did not move can never show on its own")
                .extracting(BillOccurrenceView::outcome)
                .containsExactly("UNPAID");
        assertThat(app.spendingThisMonthOf(theCustomer).theCategory(transport.categoryId()))
                .as("and the category it is filed in reports the whole of what it cost, which is "
                        + "nothing — the row is read either way so that the arithmetic has one "
                        + "place and one exception rather than a silence")
                .satisfies(row -> {
                    assertThat(row.committed()).isEqualByComparingTo("0.00");
                    assertThat(row.spent()).isEqualByComparingTo("0.00");
                    assertThat(row.budgeted()).isEqualByComparingTo("2500.00");
                    assertThat(row.left()).isEqualByComparingTo("2500.00");
                    assertThat(row.overspent()).isFalse();
                });
        assertThat(YearMonth.from(app.theDateTheClockReads()))
                .as("the whole of the second half of this test happened inside one month, so the "
                        + "figures above are about the month the rent was taken in")
                .isEqualTo(theMonthTheRentWasTakenIn);
    }

    /** The first day of a month, which is where a bill declared for the first falls due. */
    private static LocalDate theFirstOf(YearMonth month) {
        return month.atDay(1);
    }

    /**
     * Moves the clock to a day, counted through the calendar rather than by a fixed span. The clock
     * only goes forward and only in whole days, which is exactly the way a trainer moves it.
     */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days).describedAs("the clock only goes forward").isPositive();
        app.daysPass(days);
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
    }
}
