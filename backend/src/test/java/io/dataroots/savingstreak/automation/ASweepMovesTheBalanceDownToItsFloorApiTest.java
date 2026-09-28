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
 * A sweep moves everything above the floor its holder named, and nothing at all once the account is
 * already at that floor.
 *
 * <p>User stories 12 and 13, and the amount kind that only means anything because money now arrives:
 * a current account whose balance only ever went down would be swept once and be inert for ever.
 *
 * <p><strong>Both directions, and the second is the one that catches a sweep written as a
 * subtraction.</strong> A rule that moved {@code balance - floor} without asking whether that figure
 * is above nothing would move a negative amount out of an account already under its line — money
 * travelling the way nobody asked for. The week after the first sweep is exactly that case, and it
 * has to leave the account untouched.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ASweepMovesTheBalanceDownToItsFloorApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    /** Well under what the seeded account holds, so that the first sweep has a surplus to move. */
    private static final String THE_FLOOR = "800.00";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-sweep-moves-down-to-its-floor"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_sweep_takes_the_account_down_to_its_floor_and_then_finds_nothing_to_take() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        BigDecimal held = app.currentAccountBalanceOf(ANKE);
        BigDecimal floor = new BigDecimal(THE_FLOOR);
        assertThat(held)
                .as("this test needs an account with a surplus above the floor to sweep")
                .isGreaterThan(floor);

        LocalDate theDayItSweepsOn = app.theDateTheClockReads().plusDays(2);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.everythingAboveAFloorEveryWeek(
                        app.currentAccountOf(ANKE), "Everything above eight hundred",
                        theDayItSweepsOn.getDayOfWeek().name(), THE_FLOOR));
        BigDecimal saved = app.balancesOf(savingsAccount).moneyBalance();

        app.daysPass(2);
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("the sweep moved the balance down to the line its holder drew, and not past it")
                .isEqualByComparingTo(floor);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("and everything that was above the line is now in the savings account")
                .isEqualByComparingTo(saved.add(held.subtract(floor)));

        List<RuleOccurrenceView> afterTheFirstSweep = app.historyOf(savingsAccount, rule.id());
        assertThat(afterTheFirstSweep).hasSize(1);
        assertThat(afterTheFirstSweep.get(0).outcome()).isEqualTo("MOVED");
        assertThat(afterTheFirstSweep.get(0).amount())
                .isEqualByComparingTo(held.subtract(floor));

        // A week on, with nothing having arrived in the meantime: the account is sitting exactly on
        // its floor, which is the case a sweep written as a bare subtraction gets wrong.
        app.aWeekPasses();
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("there is nothing above the floor, so nothing moves and the account is left "
                        + "exactly as it was")
                .isEqualByComparingTo(floor);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("and nothing arrives in the savings account either")
                .isEqualByComparingTo(saved.add(held.subtract(floor)));

        List<RuleOccurrenceView> afterTheSecond = app.historyOf(savingsAccount, rule.id());
        assertThat(afterTheSecond)
                .as("the day still fell due, and a day that fell due is recorded whether money "
                        + "moved or not — that is the half a deposits ledger can never hold")
                .hasSize(2);
        RuleOccurrenceView theSecond = afterTheSecond.get(0);
        assertThat(theSecond.dueOn())
                .as("newest first, so the week that has just passed is the one at the front")
                .isEqualTo(theDayItSweepsOn.plusWeeks(1));
        assertThat(theSecond.outcome())
                .as("nothing to move is arithmetic rather than a failure, and is said as such")
                .isEqualTo("NOTHING_TO_MOVE");
        assertThat(theSecond.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(theSecond.depositId())
                .as("nothing moved, so there is no deposit to name")
                .isNull();
    }
}
