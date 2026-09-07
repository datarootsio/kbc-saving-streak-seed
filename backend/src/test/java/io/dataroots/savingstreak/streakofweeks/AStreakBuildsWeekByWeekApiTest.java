package io.dataroots.savingstreak.streakofweeks;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A savings account with no history at all secures its first week, then the week after it, and
 * reports a run of one week and then of two.
 *
 * <p>Its own application on a database nothing has ever been written to, because a run counted from
 * zero needs an account nothing has landed in and a clock only this class moves — see
 * {@link AnApplicationWithAClockToMove}.
 *
 * <p>One test, because the clock only goes forward: a second method in this class would find the
 * weeks already moved on and would be asserting against whatever order the two happened to run in.
 * The narrative is asserted on after every step instead, which is what a run of weeks is.
 */
class AStreakBuildsWeekByWeekApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-builds-week-by-week"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_week_secured_and_then_the_week_after_it_make_a_run_of_two() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        long theSameCustomersOther = app.otherSavingsAccountOf(ANKE);

        // Nothing has ever landed in it: no run now, and no run to remember.
        BalancesView beforeAnythingLanded = app.balancesOf(savingsAccount);
        assertThat(beforeAnythingLanded.newSavingsThisWeek()).isEqualByComparingTo("0.00");
        assertThat(beforeAnythingLanded.currentStreakWeeks()).isZero();
        assertThat(beforeAnythingLanded.bestStreakWeeks()).isZero();

        // The first week, secured by a single deposit of exactly what a week asks for. At least the
        // minimum, not more than it: a week that took in precisely EUR 50 has done what was asked.
        DepositView securedTheFirstWeek = app.deposit(savingsAccount, ANKE, "50.00");
        BalancesView weekOne = app.balancesOf(savingsAccount);
        assertThat(weekOne.newSavingsThisWeek()).isEqualByComparingTo("50.00");
        assertThat(weekOne.stillNeededThisWeek()).isEqualByComparingTo("0.00");
        assertThat(weekOne.currentStreakWeeks()).isEqualTo(1);
        assertThat(weekOne.bestStreakWeeks()).isEqualTo(1);
        // Nothing about the points changed: a euro paid in still earns a point, and the streak is a
        // figure beside that rather than a change to it.
        assertThat(securedTheFirstWeek.pointsEarned()).isEqualTo(50);
        assertThat(weekOne.pointsBalance()).isEqualTo(50);

        app.aWeekPasses();

        // A new week asks for the whole minimum again, and the run behind it is untouched: the week
        // just gone is the one immediately before this one, so the run is still alive.
        BalancesView weekTwoBeforeAnythingLanded = app.balancesOf(savingsAccount);
        assertThat(weekTwoBeforeAnythingLanded.newSavingsThisWeek()).isEqualByComparingTo("0.00");
        assertThat(weekTwoBeforeAnythingLanded.stillNeededThisWeek())
                .isEqualByComparingTo(weekTwoBeforeAnythingLanded.weeklyMinimum());
        assertThat(weekTwoBeforeAnythingLanded.currentStreakWeeks()).isEqualTo(1);

        // The second week is secured by three deposits that only together reach the minimum, which
        // is what makes this worth asserting step by step: a week is secured by what has landed in
        // it and not by any one deposit, so neither of the first two moves the run.
        app.deposit(savingsAccount, ANKE, "20.00");
        assertThat(app.balancesOf(savingsAccount).currentStreakWeeks()).isEqualTo(1);
        app.deposit(savingsAccount, ANKE, "25.00");
        assertThat(app.balancesOf(savingsAccount).currentStreakWeeks()).isEqualTo(1);
        app.deposit(savingsAccount, ANKE, "5.00");

        BalancesView weekTwo = app.balancesOf(savingsAccount);
        // Together exactly the minimum, so this week is secured exactly as the single EUR 50 deposit
        // secured the first one.
        assertThat(weekTwo.newSavingsThisWeek()).isEqualByComparingTo("50.00");
        assertThat(weekTwo.stillNeededThisWeek()).isEqualByComparingTo("0.00");
        assertThat(weekTwo.currentStreakWeeks()).isEqualTo(2);
        assertThat(weekTwo.bestStreakWeeks()).isEqualTo(2);
        assertThat(weekTwo.pointsBalance()).isEqualTo(100);

        // The same customer's other savings account watched the whole thing and has no run at all.
        // Every figure in this application is per-account, and a streak is not the exception: one
        // account's saving is not quietly funding the other's.
        BalancesView theOtherAccount = app.balancesOf(theSameCustomersOther);
        assertThat(theOtherAccount.newSavingsThisWeek()).isEqualByComparingTo("0.00");
        assertThat(theOtherAccount.currentStreakWeeks()).isZero();
        assertThat(theOtherAccount.bestStreakWeeks()).isZero();
    }
}
