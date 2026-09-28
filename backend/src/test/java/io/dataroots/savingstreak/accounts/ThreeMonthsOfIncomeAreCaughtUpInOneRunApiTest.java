package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MonthlyIncomeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A clock wound three months forward and one run of the job credits three months of income, oldest
 * first.
 *
 * <p>User story 18. Catching up is not a nicety here, it is the only path there is:
 * {@code MovableClock} moves in whole calendar days and a cron expression never fires for the days
 * it skipped, so on a wound clock every payday that is ever credited is credited by catch-up. A job
 * that only ever asked "is today a payday" would find nothing to do on a clock three months on and
 * would say so convincingly.
 *
 * <p>The order is asserted as well as the total, and it is the half a balance cannot say: three
 * salaries arriving in one run add up the same however they were dealt with, and "oldest first" is
 * the promise that makes the record reconcilable against a bank statement. There is no endpoint that
 * reports it yet — the history that will is a later ticket's — so it is read through the escape
 * hatch {@link AnApplicationWithAClockToMove#theApplicationsOwn} documents, from rows this test put
 * there by winding the clock and running the job over HTTP.
 *
 * <p>The fifteenth, because this test is about three months and not about what the 31st means in
 * February, which is a sentence of its own.
 */
class ThreeMonthsOfIncomeAreCaughtUpInOneRunApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "creditMonthlyIncome";
    private static final String A_SALARY = "1000.00";
    private static final int THE_FIFTEENTH = 15;
    private static final int HOW_MANY_MONTHS_ARE_MISSED = 3;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-income-caught-up"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void three_missed_paydays_are_credited_by_one_run_oldest_first() {
        MonthlyIncomeView declared = app.declareIncomeFor(ANKE, THE_FIFTEENTH, A_SALARY);
        LocalDate firstPayday = declared.nextPayday();
        LocalDate secondPayday = theMonthAfter(firstPayday, 1);
        LocalDate thirdPayday = theMonthAfter(firstPayday, 2);
        BigDecimal before = app.currentAccountBalanceOf(ANKE);

        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), thirdPayday));
        assertThat(app.theDateTheClockReads())
                .as("three paydays have now fallen with nothing running to notice them")
                .isEqualTo(thirdPayday);

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("winding three months forward and running the job once produces three salaries "
                        + "and not one")
                .isEqualByComparingTo(
                        before.add(new BigDecimal(A_SALARY).multiply(BigDecimal.valueOf(
                                HOW_MANY_MONTHS_ARE_MISSED))));

        List<IncomePaid> record = app.theApplicationsOwn(IncomePaidRepository.class)
                .findAllByCurrentAccountIdOrderByIdAsc(app.currentAccountOf(ANKE));
        assertThat(record.stream().map(IncomePaid::getDueOn))
                .as("written in the order the days actually fell, which is the only order a "
                        + "history can be reconciled in")
                .containsExactly(firstPayday, secondPayday, thirdPayday);
    }

    /**
     * The payday that many months after this one, clamped the way the application clamps it. The
     * fifteenth exists in every month, so the clamp never bites here — it is written out all the
     * same, so that a later reader changing the day above does not quietly get a wrong answer.
     */
    private static LocalDate theMonthAfter(LocalDate payday, int months) {
        YearMonth month = YearMonth.from(payday).plusMonths(months);
        return month.atDay(Math.min(THE_FIFTEENTH, month.lengthOfMonth()));
    }
}
