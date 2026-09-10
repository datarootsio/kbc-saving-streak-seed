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
 * A balance that reaches a rung, falls back off it and reaches it again says so all three times.
 *
 * <p>The fifth user story, and the case that decides how uniqueness is enforced. Real movement is
 * never suppressed as a duplicate, so the rung and the reason cannot be a key in the database the
 * way an announced anniversary is: a unique index over them would refuse the third of these three
 * notifications. What stops a duplicate here is the comparison of positions — a rung that has not
 * moved says nothing — and this test is what says that comparison lets a genuine repeat through.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ABalanceThatCrossesFallsBackAndCrossesAgainSaysSoAllThreeTimesApiTest
        extends ApiIntegrationTest {

    /** Exactly onto the lowest rung. */
    private static final String ONTO_THE_RUNG = "100.00";

    /** Enough back out to fall off the ladder altogether. */
    private static final String ENOUGH_BACK_OUT_TO_FALL_OFF = "60.00";

    /** And enough back in to stand on it again. */
    private static final String ENOUGH_BACK_IN_TO_REACH_IT_AGAIN = "60.00";

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseBalancesThisTestCountsFromZero() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-rung-crossed-twice"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_same_rung_reached_twice_with_a_fall_in_between_is_three_notifications() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        app.deposit(savingsAccount, ANKE, ONTO_THE_RUNG);
        sweep.runs();

        app.withdraw(savingsAccount, ANKE, ENOUGH_BACK_OUT_TO_FALL_OFF);
        sweep.runs();

        app.deposit(savingsAccount, ANKE, ENOUGH_BACK_IN_TO_REACH_IT_AGAIN);
        sweep.runs();

        List<RaisedNotification> said = sweep.whatWasSaidAbout(savingsAccount, ANKE);
        assertThat(said)
                .as("three movements between positions are three occasions")
                .hasSize(3);
        assertThat(said.stream().map(RaisedNotification::reason))
                .as("newest first: reached, lost, reached — read from the bottom up, the story of "
                        + "the account")
                .containsExactly(
                        NotificationReason.BALANCE_THRESHOLD_REACHED,
                        NotificationReason.BALANCE_THRESHOLD_LOST,
                        NotificationReason.BALANCE_THRESHOLD_REACHED);
        assertThat(said)
                .as("and every one of them about the same rung, which is why no index can enforce "
                        + "this family's uniqueness")
                .allSatisfy(raised ->
                        assertThat(raised.amount()).isEqualByComparingTo(ONTO_THE_RUNG));
    }
}
