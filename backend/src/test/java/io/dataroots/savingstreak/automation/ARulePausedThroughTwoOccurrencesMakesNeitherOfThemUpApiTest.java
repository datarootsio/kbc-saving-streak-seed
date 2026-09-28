package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * A rule paused through two of its days, then resumed, makes neither of them up: it fires once, on
 * its next day, rather than three times on the morning it comes back.
 *
 * <p>User story 32, and the sentence this whole ticket exists for. <strong>Downtime is always caught
 * up and a pause is never made up</strong>: both look like occurrences that did not fire, and the
 * application has to treat them opposite ways, because one is its own fault and the other is its
 * customer's instruction. A customer who paused through a month where money was tight and came back
 * to three transfers in one morning would never press the button again.
 *
 * <p><strong>Nothing is recorded for the days the pause covered</strong>, and that is asserted as
 * well as the money. An occurrence written and marked as skipped would be this application saying it
 * considered moving money on a morning its customer had told it not to; the history of those weeks
 * is empty because nothing happened in those weeks.
 *
 * <p><strong>The second press of pause is nine days after the first</strong>, on a clock this test
 * winds, which is the only place that assertion can bite: a second pause that re-stamped the moment
 * it began would shorten the window of days that are never made up, and a shared application's clock
 * would hide the difference in a rounding error.
 *
 * <p>Weekly, and every day named off the day the clock happens to read, so that nothing here depends
 * on what date it is when the suite runs.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ARulePausedThroughTwoOccurrencesMakesNeitherOfThemUpApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String WHAT_IT_MOVES = "40.00";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestPausesThrough() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-rule-paused-through-two-weeks"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_two_days_it_was_paused_through_are_never_made_up_and_never_recorded() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItAllBegan = app.theDateTheClockReads();
        LocalDate theFirstDayItWouldHaveMovedOn = theDayItAllBegan.plusDays(2);
        String itsDay = theFirstDayItWouldHaveMovedOn.getDayOfWeek().name();
        BigDecimal heldBefore = app.currentAccountBalanceOf(ANKE);
        BigDecimal savedBefore = app.balancesOf(savingsAccount).moneyBalance();

        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Forty a week, when I can", itsDay, WHAT_IT_MOVES));
        SavingRuleView paused = app.pauseRule(savingsAccount, rule.id());
        Instant theMomentItWasPaused = paused.pausedAt();
        assertThat(theMomentItWasPaused).isNotNull();

        // Two of its days fall while it is stopped: the one two days from now, and the one a week
        // after that.
        app.daysPass(9);
        app.runJob(THE_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("a paused rule fires nothing, and records nothing either: an occurrence written "
                        + "and marked as skipped would be this application saying it considered "
                        + "moving money on a morning its customer had told it not to")
                .isEmpty();
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and no euro left the current account while it was stopped")
                .isEqualByComparingTo(heldBefore);

        // The same button a second time, nine days on. It changes nothing, and in particular does
        // not move the moment the pause began — which is one end of the window of days that are
        // never made up.
        SavingRuleView pressedAgain = app.pauseRule(savingsAccount, rule.id());
        assertThat(pressedAgain.pausedAt())
                .as("pressing pause again is accepted quietly and re-stamps nothing: a pause that "
                        + "began again here would leave the two days it has already covered outside "
                        + "any window, and the first of them would be made up on the next run")
                .isEqualTo(asTheDatabaseKeepsIt(theMomentItWasPaused));

        LocalDate theDayItWasResumedOn = app.theDateTheClockReads();
        app.resumeRule(savingsAccount, rule.id());

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("resuming makes nothing up by itself either: the two days it was paused through "
                        + "are gone, not queued")
                .isEmpty();

        // On to its next day, which is a week after the last one it missed.
        app.daysPass(7);
        LocalDate itsFirstDayBack = theDayItWasResumedOn.plusDays(7);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history)
                .as("once, on its next day, rather than three times on the morning it came back — "
                        + "which is the whole difference between a pause and downtime")
                .hasSize(1);
        assertThat(history.get(0).dueOn())
                .as("and on the day that actually fell after the resume, not on either of the two "
                        + "the pause covered")
                .isEqualTo(itsFirstDayBack);
        assertThat(history)
                .extracting(RuleOccurrenceView::dueOn)
                .doesNotContain(theFirstDayItWouldHaveMovedOn,
                        theFirstDayItWouldHaveMovedOn.plusWeeks(1));
        assertThat(history.get(0).outcome()).isEqualTo("MOVED");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("forty euros altogether, which is one transfer: three would be a customer "
                        + "handed a lump sum they never asked for")
                .isEqualByComparingTo(heldBefore.subtract(new BigDecimal(WHAT_IT_MOVES)));
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(savedBefore.add(new BigDecimal(WHAT_IT_MOVES)));
        assertThat(app.depositsInto(savingsAccount))
                .as("and one deposit, counted off the account rather than off the rule's own record")
                .hasSize(1);
    }

    /**
     * The same moment, to the precision this application actually keeps one at. A moment answered
     * straight out of a write carries the clock's own microseconds; the same moment read back has
     * been through SQLite, which holds it to the millisecond.
     */
    private static Instant asTheDatabaseKeepsIt(Instant moment) {
        return moment.truncatedTo(ChronoUnit.MILLIS);
    }
}
