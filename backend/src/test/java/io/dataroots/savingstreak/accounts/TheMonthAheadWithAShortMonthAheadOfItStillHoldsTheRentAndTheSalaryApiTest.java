package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MonthAheadView;
import io.dataroots.savingstreak.support.MonthlyIncomeView;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A month ahead read on the 31st, with a shorter month in front of it, still holds the rent and the
 * salary that fall on the 30th.
 *
 * <p><strong>The defect this ticket was sent back for, asserted from the outside.</strong> The
 * window was {@code today + 1 month - 1 day}, and adding a month to the 31st of March lands on the
 * 30th of April before the day is taken off it: a window closing on the 29th, a day short for every
 * day the next month is short. A bill on the 30th then contributed nothing at all to what the month
 * had to cover — its March date is already behind the bill's cursor and its April date fell one day
 * past the top — and neither did a salary on the 30th, which left the card drawing the red
 * "this month asks for more than the account will hold" warning on a month that was in fact a
 * salary better off. That is the one figure the whole feature exists to put in front of somebody,
 * giving the wrong advice.
 *
 * <p>Stood on the 31st deliberately, where {@link TheMonthAheadSaysWhatThisMonthHasToCoverApiTest}
 * stands on the 3rd and the clamp never fires. The two are the same claims read from the two ends of
 * a month.
 *
 * <p><strong>And the run is made to agree.</strong> Asserting the figure alone would leave the
 * window right and the money still moving somewhere else, so the clock is wound to the last day the
 * window named and the two jobs are run: the salary has to land and the rent has to leave, on that
 * very date. A forecast and a run that disagree about which dates a bill falls on is the failure
 * this feature is under a promise about.
 */
class TheMonthAheadWithAShortMonthAheadOfItStillHoldsTheRentAndTheSalaryApiTest
        extends ApiIntegrationTest {

    private static final String THE_INCOME_JOB = "creditMonthlyIncome";
    private static final String THE_BILLS_JOB = "takeBillsDue";

    /** The day the window opens on, which is the day the old arithmetic lost a day from. */
    private static final int THE_THIRTY_FIRST = 31;

    /**
     * The day the rent and the salary fall on: one the month ahead of this one has, and the old
     * window closed one day before.
     */
    private static final int THE_THIRTIETH = 30;

    private static final String THE_SALARY = "1750.00";
    private static final String THE_RENT = "100.00";

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationStandingOnTheThirtyFirstOfAMonthFollowedByAShorterOne() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-month-ahead-short-month"));
        theCustomer = app.aCustomerOfItsOwn("the short month ahead");
        windToTheNextThirtyFirstFollowedByAShorterMonth();
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_window_is_a_whole_month_and_nothing_falling_on_the_thirtieth_drops_out_of_it() {
        LocalDate theThirtyFirst = app.theDateTheClockReads();
        LocalDate theLastDayOfTheShorterMonth = YearMonth.from(theThirtyFirst).plusMonths(1).atEndOfMonth();
        BigDecimal held = app.currentAccountBalanceOf(theCustomer);
        MonthlyIncomeView income = app.declareIncomeFor(theCustomer, THE_THIRTIETH, THE_SALARY);
        RecurringBillView rent = app.declareABillFor(theCustomer, "Rent", THE_THIRTIETH, THE_RENT);

        MonthAheadView month = app.theCurrentAccountOf(theCustomer).monthAhead();

        assertThat(month.from()).isEqualTo(theThirtyFirst);
        assertThat(month.until())
                .as("the day before the 31st of a month that has no 31st is that month's last day; "
                        + "taking a day off the clamped date instead makes a window shorter than "
                        + "the month it claims to be")
                .isEqualTo(theLastDayOfTheShorterMonth);
        assertThat(month.incomeDue())
                .as("the salary lands on the last day of the shorter month, which the page says "
                        + "one card higher up as the next payday — a month ahead quoting nothing "
                        + "due in would contradict the screen it is drawn on")
                .isEqualByComparingTo(new BigDecimal(THE_SALARY));
        assertThat(income.nextPayday())
                .as("and it is the very date the declaration itself names")
                .isEqualTo(theLastDayOfTheShorterMonth);
        assertThat(month.billsDue())
                .as("and the rent falls on the same day, and is what the month has to cover")
                .isEqualByComparingTo(new BigDecimal(THE_RENT));
        assertThat(month.arrearsOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(month.balance()).isEqualByComparingTo(held);
        assertThat(month.leavesYou())
                .as("what the month leaves is the balance plus what is due in minus what is due "
                        + "out, to the cent: " + month)
                .isEqualByComparingTo(held.add(new BigDecimal(THE_SALARY))
                        .subtract(new BigDecimal(THE_RENT)));
        assertThat(month.leavesYou())
                .as("and it is nothing like a shortfall, so the card draws no warning — the whole "
                        + "damage of the short window was a red warning on a month that was fine")
                .isPositive();

        app.daysPass(ChronoUnit.DAYS.between(theThirtyFirst, theLastDayOfTheShorterMonth));
        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_BILLS_JOB);

        assertThat(app.historyOfBill(theCustomer, rent.billId()))
                .as("the run takes the date the forecast showed, and no other")
                .singleElement()
                .satisfies(presented -> {
                    assertThat(presented.dueOn()).isEqualTo(theLastDayOfTheShorterMonth);
                    assertThat(presented.outcome()).isEqualTo("PAID");
                });
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("and the salary the forecast counted really did land, so the balance moved by "
                        + "exactly the two figures the card showed")
                .isEqualByComparingTo(held.add(new BigDecimal(THE_SALARY))
                        .subtract(new BigDecimal(THE_RENT)));
    }

    /**
     * Winds to the next 31st of a month the following month is shorter than — March, May, August,
     * October, January or any of the others whose successor has fewer days. December is no use here:
     * January has a 31st of its own, so the clamp never fires and the window loses nothing.
     *
     * <p>Always a move forwards, however far into a month the run happens to start: the clock only
     * goes one way and refuses a move of no days at all.
     */
    private static void windToTheNextThirtyFirstFollowedByAShorterMonth() {
        LocalDate today = app.theDateTheClockReads();
        LocalDate theThirtyFirst = today.plusDays(1);
        while (theThirtyFirst.getDayOfMonth() != THE_THIRTY_FIRST
                || YearMonth.from(theThirtyFirst).plusMonths(1).lengthOfMonth()
                        >= THE_THIRTY_FIRST) {
            theThirtyFirst = theThirtyFirst.plusDays(1);
        }
        app.daysPass(ChronoUnit.DAYS.between(today, theThirtyFirst));
        assertThat(app.theDateTheClockReads()).isEqualTo(theThirtyFirst);
    }
}
