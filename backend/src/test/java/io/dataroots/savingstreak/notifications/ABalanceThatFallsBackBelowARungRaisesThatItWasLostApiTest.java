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
 * A withdrawal that drops the balance below a rung it had reached is announced as that rung lost.
 *
 * <p>The second user story: a withdrawal's cost to a customer's standing is not silent. It arrives
 * on the next sweep rather than with the withdrawal itself, so a customer who takes money out at
 * noon learns at four the next morning — one producer of notifications rather than two, which is the
 * trade this feature makes on purpose.
 *
 * <p>And then nothing more. The fall is one occasion, so the sweep after it says nothing: the record
 * of a rung lost is read back as the position the balance is now in, which is the whole reason a
 * lost rung names the lowest rung the balance no longer reaches.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ABalanceThatFallsBackBelowARungRaisesThatItWasLostApiTest extends ApiIntegrationTest {

    /** Straight onto the third rung. */
    private static final String ONTO_A_RUNG = "1000.00";

    /** Half of it back out, which lands the balance exactly on the rung below. */
    private static final String HALF_OF_IT_BACK_OUT = "500.00";

    /** The rung the balance no longer reaches, and the one it had been told it had reached. */
    private static final String THE_RUNG_IT_NO_LONGER_REACHES = "1000.00";

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseBalancesThisTestCountsFromZero() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-balance-that-falls-back"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_rung_reached_and_then_dropped_below_is_announced_as_lost_on_the_next_sweep() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        app.deposit(savingsAccount, ANKE, ONTO_A_RUNG);
        sweep.runs();
        assertThat(sweep.whatWasSaidAbout(savingsAccount, ANKE))
                .as("the rung has to have been reached before it can be lost")
                .hasSize(1);

        app.withdraw(savingsAccount, ANKE, HALF_OF_IT_BACK_OUT);

        sweep.runs();

        List<RaisedNotification> said = sweep.whatWasSaidAbout(savingsAccount, ANKE);
        assertThat(said).hasSize(2);
        RaisedNotification lost = said.get(0);
        assertThat(lost.reason())
                .as("newest first, and the newest thing to happen is the fall")
                .isEqualTo(NotificationReason.BALANCE_THRESHOLD_LOST);
        assertThat(lost.amount())
                .as("the rung the balance no longer reaches")
                .isEqualByComparingTo(THE_RUNG_IT_NO_LONGER_REACHES);
        assertThat(lost.savingsAccountId()).isEqualTo(savingsAccount);
        assertThat(lost.depositId()).isNull();
        assertThat(said.get(1).reason())
                .as("and the climb it is a fall from is still in the record")
                .isEqualTo(NotificationReason.BALANCE_THRESHOLD_REACHED);

        // One fall, one notification. The sweep after it reads the row it just wrote as "the
        // balance stands on the rung below EUR 1.000", which is where it does stand.
        sweep.runs();

        assertThat(sweep.whatWasSaidAbout(savingsAccount, ANKE))
                .as("a fall already announced is not announced again the next night")
                .isEqualTo(said);
    }
}
