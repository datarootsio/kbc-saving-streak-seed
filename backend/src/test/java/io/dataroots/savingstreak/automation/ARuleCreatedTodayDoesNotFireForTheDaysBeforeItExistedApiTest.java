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
 * A rule left standing today does not fire for the days before it existed, and does fire for the
 * first of its days that comes after.
 *
 * <p>User story 42. A rule's cursor starts at the moment it was written, so a weekly rule set today
 * for a day that has already gone this week waits for next week's — a new rule is an instruction
 * about the future rather than a bill for the past.
 *
 * <p><strong>Both directions, and the pair is the whole claim.</strong> A rule that never fired at
 * all would satisfy "it did not fire for last week" perfectly, which is why the day after is
 * asserted in the same test: it has to stay quiet about the past <em>and</em> fire on its next day.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ARuleCreatedTodayDoesNotFireForTheDaysBeforeItExistedApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String TWENTY_EUROS = "20.00";

    /** How long ago the day the rule is set for last fell, before the rule was ever written. */
    private static final int DAYS_AGO = 2;

    private static final int DAYS_IN_A_WEEK = 7;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeekThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-new-rule-is-not-a-bill"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void it_passes_over_the_day_that_had_already_gone_and_fires_on_the_next_one() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayThatHasAlreadyGone = app.theDateTheClockReads().minusDays(DAYS_AGO);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Twenty a week", theDayThatHasAlreadyGone.getDayOfWeek().name(),
                        TWENTY_EUROS));
        BigDecimal beforeAnythingFired = app.currentAccountBalanceOf(ANKE);

        app.runJob(THE_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("the rule's day last fell two days before the rule was written, and a rule "
                        + "that fired for it would be charging its holder for a week they had not "
                        + "asked to save in")
                .isEmpty();
        assertThat(app.currentAccountBalanceOf(ANKE)).isEqualByComparingTo(beforeAnythingFired);

        LocalDate theNextOneComing = theDayThatHasAlreadyGone.plusDays(DAYS_IN_A_WEEK);
        app.daysPass(DAYS_IN_A_WEEK - DAYS_AGO);
        assertThat(app.theDateTheClockReads()).isEqualTo(theNextOneComing);

        app.runJob(THE_JOB);

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history)
                .as("and now the first of its days that falls after it was written has come, so it "
                        + "fires — once")
                .hasSize(1);
        assertThat(history.get(0).dueOn())
                .as("against the day that came after the rule existed, not the one that went before")
                .isEqualTo(theNextOneComing);
        assertThat(app.currentAccountBalanceOf(ANKE))
                .isEqualByComparingTo(beforeAnythingFired.subtract(new BigDecimal(TWENTY_EUROS)));
    }
}
