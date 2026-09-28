package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A payday rule fires once for each salary that actually landed, and not at all for the months no
 * salary landed in.
 *
 * <p>Criterion 4 read strictly: "a payday rule fires on the day its holder's income lands". A month
 * in which nothing landed has no such day, so a firing in it is money leaving a current account on a
 * morning nobody was paid — and a sweep on such a morning would take the account down to its floor.
 *
 * <p><strong>The two cursors are the defect this test exists for.</strong> A declared income carries
 * one — the moment through which its paydays are settled — and the rule carries another, and nothing
 * reconciles them. Work the rule's days out from the declared day of the month and walk the
 * <em>rule's</em> cursor and the calendar hands back every month in between, whether a salary landed
 * in it or not. The record of what was credited is the only thing that knows, and
 * {@code AccountsService.paydaysCreditedBetween} is where a rule now asks it. The same mistake
 * {@code accounts} was sent back for over a salary paid twice, and
 * {@link ARulesDayMovedInsideItsPeriodFiresItOnceApiTest} over a rule fired twice in a period:
 * asking the calendar a question only the record can answer.
 *
 * <p><strong>Two ways in, and the second is a user story rather than an oddity.</strong> A rule left
 * standing before its holder gets round to declaring an income is the first. The second is story 17,
 * "change or remove my declared income": a declaration withdrawn and made again starts a fresh
 * cursor of its own while the rule's stays where it was, and every calendar payday in the gap is a
 * phantom. Neither needs an unusual order of events — on a wound clock the cron never fires, so "the
 * job did not run in between" is the ordinary case rather than an unlucky one.
 *
 * <p><strong>And it still fires.</strong> The last act winds on a month and asserts the rule moves
 * again, so that "fires only for the salaries that landed" cannot be satisfied by a rule that has
 * quietly stopped firing.
 *
 * <p>A fixed amount rather than a sweep, for the reason
 * {@link RulesAsSomebodyWouldTypeThem#aFixedAmountOnPayday} gives: a sweep firing on a phantom
 * payday finds the account at its floor the second time and moves nothing, and the defect hides
 * behind balances that look right. Every assertion here is about money — the savings balance, the
 * current account balance and the history a customer would read — rather than about a log line.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class APaydayRuleFiresOnlyForTheSalariesThatLandedApiTest extends ApiIntegrationTest {

    /** The two names a trainer types, in the order the night runs them. */
    private static final String THE_INCOME_JOB = "creditMonthlyIncome";

    private static final String THE_RULES_JOB = "fireSavingRulesDue";

    private static final String A_SALARY = "2000.00";

    /**
     * Small enough that the account can afford it many times over, so that a firing this test
     * refuses is a firing refused on its merits rather than one the balance happened to stop.
     */
    private static final String WHAT_THE_RULE_MOVES = "20.00";

    /** A day every month has, so that this test is about payday and not about what the 31st means. */
    private static final int THE_FIFTH = 5;

    /**
     * Long enough that the calendar is certain to have passed a fifth of some month in it, which is
     * exactly the phantom payday this test is about. Any stretch over thirty-one days would do; a
     * month and a half says why without arithmetic.
     */
    private static final int A_STRETCH_NO_SALARY_LANDS_IN = 45;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhosePaydaysThisTestCounts() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-payday-rule-and-the-salaries"));
        theCustomer = app.aCustomerOfItsOwn("a payday rule and the salaries");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_payday_rule_moves_money_once_for_each_salary_credited_and_never_without_one() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        assertThat(app.incomeOf(theCustomer).declared())
                .as("nobody has said when this customer is paid, and the rule is written anyway")
                .isFalse();
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountOnPayday(
                        app.currentAccountOf(theCustomer), "Twenty on payday", WHAT_THE_RULE_MOVES));
        BigDecimal savedBeforeAnySalary = app.balancesOf(savingsAccount).moneyBalance();
        BigDecimal heldBeforeAnySalary = app.currentAccountBalanceOf(theCustomer);

        // The gap. Nothing is declared, no job is run, and the calendar goes past a fifth of the
        // month that the rule's own cursor has no reason to exclude.
        app.daysPass(A_STRETCH_NO_SALARY_LANDS_IN);
        MonthlyIncomeView income = app.declareIncomeFor(theCustomer, THE_FIFTH, A_SALARY);
        windTo(income.nextPayday());

        // The night, in the order the night runs it: money arrives at one, the rules read it at two.
        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_RULES_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("one salary landed, so the rule fell due on one day — the fifths of the months "
                        + "before the income was ever declared are days on which nobody was paid")
                .singleElement()
                .satisfies(occurrence -> {
                    assertThat(occurrence.dueOn()).isEqualTo(income.nextPayday());
                    assertThat(occurrence.outcome()).isEqualTo("MOVED");
                    assertThat(occurrence.amount()).isEqualByComparingTo(WHAT_THE_RULE_MOVES);
                });
        BigDecimal savedAfterOneSalary =
                savedBeforeAnySalary.add(new BigDecimal(WHAT_THE_RULE_MOVES));
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("and one twenty moved, not one per month the calendar walked through")
                .isEqualByComparingTo(savedAfterOneSalary);
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("the current account holds the one salary that landed less the one twenty that "
                        + "left it: a firing on a morning nobody was paid is visible here as money")
                .isEqualByComparingTo(heldBeforeAnySalary
                        .add(new BigDecimal(A_SALARY))
                        .subtract(new BigDecimal(WHAT_THE_RULE_MOVES)));

        // Story 17, and the way an ordinary customer reaches this: the declaration is taken away and
        // made again, which starts a cursor of its own while the rule's stays where it is.
        app.withdrawTheIncomeOf(theCustomer);
        app.daysPass(A_STRETCH_NO_SALARY_LANDS_IN);
        MonthlyIncomeView declaredAgain = app.declareIncomeFor(theCustomer, THE_FIFTH, A_SALARY);
        windTo(declaredAgain.nextPayday());
        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_RULES_JOB);

        List<RuleOccurrenceView> afterTheSecondSalary = app.historyOf(savingsAccount, rule.id());
        assertThat(afterTheSecondSalary)
                .as("two salaries have been credited altogether, so the rule has fired twice — the "
                        + "fifths that fell while no income was declared are still nobody's payday")
                .hasSize(2);
        assertThat(afterTheSecondSalary.get(0).dueOn())
                .as("and the second firing is on the day the second salary landed")
                .isEqualTo(declaredAgain.nextPayday());
        BigDecimal savedAfterTwoSalaries =
                savedAfterOneSalary.add(new BigDecimal(WHAT_THE_RULE_MOVES));
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(savedAfterTwoSalaries);

        // The month after, so that this is a rule that fires for each salary rather than one that
        // has quietly stopped firing.
        LocalDate theFifthOfTheMonthAfter = YearMonth.from(declaredAgain.nextPayday())
                .plusMonths(1).atDay(THE_FIFTH);
        windTo(theFifthOfTheMonthAfter);
        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_RULES_JOB);

        List<RuleOccurrenceView> afterTheThirdSalary = app.historyOf(savingsAccount, rule.id());
        assertThat(afterTheThirdSalary)
                .as("a new month is a new salary, and the rule moves on the day it lands")
                .hasSize(3);
        assertThat(afterTheThirdSalary.get(0).dueOn()).isEqualTo(theFifthOfTheMonthAfter);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(savedAfterTwoSalaries.add(new BigDecimal(WHAT_THE_RULE_MOVES)));
    }

    /** Winds the clock to a named day, which is how a test says "the month turns" in days. */
    private static void windTo(LocalDate day) {
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), day));
    }
}
