package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer who moves the day a rule fires on, inside a period it has already fired in, does not
 * have their money moved twice in that period.
 *
 * <p>The second door onto the same hole as
 * {@link APaydayMovedInsideItsMonthFiresTheRuleOnceApiTest}, and it is the same money. Changing a
 * rule rewrites the day it moves on and deliberately leaves its cursor where it was — a change is
 * not a new rule and must not become a bill for the month it was made in — and the uniqueness over
 * rule and day due cannot catch it, because the 15th and the 25th are two different days. What keeps
 * it is that the month is one month, and the week is one week.
 *
 * <p><strong>A month for a monthly rule and a savings week for a weekly one.</strong> Both are
 * asserted, because "once a period" said only of months would leave a weekly rule moved from Monday
 * to Friday moving twice in the week — the same defect wearing a different calendar.
 *
 * <p><strong>Both directions, and the period after.</strong> The day moved later is the one that
 * pays twice; the day moved earlier is asserted beside it; and the rule fires again in the next
 * month and the next week, so that none of this is satisfied by a rule that quietly stopped firing.
 *
 * <p>Two customers, one per trigger, so that neither story is reading the other's occurrences — and
 * each winds the clock only forwards from wherever it finds it, so the two are independent of the
 * order they run in. Its own application, for the reason {@link AnApplicationWithAClockToMove}
 * gives.
 */
class ARulesDayMovedInsideItsPeriodFiresItOnceApiTest extends ApiIntegrationTest {

    private static final String THE_RULES_JOB = "fireSavingRulesDue";

    /** Small enough that neither seeded account can run out of it over this test's few firings. */
    private static final String WHAT_THE_RULE_MOVES = "10.00";

    private static final int THE_FIFTH = 5;

    private static final int THE_FIFTEENTH = 15;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseRulesThisTestEdits() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-rules-day-moved-mid-period"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_monthly_rules_date_moved_later_in_a_month_it_has_fired_in_moves_no_more_money() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryMonth(app.currentAccountOf(ANKE),
                        "Ten on the fifteenth", String.valueOf(THE_FIFTEENTH), WHAT_THE_RULE_MOVES));

        LocalDate theFifteenth = theNextFifteenthAfterToday();
        windTo(theFifteenth);
        app.runJob(THE_RULES_JOB);

        BigDecimal savedOnceTheMonthHadBeenHonoured = app.balancesOf(savingsAccount).moneyBalance();
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("the rule moved its ten euros on the date its holder wrote")
                .singleElement()
                .satisfies(occurrence -> assertThat(occurrence.dueOn()).isEqualTo(theFifteenth));

        // The correction that used to cost ten euros: the same monthly rule, ten days later, in the
        // month it has already fired in.
        app.changeRule(savingsAccount, rule.id(), Map.of("dayOfMonth", "25"));
        windTo(theFifteenth.plusDays(10));
        app.runJob(THE_RULES_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("a monthly rule moves once a month, and moving its date to the twenty-fifth is "
                        + "saying when it moves rather than asking it to move again")
                .hasSize(1);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(savedOnceTheMonthHadBeenHonoured);

        // The other direction, to a date this month has already gone past.
        app.changeRule(savingsAccount, rule.id(), Map.of("dayOfMonth", String.valueOf(THE_FIFTH)));
        windTo(theFifteenth.plusDays(13));
        app.runJob(THE_RULES_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("and moving it earlier in a month already honoured is the same one firing")
                .hasSize(1);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(savedOnceTheMonthHadBeenHonoured);

        // And the month after, so that "once a month" is not a rule that has stopped firing.
        LocalDate theFifthOfTheMonthAfter = YearMonth.from(theFifteenth).plusMonths(1).atDay(THE_FIFTH);
        windTo(theFifthOfTheMonthAfter);
        app.runJob(THE_RULES_JOB);

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).dueOn())
                .as("on the date it now says, in the next month it has")
                .isEqualTo(theFifthOfTheMonthAfter);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(
                        savedOnceTheMonthHadBeenHonoured.add(new BigDecimal(WHAT_THE_RULE_MOVES)));
    }

    @Test
    void a_weekly_rules_day_moved_later_in_a_week_it_has_fired_in_moves_no_more_money() {
        long savingsAccount = app.savingsAccountOf(BRAM);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(BRAM),
                        "Ten every Monday", "MONDAY", WHAT_THE_RULE_MOVES));

        LocalDate monday = app.theDateTheClockReads().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        windTo(monday);
        app.runJob(THE_RULES_JOB);

        BigDecimal savedOnceTheWeekHadBeenHonoured = app.balancesOf(savingsAccount).moneyBalance();
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("the rule moved its ten euros on the day its holder wrote")
                .singleElement()
                .satisfies(occurrence -> assertThat(occurrence.dueOn()).isEqualTo(monday));

        // Later in the same savings week, which is the week the deposit it already made counts
        // toward — so a second firing would be two transfers in one week of one weekly rule.
        app.changeRule(savingsAccount, rule.id(), Map.of("dayOfWeek", "FRIDAY"));
        LocalDate friday = monday.plusDays(4);
        assertThat(SavingsWeek.containing(friday))
                .as("the Friday this test winds to is in the same savings week as the Monday that "
                        + "fired, or it is testing nothing")
                .isEqualTo(SavingsWeek.containing(monday));
        windTo(friday);
        app.runJob(THE_RULES_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("a weekly rule moves once a week, and moving its day to Friday is saying when "
                        + "it moves rather than asking it to move again")
                .hasSize(1);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(savedOnceTheWeekHadBeenHonoured);

        // The other direction, to a day this week has already gone past.
        app.changeRule(savingsAccount, rule.id(), Map.of("dayOfWeek", "WEDNESDAY"));
        app.runJob(THE_RULES_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("and moving it earlier in a week already honoured is the same one firing")
                .hasSize(1);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(savedOnceTheWeekHadBeenHonoured);

        // And the week after, so that "once a week" is not a rule that has stopped firing.
        LocalDate theWednesdayOfTheWeekAfter = friday.plusDays(5);
        assertThat(SavingsWeek.containing(theWednesdayOfTheWeekAfter))
                .as("and the Wednesday it winds to next is in the week after that one")
                .isEqualTo(SavingsWeek.containing(monday.plusWeeks(1)));
        windTo(theWednesdayOfTheWeekAfter);
        app.runJob(THE_RULES_JOB);

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).dueOn())
                .as("on the day it now says, in the next week it has")
                .isEqualTo(theWednesdayOfTheWeekAfter);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(
                        savedOnceTheWeekHadBeenHonoured.add(new BigDecimal(WHAT_THE_RULE_MOVES)));
    }

    /**
     * The next fifteenth the rule could actually fire on: a rule's cursor starts at the moment it was
     * left standing, so a fifteenth that had already begun when it was written is not one it fires
     * for.
     */
    private static LocalDate theNextFifteenthAfterToday() {
        LocalDate today = app.theDateTheClockReads();
        return today.getDayOfMonth() < THE_FIFTEENTH
                ? today.withDayOfMonth(THE_FIFTEENTH)
                : today.plusMonths(1).withDayOfMonth(THE_FIFTEENTH);
    }

    /** Winds the clock to a named day, which is how a test says "the month turns" in days. */
    private static void windTo(LocalDate day) {
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), day));
    }
}
