package io.dataroots.savingstreak.challenges;

import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * There are not two kinds of point. What a rung pays ages, expires, is spent oldest-first, shows up
 * in what the customer is told goes next, can be given away and buys anything in the catalogue —
 * exactly like the points a deposit earns, because it <em>is</em> the same sort of thing.
 *
 * <p>This is the test that proves the ledger did not learn a second currency. Everything else in
 * this feature would still pass if challenge points were kept in a pot of their own with rules of
 * their own; only this one would not. The whole change to the points module is one new reason and
 * one new way in, and the claim being made here is that nothing else anywhere needed a special case
 * for it.
 *
 * <p><strong>The construction is the argument.</strong> Anke saves EUR 100, which earns her points
 * at whatever rate this week is paying, and clears bronze, which pays twenty-five more. She then
 * gives away exactly what the deposit earned — and because spending and gifting both draw
 * oldest-first, the deposit's batches are the ones that go, which leaves her holding twenty-five
 * points that can only be the challenge's. Every assertion after that is about those twenty-five and
 * nothing else, and the fact that the gift drained the older batches first is itself the FIFO claim.
 *
 * <p>One test method, because the clock only goes forward and the last step is a year passing. Its
 * own application on its own database for the same reason.
 */
class ChallengePointsAreOrdinaryPointsApiTest extends ApiIntegrationTest {

    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";

    /** What the seed prices bronze on {@code SAVE_FIVE_HUNDRED} at. */
    private static final long BRONZE_PAYS = 25;

    /** What the catalogue charges for the cheapest thing in it. */
    private static final long A_CHARITY_DONATION_COSTS = 10;

    /**
     * A year and a day, in days, which is the shortest wind that is past every batch's anniversary
     * whichever day of which year the suite happens to run on.
     */
    private static final long A_YEAR_AND_A_DAY = 366;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-ordinary-points"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void what_a_rung_paid_expires_spends_gifts_and_buys_exactly_like_every_other_point() {
        long savings = app.savingsAccountOf(ANKE);
        LocalDate theDayItAllHappens = app.theDateTheClockReads();
        app.enrolIn(ANKE, FIVE_HUNDRED);

        app.deposit(savings, ANKE, "100.00");
        long whatTheDepositEarned = app.pointsBalanceOf(ANKE);
        assertThat(whatTheDepositEarned)
                .as("a hundred euros earn at least a hundred points, whatever the week's rate")
                .isGreaterThanOrEqualTo(100);

        app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("bronze paid into the same pot — there is no second balance to go and look at")
                .isEqualTo(whatTheDepositEarned + BRONZE_PAYS);

        // Giving away exactly what the deposit earned. Gifts draw oldest-first, so what leaves is
        // the deposit's batches and what stays can only be the rung's.
        long bramHadBefore = app.pointsBalanceOf(BRAM);
        app.give(ANKE, BRAM, String.valueOf(whatTheDepositEarned));
        assertThat(app.pointsBalanceOf(BRAM))
                .as("the gift arrived whole")
                .isEqualTo(bramHadBefore + whatTheDepositEarned);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("oldest first, so the deposit's points went and the challenge's are what is "
                        + "left — which is the FIFO rule holding across both kinds at once")
                .isEqualTo(BRONZE_PAYS);

        // From here on, every point Anke holds was paid by a rung.
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("they are counted in what she is told she stands to lose next")
                .isEqualTo(BRONZE_PAYS);
        assertThat(app.pointsExpiringNextOnOf(ANKE))
                .as("and they go twelve months after the day the rung was cleared, which is the "
                        + "schedule every other point in this application is on")
                .isEqualTo(theDayItAllHappens.plusYears(1));

        app.claim(ANKE, "CHARITY_DONATION");
        assertThat(app.pointsBalanceOf(ANKE))
                .as("they buy things out of the catalogue, at the price on the catalogue")
                .isEqualTo(BRONZE_PAYS - A_CHARITY_DONATION_COSTS);

        long bramBeforeTheSecondGift = app.pointsBalanceOf(BRAM);
        app.give(ANKE, BRAM, "5");
        assertThat(app.pointsBalanceOf(ANKE))
                .as("and they can be given away like any others: where a point came from stops "
                        + "mattering once it is hers")
                .isEqualTo(BRONZE_PAYS - A_CHARITY_DONATION_COSTS - 5);
        assertThat(app.pointsBalanceOf(BRAM)).isEqualTo(bramBeforeTheSecondGift + 5);

        long whatIsLeftToLose = app.pointsBalanceOf(ANKE);
        assertThat(whatIsLeftToLose).as("there is something left for the year to take").isPositive();

        app.daysPass(A_YEAR_AND_A_DAY);
        app.runJob("expireOldPoints");

        assertThat(app.pointsBalanceOf(ANKE))
                .as("twelve months on, what a rung paid is gone exactly as anything else would be — "
                        + "no special case anywhere in the ledger kept it alive")
                .isZero();
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("and there is nothing left to lose")
                .isNull();

        assertThat(app.achievementsOf(ANKE))
                .as("the points expired and the achievement did not: an award does not age out, "
                        + "whatever becomes of what it paid")
                .hasSize(1);
        assertThat(app.achievementsOf(ANKE).get(0).points())
                .as("and it still says what it paid, a year after the points themselves went")
                .isEqualTo(BRONZE_PAYS);
    }
}
