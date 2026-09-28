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
 * A week counts what the customer put away less what they took back out during it, so money that
 * does not stay does not secure a week.
 *
 * <p>The rule this replaces counted deposits only, and it could be farmed: EUR 50 paid in on Monday
 * and taken straight back out on Tuesday secured the week, so the same fifty euros secured every
 * week for ever and walked anybody who cared to up the whole multiplier ladder — six weeks of
 * churning one deposit, and the seventh week's real saving was paid at 1.50×, half as much again for
 * money nobody had saved.
 *
 * <p>Which week a withdrawal counts against is the week it was made in, not the week the deposit it
 * drew down landed in. So a customer who saves for six weeks and dips into the savings in the
 * seventh loses the seventh week and keeps the six: what has already stayed for a week has stayed.
 *
 * <p>Its own application, its own database and its own clock, for the reason
 * {@link AnApplicationWithAClockToMove} gives. One test, because the clock only goes forward and
 * this is one story told in order.
 */
class AWithdrawalCountsAgainstTheWeekItWasMadeInApiTest extends ApiIntegrationTest {

    /** What a week asks for, which every figure below is placed either side of. */
    private static final String THE_WEEKLY_MINIMUM = "50.00";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-withdrawals-count-against-the-week"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void money_that_does_not_stay_secures_no_week_and_builds_no_run() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // The first week, secured the ordinary way.
        app.deposit(savingsAccount, ANKE, "60.00");
        BalancesView secured = app.balancesOf(savingsAccount);
        assertThat(secured.newSavingsThisWeek()).isEqualByComparingTo("60.00");
        assertThat(secured.currentStreakWeeks()).isEqualTo(1);
        assertThat(secured.bestStreakWeeks()).isEqualTo(1);

        // And then emptied again, in the same week. The money was never put away, so the week has
        // nothing in it: this is the round trip the old rule paid for.
        app.withdraw(savingsAccount, ANKE, "60.00");

        BalancesView emptied = app.balancesOf(savingsAccount);
        assertThat(emptied.moneyBalance()).isEqualByComparingTo("0.00");
        assertThat(emptied.newSavingsThisWeek())
                .as("sixty in and sixty back out is nothing put away")
                .isEqualByComparingTo("0.00");
        assertThat(emptied.stillNeededThisWeek()).isEqualByComparingTo(THE_WEEKLY_MINIMUM);
        assertThat(emptied.currentStreakWeeks()).isZero();
        assertThat(emptied.bestStreakWeeks())
                .as("the week was never secured, so there is no record of one")
                .isZero();
        assertThat(emptied.pointsBalance())
                .as("the points the deposit earned are untouched — a withdrawal takes nothing back, "
                        + "and losing the week is the whole of what it costs")
                .isEqualTo(60);

        // Saved again, and this time it stays. The same week is secured on what is in it now.
        app.deposit(savingsAccount, ANKE, "60.00");
        BalancesView securedAgain = app.balancesOf(savingsAccount);
        assertThat(securedAgain.newSavingsThisWeek()).isEqualByComparingTo("60.00");
        assertThat(securedAgain.currentStreakWeeks()).isEqualTo(1);

        app.aWeekPasses();

        // The second week, with a withdrawal in the middle of it. EUR 30 in, EUR 10 out and EUR 20
        // in is EUR 40 put away, which is short of what the week asks for.
        app.deposit(savingsAccount, ANKE, "30.00");
        app.withdraw(savingsAccount, ANKE, "10.00");
        app.deposit(savingsAccount, ANKE, "20.00");
        BalancesView partWayThrough = app.balancesOf(savingsAccount);
        assertThat(partWayThrough.newSavingsThisWeek()).isEqualByComparingTo("40.00");
        assertThat(partWayThrough.stillNeededThisWeek()).isEqualByComparingTo("10.00");
        assertThat(partWayThrough.currentStreakWeeks())
                .as("last week is secured and this one is still running, so the run stands at one")
                .isEqualTo(1);

        // The ten it was short of, which carries the week over the line and the run to two.
        app.deposit(savingsAccount, ANKE, "10.00");
        BalancesView secondWeekSecured = app.balancesOf(savingsAccount);
        assertThat(secondWeekSecured.newSavingsThisWeek()).isEqualByComparingTo(THE_WEEKLY_MINIMUM);
        assertThat(secondWeekSecured.stillNeededThisWeek()).isEqualByComparingTo("0.00");
        assertThat(secondWeekSecured.currentStreakWeeks()).isEqualTo(2);
        assertThat(secondWeekSecured.bestStreakWeeks()).isEqualTo(2);

        // Now the farm, run properly: the weekly minimum paid in and taken straight back out again,
        // week after week. Under the old rule this was a run of six and a rate of 1.50×.
        for (int week = 1; week <= 4; week++) {
            app.aWeekPasses();
            app.deposit(savingsAccount, ANKE, THE_WEEKLY_MINIMUM);
            app.withdraw(savingsAccount, ANKE, THE_WEEKLY_MINIMUM);
            assertThat(app.balancesOf(savingsAccount).newSavingsThisWeek())
                    .as("churn week %d put nothing away", week)
                    .isEqualByComparingTo("0.00");
        }

        BalancesView afterTheChurn = app.balancesOf(savingsAccount);
        assertThat(afterTheChurn.currentStreakWeeks())
                .as("four weeks of money that never stayed end the run rather than extend it")
                .isZero();
        assertThat(afterTheChurn.currentMultiplier())
                .as("and the ladder is not climbed by moving the same fifty euros back and forth")
                .isEqualByComparingTo("1.00");
        assertThat(afterTheChurn.bestStreakWeeks())
                .as("the two weeks that were genuinely saved are still the record")
                .isEqualTo(2);
        assertThat(afterTheChurn.moneyBalance())
                .as("and the account holds exactly what was saved and left alone")
                .isEqualByComparingTo("110.00");
    }
}
