package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
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
 * A preview quotes the same amount the rule then actually moves when the clock reaches that day —
 * and names the same day, in the same order, into the same goals.
 *
 * <p><strong>This is the only test of this ticket that can prove anything.</strong> A forecast
 * asserted against figures the test worked out for itself is a forecast checked against a second
 * opinion; the claim worth making is that the prediction and the firing agree, and the only way to
 * see it is to predict, wind the clock, run the real job, and put the two lists side by side. Every
 * other test here describes the shape of the answer; this one is about whether the answer is true.
 *
 * <p>Nothing in the comparison is written out by hand. The days, the figures and the goal
 * allocations are all read out of the preview <em>before</em> the clock moves, and then read out of
 * the rules' own histories afterwards. A preview that quoted its own arithmetic, and a firing that
 * used different arithmetic, would part company here and nowhere else.
 *
 * <p>Two rules on two days, one of them with a split, so that the claim covers the order across
 * rules as well as the days within one — and fixed amounts rather than sweeps, because only a fixed
 * amount is a figure a preview promises. What a sweep would move is an illustration of today's
 * balance by construction, and the balance on the day is a different balance precisely because the
 * rules have been moving money out of it.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test
 * winds the clock, and the file the rest of the run shares is not one to leave wound forward.
 */
class APreviewQuotesTheSameAmountTheRuleThenMovesApiTest extends ApiIntegrationTest {

    /** The name a trainer types into the development jobs endpoint. */
    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String FIFTY_EUROS = "50.00";

    private static final String FORTY_EUROS = "40.00";

    /**
     * How many days of the forecast are then actually lived through. Long enough to hold three
     * occurrences of each of the two rules, which is what makes the order across rules a claim
     * rather than a coincidence, and few enough that the account can cover all of them.
     *
     * <p><strong>It ends on one of the rules' own days on purpose.</strong> The last thing this test
     * asserts is that nothing already settled is still being promised, and a window that opened the
     * morning <em>after</em> the last transfer would satisfy that by arithmetic rather than by the
     * application having asked anything: the settled day would be behind the window's own left-hand
     * edge. Ending on a day one of the rules fires on puts that day inside the window it is then
     * read out of, so the assertion is about the record being asked.
     */
    private static final int HOW_MANY_DAYS_ARE_LIVED_THROUGH = 23;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseClockThisTestWinds() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-preview-comes-true"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void every_day_and_figure_the_preview_quoted_is_the_day_and_figure_the_job_then_moved() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        long currentAccount = app.currentAccountOf(ANKE);
        GoalView bike = app.openAGoal(savingsAccount, "Bike", "5000.00");
        GoalView holiday = app.openAGoal(savingsAccount, "Holiday", "5000.00");
        LocalDate today = app.theDateTheClockReads();
        LocalDate lastDayLivedThrough = today.plusDays(HOW_MANY_DAYS_ARE_LIVED_THROUGH);

        SavingRuleView plain = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(currentAccount, "Fifty a week",
                        today.plusDays(2).getDayOfWeek().name(), FIFTY_EUROS));
        SavingRuleView split = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.spreadAcross(
                        RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(currentAccount,
                                "Forty a week, split", today.plusDays(4).getDayOfWeek().name(),
                                FORTY_EUROS),
                        RulesAsSomebodyWouldTypeThem.inTurn(
                                RulesAsSomebodyWouldTypeThem.aShareFor(bike.id(), "60"),
                                RulesAsSomebodyWouldTypeThem.aShareFor(holiday.id(), "40"))));

        // The prediction, taken before a single day passes and never read again.
        List<OccurrenceToComeView> predicted = app.previewOn(savingsAccount).occurrences().stream()
                .filter(coming -> !coming.dueOn().isAfter(lastDayLivedThrough))
                .toList();
        assertThat(predicted)
                .as("four occurrences of the first rule and three of the second, and a prediction "
                        + "of nothing would make every assertion below pass for the wrong reason")
                .hasSize(7);
        assertThat(predicted.get(predicted.size() - 1).dueOn())
                .as("the last of them falls on the last day this test lives through, which is what "
                        + "puts a settled day inside the window the preview is read from at the end")
                .isEqualTo(lastDayLivedThrough);

        // The days themselves, one at a time, with the real job on each of them — which is how a
        // trainer lives through a month and is the only path on which an occurrence ever fires.
        for (int day = 0; day < HOW_MANY_DAYS_ARE_LIVED_THROUGH; day++) {
            app.daysPass(1);
            app.runJob(THE_JOB);
        }
        assertThat(app.theDateTheClockReads()).isEqualTo(lastDayLivedThrough);

        List<RuleOccurrenceView> happened = new ArrayList<>();
        happened.addAll(app.historyOf(savingsAccount, plain.id()));
        happened.addAll(app.historyOf(savingsAccount, split.id()));
        happened.sort(Comparator.comparing(RuleOccurrenceView::dueOn)
                .thenComparing(RuleOccurrenceView::ruleId));

        assertThat(happened)
                .as("exactly the transfers the preview said, and no others — a seventh would be a "
                        + "morning the customer was never shown")
                .hasSize(predicted.size());
        for (int i = 0; i < predicted.size(); i++) {
            OccurrenceToComeView wasPredicted = predicted.get(i);
            RuleOccurrenceView thenHappened = happened.get(i);
            assertThat(thenHappened.ruleId())
                    .as("the " + (i + 1) + "th transfer is the rule the preview said it would be, "
                            + "in the order the preview listed them")
                    .isEqualTo(wasPredicted.ruleId());
            assertThat(thenHappened.dueOn())
                    .as("on the day the preview named for it")
                    .isEqualTo(wasPredicted.dueOn());
            assertThat(thenHappened.outcome())
                    .as("and it moved, rather than finding nothing there")
                    .isEqualTo("MOVED");
            assertThat(thenHappened.amount())
                    .as("and it moved exactly the figure the preview quoted, which is the whole of "
                            + "what a preview is worth")
                    .isEqualByComparingTo(wasPredicted.wouldMove().amount());
            assertThat(thenHappened.intoGoals())
                    .as("into the goals the preview said, to the cent")
                    .extracting(RuleAllocationView::goalId, RuleAllocationView::amount)
                    .containsExactlyElementsOf(wasPredicted.wouldMove().intoGoals().stream()
                            .map(share -> Tuple.tuple(share.goalId(), share.amount()))
                            .toList());
            assertThat(thenHappened.leftUnallocated())
                    .as("and left the same behind for no goal to claim")
                    .isEqualByComparingTo(wasPredicted.wouldMove().leftUnallocated());
        }

        assertThat(totalOf(predicted))
                .as("and the money that actually left the current account comes to what a customer "
                        + "adding the preview up would have expected")
                .isEqualByComparingTo(new BigDecimal(FIFTY_EUROS).multiply(BigDecimal.valueOf(4))
                        .add(new BigDecimal(FORTY_EUROS).multiply(BigDecimal.valueOf(3))));
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("and it is all in the savings account")
                .isEqualByComparingTo(totalOf(predicted));

        // And the other direction: what has already happened is no longer coming. A preview read
        // after the run has to have moved on past every day it has now settled, or a customer would
        // be shown this morning's transfer a second time and could reasonably expect the money
        // twice.
        assertThat(app.previewOn(savingsAccount).occurrences())
                .as("nothing already settled is still in the preview, because the preview asks the "
                        + "record which periods this rule has already had its turn in — the same "
                        + "question the run asks")
                .allSatisfy(stillToCome -> assertThat(stillToCome.dueOn())
                        .isAfter(happened.get(happened.size() - 1).dueOn()));
    }

    /**
     * What a customer adding the preview up would have expected to see move — the sum only makes
     * sense because every line of it is a fixed amount, which is the half of this feature that can
     * be added up at all.
     */
    private static BigDecimal totalOf(List<OccurrenceToComeView> predicted) {
        return predicted.stream()
                .map(coming -> coming.wouldMove().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
