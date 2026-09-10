package io.dataroots.savingstreak.notifications;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A savings balance that reaches a rung is told about it once, and the notification names the rung.
 *
 * <p>The first user story of this feature: progress a customer has been making slowly is marked at
 * the moment it happens rather than being left for them to notice on a page. And it is the answer to
 * "what does the very first sweep do" — an account that already stands on a rung and has never been
 * told so is announced, which is what makes the seeded demo data produce something the first time a
 * trainer runs the job rather than nothing until the next deposit.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test
 * counts the notifications about an account from zero, and the shared application's accounts have
 * whatever balance the rest of the suite left in them.
 */
class ABalanceThatPassesARungRaisesOneNotificationNamingThatRungApiTest extends ApiIntegrationTest {

    /**
     * Exactly the lowest rung, which is also the boundary: a balance that reaches a round figure
     * exactly has reached it. Which figures are rungs is asserted once, in
     * {@link BalanceThresholdsTest}.
     */
    private static final String A_BALANCE_ON_THE_LOWEST_RUNG = "100.00";

    /** A cent short of that same rung. */
    private static final String A_BALANCE_A_CENT_SHORT_OF_IT = "99.99";

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseBalancesThisTestCountsFromZero() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-balance-that-passes-a-rung"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void an_account_standing_on_a_rung_it_has_never_been_told_about_is_announced_once() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        assertThat(sweep.whatWasSaidAbout(savingsAccount, ANKE))
                .as("nothing has been said about an account nothing has happened to")
                .isEmpty();

        app.deposit(savingsAccount, ANKE, A_BALANCE_ON_THE_LOWEST_RUNG);
        Instant theApplicationThinksItIs = app.theClockReads();

        sweep.runs();

        List<RaisedNotification> said = sweep.whatWasSaidAbout(savingsAccount, ANKE);
        assertThat(said)
                .as("one occasion, one notification")
                .hasSize(1);
        RaisedNotification reached = said.get(0);
        assertThat(reached.reason()).isEqualTo(NotificationReason.BALANCE_THRESHOLD_REACHED);
        assertThat(reached.amount())
                .as("the notification names the rung the balance landed on")
                .isEqualByComparingTo(A_BALANCE_ON_THE_LOWEST_RUNG);
        assertThat(reached.savingsAccountId())
                .as("which pot it is about, so the account's own page can carry the notice")
                .isEqualTo(savingsAccount);
        assertThat(reached.depositId())
                .as("a balance rung is about the account and not about any one deposit")
                .isNull();
        assertThat(reached.points()).isNull();
        assertThat(reached.occursOn()).isNull();
        assertThat(reached.readAt())
                .as("nobody has looked at it yet")
                .isNull();
        assertThat(Duration.between(theApplicationThinksItIs, reached.raisedAt()).abs())
                .as("raised at the moment the application's clock reads, which is what makes a "
                        + "wound-forward clock demonstrable")
                .isLessThan(Duration.ofMinutes(5));
    }

    @Test
    void an_account_a_cent_short_of_a_rung_is_told_nothing() {
        long otherSavingsAccount = app.otherSavingsAccountOf(ANKE);
        app.deposit(otherSavingsAccount, ANKE, A_BALANCE_A_CENT_SHORT_OF_IT);

        sweep.runs();

        assertThat(sweep.whatWasSaidAbout(otherSavingsAccount, ANKE))
                .as("a balance that has not reached the figure has nothing to be told about, and a "
                        + "ladder that rounded up would tell it anyway")
                .isEmpty();
    }
}
