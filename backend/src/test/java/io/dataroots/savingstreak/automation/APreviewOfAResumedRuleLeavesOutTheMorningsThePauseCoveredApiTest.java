package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
 * Pause a rule, let two of its mornings go by, resume it, read the preview — and then run the job:
 * the transfers the run makes are exactly the ones the preview named, and the two mornings the pause
 * covered are in neither.
 *
 * <p><strong>The shape that bites, and it only bites in this order.</strong> A forecast is counted
 * from each rule's own cursor, because that is where the nightly run counts from and the two have to
 * agree about the transfers the next run is about to make. {@link AutomationService#resumeRule}
 * deliberately does <em>not</em> move that cursor — the days in front of it include the ones the
 * rule was already owed from <em>before</em> the pause, which are downtime and are always caught up
 * — so every day inside a closed pause is inside the window the forecast walks. Nothing about the
 * cursor takes them out again, and the record cannot either: a day passed over never gets an
 * occurrence row, so for a weekly rule each pause Monday sits alone in its own unsettled week and
 * "has this rule already had its turn in that week" answers no. Only the pause record can say, and
 * only if the forecast asks it the way the run does.
 *
 * <p>So this test arranges a rule that is owed one morning <em>before</em> the pause — which is the
 * part that must survive, because downtime is always caught up — and two <em>inside</em> it, which
 * must not. A forecast that dropped all three would be the opposite defect, and is asserted against
 * in the same breath.
 *
 * <p>{@link APreviewReadBeforeTheRunHasCaughtUpAgreesWithItApiTest} is this test without the pause,
 * and passed while this one would have failed: it exercises one weekly rule nobody ever stopped.
 *
 * <p>Every day is named off the day the clock happens to read, so nothing here depends on what date
 * it is when the suite runs. Its own application, for the reason
 * {@link AnApplicationWithAClockToMove} gives: this test winds the clock, and the file the rest of
 * the run shares is not one to leave wound forward.
 */
class APreviewOfAResumedRuleLeavesOutTheMorningsThePauseCoveredApiTest extends ApiIntegrationTest {

    /** The name a trainer types into the development jobs endpoint. */
    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String WHAT_IT_MOVES = "10.00";

    /**
     * How many days pass before the customer presses pause. Two, so that the rule's first morning
     * has already fallen — with the job deliberately not run — and is owed rather than paused
     * through.
     */
    private static final int DAYS_BEFORE_THE_PAUSE = 2;

    /** How long the rule stays stopped: two whole weeks, so that two of its mornings fall inside. */
    private static final int DAYS_THE_PAUSE_LASTS = 14;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseClockThisTestWinds() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-preview-of-a-resumed-rule"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_resumed_rule_promises_the_morning_it_was_owed_and_not_the_ones_the_pause_covered() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItWasWritten = app.theDateTheClockReads();
        LocalDate theMorningItIsOwed = theDayItWasWritten.plusDays(1);
        LocalDate theFirstMorningInsideThePause = theMorningItIsOwed.plusWeeks(1);
        LocalDate theSecondMorningInsideThePause = theMorningItIsOwed.plusWeeks(2);
        LocalDate theFirstMorningAfterTheResume = theMorningItIsOwed.plusWeeks(3);
        BigDecimal heldBefore = app.currentAccountBalanceOf(ANKE);

        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Ten a week, when I can", theMorningItIsOwed.getDayOfWeek().name(),
                        WHAT_IT_MOVES));

        // Its first morning falls, and nothing is run on it: that is what makes the rule owe a
        // transfer from before the pause, which is downtime and is always caught up.
        app.daysPass(DAYS_BEFORE_THE_PAUSE);
        app.pauseRule(savingsAccount, rule.id());

        // And now two whole weeks inside the pause, which are the customer's own instruction and are
        // never made up.
        app.daysPass(DAYS_THE_PAUSE_LASTS);
        app.resumeRule(savingsAccount, rule.id());
        LocalDate theDayItCameBack = app.theDateTheClockReads();

        List<LocalDate> promised = app.previewOn(savingsAccount).occurrences().stream()
                .filter(coming -> coming.ruleId() == rule.id())
                .map(OccurrenceToComeView::dueOn)
                .toList();

        assertThat(promised)
                .as("the two mornings that fell inside the pause are not promised: a pause is the "
                        + "customer's own instruction and is never made up, so a preview offering "
                        + "them would be promising transfers the very next run passes over")
                .doesNotContain(theFirstMorningInsideThePause, theSecondMorningInsideThePause);
        assertThat(promised)
                .as("while the morning that fell before the pause is still promised — that one is "
                        + "downtime, which is always caught up, and dropping it would be the "
                        + "opposite mistake")
                .contains(theMorningItIsOwed);
        assertThat(promised.stream().filter(day -> !day.isAfter(theDayItCameBack)).toList())
                .as("one morning owed altogether, and it is that one")
                .containsExactly(theMorningItIsOwed);
        assertThat(promised.get(1))
                .as("and the next line is the first morning after the resume, three weeks on")
                .isEqualTo(theFirstMorningAfterTheResume);

        assertThat(app.previewOn(savingsAccount).occurrences().stream()
                .filter(coming -> coming.ruleId() == rule.id())
                .filter(OccurrenceToComeView::owedRatherThanStillToCome)
                .map(OccurrenceToComeView::dueOn)
                .toList())
                .as("and the preview says out loud which of its lines already fell, so a page can "
                        + "render \"this one is owed\" without reading a log")
                .containsExactly(theMorningItIsOwed);

        assertThat(theListed(savingsAccount, rule.id()).nextFiresOn())
                .as("the rule's own entry says the same day it next fires: the morning it owes, "
                        + "and not one the pause swallowed")
                .isEqualTo(theMorningItIsOwed);

        // And only now the job, once, exactly as a trainer runs it after winding.
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> happened = app.historyOf(savingsAccount, rule.id());
        assertThat(happened)
                .extracting(RuleOccurrenceView::dueOn)
                .as("the run moved money on exactly the one morning the preview named and on no "
                        + "other — three promised against one fired is the failure this test "
                        + "exists for")
                .containsExactly(theMorningItIsOwed);
        assertThat(happened.get(0).outcome()).isEqualTo("MOVED");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("ten euros altogether, which is one transfer rather than the three the preview "
                        + "would have promised")
                .isEqualByComparingTo(heldBefore.subtract(new BigDecimal(WHAT_IT_MOVES)));

        assertThat(app.previewOn(savingsAccount).occurrences().stream()
                .filter(coming -> coming.ruleId() == rule.id())
                .map(OccurrenceToComeView::dueOn)
                .findFirst())
                .as("and once it has fired, the morning it owed is no longer owed: the next line "
                        + "is the first one after the resume")
                .contains(theFirstMorningAfterTheResume);
    }

    /** The rule as its own account lists it, which is where {@code nextFiresOn} is read. */
    private static SavingRuleView theListed(long savingsAccount, long ruleId) {
        return app.rulesOn(savingsAccount).stream()
                .filter(listed -> listed.id() == ruleId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("rule " + ruleId + " is not in the list"));
    }
}
