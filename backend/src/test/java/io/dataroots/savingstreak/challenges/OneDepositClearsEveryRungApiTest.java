package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
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
 * Saving a lot at once is never worse than saving it in instalments: one deposit that carries the
 * reading past all three rungs is paid for all three, each with its own dated badge — and then the
 * customer takes every euro of it back out and keeps the lot.
 *
 * <p>Both halves follow from the same decision. Rungs are marks on one running figure rather than
 * challenges of their own, so a reading of EUR 500 has cleared bronze, silver and gold and there is
 * no order in which they have to arrive; and an award is a fact rather than a derivation, so nothing
 * that happens to the money afterwards can reach back and unmake one. An application that took a
 * customer's badge away for using their own savings would be teaching them not to use their savings,
 * which is the opposite of what it is for.
 *
 * <p>Its own application on a database nothing has ever been written to, for two reasons: every
 * figure here is exact, and this test withdraws, which moves the mark somebody else's test would
 * have measured from.
 */
class OneDepositClearsEveryRungApiTest extends ApiIntegrationTest {

    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";

    /** What the seed prices the three rungs of {@code SAVE_FIVE_HUNDRED} at, cheapest first. */
    private static final long BRONZE_PAYS = 25;
    private static final long SILVER_PAYS = 75;
    private static final long GOLD_PAYS = 200;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-all-three-at-once"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * One narrative, because it is one: five hundred euros in, three badges and three lots of points
     * out, the enrolment finished — and then five hundred euros back out again, which changes none
     * of it.
     */
    @Test
    void five_hundred_euros_at_once_wins_all_three_rungs_and_taking_them_out_again_takes_none_back() {
        long savings = app.savingsAccountOf(ANKE);
        app.enrolIn(ANKE, FIVE_HUNDRED);

        app.deposit(savings, ANKE, "500.00");
        long beforeTheTabWasOpened = app.pointsBalanceOf(ANKE);

        List<AchievementView> won = app.achievementsOf(ANKE);
        assertThat(won)
                .as("all three rungs, newest first, the ladder read from the top down")
                .extracting(AchievementView::rung)
                .containsExactly("GOLD", "SILVER", "BRONZE");
        assertThat(won)
                .as("each with its own dated record, and each dated")
                .allSatisfy(badge -> assertThat(badge.awardedAt()).isNotNull());
        assertThat(won)
                .as("all three were won by the one reading that cleared them, which is what "
                        + "actually happened")
                .allSatisfy(badge -> assertThat(badge.reading()).isEqualByComparingTo("500.00"));
        assertThat(won).extracting(AchievementView::points)
                .containsExactly(GOLD_PAYS, SILVER_PAYS, BRONZE_PAYS);
        assertThat(won).extracting(AchievementView::id)
                .as("three separate awards and not one badge with three names on it")
                .doesNotHaveDuplicates();

        long afterAllThree = app.pointsBalanceOf(ANKE);
        assertThat(afterAllThree - beforeTheTabWasOpened)
                .as("paid for all three, so saving it in one go was worth exactly what saving it "
                        + "in three instalments would have been")
                .isEqualTo(BRONZE_PAYS + SILVER_PAYS + GOLD_PAYS);

        ChallengeView finished = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(finished.state())
                .as("its last rung was awarded, so the enrolment is over")
                .isEqualTo("COMPLETED");
        assertThat(finished.enrolled()).isFalse();
        assertThat(finished.reading())
                .as("and it reports the reading that finished it, frozen where it was")
                .isEqualByComparingTo("500.00");
        assertThat(finished.nextRung()).isNull();

        // Everything back out. The award is not a claim on the money and never was.
        BigDecimal inTheCurrentAccountBefore = app.currentAccountBalanceOf(ANKE);
        app.withdraw(savings, ANKE, "500.00");

        assertThat(app.stillSavedBy(ANKE))
                .as("she is holding nothing, which is as low as a balance goes")
                .isEqualByComparingTo("0.00");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and every euro of it is where she asked for it, so nothing went anywhere else")
                .isEqualByComparingTo(inTheCurrentAccountBefore.add(new BigDecimal("500.00")));
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the points a rung paid are hers; spending her own savings does not claw them "
                        + "back")
                .isEqualTo(afterAllThree);
        assertThat(app.achievementsOf(ANKE))
                .as("and neither are the badges taken away, whatever she does with the money")
                .extracting(AchievementView::rung)
                .containsExactly("GOLD", "SILVER", "BRONZE");
        assertThat(app.achievementsOf(ANKE))
                .as("nor re-priced, nor re-read: the figures on them are the ones recorded")
                .allSatisfy(badge -> assertThat(badge.reading()).isEqualByComparingTo("500.00"));
        assertThat(app.challengeOf(ANKE, FIVE_HUNDRED).state())
                .as("and a challenge she finished stays finished")
                .isEqualTo("COMPLETED");
    }
}
