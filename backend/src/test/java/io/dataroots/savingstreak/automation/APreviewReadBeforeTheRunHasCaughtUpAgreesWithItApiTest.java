package io.dataroots.savingstreak.automation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.OccurrenceToComeView;
import io.dataroots.savingstreak.support.RuleAllocationView;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wind the clock several days, read the preview, <em>then</em> run the job — and the transfers the
 * run makes are exactly the ones the preview named.
 *
 * <p><strong>The shape that actually bites, and the one the first attempt at this ticket got
 * wrong.</strong> {@code MovableClock} moves in whole calendar days and a cron expression never
 * fires for the days it skipped, so between a wind and a run <em>every</em> rule is behind its own
 * cursor: catching up is not an edge case here but the only way an occurrence ever fires. A forecast
 * that counted from today rather than from the rule's cursor answered a different question from the
 * run — it showed a trainer a year of transfers starting next month, said the rule next fired next
 * month, and then the very next run moved money on two mornings it had never mentioned. Living
 * through the days one at a time and running the job on each, as
 * {@link APreviewQuotesTheSameAmountTheRuleThenMovesApiTest} does, never lets the cursor fall more
 * than a night behind and so cannot see it.
 *
 * <p>So the days pass here with the job deliberately <em>not</em> run, which is what a trainer does
 * when they wind a fortnight forward and open the page before pressing anything, and what downtime
 * does by itself.
 *
 * <p>Nothing in the comparison is written out by hand: the days, the figures and the goal shares are
 * read out of the preview before the job runs and out of the rules' own histories afterwards.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test
 * winds the clock, and the file the rest of the run shares is not one to leave wound forward.
 */
class APreviewReadBeforeTheRunHasCaughtUpAgreesWithItApiTest extends ApiIntegrationTest {

    /** The name a trainer types into the development jobs endpoint. */
    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String TWENTY_FIVE_EUROS = "25.00";

    private static final String THIRTY_EUROS = "30.00";

    /**
     * How many days are wound through before anything is run. Long enough for both rules to fall due
     * more than once, so that what the run owes is a list rather than a single morning, and well
     * past the one day a forward-looking window would have clamped itself to.
     */
    private static final int HOW_MANY_DAYS_ARE_WOUND_THROUGH = 10;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseClockThisTestWinds() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-preview-before-the-catch-up"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_preview_names_every_morning_the_next_run_then_moves_money_on() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        long currentAccount = app.currentAccountOf(ANKE);
        GoalView bike = app.openAGoal(savingsAccount, "Bike", "5000.00");
        GoalView holiday = app.openAGoal(savingsAccount, "Holiday", "5000.00");
        LocalDate theDayTheRulesWereWritten = app.theDateTheClockReads();
        LocalDate theDayTheJobIsFinallyRun =
                theDayTheRulesWereWritten.plusDays(HOW_MANY_DAYS_ARE_WOUND_THROUGH);

        SavingRuleView plain = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(currentAccount,
                        "Twenty-five a week",
                        theDayTheRulesWereWritten.plusDays(2).getDayOfWeek().name(),
                        TWENTY_FIVE_EUROS));
        SavingRuleView split = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.spreadAcross(
                        RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(currentAccount,
                                "Thirty a week, split",
                                theDayTheRulesWereWritten.plusDays(4).getDayOfWeek().name(),
                                THIRTY_EUROS),
                        RulesAsSomebodyWouldTypeThem.inTurn(
                                RulesAsSomebodyWouldTypeThem.aShareFor(bike.id(), "70"),
                                RulesAsSomebodyWouldTypeThem.aShareFor(holiday.id(), "30"))));

        // The days pass with nothing run on any of them. This is the whole of the shape.
        app.daysPass(HOW_MANY_DAYS_ARE_WOUND_THROUGH);
        assertThat(app.theDateTheClockReads()).isEqualTo(theDayTheJobIsFinallyRun);

        List<OccurrenceToComeView> predicted = app.previewOn(savingsAccount).occurrences().stream()
                .filter(coming -> !coming.dueOn().isAfter(theDayTheJobIsFinallyRun))
                .toList();
        assertThat(predicted)
                .as("three mornings fell while the clock was wound and the job was not run — two of "
                        + "the first rule and one of the second — and the preview owes the customer "
                        + "every one of them, because the next run is about to make all three")
                .hasSize(3);
        assertThat(predicted).extracting(OccurrenceToComeView::dueOn)
                .as("on the days they actually fell, oldest first")
                .containsExactly(theDayTheRulesWereWritten.plusDays(2),
                        theDayTheRulesWereWritten.plusDays(4),
                        theDayTheRulesWereWritten.plusDays(9));
        assertThat(predicted.get(0).dueOn())
                .as("and the first of them is behind today, which is the point: a forecast that "
                        + "began at the day it was read would have dropped all three")
                .isBefore(theDayTheJobIsFinallyRun);

        List<SavingRuleView> asListed = app.rulesOn(savingsAccount);
        assertThat(asListed).extracting(SavingRuleView::id, SavingRuleView::nextFiresOn)
                .as("and each rule in the account's list says the same day it next fires as the "
                        + "preview does — the oldest morning it owes, not the next one on the "
                        + "calendar")
                .contains(Tuple.tuple(plain.id(), theDayTheRulesWereWritten.plusDays(2)),
                        Tuple.tuple(split.id(), theDayTheRulesWereWritten.plusDays(4)));

        // And only now the job, once, exactly as a trainer runs it after winding.
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> happened = new ArrayList<>();
        happened.addAll(app.historyOf(savingsAccount, plain.id()));
        happened.addAll(app.historyOf(savingsAccount, split.id()));
        happened.sort(Comparator.comparing(RuleOccurrenceView::dueOn)
                .thenComparing(RuleOccurrenceView::ruleId));

        assertThat(happened)
                .as("the run moved money on exactly the mornings the preview named, and on no "
                        + "others — a transfer the customer was never shown is the failure this "
                        + "test exists for")
                .hasSize(predicted.size());
        for (int i = 0; i < predicted.size(); i++) {
            OccurrenceToComeView wasPredicted = predicted.get(i);
            RuleOccurrenceView thenHappened = happened.get(i);
            assertThat(thenHappened.ruleId())
                    .as("the " + (i + 1) + "th transfer is the rule the preview said it would be")
                    .isEqualTo(wasPredicted.ruleId());
            assertThat(thenHappened.dueOn())
                    .as("on the day the preview named for it")
                    .isEqualTo(wasPredicted.dueOn());
            assertThat(thenHappened.outcome())
                    .as("and it moved, rather than finding nothing there")
                    .isEqualTo("MOVED");
            assertThat(thenHappened.amount())
                    .as("and it moved exactly the figure the preview quoted")
                    .isEqualByComparingTo(wasPredicted.wouldMove().amount());
            assertThat(thenHappened.intoGoals())
                    .as("into the goals the preview said, to the cent")
                    .extracting(RuleAllocationView::goalId, RuleAllocationView::amount)
                    .containsExactlyElementsOf(wasPredicted.wouldMove().intoGoals().stream()
                            .map(share -> Tuple.tuple(share.goalId(), share.amount()))
                            .toList());
        }

        // And the other direction, so that the forecast reaching backwards cannot be mistaken for it
        // promising things twice: what has now been settled is no longer owed.
        assertThat(app.previewOn(savingsAccount).occurrences())
                .as("nothing the run has just settled is still in the preview, because the record "
                        + "is asked which periods each rule has already had its turn in")
                .allSatisfy(stillToCome -> assertThat(stillToCome.dueOn())
                        .isAfter(happened.get(happened.size() - 1).dueOn()));
        assertThat(app.rulesOn(savingsAccount))
                .extracting(SavingRuleView::nextFiresOn)
                .as("and every rule has moved its next day on past the mornings it has just fired")
                .allSatisfy(nextFiresOn -> assertThat(nextFiresOn)
                        .isAfter(happened.get(happened.size() - 1).dueOn()));
    }
}
