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
 * The two gaps are distinguishable end to end: one wind of the clock, two identical rules, and the
 * live one is caught up in full while the paused one produces nothing.
 *
 * <p><strong>This is the feature's one real idea, asserted in a single night.</strong> Three weeks
 * pass with nobody running anything — downtime, which is the application's fault and is always
 * caught up — and in the same three weeks one of the two rules is paused, which is its customer's
 * instruction and is never made up. Both rules are due on the same day, draw from the same current
 * account and move the same figure, so the only thing that differs between them is which of the two
 * kinds of gap they were in.
 *
 * <p><strong>One run, and both directions of it.</strong> A job that caught nothing up would satisfy
 * "the paused rule fired nothing" while being entirely broken, and a job that ignored pausing would
 * satisfy "the live rule was caught up" while costing a customer three transfers they had said no
 * to. Neither can pass here: the numbers are three and nought in the same breath.
 *
 * <p>Weekly, and the day named off whatever day the clock reads, so nothing here depends on what
 * date it is when the suite runs.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class AWindOfTheClockIsCaughtUpInFullAndTheSameWindWhilePausedIsNotApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String WHAT_EACH_MOVES = "15.00";

    /** Three of the rule's days fall inside it, which is a catch-up worth the name. */
    private static final int HOW_LONG_NOBODY_WAS_LOOKING = 21;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDowntimeThisTestStages() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-downtime-against-a-pause"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_live_rule_catches_up_three_weeks_and_the_paused_one_catches_up_none() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItAllBegan = app.theDateTheClockReads();
        LocalDate theFirstDayTheyBothFallOn = theDayItAllBegan.plusDays(2);
        String theirDay = theFirstDayTheyBothFallOn.getDayOfWeek().name();
        BigDecimal heldBefore = app.currentAccountBalanceOf(ANKE);

        SavingRuleView throughDowntime = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "The one nobody stopped", theirDay, WHAT_EACH_MOVES));
        SavingRuleView throughAPause = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "The one she stopped", theirDay, WHAT_EACH_MOVES));
        app.pauseRule(savingsAccount, throughAPause.id());

        app.daysPass(HOW_LONG_NOBODY_WAS_LOOKING);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> caughtUp = app.historyOf(savingsAccount, throughDowntime.id());
        assertThat(caughtUp)
                .as("downtime is the application's fault and costs its customer nothing: every day "
                        + "that fell while nobody was looking is fired when it comes back")
                .hasSize(3);
        assertThat(caughtUp)
                .extracting(RuleOccurrenceView::dueOn)
                .as("recorded against the days they were actually due, oldest last in a history "
                        + "read newest first")
                .containsExactly(theFirstDayTheyBothFallOn.plusWeeks(2),
                        theFirstDayTheyBothFallOn.plusWeeks(1), theFirstDayTheyBothFallOn);
        assertThat(caughtUp).allSatisfy(occurrence ->
                assertThat(occurrence.outcome()).isEqualTo("MOVED"));

        assertThat(app.historyOf(savingsAccount, throughAPause.id()))
                .as("while the identical rule beside it, paused through exactly the same three "
                        + "weeks, has nothing at all: a pause is its customer's instruction and is "
                        + "honoured, so those days were never even considered")
                .isEmpty();

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("three transfers of fifteen euros and not six, which is the same sentence in "
                        + "money: the paused rule moved none of them")
                .isEqualByComparingTo(heldBefore.subtract(
                        new BigDecimal(WHAT_EACH_MOVES).multiply(BigDecimal.valueOf(3))));
        assertThat(app.depositsInto(savingsAccount))
                .as("and three deposits, counted off the account rather than off either rule's "
                        + "own record")
                .hasSize(3);

        assertThat(app.rulesOn(savingsAccount))
                .filteredOn(rule -> rule.id().equals(throughAPause.id()))
                .singleElement()
                .satisfies(read -> assertThat(read.state())
                        .as("and the paused rule is still there, still saying it is paused, so its "
                                + "holder can start it again")
                        .isEqualTo("PAUSED"));
    }
}
