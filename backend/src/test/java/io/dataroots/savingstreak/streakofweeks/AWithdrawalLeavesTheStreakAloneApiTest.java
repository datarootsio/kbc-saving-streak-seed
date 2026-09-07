package io.dataroots.savingstreak.streakofweeks;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Taking money back out is invisible to a run of weeks. A withdrawal of any size, at any point in a
 * week, neither ends a run nor un-secures a week that had already taken the minimum in, nor reduces
 * a week's progress towards it.
 *
 * <p>Both halves are asserted, because they are two different promises. A week already secured stays
 * secured however much of the money leaves again — even all of it, which is the emptied account
 * below. And a week only part-way there keeps the progress it has: EUR 30 in, EUR 10 out and EUR 20
 * in is a week that has taken EUR 50 in, and it secures.
 *
 * <p>Its own application, its own database and its own clock, for the reason
 * {@link AnApplicationWithAClockToMove} gives. One test, because the clock only goes forward.
 */
class AWithdrawalLeavesTheStreakAloneApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-withdrawals-leave-it-alone"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void money_taken_back_out_neither_ends_a_run_nor_un_secures_the_week_it_came_from() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // The first week secured, and then emptied. The whole EUR 60 goes back to the current
        // account, so the savings account holds nothing at all — and the week has still taken EUR 60
        // in, because a withdrawal does not un-happen the deposit it came out of.
        app.deposit(savingsAccount, ANKE, "60.00");
        assertThat(app.balancesOf(savingsAccount).currentStreakWeeks()).isEqualTo(1);
        app.withdraw(savingsAccount, ANKE, "60.00");

        BalancesView emptied = app.balancesOf(savingsAccount);
        assertThat(emptied.moneyBalance()).isEqualByComparingTo("0.00");
        assertThat(emptied.newSavingsThisWeek()).isEqualByComparingTo("60.00");
        assertThat(emptied.stillNeededThisWeek()).isEqualByComparingTo("0.00");
        assertThat(emptied.currentStreakWeeks()).isEqualTo(1);
        assertThat(emptied.bestStreakWeeks()).isEqualTo(1);
        // And it took nothing back that was already earned.
        assertThat(emptied.pointsBalance()).isEqualTo(60);

        app.aWeekPasses();

        // The second week, with a withdrawal in the middle of it — before the week has what it asks
        // for. The money that left is still counted towards the week, so EUR 30 in, EUR 10 out and
        // EUR 20 in is a week that has taken EUR 50 in and secures on the last of them.
        app.deposit(savingsAccount, ANKE, "30.00");
        app.withdraw(savingsAccount, ANKE, "10.00");
        BalancesView partWayThrough = app.balancesOf(savingsAccount);
        assertThat(partWayThrough.moneyBalance()).isEqualByComparingTo("20.00");
        assertThat(partWayThrough.newSavingsThisWeek()).isEqualByComparingTo("30.00");
        assertThat(partWayThrough.currentStreakWeeks()).isEqualTo(1);

        app.deposit(savingsAccount, ANKE, "20.00");

        BalancesView secondWeekSecured = app.balancesOf(savingsAccount);
        assertThat(secondWeekSecured.newSavingsThisWeek()).isEqualByComparingTo("50.00");
        assertThat(secondWeekSecured.stillNeededThisWeek()).isEqualByComparingTo("0.00");
        assertThat(secondWeekSecured.currentStreakWeeks()).isEqualTo(2);
        assertThat(secondWeekSecured.bestStreakWeeks()).isEqualTo(2);
    }
}
