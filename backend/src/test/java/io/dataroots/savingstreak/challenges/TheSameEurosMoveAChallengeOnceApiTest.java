package io.dataroots.savingstreak.challenges;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ChallengeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The round trip earns nothing: pay in, take it back out, pay the same euros in again, and the
 * challenge has not moved.
 *
 * <p>This is the case the spec asks a reviewer to look at first, and the reason the module measures
 * what it measures. A challenge counting euros <em>moved</em> could be filled by one afternoon and
 * EUR 400 walked in and out of an account; a challenge counting the customer's high-water mark above
 * the mark their enrolment recorded cannot be, because money that only refills the gap a withdrawal
 * left moves no mark. Nothing here polices anything — the arithmetic simply gives no other answer.
 *
 * <p>Those are the same euros the points ledger already refuses to pay for twice, and that sameness
 * is the point: there is one notion of "money that counts" in this application, {@code
 * TheMostEverSaved} owns it, and a second one invented here would have drifted away from it.
 *
 * <p>The other half of the rule is asserted too, and it matters as much: a withdrawal leaves the
 * reading exactly where it was. A challenge about money having been put away is not a challenge
 * about money being there, and an application that took a customer's progress away for using their
 * own savings would be teaching them not to.
 *
 * <p>Its own application on a database nothing has ever been written to, because every figure here
 * is exact and a mark somebody else's test moved would decide the answer.
 */
class TheSameEurosMoveAChallengeOnceApiTest extends ApiIntegrationTest {

    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-round-trip"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** The exploit, written out as the round trip it is, and run three times over. */
    @Test
    void the_same_four_hundred_euros_fill_a_challenge_once_however_often_they_go_round() {
        long savings = app.savingsAccountOf(ANKE);
        assertThat(app.enrolIn(ANKE, FIVE_HUNDRED).measuringFrom())
                .as("she has saved nothing yet, so the challenge measures from nothing")
                .isEqualByComparingTo("0.00");

        app.deposit(savings, ANKE, "400.00");
        ChallengeView filled = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(filled.reading())
                .as("nobody has saved these euros before, so all four hundred of them count")
                .isEqualByComparingTo("400.00");
        assertThat(filled.nextRung()).isEqualTo("GOLD");
        assertThat(filled.stillNeeded()).isEqualByComparingTo("100.00");

        for (int lap = 1; lap <= 3; lap++) {
            app.withdraw(savings, ANKE, "400.00");
            ChallengeView afterTakingItOut = app.challengeOf(ANKE, FIVE_HUNDRED);
            assertThat(afterTakingItOut.reading())
                    .as("lap %d: taking money out takes no progress back — this challenge counts "
                            + "what she put away, not what she is holding", lap)
                    .isEqualByComparingTo("400.00");
            assertThat(afterTakingItOut.stillNeeded()).as("lap %d", lap).isEqualByComparingTo("100.00");

            app.deposit(savings, ANKE, "400.00");
            assertThat(app.challengeOf(ANKE, FIVE_HUNDRED).reading())
                    .as("lap %d: the same four hundred euros going back where they were are not "
                            + "new saving, so the challenge has not moved", lap)
                    .isEqualByComparingTo("400.00");
        }

        assertThat(app.mostEverSavedOf(ANKE))
                .as("three round trips, two thousand four hundred euros moved, and the mark the "
                        + "challenge reads is still the four hundred she genuinely saved")
                .isEqualByComparingTo("400.00");

        // And saving genuinely more still fills it, which is the half of the rule that has to keep
        // working: the mark is a floor under what has already been counted, not a cap on counting.
        app.deposit(savings, ANKE, "100.00");
        ChallengeView allTheWayUp = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(allTheWayUp.reading()).isEqualByComparingTo("500.00");
        assertThat(allTheWayUp.nextRung()).isNull();
        assertThat(allTheWayUp.stillNeeded()).isNull();
    }
}
