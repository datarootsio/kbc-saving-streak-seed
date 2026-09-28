package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A trainer who runs the rules job twice in a row moves the money once.
 *
 * <p>User story 40, and the reason the occurrence record is unique over the rule and the day it was
 * due rather than merely written carefully: a demonstration somebody repeats must not double every
 * figure in it, and a demonstration that did would be worse than no demonstration at all.
 *
 * <p>Both runs are in one test rather than in two, because the second is only meaningful against the
 * first: the claim is about the pair of runs and not about either alone.
 *
 * <p>The third run is not padding. Twice is what a trainer does by accident; a rule that settled its
 * occurrence on the second run and found it fresh again on the third would pass a test that stopped
 * at two, and the cursor and the record are two different guarantees to get wrong.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class RunningTheRulesJobTwiceMovesTheMoneyOnceApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String FIFTY_EUROS = "50.00";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseJobThisTestRunsTwice() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-running-the-rules-job-twice"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_money_moves_on_the_first_run_and_no_run_after_it_moves_any_more() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItMovesOn = app.theDateTheClockReads().plusDays(1);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Fifty a week", theDayItMovesOn.getDayOfWeek().name(), FIFTY_EUROS));
        BigDecimal beforeAnythingFired = app.currentAccountBalanceOf(ANKE);

        app.daysPass(1);
        app.runJob(THE_JOB);

        BigDecimal afterOneRun = app.currentAccountBalanceOf(ANKE);
        BigDecimal savedAfterOneRun = app.balancesOf(savingsAccount).moneyBalance();
        assertThat(afterOneRun)
                .as("the first run is the one that moves the money")
                .isEqualByComparingTo(beforeAnythingFired.subtract(new BigDecimal(FIFTY_EUROS)));

        app.runJob(THE_JOB);
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("a demonstration a trainer repeats must not double every figure in it: one row "
                        + "per rule per day due, unique in the database, is what makes every run "
                        + "after the first move nothing")
                .isEqualByComparingTo(afterOneRun);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("and nothing arrives in the savings account a second time either")
                .isEqualByComparingTo(savedAfterOneRun);
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("one day fell due, so there is one occurrence however many times the job is "
                        + "run — three occurrences here would be three deposits somewhere")
                .hasSize(1);
        assertThat(app.depositsInto(savingsAccount))
                .as("and one deposit, which is the figure a customer would be reading")
                .hasSize(1);
    }
}
