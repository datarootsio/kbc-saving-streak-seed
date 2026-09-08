package io.dataroots.savingstreak.pointsexpiry;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Points that have expired cannot be spent, and the refusal quotes the balance that is actually left.
 *
 * <p>The half of the rule a customer would notice second. A balance dropping is a number on a screen;
 * being told that the cinema ticket is out of reach after saving for it is the consequence, and the
 * two have to agree. A ledger that stopped counting expired points towards the balance but still let a
 * claim draw from them would leave a customer holding a voucher their balance said they could not
 * afford.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ExpiredPointsCannotBeSpentApiTest extends ApiIntegrationTest {

    /** Comfortably the far side of any anniversary the clock could land near. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    /** What a cinema ticket costs, which is more than this test ever lets the customer keep. */
    private static final int A_CINEMA_TICKET_COSTS = 100;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-expired-points-unspendable"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_claim_that_would_have_been_paid_for_out_of_expired_points_is_refused_with_the_smaller_balance() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // Enough to afford the ticket, and left alone for a year.
        app.deposit(savingsAccount, ANKE, "120.00");
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(120);
        app.daysPass(DAYS_WELL_PAST_A_YEAR);

        // Still affordable right up to the sweep, because nothing has swept yet: the anniversary has
        // passed and the job is what acts on it.
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(120);

        app.runJob("expireOldPoints");

        assertThat(app.pointsBalanceOf(ANKE))
                .as("a hundred and twenty points, all of them a year old, all of them gone")
                .isZero();

        String refused = app.whyTheClaimWasRefused(ANKE, "CINEMA_TICKET");

        // The words say what it costs and what they have, and what they have is the figure the
        // balance reports rather than the figure their expired batches still hold.
        assertThat(refused)
                .as("the refusal quotes the balance that is left, not the points that expired")
                .contains(String.valueOf(A_CINEMA_TICKET_COSTS))
                .contains("you have 0");
    }
}
