package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A salary credited <em>late</em> — after a resume, for a payday that fell <em>inside</em> the
 * pause — does not fire the rule.
 *
 * <p><strong>The one case the cursor cannot answer, and the reason a pause is a record rather than a
 * moved cursor.</strong> A payday rule's days are not a calendar fact: they are the days a salary
 * actually landed, read out of what the accounts module credited and asked for by <em>when it was
 * credited</em>, so that a salary credited after the rules job had already gone past its day is
 * still found. That is right, and ticket 04 built it deliberately — and it is exactly what makes
 * this case possible. Resuming moves the rule's cursor to the resume moment, so a salary credited
 * after that arrives in front of the cursor however old the day it was due for. Nothing about the
 * cursor can say that day was one its customer had told the application to skip.
 *
 * <p>The pause window can, because it is written down: the moment the pause began and the moment it
 * ended, kept after the resume precisely so that a day arriving late can still be placed inside it.
 * Without it this test finds a transfer out of a customer's account on a day they had explicitly
 * paused through — money, not bookkeeping.
 *
 * <p><strong>The second month is the other direction.</strong> A rule that excluded the late salary
 * by simply having stopped working afterwards would pass the first half and be broken; the same rule
 * fires, once, on the following payday, with the jobs run in the order the night runs them in.
 *
 * <p><strong>A fixed amount rather than a sweep</strong>, for the reason
 * {@code RulesAsSomebodyWouldTypeThem.aFixedAmountOnPayday} gives: a sweep that fired when it should
 * not would find the account already at its floor on the second look and the defect would hide
 * behind balances that looked right.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ASalaryCreditedLateForADayInsideAPauseIsStillNeverMadeUpApiTest extends ApiIntegrationTest {

    private static final String THE_RULES_JOB = "fireSavingRulesDue";
    private static final String THE_INCOME_JOB = "creditMonthlyIncome";

    private static final String THE_SALARY = "1200.00";
    private static final String WHAT_THE_RULE_MOVES = "300.00";

    /** The latest day of the month every month has, so that nothing here is about the clamp. */
    private static final int A_DAY_EVERY_MONTH_HAS = 28;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseSalaryThisTestCreditsLate() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-late-salary-inside-a-pause"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_payday_that_fell_inside_the_pause_fires_nothing_however_late_its_salary_lands() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItAllBegan = app.theDateTheClockReads();
        int payday = Math.min(theDayItAllBegan.getDayOfMonth(), A_DAY_EVERY_MONTH_HAS);
        BigDecimal savedBefore = app.balancesOf(savingsAccount).moneyBalance();
        BigDecimal heldBefore = app.currentAccountBalanceOf(ANKE);

        app.declareIncomeFor(ANKE, payday, THE_SALARY);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountOnPayday(app.currentAccountOf(ANKE),
                        "Three hundred out of every salary", WHAT_THE_RULE_MOVES));
        app.pauseRule(savingsAccount, rule.id());

        // A payday falls while the rule is stopped, and nobody runs anything on it.
        LocalDate thePaydayShePausedThrough = theDayItAllBegan.plusMonths(1).withDayOfMonth(payday);
        windTo(thePaydayShePausedThrough);
        app.runJob(THE_RULES_JOB);
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("a paused rule fires nothing, and nothing has been credited yet either")
                .isEmpty();

        // She starts it again the day after.
        app.daysPass(1);
        app.resumeRule(savingsAccount, rule.id());

        // And only now does the salary for the day inside the pause land — after the resume, which
        // puts it in front of the rule's cursor with nothing but the pause window to exclude it.
        app.runJob(THE_INCOME_JOB);
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("the salary for the payday she paused through is credited late, after she had "
                        + "already started the rule again")
                .isEqualByComparingTo(heldBefore.add(new BigDecimal(THE_SALARY)));

        app.runJob(THE_RULES_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("and it still fires nothing: the day that salary was for fell inside the pause, "
                        + "which is her own instruction and is never made up — the cursor cannot "
                        + "say that, because a late salary is deliberately allowed in front of it, "
                        + "so the recorded window is what says it")
                .isEmpty();
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("her salary is hers: not one euro of it was swept into savings on a day she "
                        + "had paused through")
                .isEqualByComparingTo(heldBefore.add(new BigDecimal(THE_SALARY)));
        assertThat(app.balancesOf(savingsAccount).moneyBalance()).isEqualByComparingTo(savedBefore);
        assertThat(app.depositsInto(savingsAccount))
                .as("counted off the account rather than off the rule's own record")
                .isEmpty();

        // The following month, with the jobs in the order the night actually runs them in. The rule
        // is live and has been all along, and it fires once.
        LocalDate theNextPayday = thePaydayShePausedThrough.plusMonths(1);
        windTo(theNextPayday);
        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_RULES_JOB);

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history)
                .as("the rule she started again is working: a pause that had quietly broken it for "
                        + "good would pass everything above and be the worse defect of the two")
                .hasSize(1);
        assertThat(history.get(0).dueOn())
                .as("and on the payday after the pause, never on the one inside it")
                .isEqualTo(theNextPayday);
        assertThat(history.get(0).outcome()).isEqualTo("MOVED");
        assertThat(history.get(0).amount())
                .isEqualByComparingTo(new BigDecimal(WHAT_THE_RULE_MOVES));
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("two salaries in and one transfer out: the paused month cost her nothing and "
                        + "gave her nothing")
                .isEqualByComparingTo(heldBefore
                        .add(new BigDecimal(THE_SALARY).multiply(BigDecimal.valueOf(2)))
                        .subtract(new BigDecimal(WHAT_THE_RULE_MOVES)));
    }

    /** The clock forward to that day, through the endpoint a trainer would use. */
    private static void windTo(LocalDate day) {
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), day));
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
    }
}
