package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
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
 * A rule whose catch-up would run to more than five hundred occurrences fires five hundred, leaves
 * the rest, and the next run picks them up from the day the last one stopped at.
 *
 * <p>User story 41. The development clock goes a hundred years forward in one move and a weekly rule
 * over that span is five thousand two hundred transfers: a trainer who winds a century by accident
 * should be told what happened, not watch one transaction work through five thousand deposits while
 * the page waits. The cap is a guard against a demonstration, not against a customer.
 *
 * <p><strong>The cap delays a catch-up; it never loses one.</strong> That is the half worth testing
 * and the half a cap is easy to get wrong: an implementation that capped the work and then moved the
 * cursor to the moment it ran would silently drop every occurrence it did not get to, and would pass
 * any test that only counted the first run. So the second run is asserted as well — that it fires
 * the rest, that it starts at the day after the one the first run stopped at, and that not one day
 * appears twice.
 *
 * <p><strong>Eleven and a half years of Mondays, and a fixed amount bigger than the account
 * holds.</strong> The rule is a real fixed-amount rule and every occurrence is really settled, but
 * the amount cannot be honoured, so each one is recorded as {@code NOT_ENOUGH_MONEY} and no deposit
 * is made. That is deliberate. What this test is counting is <em>occurrences</em> — the record is
 * written whether money moved or not, which is the whole argument for keeping it — and six hundred
 * genuine transfers would add six hundred deposits, six hundred points ledger entries and six
 * hundred streak recalculations to a suite that runs on every check, to assert a number that is
 * already fully visible in the record. Nothing hides behind it: an occurrence that fired when it
 * should not have written a row exactly as an occurrence that moved money does.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ACatchUpOfMoreThanFiveHundredOccurrencesIsCappedAndContinuesApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    /** What the run will settle for one rule in one go, which is the figure under test. */
    private static final int MOST_IN_ONE_RUN = 500;

    /**
     * Weeks of downtime: comfortably more than the cap, so that the first run is capped and the
     * second is not, and the two of them together are the whole catch-up.
     */
    private static final int WEEKS_OF_DOWNTIME = 600;

    private static final int DAYS_IN_A_WEEK = 7;

    /**
     * More than the seeded current account holds, so that every occurrence is settled as
     * {@code NOT_ENOUGH_MONEY} and this test costs six hundred rows rather than six hundred
     * deposits. What is being counted is occurrences, and an occurrence is recorded either way.
     */
    private static final String MORE_THAN_THE_ACCOUNT_HOLDS = "99999.00";

    private static final DayOfWeek MONDAY = DayOfWeek.MONDAY;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseCenturyThisTestWinds() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-capped-catch-up"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void five_hundred_are_fired_the_rest_are_left_and_the_next_run_continues_from_there() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItWasLeftStanding = app.theDateTheClockReads();
        BigDecimal heldBefore = app.currentAccountBalanceOf(ANKE);

        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Every Monday, through a very long outage", MONDAY.name(),
                        MORE_THAN_THE_ACCOUNT_HOLDS));

        // A multiple of seven, so the number of Mondays that fall is exactly the number of weeks:
        // the Monday the rule was written on is behind its cursor and the last Monday is on the day
        // the clock lands on.
        app.daysPass((long) WEEKS_OF_DOWNTIME * DAYS_IN_A_WEEK);
        List<LocalDate> everyMondayThatFell = theMondaysBetween(theDayItWasLeftStanding,
                app.theDateTheClockReads());
        assertThat(everyMondayThatFell)
                .as("the outage this test stages is six hundred Mondays long, which is more than "
                        + "one run will settle")
                .hasSize(WEEKS_OF_DOWNTIME);

        app.runJob(THE_JOB);

        List<RuleOccurrenceView> afterTheFirstRun = oldestFirst(
                app.historyOf(savingsAccount, rule.id()));
        assertThat(afterTheFirstRun)
                .extracting(RuleOccurrenceView::dueOn)
                .as("five hundred and no more: a run that worked through all six hundred would be "
                        + "the run that hangs the application when somebody winds a century")
                .containsExactlyElementsOf(everyMondayThatFell.subList(0, MOST_IN_ONE_RUN));

        app.runJob(THE_JOB);

        List<RuleOccurrenceView> afterTheSecondRun = oldestFirst(
                app.historyOf(savingsAccount, rule.id()));
        assertThat(afterTheSecondRun)
                .extracting(RuleOccurrenceView::dueOn)
                .as("and the next run continues from the day the last one stopped at rather than "
                        + "starting again or writing the rest off — nothing skipped, nothing twice")
                .containsExactlyElementsOf(everyMondayThatFell);
        assertThat(afterTheSecondRun)
                .extracting(RuleOccurrenceView::dueOn)
                .as("one row per Monday, which is what makes the cap a delay rather than a repeat")
                .doesNotHaveDuplicates();
        assertThat(afterTheSecondRun.get(MOST_IN_ONE_RUN).dueOn())
                .as("the first occurrence of the second run is the Monday after the last one of "
                        + "the first run, to the day")
                .isEqualTo(afterTheFirstRun.get(MOST_IN_ONE_RUN - 1).dueOn().plusWeeks(1));

        app.runJob(THE_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("a third run has nothing left to catch up")
                .hasSize(WEEKS_OF_DOWNTIME);
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("every one of them was settled as more than the account could honour, which is "
                        + "how this test costs six hundred rows rather than six hundred deposits")
                .allSatisfy(occurrence -> {
                    assertThat(occurrence.outcome()).isEqualTo("NOT_ENOUGH_MONEY");
                    assertThat(occurrence.depositId()).isNull();
                });
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and not a cent left the current account, because a fixed amount moves all of "
                        + "itself or none of it")
                .isEqualByComparingTo(heldBefore);
    }

    /** The history as the days fell, because the endpoint reports it newest first. */
    private static List<RuleOccurrenceView> oldestFirst(List<RuleOccurrenceView> history) {
        return history.stream()
                .sorted(Comparator.comparing(RuleOccurrenceView::id))
                .toList();
    }

    /**
     * Every Monday strictly after the day the rule was written and at or before the day the clock
     * now reads — the range the run itself judges by, worked out here from the calendar the test can
     * see rather than taken from however many rows happened to be written.
     */
    private static List<LocalDate> theMondaysBetween(LocalDate theDayItWasLeftStanding,
                                                     LocalDate today) {
        List<LocalDate> mondays = new ArrayList<>();
        LocalDate day = theDayItWasLeftStanding.plusDays(1)
                .with(TemporalAdjusters.nextOrSame(MONDAY));
        while (!day.isAfter(today)) {
            mondays.add(day);
            day = day.plusWeeks(1);
        }
        return mondays;
    }
}
