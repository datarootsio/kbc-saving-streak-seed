package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * A monthly rule moves on the date its holder chose, and one set for the 31st moves on the last day
 * of February rather than skipping the month.
 *
 * <p>User stories 2 and 7. Clamping is the banking convention and it is the only rule that does not
 * silently skip February: a customer who saves on the 31st saves twelve times a year, not seven.
 *
 * <p><strong>Both directions, on the day before as well as on the day.</strong> A run on the 27th
 * finding nothing and a run on the 28th finding the occurrence are one claim between them — a rule
 * that fired on any day of February would satisfy the second half of this on its own, and would be a
 * standing order that moves money on a date nobody chose.
 *
 * <p>The February a trainer reaches is the next one, whichever side of it the clock starts on, and
 * whether it is a leap year is left to the calendar rather than written into this test: the last day
 * of February is asked of {@link YearMonth}, so a run of this suite in 2027 asserts the 28th and one
 * in 2028 the 29th without anybody editing it.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class AMonthlyRuleFiresOnItsDateAndOnTheLastDayOfFebruaryApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    /**
     * Small, because winding a clock to next February fires every month in between: the seeded
     * current account has to be able to pay for all of them and still be the thing under test.
     */
    private static final String TEN_EUROS = "10.00";

    private static final String THE_THIRTY_FIRST = "31";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesThrough() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-monthly-rule-fires"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_rule_set_for_the_thirty_first_moves_on_the_last_day_of_february_and_not_before() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryMonth(app.currentAccountOf(ANKE),
                        "Ten on the last of the month", THE_THIRTY_FIRST, TEN_EUROS));

        LocalDate theLastDayOfFebruary = theNextEndOfFebruaryAfter(app.theDateTheClockReads());
        windTo(theLastDayOfFebruary.minusDays(1));
        app.runJob(THE_JOB);

        assertThat(februaryIn(app.historyOf(savingsAccount, rule.id())))
                .as("the last day of February has not arrived, and a rule that had already moved "
                        + "this month would have moved on a date its holder never named")
                .isEmpty();
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("whichever months fell between here and February moved on the 31st, or on the "
                        + "last day a shorter month has — which is the same clamp, month by month")
                .allSatisfy(occurrence -> assertThat(occurrence.dueOn().getDayOfMonth())
                        .isEqualTo(YearMonth.from(occurrence.dueOn()).lengthOfMonth()));

        app.daysPass(1);
        assertThat(app.theDateTheClockReads()).isEqualTo(theLastDayOfFebruary);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> inFebruary = februaryIn(app.historyOf(savingsAccount, rule.id()));
        assertThat(inFebruary)
                .as("February is not skipped: the rule moved once, on the last day the month has")
                .hasSize(1);
        assertThat(inFebruary.get(0).dueOn())
                .as("which is the 29th in a leap year and the 28th otherwise, asked of the "
                        + "calendar rather than written down here")
                .isEqualTo(theLastDayOfFebruary);
        assertThat(inFebruary.get(0).outcome()).isEqualTo("MOVED");
        assertThat(inFebruary.get(0).amount()).isEqualByComparingTo(new BigDecimal(TEN_EUROS));
    }

    @Test
    void a_rule_set_for_a_date_every_month_has_moves_on_that_date() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        // A rule left standing now, whatever month the clock has reached by the time this method
        // runs: its cursor starts where it was written, so the next fifteenth is the first one.
        LocalDate theFifteenthComing = theNextFifteenthAfter(app.theDateTheClockReads());
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryMonth(app.currentAccountOf(ANKE),
                        "Ten on the fifteenth", "15", TEN_EUROS));

        windTo(theFifteenthComing.minusDays(1));
        app.runJob(THE_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("the fourteenth is not the fifteenth, and a rule that had already moved would "
                        + "be moving money on a day nobody chose")
                .isEmpty();

        app.daysPass(1);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).dueOn())
                .as("the date the customer named, and that date alone")
                .isEqualTo(theFifteenthComing);
        assertThat(history.get(0).amount()).isEqualByComparingTo(new BigDecimal(TEN_EUROS));
    }

    /** Only the occurrences that fell in a February, which is what both halves of the first test ask about. */
    private static List<RuleOccurrenceView> februaryIn(List<RuleOccurrenceView> history) {
        return history.stream()
                .filter(occurrence -> occurrence.dueOn().getMonthValue() == 2)
                .toList();
    }

    private static LocalDate theNextEndOfFebruaryAfter(LocalDate today) {
        YearMonth february = YearMonth.of(today.getYear(), 2);
        return february.atEndOfMonth().isAfter(today)
                ? february.atEndOfMonth()
                : february.plusYears(1).atEndOfMonth();
    }

    private static LocalDate theNextFifteenthAfter(LocalDate today) {
        LocalDate thisMonths = today.withDayOfMonth(15);
        return thisMonths.isAfter(today) ? thisMonths : thisMonths.plusMonths(1);
    }

    /** The clock forward to that day, through the endpoint a trainer would use. */
    private static void windTo(LocalDate day) {
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), day));
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
    }
}
