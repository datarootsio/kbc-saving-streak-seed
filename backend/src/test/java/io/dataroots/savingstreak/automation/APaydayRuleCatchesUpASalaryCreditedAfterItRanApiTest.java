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
 * A payday rule catches up a salary that was credited after the rules job had already run past the
 * day it was due — the jobs run in the wrong order, and the month is not lost.
 *
 * <p><strong>The jobs are run here in the order a night never runs them in, and that is the whole
 * point.</strong> The income job is at one in the morning and the rules job at two, so in ordinary
 * use the record is complete before any rule reads it. But a trainer types job names into
 * {@code POST /api/dev/jobs/{name}/run} in whatever order they like, and an application coming back
 * from downtime is not running a night at all. The rules job settles the rule through <em>now</em>
 * and the cursor only moves forward; if a rule's days were judged on the day each salary was
 * <em>due</em>, a salary credited a moment later would already be behind that cursor and would never
 * be looked at again. The customer's money would sit in their current account, the rule that was
 * supposed to save out of it would be silently a month short, and nothing anywhere would say so.
 *
 * <p>Judged instead on when the salary was <em>credited</em>, the late one is found the first time
 * anybody looks after it landed — which is what this asserts. {@code fireSavingRulesDue} is run
 * <em>before</em> {@code creditMonthlyIncome} deliberately;
 * {@link APaydayRuleFiresOnlyForTheSalariesThatLandedApiTest} always runs them in the night's own
 * order and therefore cannot see this at all.
 *
 * <p>The second half runs them the right way round for the following month, because a fix that
 * caught the late salary by loosening the range far enough to fire on mornings nobody was paid would
 * be the defect this feature has already been sent back for twice. The rule must still fire once per
 * salary and once only.
 *
 * <p><strong>A fixed amount rather than a sweep</strong>, for the reason
 * {@code RulesAsSomebodyWouldTypeThem.aFixedAmountOnPayday} gives: a sweep that fired when it should
 * not finds the account already at its floor and moves nothing, so the defect would hide behind
 * balances that looked right.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class APaydayRuleCatchesUpASalaryCreditedAfterItRanApiTest extends ApiIntegrationTest {

    private static final String THE_RULES_JOB = "fireSavingRulesDue";
    private static final String THE_INCOME_JOB = "creditMonthlyIncome";

    private static final String THE_SALARY = "1200.00";
    private static final String WHAT_THE_RULE_MOVES = "300.00";

    /** The latest day of the month every month has, so that nothing here is about the clamp. */
    private static final int A_DAY_EVERY_MONTH_HAS = 28;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseJobsThisTestRunsOutOfOrder() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-late-salary"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_salary_credited_after_the_rules_job_ran_still_fires_the_rule_and_fires_it_once() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItAllBegan = app.theDateTheClockReads();
        int payday = Math.min(theDayItAllBegan.getDayOfMonth(), A_DAY_EVERY_MONTH_HAS);
        BigDecimal savedBefore = app.balancesOf(savingsAccount).moneyBalance();
        BigDecimal heldBefore = app.currentAccountBalanceOf(ANKE);

        app.declareIncomeFor(ANKE, payday, THE_SALARY);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountOnPayday(app.currentAccountOf(ANKE),
                        "Three hundred out of every salary", WHAT_THE_RULE_MOVES));

        LocalDate theFirstPayday = theDayItAllBegan.plusMonths(1).withDayOfMonth(payday);
        windTo(theFirstPayday);

        // The wrong way round, on purpose: the rule is settled through this moment while the record
        // of what landed is still empty.
        app.runJob(THE_RULES_JOB);
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("nobody has been paid yet, so there is no payday for the rule to fire on — a "
                        + "rule that fired here would be moving money on a morning nobody was paid")
                .isEmpty();

        app.runJob(THE_INCOME_JOB);
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("the salary landed, a moment after the rules job had already gone past the day "
                        + "it was due for")
                .isEqualByComparingTo(heldBefore.add(new BigDecimal(THE_SALARY)));

        app.runJob(THE_RULES_JOB);

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history)
                .as("the late salary is caught up rather than lost: judged on when it was credited "
                        + "rather than on the day it was due, it is still there to be found the "
                        + "first time the rule looks after it landed")
                .hasSize(1);
        assertThat(history.get(0).dueOn())
                .as("recorded against the day the salary was due, which is the day the customer "
                        + "recognises, rather than against the day it was eventually dealt with")
                .isEqualTo(theFirstPayday);
        assertThat(history.get(0).outcome()).isEqualTo("MOVED");
        assertThat(history.get(0).amount()).isEqualByComparingTo(new BigDecimal(WHAT_THE_RULE_MOVES));
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(savedBefore.add(new BigDecimal(WHAT_THE_RULE_MOVES)));

        app.runJob(THE_RULES_JOB);
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("and running it again moves the money once, as it does everywhere else")
                .hasSize(1);

        // A month on, with the jobs in the order the night actually runs them in.
        LocalDate theSecondPayday = theFirstPayday.plusMonths(1);
        windTo(theSecondPayday);
        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_RULES_JOB);

        List<RuleOccurrenceView> both = app.historyOf(savingsAccount, rule.id());
        assertThat(both)
                .extracting(RuleOccurrenceView::dueOn)
                .as("two salaries have landed altogether, so the rule has fired twice and on the "
                        + "two days a salary actually landed on — the fix for the late one does "
                        + "not buy back the phantom paydays this feature was sent back for twice")
                .containsExactlyInAnyOrder(theFirstPayday, theSecondPayday);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(savedBefore.add(new BigDecimal(WHAT_THE_RULE_MOVES)
                        .multiply(BigDecimal.valueOf(2))));
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("two salaries in and two transfers out of the current account, and nothing else")
                .isEqualByComparingTo(heldBefore
                        .add(new BigDecimal(THE_SALARY).multiply(BigDecimal.valueOf(2)))
                        .subtract(new BigDecimal(WHAT_THE_RULE_MOVES).multiply(BigDecimal.valueOf(2))));
    }

    /** The clock forward to that day, through the endpoint a trainer would use. */
    private static void windTo(LocalDate day) {
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), day));
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
    }
}
