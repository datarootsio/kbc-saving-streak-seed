package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MonthlyIncomeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An income due on the 31st is paid on the last day of February.
 *
 * <p>User story 7 read against the income half of the feature. Clamping is the banking convention
 * and it is the only rule that does not skip February outright: a customer paid on the last day of
 * the month is paid twelve times a year, and an application that treated "the 31st" literally would
 * pay them seven and leave them to work out why.
 *
 * <p>The clock is wound to the day before the last day of the next February, so that the claim can
 * be made twice over: the account says the money is coming on the 28th (or the 29th) rather than on
 * a 31st February that does not exist, and a run made on the day before credits nothing while a run
 * made on the day itself credits the salary. One of those alone would be satisfied by a job that
 * paid on any day it happened to be run.
 *
 * <p>The last day is asked of the calendar rather than written down, because it is the 29th in a
 * leap year and this test has to be true in one.
 */
class AnIncomeDueOnTheThirtyFirstIsPaidOnTheLastDayOfFebruaryApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "creditMonthlyIncome";
    private static final String A_SALARY = "1500.00";
    private static final int THE_THIRTY_FIRST = 31;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseFebruaryThisTestReaches() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-income-clamped-to-february"));
        theCustomer = app.aCustomerOfItsOwn("income clamped to february");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_thirty_first_falls_on_the_last_day_of_february() {
        LocalDate lastDayOfFebruary = theNextFebruaryAfter(app.theDateTheClockReads()).atEndOfMonth();
        LocalDate theDayBefore = lastDayOfFebruary.minusDays(1);
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), theDayBefore));
        assertThat(app.theDateTheClockReads()).isEqualTo(theDayBefore);

        MonthlyIncomeView declared = app.declareIncomeFor(theCustomer, THE_THIRTY_FIRST, A_SALARY);

        assertThat(declared.nextPayday())
                .as("February has no 31st, and the money arrives on the last day it does have")
                .isEqualTo(lastDayOfFebruary);

        BigDecimal before = app.currentAccountBalanceOf(theCustomer);
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("the day before is not the payday, however short the month is")
                .isEqualByComparingTo(before);

        app.daysPass(1);
        assertThat(app.theDateTheClockReads()).isEqualTo(lastDayOfFebruary);
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("and February is paid rather than skipped")
                .isEqualByComparingTo(before.add(new BigDecimal(A_SALARY)));
    }

    /**
     * The first February that starts after the month the given day falls in, so that the wind
     * forward is always a move the clock will accept — it only goes forwards, and it refuses a move
     * of no days at all.
     */
    private static YearMonth theNextFebruaryAfter(LocalDate today) {
        YearMonth february = YearMonth.of(today.getYear(), 2);
        return february.isAfter(YearMonth.from(today)) ? february : february.plusYears(1);
    }
}
