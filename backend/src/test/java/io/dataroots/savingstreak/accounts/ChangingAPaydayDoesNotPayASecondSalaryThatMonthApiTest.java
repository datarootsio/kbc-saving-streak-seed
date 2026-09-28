package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MonthlyIncomeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer who corrects the day they are paid on is paid once that month, whichever way the day
 * moved.
 *
 * <p>User story 17, and the half of it that costs money if it is wrong. Changing a declaration is a
 * correction, not a second salary — but a month is not a day, and the two are easy to confuse. An
 * account paid on the 5th whose holder then says they are paid on the 25th has a 25th that no cursor
 * sitting at the 6th excludes and no record under that day denies; before this test existed, the job
 * credited it and the customer was paid twice in January for having told the application the truth.
 * The rule is one salary a calendar month, and it is kept by asking the record which <em>months</em>
 * have been paid rather than which days.
 *
 * <p><strong>Both directions, and that is the whole point of the class.</strong> Moving a payday
 * earlier was already safe — the new day falls behind a cursor that has passed the old one — and
 * that is exactly why the defect survived a suite and a review: every test and every walk over the
 * API happened to move the day the safe way. A test that proves only the direction that worked is
 * the test that let this through.
 *
 * <p>Two customers rather than one, so that neither direction depends on the order JUnit runs the
 * two methods in, and each method winds the clock to the first of a fresh month before it starts —
 * a payday demonstrated inside one named month is the only way to say "twice in one month" at all.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test
 * winds the clock, and the file the rest of the run shares is not one to leave wound forward.
 */
class ChangingAPaydayDoesNotPayASecondSalaryThatMonthApiTest extends ApiIntegrationTest {

    /** The name a trainer types into the development jobs endpoint. */
    private static final String THE_JOB = "creditMonthlyIncome";

    private static final String A_SALARY = "2500.00";

    /** Early in the month, and a day every month has. */
    private static final int THE_FIFTH = 5;

    /** Late in the month, and a day every month has — so this test is not about February's clamp. */
    private static final int THE_TWENTY_FIFTH = 25;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;
    private static String somebodyElse;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-changing-a-payday"));
        theCustomer = app.aCustomerOfItsOwn("changing a payday");
        somebodyElse = app.aCustomerOfItsOwn("changing a payday somebody else");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void moving_a_payday_later_into_a_month_already_paid_does_not_pay_a_second_salary() {
        LocalDate theFirstOfTheMonth = theClockWoundToTheFirstOfANewMonth();
        app.declareIncomeFor(theCustomer, THE_FIFTH, A_SALARY);
        BigDecimal beforeAnySalary = app.currentAccountBalanceOf(theCustomer);

        app.daysPass(6 - 1);
        app.runJob(THE_JOB);
        BigDecimal afterTheFifth = app.currentAccountBalanceOf(theCustomer);
        assertThat(afterTheFifth)
                .as("the salary this test is about to make sure is not paid a second time")
                .isEqualByComparingTo(beforeAnySalary.add(new BigDecimal(A_SALARY)));

        // The correction, made on the 6th: the customer is paid later in the month than they said,
        // in a month whose salary has already landed.
        MonthlyIncomeView corrected = app.declareIncomeFor(theCustomer, THE_TWENTY_FIFTH, A_SALARY);

        assertThat(corrected.nextPayday())
                .as("the day the account tells its holder to expect money has to be the day the job "
                        + "will credit, and the job will not pay this month twice")
                .isEqualTo(theFirstOfTheMonth.plusMonths(1).withDayOfMonth(THE_TWENTY_FIFTH));

        app.daysPass(26 - 6);
        assertThat(app.theDateTheClockReads())
                .as("the day after the corrected payday, in the month that has already been paid")
                .isEqualTo(theFirstOfTheMonth.withDayOfMonth(26));
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("correcting the day you are paid on is not a second salary: the 5th and the "
                        + "25th are two different days and one single January")
                .isEqualByComparingTo(afterTheFifth);

        windTo(theFirstOfTheMonth.plusMonths(1).withDayOfMonth(26));
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("and the correction does take: next month's salary lands on the new day, once, "
                        + "because a month skipped is not a payday lost for ever")
                .isEqualByComparingTo(afterTheFifth.add(new BigDecimal(A_SALARY)));
    }

    @Test
    void moving_a_payday_earlier_into_a_month_already_paid_does_not_pay_a_second_salary() {
        LocalDate theFirstOfTheMonth = theClockWoundToTheFirstOfANewMonth();
        app.declareIncomeFor(somebodyElse, THE_TWENTY_FIFTH, A_SALARY);
        BigDecimal beforeAnySalary = app.currentAccountBalanceOf(somebodyElse);

        app.daysPass(26 - 1);
        app.runJob(THE_JOB);
        BigDecimal afterTheTwentyFifth = app.currentAccountBalanceOf(somebodyElse);
        assertThat(afterTheTwentyFifth)
                .as("the salary this test is about to make sure is not paid a second time")
                .isEqualByComparingTo(beforeAnySalary.add(new BigDecimal(A_SALARY)));

        // The mirror correction, made on the 26th: the customer is paid earlier in the month than
        // they said, in a month whose salary has already landed.
        MonthlyIncomeView corrected = app.declareIncomeFor(somebodyElse, THE_FIFTH, A_SALARY);

        assertThat(corrected.nextPayday())
                .isEqualTo(theFirstOfTheMonth.plusMonths(1).withDayOfMonth(THE_FIFTH));

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(somebodyElse))
                .as("the 5th of this month is behind the money that already landed on the 25th, "
                        + "and a correction never reaches backwards for a month already paid")
                .isEqualByComparingTo(afterTheTwentyFifth);

        windTo(theFirstOfTheMonth.plusMonths(1).withDayOfMonth(6));
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(somebodyElse))
                .as("and next month's salary lands on the new day, once")
                .isEqualByComparingTo(afterTheTwentyFifth.add(new BigDecimal(A_SALARY)));
    }

    /**
     * Winds the clock to the first of the month after the one it is reading, and says which day that
     * is.
     *
     * <p>Both tests in this class start here, so that each one owns a whole named month whatever the
     * day the run happens on and whatever the other test left the clock reading. A claim about being
     * paid twice "in one month" cannot be made from a stretch of days that straddles two.
     */
    private static LocalDate theClockWoundToTheFirstOfANewMonth() {
        LocalDate theFirst = app.theDateTheClockReads().plusMonths(1).withDayOfMonth(1);
        windTo(theFirst);
        return theFirst;
    }

    /** The clock moved on to a named day, through the endpoint a trainer would use. */
    private static void windTo(LocalDate day) {
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), day));
        assertThat(app.theDateTheClockReads())
                .as("a test that asserts about a payday has to be standing on the day it means")
                .isEqualTo(day);
    }
}
