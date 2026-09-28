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

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The money arrives on the day it was declared for, and arrives once however many times the job is
 * run.
 *
 * <p>User stories 15 and 40. This is the sentence the conditional half of the whole feature stands
 * on: a current account's balance in this application only ever went down, so a rule that swept
 * everything above a floor would fire once and be inert for ever. Money arriving is what makes
 * automation demonstrable for a year rather than once.
 *
 * <p>Running it twice is asserted in the same test rather than in one of its own, because the second
 * run is only meaningful against the first: a trainer repeating a demonstration must not double
 * every figure in it, and the claim is about the pair of runs and not about either alone.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test
 * winds the clock, and the file the rest of the run shares is not one to leave wound forward.
 */
class IncomeLandsOnThePaydayItIsDueApiTest extends ApiIntegrationTest {

    /** The name a trainer types into the development jobs endpoint. */
    private static final String THE_JOB = "creditMonthlyIncome";

    private static final String A_SALARY = "2500.00";

    /**
     * A day every month has, so that this test is about the payday arriving rather than about what
     * the 31st means in February — which is a sentence of its own.
     */
    private static final int THE_FIFTEENTH = 15;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMonthThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-income-lands-on-payday"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_salary_lands_on_its_payday_and_lands_once_however_often_the_job_is_run() {
        MonthlyIncomeView declared = app.declareIncomeFor(ANKE, THE_FIFTEENTH, A_SALARY);
        BigDecimal beforeAnyPayday = app.currentAccountBalanceOf(ANKE);

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("the payday has not arrived yet, and a job that paid early would be paying for "
                        + "a month nobody has worked")
                .isEqualByComparingTo(beforeAnyPayday);

        LocalDate today = app.theDateTheClockReads();
        app.daysPass(ChronoUnit.DAYS.between(today, declared.nextPayday()));
        assertThat(app.theDateTheClockReads())
                .as("the clock now reads the day the account said the money was coming")
                .isEqualTo(declared.nextPayday());

        app.runJob(THE_JOB);

        BigDecimal afterOneRun = app.currentAccountBalanceOf(ANKE);
        assertThat(afterOneRun)
                .as("running the job on the day an income is due credits the amount")
                .isEqualByComparingTo(beforeAnyPayday.add(new BigDecimal(A_SALARY)));

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("a demonstration a trainer repeats must not double every figure in it: one row "
                        + "per account per day due, unique in the database, is what makes the "
                        + "second run pay nothing")
                .isEqualByComparingTo(afterOneRun);
    }

    @Test
    void an_account_nobody_declared_an_income_against_is_credited_nothing() {
        // Bram has never declared anything, and this test never declares anything for him. He is in
        // the same application as the account above, so the job walks a night in which one account
        // is paid and one is not — which is the claim: nothing is invented for an account whose
        // holder said nothing.
        BigDecimal before = app.currentAccountBalanceOf(BRAM);

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(BRAM))
                .as("a default salary would be the application putting words in a customer's mouth "
                        + "and then paying them")
                .isEqualByComparingTo(before);
    }

    @Test
    void a_withdrawn_declaration_is_credited_nothing_afterwards() {
        app.declareIncomeFor(ANKE, THE_FIFTEENTH, A_SALARY);
        app.withdrawTheIncomeOf(ANKE);
        BigDecimal before = app.currentAccountBalanceOf(ANKE);

        // Far enough on that a standing declaration would certainly have been paid at least once.
        app.daysPass(40);
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("there is no declaration left for the job to walk")
                .isEqualByComparingTo(before);
    }
}
