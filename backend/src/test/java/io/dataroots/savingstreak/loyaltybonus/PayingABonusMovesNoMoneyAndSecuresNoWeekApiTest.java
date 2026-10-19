package io.dataroots.savingstreak.loyaltybonus;

import java.math.BigDecimal;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.MoneyMovementView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paying a bonus pays points and nothing else: no euros move, no week is secured, and no streak
 * changes.
 *
 * <p>Three promises that are each a way of saying the same thing — this scheme rewards money for
 * staying put and is not itself an act of saving. The euros stay exactly where they were, in the
 * savings account and out of the current account, so the ledger of money that moved has nothing new
 * in it. And the week the sweep happened to run in has had nothing paid into it, so a customer
 * cannot keep a streak alive by leaving money alone: the streak still measures money they actually
 * paid in.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: a year passes
 * in it.
 */
class PayingABonusMovesNoMoneyAndSecuresNoWeekApiTest extends ApiIntegrationTest {

    private static final String THE_SWEEP = "payLoyaltyBonuses";

    /** Comfortably the far side of any anniversary the clock could land near. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-bonus-moves-no-money"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_bonus_arrives_in_points_and_leaves_the_euros_and_the_week_alone() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        app.deposit(savingsAccount, ANKE, "500.00");

        // A year in which nothing was paid in, so the run of weeks the deposit secured has long
        // since lapsed and the week the sweep runs in is empty.
        app.daysPass(DAYS_WELL_PAST_A_YEAR);

        BalancesView beforeTheSweep = app.balancesOf(savingsAccount);
        BigDecimal inTheCurrentAccount = app.currentAccountBalanceOf(ANKE);
        MoneyMovementView[] ledgerBefore = app.moneyMovementsOf(ANKE);
        assertThat(beforeTheSweep.currentStreakWeeks())
                .as("a year of paying nothing in ends any run of weeks")
                .isZero();

        app.runJob(THE_SWEEP);

        BalancesView afterTheSweep = app.balancesOf(savingsAccount);

        // The points arrived, which is what says the sweep did something at all.
        assertThat(afterTheSweep.pointsBalance())
                .isEqualTo(beforeTheSweep.pointsBalance() + 50);

        // And nothing else did. The euros are where they were, at both ends of the movement that
        // put them there.
        assertThat(afterTheSweep.moneyBalance())
                .as("a bonus is paid in points, so the money in savings is untouched")
                .isEqualByComparingTo(beforeTheSweep.moneyBalance());
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and nothing came back out to the current account either")
                .isEqualByComparingTo(inTheCurrentAccount);
        assertThat(app.moneyMovementsOf(ANKE))
                .as("the ledger is a record of euros that moved, and none did")
                .hasSameSizeAs(ledgerBefore);

        // The week is still empty and the streak is still nothing. A bonus is not a payment in.
        assertThat(afterTheSweep.newSavingsThisWeek())
                .as("a bonus secures no week, because no money was saved this week")
                .isEqualByComparingTo("0.00");
        assertThat(afterTheSweep.currentStreakWeeks())
                .as("and so it starts no run of weeks")
                .isZero();
        assertThat(afterTheSweep.currentMultiplier())
                .as("and changes no rate")
                .isEqualByComparingTo(beforeTheSweep.currentMultiplier());
    }
}
