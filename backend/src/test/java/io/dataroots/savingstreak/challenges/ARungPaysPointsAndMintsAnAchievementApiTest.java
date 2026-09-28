package io.dataroots.savingstreak.challenges;

import java.util.List;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ChallengeView;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reaching a rung pays its points into the pot the customer already spends on rewards, and mints an
 * achievement that is never taken back.
 *
 * <p>Two claims, and they are separate on purpose. The <em>points</em> are ordinary points — the
 * ledger learned one new reason and nothing else, which
 * {@code ChallengePointsAreOrdinaryPointsApiTest} takes apart end to end. The <em>achievement</em> is
 * a record of something that happened: it carries the reading that won it and the points it paid as
 * they stood at that moment, and neither figure is ever worked out again. That is the half this
 * class is mostly about, and the sharpest assertion in it is that bronze still says EUR 100 after
 * Anke has saved three times that.
 *
 * <p><strong>Paid once and only once.</strong> Every read of the challenges tab judges before it
 * answers, so a test that reads the tab four times has run the judging pass four times — and the
 * points balance says so by not moving. That is the idempotence claim made the way a customer would
 * accidentally make it, rather than by calling a method twice.
 *
 * <p>Balances are asserted as differences rather than as absolutes, because a deposit earns points
 * of its own at whatever rate the week is paying and that rate is the Streaks module's business.
 * What this class claims is what the <em>award</em> added, which is the gap between the balance
 * before the tab was read and the balance after.
 *
 * <p>Its own application on a database nothing has ever been written to, because every figure here
 * is exact: on the shared one Anke arrives with whatever the tests that ran first left behind, and a
 * reading measured from a mark somebody else moved is really measuring the order the classes
 * happened to run in.
 */
class ARungPaysPointsAndMintsAnAchievementApiTest extends ApiIntegrationTest {

    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";

    /** What the seed prices the rungs of {@code SAVE_FIVE_HUNDRED} at. */
    private static final long BRONZE_PAYS = 25;
    private static final long SILVER_PAYS = 75;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-a-rung-pays"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The whole of the ordinary path in one narrative, because it is one: a rung is cleared, it pays,
     * it is written down, and neither the payment nor the record moves again however often anybody
     * looks or however much more the customer saves.
     */
    @Test
    void clearing_a_rung_pays_its_points_once_and_writes_an_achievement_that_never_moves() {
        long savings = app.savingsAccountOf(ANKE);
        app.enrolIn(ANKE, FIVE_HUNDRED);

        assertThat(app.achievementsOf(ANKE))
                .as("nothing has been reached yet, so there is nothing to show for it")
                .isEmpty();

        // Bronze asks for a hundred. The deposit earns its own points at whatever rate this week is
        // paying, and that is read off before the tab is opened, so the only thing the next
        // assertion can be measuring is what the rung paid.
        app.deposit(savings, ANKE, "100.00");
        long beforeTheTabWasOpened = app.pointsBalanceOf(ANKE);
        assertThat(app.achievementsOf(ANKE))
                .as("the trophy case judges before it answers, so bronze is in it the first time "
                        + "she looks")
                .hasSize(1);
        assertThat(app.pointsBalanceOf(ANKE) - beforeTheTabWasOpened)
                .as("bronze pays what the card said it pays, into the pot she already spends")
                .isEqualTo(BRONZE_PAYS);

        AchievementView bronze = app.achievementsOf(ANKE).get(0);
        assertThat(bronze.id()).isNotNull();
        assertThat(bronze.challenge()).isEqualTo(FIVE_HUNDRED);
        assertThat(bronze.title()).as("the name of the challenge, so an old badge labels itself")
                .isNotBlank();
        assertThat(bronze.rung()).isEqualTo("BRONZE");
        assertThat(bronze.awardedAt()).as("dated, because a trophy case is a history")
                .isNotNull();
        assertThat(bronze.reading())
                .as("the reading that won it, recorded rather than recomputed")
                .isEqualByComparingTo("100.00");
        assertThat(bronze.points()).isEqualTo(BRONZE_PAYS);

        // Reading the tab is running the pass. Four more reads, and nothing is paid a second time.
        long afterBronze = app.pointsBalanceOf(ANKE);
        app.challengeOf(ANKE, FIVE_HUNDRED);
        app.achievementsOf(ANKE);
        app.challengesOf(ANKE);
        app.achievementsOf(ANKE);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("a rung is paid once and only once, however often the pass runs")
                .isEqualTo(afterBronze);
        assertThat(app.achievementsOf(ANKE))
                .as("and it is minted once and only once")
                .hasSize(1);

        // Silver asks for two hundred and fifty. She saves past it, and only silver is paid: bronze
        // is a mark on the same running figure and it is already behind her.
        app.deposit(savings, ANKE, "200.00");
        long beforeSilver = app.pointsBalanceOf(ANKE);
        List<AchievementView> afterSilver = app.achievementsOf(ANKE);
        assertThat(app.pointsBalanceOf(ANKE) - beforeSilver)
                .as("silver's points and nothing else — the same hundred euros do not pay bronze "
                        + "again on their way past")
                .isEqualTo(SILVER_PAYS);
        assertThat(afterSilver)
                .as("newest first, so the rung she has just reached is at the top")
                .extracting(AchievementView::rung)
                .containsExactly("SILVER", "BRONZE");
        assertThat(afterSilver.get(0).reading())
                .as("silver was won at three hundred, which is where she actually stood")
                .isEqualByComparingTo("300.00");
        assertThat(afterSilver.get(1).reading())
                .as("and bronze still says what it said, because an award is a fact and not a "
                        + "derivation — she has saved three times this since")
                .isEqualByComparingTo("100.00");
        assertThat(afterSilver.get(1).points())
                .as("and it was never re-priced either")
                .isEqualTo(BRONZE_PAYS);

        ChallengeView card = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(card.state())
                .as("two rungs of three: still running, because gold is the last one")
                .isEqualTo("ACTIVE");
        assertThat(card.reading()).isEqualByComparingTo("300.00");
        assertThat(card.nextRung()).isEqualTo("GOLD");
    }

    /**
     * What a deposit says it earned is exactly what it said before challenges existed.
     *
     * <p>The one assertion in this feature that is about something <em>not</em> changing. A rung's
     * points are credited under a reason no deposit earns under, against the award rather than
     * against a deposit, so no deposit's breakdown grows a field and the three figures a deposit has
     * always reported still add up to the total it has always reported.
     *
     * <p>Its own customer, so that nothing this asserts depends on what the narrative above did.
     */
    @Test
    void the_deposit_that_carried_her_past_a_rung_says_it_earned_exactly_what_it_always_did() {
        String customer = app.aCustomerOfItsOwn("a-deposit-that-clears-a-rung");
        long savings = app.savingsAccountOf(customer);
        app.enrolIn(customer, FIVE_HUNDRED);

        DepositView asItLanded = app.deposit(savings, customer, "120.00");
        assertThat(asItLanded.pointsEarned())
                .as("the three reasons a deposit earns under are all it earned under")
                .isEqualTo(asItLanded.basePoints() + asItLanded.streakBonusPoints()
                        + asItLanded.loyaltyBonusPoints());

        assertThat(app.achievementsOf(customer))
                .as("and it did carry them past bronze, so there is something to be wrong about")
                .hasSize(1);

        DepositView readBackAfterTheAward = app.depositsInto(savings)[0];
        assertThat(readBackAfterTheAward.pointsEarned())
                .as("what the deposit earned is unchanged by a rung it happened to clear")
                .isEqualTo(asItLanded.pointsEarned());
        assertThat(readBackAfterTheAward.basePoints()).isEqualTo(asItLanded.basePoints());
        assertThat(readBackAfterTheAward.streakBonusPoints())
                .isEqualTo(asItLanded.streakBonusPoints());
        assertThat(readBackAfterTheAward.loyaltyBonusPoints())
                .isEqualTo(asItLanded.loyaltyBonusPoints());
    }

    /** A customer who never joined anything has an empty trophy case, and one who is not there is not found. */
    @Test
    void a_trophy_case_is_empty_before_anything_is_won_and_not_found_for_a_stranger() {
        String customer = app.aCustomerOfItsOwn("a-trophy-case-nobody-filled");

        assertThat(app.achievementsOf(customer)).isEmpty();

        assertThat(app.tryToReadTheAchievementsOf(app.anIdNoCustomerHas()).getStatusCode().value())
                .as("being told there is no such customer is more use than an empty case")
                .isEqualTo(404);
    }
}
