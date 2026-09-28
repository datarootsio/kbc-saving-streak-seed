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
 * An income declared today is not credited for the paydays that fell before anybody declared it.
 *
 * <p>User story 42 read against the income half of the feature. The catch-up is deliberately
 * unbounded in the direction of the past — it has to be, or downtime would cost a customer a month
 * of saving — and the only thing standing between "make up what was missed" and "invoice the
 * customer for the last year" is where the cursor starts. It starts at the declaration, and this is
 * the test of that.
 *
 * <p>The clock is wound more than a year on <em>before</em> anything is declared, so that there is
 * genuinely a year of paydays behind the declaration for a wrong answer to find. On a fresh
 * application standing at today, a cursor that started at the beginning of time would look almost
 * the same as one that started at the declaration.
 *
 * <p>The first of the month, because its day has always already begun: whatever day the clock
 * happens to read, this month's first is behind the declaration, so a run made straight afterwards
 * has a payday it could wrongly reach for.
 */
class AnIncomeDeclaredTodayIsNotAYearOfBackPayApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "creditMonthlyIncome";
    private static final String A_SALARY = "1800.00";
    private static final int THE_FIRST_OF_THE_MONTH = 1;

    /** Comfortably more than a year, so there is a year of first-of-the-months to be wrong about. */
    private static final int DAYS_WELL_PAST_A_YEAR = 400;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWithAYearBehindIt() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-income-is-not-back-pay"));
        theCustomer = app.aCustomerOfItsOwn("income is not back pay");
        app.daysPass(DAYS_WELL_PAST_A_YEAR);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_paydays_that_fell_before_the_declaration_are_never_credited() {
        LocalDate today = app.theDateTheClockReads();

        MonthlyIncomeView declared = app.declareIncomeFor(theCustomer, THE_FIRST_OF_THE_MONTH, A_SALARY);

        assertThat(declared.nextPayday())
                .as("this month's first has already begun, so what is coming is next month's")
                .isEqualTo(today.withDayOfMonth(1).plusMonths(1));

        BigDecimal beforeTheJobRan = app.currentAccountBalanceOf(theCustomer);
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("a declaration made today is a promise about the future, not a year of back pay")
                .isEqualByComparingTo(beforeTheJobRan);

        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), declared.nextPayday()));
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("and the first payday on the far side of the declaration is paid, once — the "
                        + "cursor bounds the catch-up rather than switching it off")
                .isEqualByComparingTo(beforeTheJobRan.add(new BigDecimal(A_SALARY)));
    }
}
