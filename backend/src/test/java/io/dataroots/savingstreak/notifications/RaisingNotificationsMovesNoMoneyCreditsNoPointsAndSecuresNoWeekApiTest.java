package io.dataroots.savingstreak.notifications;

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
 * Raising notifications writes a record and nothing else: no euros move, no points are credited, no
 * week is secured and no streak changes.
 *
 * <p>Four promises that are one statement — this module reports on the rules and is not one of them.
 * A notification is a sentence addressed to a customer, and a sweep that quietly paid something
 * while it was in there would put a second producer of points beside the one that owns them.
 *
 * <p>Worth its own test because the sweep reads from two modules that do move things. It asks
 * Deposits what an account holds and Accounts who holds it, and a reader who saw those two names in
 * the constructor would be right to want the promise asserted rather than argued.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class RaisingNotificationsMovesNoMoneyCreditsNoPointsAndSecuresNoWeekApiTest
        extends ApiIntegrationTest {

    /** Enough to be worth announcing, so that the sweep under test has done something. */
    private static final String ENOUGH_TO_REACH_A_RUNG = "1000.00";

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseBalancesThisTestCountsFromZero() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-notifications-move-no-money"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_notification_arrives_and_leaves_the_money_the_points_and_the_week_alone() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        app.deposit(savingsAccount, ANKE, ENOUGH_TO_REACH_A_RUNG);

        BalancesView beforeTheSweep = app.balancesOf(savingsAccount);
        BigDecimal inTheCurrentAccount = app.currentAccountBalanceOf(ANKE);
        MoneyMovementView[] ledgerBefore = app.moneyMovementsOf(ANKE);

        sweep.runs();

        // Something happened, or the rest of this test says nothing.
        assertThat(sweep.whatWasSaidAbout(savingsAccount, ANKE))
                .as("the sweep had something to say, so it was in there doing its work")
                .hasSize(1);

        BalancesView afterTheSweep = app.balancesOf(savingsAccount);
        assertThat(afterTheSweep.moneyBalance())
                .as("a notification is a sentence, and sentences do not move euros")
                .isEqualByComparingTo(beforeTheSweep.moneyBalance());
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and nothing came back out to the current account either")
                .isEqualByComparingTo(inTheCurrentAccount);
        assertThat(app.moneyMovementsOf(ANKE))
                .as("the ledger is a record of euros that moved, and none did")
                .hasSameSizeAs(ledgerBefore);
        assertThat(afterTheSweep.pointsBalance())
                .as("being told about a rung is not being paid for reaching it")
                .isEqualTo(beforeTheSweep.pointsBalance());
        assertThat(afterTheSweep.newSavingsThisWeek())
                .as("and nothing was saved this week by the sweep saying so")
                .isEqualByComparingTo(beforeTheSweep.newSavingsThisWeek());
        assertThat(afterTheSweep.currentStreakWeeks())
                .as("so no week was secured")
                .isEqualTo(beforeTheSweep.currentStreakWeeks());
        assertThat(afterTheSweep.currentMultiplier())
                .as("and no rate changed")
                .isEqualByComparingTo(beforeTheSweep.currentMultiplier());
    }
}
