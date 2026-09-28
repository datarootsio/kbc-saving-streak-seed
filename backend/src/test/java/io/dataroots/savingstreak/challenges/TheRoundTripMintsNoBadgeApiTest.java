package io.dataroots.savingstreak.challenges;

import java.util.List;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ChallengeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The farming attempt, run end to end and paid for once: enrol, save to bronze, take it all back
 * out, pay the same euros in again — and neither the reading, nor the trophy case, nor the points
 * balance moves.
 *
 * <p>{@code TheSameEurosMoveAChallengeOnceApiTest} already makes half of this claim, and makes it
 * about the <em>reading</em>: the figure on the card does not climb when the same money goes round.
 * That is the arithmetic. This class makes the other half, which is the half a customer trying it
 * on would actually care about — that the round trip <em>pays</em> nothing. A reading that stood
 * still while a second bronze badge was minted and a second batch of points was credited would be
 * an application that had documented the hole and then left it open.
 *
 * <p>The two are separate assertions because they could separately be wrong. The reading comes out
 * of {@code TheReadingSinceYouEnrolled}; the paying comes out of the judging pass, which awards only
 * rungs that have no award row against this enrolment yet. Either could be right while the other is
 * not, and only one of them is what the bank's money turns on.
 *
 * <p>Three laps rather than one, because a hole worth exploiting is a hole worth exploiting
 * repeatedly, and because a rule that held for the first circuit and not the fourth would be a rule
 * that only ever met one test.
 *
 * <p>Its own application on a database nothing has ever been written to, because this class
 * withdraws: on the shared one it would be taking Anke below a mark that somebody else's test put
 * there, and a reading measured from that is really measuring the order the classes happened to run
 * in.
 */
class TheRoundTripMintsNoBadgeApiTest extends ApiIntegrationTest {

    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";

    /** What the seed prices the bronze rung of {@code SAVE_FIVE_HUNDRED} at. */
    private static final long BRONZE_PAYS = 25;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-a-round-trip-pays-nothing"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * One narrative, because it is one: the euros that bought a badge cannot be sent round again to
     * buy a second one.
     */
    @Test
    void walking_the_same_hundred_euros_round_in_a_circle_wins_one_bronze_and_no_more() {
        long savings = app.savingsAccountOf(ANKE);
        app.enrolIn(ANKE, FIVE_HUNDRED);

        // Bronze asks for a hundred, and a hundred is what she genuinely saves. The deposit earns
        // its own points at whatever rate this week is paying, and that is read off before the tab
        // is opened, so what the next assertion measures is only what the rung paid.
        app.deposit(savings, ANKE, "100.00");
        long beforeTheTabWasOpened = app.pointsBalanceOf(ANKE);
        assertThat(app.achievementsOf(ANKE))
                .as("a hundred euros genuinely saved is a bronze rung, and it is paid for")
                .hasSize(1);
        assertThat(app.pointsBalanceOf(ANKE) - beforeTheTabWasOpened).isEqualTo(BRONZE_PAYS);

        long afterBronzeWasPaid = app.pointsBalanceOf(ANKE);
        AchievementView bronze = app.achievementsOf(ANKE).get(0);

        for (int lap = 1; lap <= 3; lap++) {
            app.withdraw(savings, ANKE, "100.00");
            app.deposit(savings, ANKE, "100.00");

            ChallengeView card = app.challengeOf(ANKE, FIVE_HUNDRED);
            assertThat(card.reading())
                    .as("lap %d: the same hundred euros going back where they were are not new "
                            + "saving, so the reading has not moved", lap)
                    .isEqualByComparingTo("100.00");

            List<AchievementView> caseNow = app.achievementsOf(ANKE);
            assertThat(caseNow)
                    .as("lap %d: and no second badge was minted for them", lap)
                    .hasSize(1);
            assertThat(caseNow.get(0).id())
                    .as("lap %d: it is the same bronze, not a fresh one wearing its name", lap)
                    .isEqualTo(bronze.id());
            assertThat(caseNow.get(0).awardedAt())
                    .as("lap %d: won when it was won, and not re-dated by the circuit", lap)
                    .isEqualTo(bronze.awardedAt());
            assertThat(caseNow.get(0).reading())
                    .as("lap %d: the reading that won it is a fact and not a derivation", lap)
                    .isEqualByComparingTo(bronze.reading());
            assertThat(app.pointsBalanceOf(ANKE))
                    .as("lap %d: and the bank paid for bronze exactly once, which is the whole of "
                            + "the anti-farming claim", lap)
                    .isEqualTo(afterBronzeWasPaid);
        }

        assertThat(app.mostEverSavedOf(ANKE))
                .as("three round trips, six hundred euros moved, and the mark the challenge reads "
                        + "is still the hundred she genuinely saved")
                .isEqualByComparingTo("100.00");

        // And the half of the rule that has to keep working: genuinely new saving still pays. The
        // mark is a floor under what has already been counted, not a cap on counting.
        app.deposit(savings, ANKE, "150.00");
        long beforeSilver = app.pointsBalanceOf(ANKE);
        assertThat(app.achievementsOf(ANKE))
                .as("two hundred and fifty genuinely saved is silver, and the circuits never "
                        + "stopped her earning it")
                .extracting(AchievementView::rung)
                .containsExactly("SILVER", "BRONZE");
        assertThat(app.pointsBalanceOf(ANKE) - beforeSilver)
                .as("silver's points, and bronze's are not paid a second time on the way past")
                .isEqualTo(75);
    }
}
