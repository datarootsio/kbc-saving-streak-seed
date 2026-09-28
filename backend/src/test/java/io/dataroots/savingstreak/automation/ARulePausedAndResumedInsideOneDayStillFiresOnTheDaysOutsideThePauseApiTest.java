package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * A rule paused and resumed inside one day goes on firing on the days outside the pause; a rule
 * paused, resumed and paused again is stopped again, and started again, each time.
 *
 * <p><strong>The other direction of the pause, and the one a careless implementation gets
 * wrong.</strong> It is easy to write a pause that swallows everything after it — moving a cursor
 * too far, or leaving a window that never closes — and every assertion about "a pause is never made
 * up" would still pass. What would be broken is the customer's rule, silently and for ever. So this
 * winds four of the rule's days past it and asserts the two outside each pause fired and the two
 * inside did not.
 *
 * <p><strong>Pause, resume, pause, resume</strong>, because a rule stopped for a month in the winter
 * and again in the spring is the ordinary way somebody uses this, and one remembered window would
 * quietly forget the first of them.
 *
 * <p>The clock moves in whole calendar days, so "inside one day" is a pause and a resume with no day
 * between them — which is exactly the window that must swallow nothing.
 *
 * <p>Weekly, and every day named off the day the clock happens to read, so that nothing here depends
 * on what date it is when the suite runs.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ARulePausedAndResumedInsideOneDayStillFiresOnTheDaysOutsideThePauseApiTest
        extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String WHAT_IT_MOVES = "12.00";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhosePausesThisTestOpensAndCloses() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-pause-inside-one-day"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_days_either_side_of_a_pause_fire_and_the_day_inside_the_second_one_does_not() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItAllBegan = app.theDateTheClockReads();
        LocalDate itsFirstDay = theDayItAllBegan.plusDays(2);
        String itsDay = itsFirstDay.getDayOfWeek().name();
        BigDecimal heldBefore = app.currentAccountBalanceOf(ANKE);

        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Twelve a week, on and off", itsDay, WHAT_IT_MOVES));

        // Its first day, with nothing stopped: the rule works before any of this.
        app.daysPass(2);
        app.runJob(THE_JOB);
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("the rule fires on its day to begin with, or the rest of this test is watching "
                        + "a rule that never worked")
                .hasSize(1);

        // Paused and started again the same day, with no day in between. A window of no days at all
        // has to swallow no days at all.
        app.daysPass(1);
        app.pauseRule(savingsAccount, rule.id());
        app.resumeRule(savingsAccount, rule.id());

        app.daysPass(6);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> afterTheBriefPause = app.historyOf(savingsAccount, rule.id());
        assertThat(afterTheBriefPause)
                .extracting(RuleOccurrenceView::dueOn)
                .as("a pause and a resume with no day between them stop nothing: the rule's next "
                        + "day is outside that window and fires exactly as it always did")
                .containsExactly(itsFirstDay.plusWeeks(1), itsFirstDay);

        // And now a real pause, over its next day.
        app.pauseRule(savingsAccount, rule.id());
        app.daysPass(7);
        app.runJob(THE_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("the second pause stops it again — a rule that has been paused once before is "
                        + "not a rule that can no longer be paused")
                .hasSize(2);

        // Started again, and on to the day after that.
        app.resumeRule(savingsAccount, rule.id());
        LocalDate theDayItCameBack = app.theDateTheClockReads();
        app.daysPass(7);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> all = app.historyOf(savingsAccount, rule.id());
        assertThat(all)
                .extracting(RuleOccurrenceView::dueOn)
                .as("three days fired and one passed over: the two either side of the brief pause, "
                        + "the one after the second resume, and never the one the second pause "
                        + "covered — which is neither made up nor recorded")
                .containsExactly(theDayItCameBack.plusDays(7), itsFirstDay.plusWeeks(1), itsFirstDay);
        assertThat(all)
                .extracting(RuleOccurrenceView::dueOn)
                .doesNotContain(itsFirstDay.plusWeeks(2));
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("three transfers of twelve euros, and not four")
                .isEqualByComparingTo(heldBefore.subtract(
                        new BigDecimal(WHAT_IT_MOVES).multiply(BigDecimal.valueOf(3))));
        assertThat(app.depositsInto(savingsAccount))
                .as("counted off the account rather than off the rule's own record")
                .hasSize(3);
    }
}
