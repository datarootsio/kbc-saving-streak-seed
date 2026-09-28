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

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer who corrects when they are paid does not have their money moved twice that month.
 *
 * <p>A month holds one salary and therefore one payday firing. The day it lands on is not the rule's
 * own — a payday rule reads it from its holder's declaration, which is exactly what lets somebody
 * move payday once and have every rule waiting for it follow — so the day the rule falls due on can
 * move underneath a cursor that has already passed the old one. The 15th and the 20th are different
 * days; April is one April, and telling the application when you are paid must not cost you twenty
 * euros.
 *
 * <p>The same hole the {@code accounts} module was sent back for and fixed in
 * {@code dueInMonthsNotAlreadyPaid}: this is that question asked about a rule instead of a salary.
 * It is asserted here against the rule's own history and its savings balance, because those are what
 * a customer would see.
 *
 * <p><strong>Both directions.</strong> Payday moved later in the month is the one that pays twice,
 * and payday moved earlier is asserted beside it so that the guard cannot be a cursor that simply
 * refuses to look backwards. And a month later the rule fires again, so that "fires once a month"
 * has not been satisfied by a rule that quietly stopped firing at all.
 *
 * <p>A fixed amount rather than a sweep, for the reason
 * {@link RulesAsSomebodyWouldTypeThem#aFixedAmountOnPayday} gives: a sweep firing twice in a month
 * would find the account at its floor the second time and move nothing, and the defect would hide.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class APaydayMovedInsideItsMonthFiresTheRuleOnceApiTest extends ApiIntegrationTest {

    /** The two names a trainer types, in the order the night runs them. */
    private static final String THE_INCOME_JOB = "creditMonthlyIncome";

    private static final String THE_RULES_JOB = "fireSavingRulesDue";

    private static final String A_SALARY = "2000.00";

    /** Small enough that Anke's account can afford it many times over, so nothing here is about money running out. */
    private static final String WHAT_THE_RULE_MOVES = "20.00";

    /** Three days every month has, so that this test is about payday and not about what the 31st means. */
    private static final int THE_FIFTH = 5;

    private static final int THE_FIFTEENTH = 15;

    private static final int THE_TWENTIETH = 20;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhosePaydayThisTestMoves() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-payday-moved-inside-its-month"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void moving_payday_inside_a_month_the_rule_has_already_fired_in_moves_no_more_money() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        MonthlyIncomeView paidOnTheFifteenth = app.declareIncomeFor(ANKE, THE_FIFTEENTH, A_SALARY);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountOnPayday(
                        app.currentAccountOf(ANKE), "Twenty on payday", WHAT_THE_RULE_MOVES));

        windTo(paidOnTheFifteenth.nextPayday());
        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_RULES_JOB);

        LocalDate theFifteenth = app.theDateTheClockReads();
        BigDecimal savedOnceThePaydayHadBeenHonoured = app.balancesOf(savingsAccount).moneyBalance();
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("the salary landed on the day its holder declared, and the rule moved its "
                        + "twenty euros on it")
                .singleElement()
                .satisfies(occurrence -> {
                    assertThat(occurrence.dueOn()).isEqualTo(theFifteenth);
                    assertThat(occurrence.amount()).isEqualByComparingTo(WHAT_THE_RULE_MOVES);
                    assertThat(occurrence.outcome()).isEqualTo("MOVED");
                });

        // The correction that used to cost twenty euros: the same salary, five days later, in the
        // month the rule has already fired in.
        app.declareIncomeFor(ANKE, THE_TWENTIETH, A_SALARY);
        app.daysPass(5);
        assertThat(app.theDateTheClockReads())
                .as("the clock reads the day payday has been moved to")
                .isEqualTo(theFifteenth.plusDays(5));
        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_RULES_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("a month holds one salary and therefore one payday firing: moving payday from "
                        + "the fifteenth to the twentieth is a correction, not a second salary")
                .hasSize(1);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("and no second twenty euros moved")
                .isEqualByComparingTo(savedOnceThePaydayHadBeenHonoured);

        // The other direction, so that this cannot be a cursor that merely refuses to look backwards.
        app.declareIncomeFor(ANKE, THE_FIFTH, A_SALARY);
        app.daysPass(5);
        assertThat(app.theDateTheClockReads()).isEqualTo(theFifteenth.plusDays(10));
        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_RULES_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("payday moved earlier in a month already honoured is the same correction seen "
                        + "from the other side, and it is the same one firing")
                .hasSize(1);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(savedOnceThePaydayHadBeenHonoured);

        // And the month after, so that "once a month" is not a rule that has quietly stopped firing.
        LocalDate theFifthOfTheMonthAfter = YearMonth.from(theFifteenth).plusMonths(1)
                .atDay(THE_FIFTH);
        windTo(theFifthOfTheMonthAfter);
        app.runJob(THE_INCOME_JOB);
        app.runJob(THE_RULES_JOB);

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history)
                .as("a new month is a new salary, and the rule moves again on the day payday now "
                        + "lands on")
                .hasSize(2);
        assertThat(history.get(0).dueOn()).isEqualTo(theFifthOfTheMonthAfter);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(
                        savedOnceThePaydayHadBeenHonoured.add(new BigDecimal(WHAT_THE_RULE_MOVES)));
    }

    /** Winds the clock to a named day, which is how a test says "the month turns" in days. */
    private static void windTo(LocalDate day) {
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), day));
    }
}
