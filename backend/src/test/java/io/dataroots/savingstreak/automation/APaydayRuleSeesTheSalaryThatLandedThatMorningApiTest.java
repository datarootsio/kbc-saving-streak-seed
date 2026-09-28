package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MonthlyIncomeView;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule that fires on payday fires on the day its holder's income lands, and the income has landed
 * before it reads the balance.
 *
 * <p>User stories 3 and 16, and the reason the two jobs run an hour apart: income at one in the
 * morning, rules at two. A sweep fired before the salary landed would find the account at its floor
 * and move nothing, on the one morning it had most to do — and the customer would be told their
 * automation did nothing when what happened was that it ran too early.
 *
 * <p><strong>The figure is what proves the order.</strong> A sweep that ran before the salary would
 * move the surplus over the floor and no more; a sweep that ran after moves that surplus <em>plus
 * the whole salary</em>. The two are different numbers, so this test cannot pass with the jobs in
 * the wrong order.
 *
 * <p>Both directions, again: on a day that is not payday the rule stays quiet, or "it fires on
 * payday" would be satisfied by a rule that fires every night.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class APaydayRuleSeesTheSalaryThatLandedThatMorningApiTest extends ApiIntegrationTest {

    /** The two names a trainer types, in the order the night runs them. */
    private static final String THE_INCOME_JOB = "creditMonthlyIncome";

    private static final String THE_RULES_JOB = "fireSavingRulesDue";

    private static final String A_SALARY = "2000.00";

    private static final String THE_FLOOR = "800.00";

    /** A day every month has, so that this test is about payday and not about what the 31st means. */
    private static final int THE_FIFTEENTH = 15;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhosePaydayThisTestMovesTo() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-payday-rule-sees-the-salary"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_sweep_moves_the_salary_that_landed_an_hour_before_it() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        MonthlyIncomeView income = app.declareIncomeFor(ANKE, THE_FIFTEENTH, A_SALARY);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.everythingAboveAFloorOnPayday(
                        app.currentAccountOf(ANKE), "Everything above eight hundred on payday",
                        THE_FLOOR));
        BigDecimal heldBeforePayday = app.currentAccountBalanceOf(ANKE);
        BigDecimal savedBeforePayday = app.balancesOf(savingsAccount).moneyBalance();

        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_RULES_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("payday has not come, so nothing is due — a payday rule that fired every night "
                        + "would be a sweep on a day nobody named")
                .isEmpty();
        assertThat(app.currentAccountBalanceOf(ANKE)).isEqualByComparingTo(heldBeforePayday);

        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), income.nextPayday()));
        assertThat(app.theDateTheClockReads())
                .as("the clock reads the day the salary is due")
                .isEqualTo(income.nextPayday());

        // The night, in the order the night runs it: money arrives at one, the rules that read a
        // balance fire at two.
        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_RULES_JOB);

        BigDecimal floor = new BigDecimal(THE_FLOOR);
        BigDecimal whatTheSweepShouldHaveSeen = heldBeforePayday.add(new BigDecimal(A_SALARY));
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("the sweep took the account down to its floor")
                .isEqualByComparingTo(floor);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("and what it moved was the surplus over that floor including the salary that "
                        + "landed an hour earlier — a sweep that had run first would have moved "
                        + A_SALARY + " less than this")
                .isEqualByComparingTo(savedBeforePayday.add(whatTheSweepShouldHaveSeen.subtract(floor)));

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history)
                .as("one payday, one occurrence")
                .hasSize(1);
        assertThat(history.get(0).dueOn())
                .as("recorded against the day the salary was due, which is the day the rule's "
                        + "holder declared rather than a day of the rule's own")
                .isEqualTo(income.nextPayday());
        assertThat(history.get(0).amount())
                .isEqualByComparingTo(whatTheSweepShouldHaveSeen.subtract(floor));
    }
}
