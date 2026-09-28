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
 * Both halves of the one distinction, on one rule and in one sitting: the days a rule was already
 * owed when its holder pressed pause are caught up afterwards, and the days that fell inside the
 * pause never are.
 *
 * <p><strong>The half that was missing.</strong> Everything else about this feature tests a pause
 * that swallows the right days. This tests the other edge of the same window — that it swallows
 * nothing outside itself, including the past. A rule whose cursor is already behind because nobody
 * ran the job for a fortnight is owed that fortnight; it is downtime, which is this application's
 * fault, and the spec says downtime is always caught up. Pressing pause and pressing resume is an
 * instruction about the days between the two presses and says nothing whatever about the fortnight
 * before them. An implementation that moved the rule's cursor to the resume moment would quietly
 * write that fortnight off, and every other test in this package would still pass — which is exactly
 * how it got as far as a reviewer.
 *
 * <p><strong>And the half that was already right, immediately after it and on the same rule</strong>,
 * because the two are one decision and a test that proved only one of them could be satisfied by an
 * application that made everything up or by one that made nothing up. The second stretch of this
 * test is a real pause over two of the rule's days, and neither of them is ever fired or recorded.
 *
 * <p>Weekly, and every day named off the day the clock happens to read, so that nothing here depends
 * on what date it is when the suite runs.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ARulePausedAndResumedStillCatchesUpTheDowntimeThatFellBeforeThePauseApiTest
        extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String WHAT_IT_MOVES = "15.00";

    /** Long enough for two of its days to fall, and short enough for the third not to. */
    private static final int HOW_LONG_NOBODY_RAN_THE_JOB = 15;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDowntimeThisTestStraddlesWithAPause() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-downtime-straddled-by-a-pause"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_days_owed_before_the_pause_are_caught_up_and_the_days_inside_it_never_are() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItAllBegan = app.theDateTheClockReads();
        LocalDate itsFirstDay = theDayItAllBegan.plusDays(2);
        String itsDay = itsFirstDay.getDayOfWeek().name();
        BigDecimal heldBefore = app.currentAccountBalanceOf(ANKE);

        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Fifteen a week, through thick and thin", itsDay, WHAT_IT_MOVES));

        // Nobody runs the job for a fortnight. Two of the rule's days fall in it and neither fires:
        // this is downtime, and at this moment the rule is owed both of them.
        app.daysPass(HOW_LONG_NOBODY_RAN_THE_JOB);
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("nothing has fired yet, because nothing has run yet — the rule is owed two days")
                .isEmpty();

        // She presses pause, thinks better of it, and presses resume. No day passes between them.
        app.pauseRule(savingsAccount, rule.id());
        app.resumeRule(savingsAccount, rule.id());

        app.runJob(THE_JOB);

        List<RuleOccurrenceView> caughtUp = app.historyOf(savingsAccount, rule.id());
        assertThat(caughtUp)
                .extracting(RuleOccurrenceView::dueOn)
                .as("the two days the rule was already owed when she pressed pause are caught up in "
                        + "full: they fell before the pause began, so they are downtime — this "
                        + "application's own fault — and a pause is an instruction about the days "
                        + "inside it and about nothing else")
                .containsExactly(itsFirstDay.plusWeeks(1), itsFirstDay);
        assertThat(caughtUp).allSatisfy(occurrence ->
                assertThat(occurrence.outcome()).isEqualTo("MOVED"));
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and in money: two transfers of fifteen euros she never asked to lose")
                .isEqualByComparingTo(heldBefore.subtract(
                        new BigDecimal(WHAT_IT_MOVES).multiply(BigDecimal.valueOf(2))));

        // And now the other half, on the same rule: a pause she means, over two of its days.
        LocalDate theDayShePausedItForReal = app.theDateTheClockReads();
        app.pauseRule(savingsAccount, rule.id());
        app.daysPass(8);
        LocalDate theDayShePickedItUpAgain = app.theDateTheClockReads();
        app.resumeRule(savingsAccount, rule.id());
        app.runJob(THE_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("the two days inside that pause are not made up by the resume, nor by the run "
                        + "after it: a pause is her own instruction and nothing hands it back")
                .hasSize(2);

        app.daysPass(7);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> all = app.historyOf(savingsAccount, rule.id());
        assertThat(all)
                .extracting(RuleOccurrenceView::dueOn)
                .as("three days altogether: the two she was owed from before the first pause and "
                        + "the first one after the second resume — never the two inside the pause")
                .containsExactly(itsFirstDay.plusWeeks(4), itsFirstDay.plusWeeks(1), itsFirstDay);
        assertThat(all)
                .extracting(RuleOccurrenceView::dueOn)
                .as("named the other way round as well, so that this cannot pass by counting")
                .doesNotContain(itsFirstDay.plusWeeks(2), itsFirstDay.plusWeeks(3));
        assertThat(theDayShePausedItForReal).isBefore(itsFirstDay.plusWeeks(2));
        assertThat(theDayShePickedItUpAgain).isEqualTo(itsFirstDay.plusWeeks(3));
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("three transfers of fifteen euros and not five")
                .isEqualByComparingTo(heldBefore.subtract(
                        new BigDecimal(WHAT_IT_MOVES).multiply(BigDecimal.valueOf(3))));
        assertThat(app.depositsInto(savingsAccount))
                .as("counted off the account rather than off the rule's own record")
                .hasSize(3);
    }
}
