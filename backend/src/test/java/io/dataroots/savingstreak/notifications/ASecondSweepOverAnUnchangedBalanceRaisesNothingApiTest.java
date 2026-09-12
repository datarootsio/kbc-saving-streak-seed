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
 * A balance that has not moved between rungs is announced once and never again.
 *
 * <p>The fourth user story and the operator's retry, which are the same promise read from two ends:
 * a panel is a record of moments rather than a diary of nights, and running the job twice in a row
 * is safe. A balance resting at EUR 1.001 would otherwise announce itself every night for a year.
 *
 * <p>Both halves matter. Sweeping again over exactly the same balance says nothing, and so does
 * sweeping after money has come in that leaves the balance on the rung it was already on — the
 * second is the one a record of crossings would have got right and a comparison of positions has to
 * be shown to get right too.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ASecondSweepOverAnUnchangedBalanceRaisesNothingApiTest extends ApiIntegrationTest {

    /** Straight onto the third rung. */
    private static final String ONTO_A_RUNG = "1000.00";

    /** A euro more, which is not enough to reach the next one. */
    private static final String A_EURO_MORE = "1.00";

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseBalancesThisTestCountsFromZero() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-second-sweep-says-nothing"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_same_balance_swept_twice_is_announced_once() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        app.deposit(savingsAccount, ANKE, ONTO_A_RUNG);

        sweep.runs();
        List<RaisedNotification> afterTheFirstSweep = sweep.whatWasSaidAbout(savingsAccount, ANKE);
        assertThat(afterTheFirstSweep)
                .as("the first sweep has something to say, or the rest of this test is vacuous")
                .hasSize(1);

        sweep.runs();

        assertThat(sweep.whatWasSaidAbout(savingsAccount, ANKE))
                .as("a retry of a nightly job raises nothing the second time")
                .isEqualTo(afterTheFirstSweep);

        // And money that arrives without moving the balance off the rung it is on is not an
        // occasion either. This is the EUR 1.001 the feature must not announce nightly.
        app.deposit(savingsAccount, ANKE, A_EURO_MORE);

        sweep.runs();

        assertThat(sweep.whatWasSaidAbout(savingsAccount, ANKE))
                .as("a balance resting just above a rung has already been announced")
                .isEqualTo(afterTheFirstSweep);
    }
}
