package io.dataroots.savingstreak.automation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

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
 * A rule written halfway through a span the job later catches up fires for the days after it existed
 * and for no others, while a rule that was standing the whole time fires for all of them.
 *
 * <p>User story 42. A catch-up is the one place this can go wrong quietly: the run reaches back
 * months, and a rule whose range was counted from anything but its own beginning would be handed the
 * months before it was written along with the months after. That is not a miscount, it is a bill for
 * a past the customer never asked to save in — real money out of their current account on days they
 * had not left any instruction standing.
 *
 * <p><strong>Two rules, because one of them is the control.</strong> A test with only the new rule
 * in it would pass against an application that had simply caught nothing up at all, which is the
 * opposite defect and the one the rest of this ticket is about. The rule left standing at the start
 * is here to say that the six months really were caught up, so that the three the second rule
 * declined are three it declined rather than three nobody offered it.
 *
 * <p>{@link ARuleCreatedTodayDoesNotFireForTheDaysBeforeItExistedApiTest} makes the same claim for a
 * rule written today on a clock nobody has wound; this is the half that only shows up in the middle
 * of a catch-up.
 *
 * <p>Fixed amounts rather than sweeps, so that a firing nobody wanted is visible as money leaving
 * rather than hidden behind an account already sitting at a floor.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ARuleWrittenMidwayThroughACatchUpFiresOnlyForWhatCameAfterItApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String TEN_EUROS = "10.00";

    /** Six months of downtime altogether, with the second rule written after three of them. */
    private static final int MONTHS_OF_DOWNTIME = 6;
    private static final int MONTHS_BEFORE_THE_SECOND_RULE = 3;

    /** The latest day of the month every month has, so that nothing here is about the clamp. */
    private static final int A_DAY_EVERY_MONTH_HAS = 28;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDowntimeThisTestStages() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-rule-written-midway"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_months_before_a_rule_was_written_are_not_caught_up_for_it() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItAllBegan = app.theDateTheClockReads();
        String dayOfMonth =
                String.valueOf(Math.min(theDayItAllBegan.getDayOfMonth(), A_DAY_EVERY_MONTH_HAS));

        SavingRuleView standingAllAlong = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryMonth(app.currentAccountOf(ANKE),
                        "Standing before the outage", dayOfMonth, TEN_EUROS));

        // Halfway through, and still with nobody running anything, the customer leaves a second
        // rule standing. Its cursor starts here, which is what the second half of this asserts.
        windTo(theDayItAllBegan.plusMonths(MONTHS_BEFORE_THE_SECOND_RULE));
        LocalDate theDayTheSecondRuleWasWritten = app.theDateTheClockReads();
        SavingRuleView writtenMidway = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryMonth(app.currentAccountOf(ANKE),
                        "Written halfway through", dayOfMonth, TEN_EUROS));

        windTo(theDayItAllBegan.plusMonths(MONTHS_OF_DOWNTIME));
        app.runJob(THE_JOB);

        List<LocalDate> everyDayThatFell = theDaysThatFellAfter(theDayItAllBegan, dayOfMonth);

        assertThat(app.historyOf(savingsAccount, standingAllAlong.id()))
                .extracting(RuleOccurrenceView::dueOn)
                .as("the rule that was standing the whole time was caught up for all six months, "
                        + "which is what says the run really did reach back that far")
                .containsExactlyInAnyOrderElementsOf(everyDayThatFell);

        List<RuleOccurrenceView> midway = app.historyOf(savingsAccount, writtenMidway.id());
        assertThat(midway)
                .extracting(RuleOccurrenceView::dueOn)
                .as("and the rule written halfway through fired for the three months after it was "
                        + "written and for none of the three before — a rule is an instruction "
                        + "about the future, never a bill for the past")
                .containsExactlyInAnyOrderElementsOf(everyDayThatFell.subList(
                        MONTHS_BEFORE_THE_SECOND_RULE, MONTHS_OF_DOWNTIME));
        assertThat(midway)
                .allSatisfy(occurrence -> assertThat(occurrence.dueOn())
                        .as("every day it fired for began after the day it was written")
                        .isAfter(theDayTheSecondRuleWasWritten));
    }

    /** The day of the month the rules move on, in each of the six months of the outage. */
    private static List<LocalDate> theDaysThatFellAfter(LocalDate theDayItAllBegan, String dayOfMonth) {
        return IntStream.rangeClosed(1, MONTHS_OF_DOWNTIME)
                .mapToObj(month -> theDayItAllBegan.plusMonths(month)
                        .withDayOfMonth(Integer.parseInt(dayOfMonth)))
                .toList();
    }

    /** The clock forward to that day, through the endpoint a trainer would use. */
    private static void windTo(LocalDate day) {
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), day));
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
    }
}
