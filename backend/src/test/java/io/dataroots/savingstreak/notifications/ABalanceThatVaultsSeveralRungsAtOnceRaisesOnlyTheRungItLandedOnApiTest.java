package io.dataroots.savingstreak.notifications;

import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * One deposit that clears several rungs at once is one notification, naming where the balance
 * landed.
 *
 * <p>The third user story, and the reason the rule is a comparison of positions rather than a record
 * of crossings. A customer who moves EUR 1.100 into savings has passed EUR 100, EUR 500 and
 * EUR 1.000 in one movement, and being told three times about one act of saving is a panel nobody
 * reads. What they want told is where they now stand.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ABalanceThatVaultsSeveralRungsAtOnceRaisesOnlyTheRungItLandedOnApiTest
        extends ApiIntegrationTest {

    /**
     * Past the third rung in one movement, and short of the fourth — three rungs cleared at once,
     * and as many as the seeded current account can pay for in one go.
     */
    private static final String CLEAR_OF_THREE_RUNGS_IN_ONE_MOVEMENT = "1100.00";

    /** The rung that balance lands on. */
    private static final String THE_RUNG_IT_LANDS_ON = "1000.00";

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseBalancesThisTestCountsFromZero() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-balance-that-vaults-rungs"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void three_rungs_cleared_in_one_movement_are_one_notification_naming_the_third() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        app.deposit(savingsAccount, ANKE, CLEAR_OF_THREE_RUNGS_IN_ONE_MOVEMENT);

        sweep.runs();

        List<RaisedNotification> said = sweep.whatWasSaidAbout(savingsAccount, ANKE);
        assertThat(said)
                .as("one deposit is one occasion, however many round figures it went past")
                .hasSize(1);
        assertThat(said.get(0).reason()).isEqualTo(NotificationReason.BALANCE_THRESHOLD_REACHED);
        assertThat(said.get(0).amount())
                .as("the rung the balance is standing on, not the first one it went past")
                .isEqualByComparingTo(THE_RUNG_IT_LANDS_ON);
    }
}
