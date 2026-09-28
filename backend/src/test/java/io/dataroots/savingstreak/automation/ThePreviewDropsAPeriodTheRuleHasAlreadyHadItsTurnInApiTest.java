package io.dataroots.savingstreak.automation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.OccurrenceToComeView;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule moved to a later day of a week it has already fired in is not promised that day, because
 * the run would not fire it either.
 *
 * <p>This is the one case where the rule's cursor is no help and only the record is. A rule that
 * fired on Monday is settled through Monday, so the Friday the customer then moves it to is in front
 * of the cursor and looks perfectly due — {@code ARulesDayMovedInsideItsPeriodFiresItOnceApiTest}
 * is why the run does not fire it: a period holds one transfer, and the record rather than the
 * cursor is what keeps that. A preview that asked only the calendar and the cursor would promise a
 * transfer the night then declines to make, and the customer would have moved their day expecting
 * their money twice that week.
 *
 * <p>So the preview asks the record the same question the run asks, and this test is what says so.
 * Both halves are here: the day the preview refuses to promise, and then the night itself passing
 * over it — a preview that agreed with a run neither of which fired would be two wrongs agreeing.
 *
 * <p>Its own application, because it winds the clock. The rule's day is chosen off the clock rather
 * than off the calendar this test happens to run on: a Monday and a Wednesday are named by stepping
 * to the next Monday, which is what makes the two days certainly one savings week whatever day the
 * clock reads.
 */
class ThePreviewDropsAPeriodTheRuleHasAlreadyHadItsTurnInApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String FIFTY_EUROS = "50.00";

    /** How far into the week the rule's day is moved — Monday to Wednesday, one week, two days. */
    private static final int DAYS_LATER_IN_THE_SAME_WEEK = 2;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeekThisTestMovesADayInside() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-preview-skips-a-settled-period"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_day_moved_inside_a_week_the_rule_already_fired_in_is_not_promised_and_is_not_fired() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theMondayItFiresOn = app.theDateTheClockReads()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate theWednesdayOfTheSameWeek =
                theMondayItFiresOn.plusDays(DAYS_LATER_IN_THE_SAME_WEEK);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Fifty on a Monday", DayOfWeek.MONDAY.name(), FIFTY_EUROS));

        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), theMondayItFiresOn));
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> afterTheMonday = app.historyOf(savingsAccount, rule.id());
        assertThat(afterTheMonday)
                .as("the rule has had its turn this week, or there is no settled period for the "
                        + "rest of this test to be about")
                .hasSize(1);
        assertThat(afterTheMonday.get(0).dueOn()).isEqualTo(theMondayItFiresOn);
        assertThat(theNextDayPromisedFor(savingsAccount, rule))
                .as("and the next day promised is a week on, because this week is spent")
                .isEqualTo(theMondayItFiresOn.plusWeeks(1));

        app.changeRule(savingsAccount, rule.id(), Map.of("dayOfWeek", DayOfWeek.WEDNESDAY.name()));

        assertThat(theNextDayPromisedFor(savingsAccount, rule))
                .as("the Wednesday of the week the rule has already fired in is in front of its "
                        + "cursor and looks due, and it is still not promised — a period holds one "
                        + "transfer, and the record rather than the cursor is what keeps that")
                .isEqualTo(theWednesdayOfTheSameWeek.plusWeeks(1));

        app.daysPass(DAYS_LATER_IN_THE_SAME_WEEK);
        app.runJob(THE_JOB);

        assertThat(app.theDateTheClockReads())
                .as("the clock is now on the day the rule was moved to")
                .isEqualTo(theWednesdayOfTheSameWeek);
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("and the night passed over it, exactly as the preview said it would — which is "
                        + "the whole claim: the two asked the record the same question")
                .hasSize(1);
    }

    private static LocalDate theNextDayPromisedFor(long savingsAccount, SavingRuleView rule) {
        return app.previewOn(savingsAccount).occurrences().stream()
                .filter(coming -> coming.ruleId() == rule.id())
                .map(OccurrenceToComeView::dueOn)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "rule " + rule.id() + " has nothing at all in the preview"));
    }
}
