package io.dataroots.savingstreak.giftingpoints;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.GiftView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gifted points expire twelve months after they were <em>earned</em>, not twelve months after they
 * were given: the clock came with them, and the sweep that ends them is the same sweep that would
 * have ended them in the pot they left.
 *
 * <p>The arrangement is chosen so that only the inherited clock can pass it. The points are earned,
 * left alone for ten months, and then given away — so twelve months after they were earned is a
 * little over two months after they were given, and the two readings of the rule are more than nine
 * months apart. A sweep run the day before that anniversary takes nothing, which is the half that
 * says the sweep is not simply taking everything it can see; a sweep run the day after takes the
 * whole of them, in a pot they had been sitting in for barely two months. Had the gift restarted
 * their twelve months, that second sweep would have found points with ten months still to run and
 * left them exactly where they were.
 *
 * <p>Asserted through the balance and through what the recipient is told expires next, which are the
 * two windows this application opens onto anybody's points. The day is the interesting figure: it is
 * the anniversary of the day <em>she</em> earned them, and he never made a deposit in his life here —
 * a pot holding points older than any deposit its owner ever made, which the spec calls the honest
 * description of having been given something second-hand.
 *
 * <p>Its own application, on a database nothing has ever been written to, for the reason every
 * clock-moving test in this repository gives and then some: this one winds the clock more than a year
 * forward, which nothing else in the run could survive sharing, and it needs points in Bram's pot,
 * which two tests on the shared database assert he has never had.
 */
class GiftedPointsExpireTwelveMonthsAfterTheyWereEarnedApiTest extends ApiIntegrationTest {

    private static final String THE_EXPIRY_SWEEP = "expireOldPoints";

    /**
     * Ten months of holding on to them before giving them away. Far enough in that the gift is
     * nowhere near the day they were earned, and far enough short of the anniversary that the points
     * are unquestionably still alive when they change hands — so what the later sweeps say is about
     * the rule rather than about which day of which month the run happens on.
     */
    private static final int DAYS_SHE_HELD_THEM_FOR = 300;

    /** A plain year in days, as the shortest thing "twelve months after the gift" could mean. */
    private static final int DAYS_IN_A_YEAR = 365;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-gifted-points-keep-their-age"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void gifted_points_go_on_the_anniversary_of_the_day_they_were_earned_and_not_before() {
        LocalDate sheEarnedThemOn = app.theDateTheClockReads();
        DepositView earnedThem = app.deposit(app.savingsAccountOf(ANKE), ANKE, "40.00");
        assertThat(earnedThem.pointsEarned()).isEqualTo(40);
        assertThat(app.pointsBalanceOf(BRAM))
                .as("nothing has ever been paid into his account in this application")
                .isZero();

        LocalDate theirTwelveMonthsAreUpOn = sheEarnedThemOn.plusYears(1);
        assertThat(app.pointsExpiringNextOnOf(ANKE)).isEqualTo(theirTwelveMonthsAreUpOn);

        // Ten months of her holding them, and then the gift.
        app.daysPass(DAYS_SHE_HELD_THEM_FOR);
        LocalDate sheGaveThemOn = app.theDateTheClockReads();
        GiftView gift = app.give(ANKE, BRAM, String.valueOf(earnedThem.pointsEarned()));

        // They count towards his balance from the moment the gift goes through: no acceptance step,
        // nothing to claim, and the figure has already moved by the time the gift comes back.
        assertThat(gift.points()).isEqualTo(earnedThem.pointsEarned());
        assertThat(app.pointsBalanceOf(BRAM))
                .as("the points are his the moment the gift goes through")
                .isEqualTo(earnedThem.pointsEarned());
        assertThat(app.pointsBalanceOf(ANKE)).isZero();
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("she gave away the only points she had, so she has nothing left to lose")
                .isNull();
        assertThat(app.pointsExpiringNextOnOf(ANKE)).isNull();

        // And they arrived carrying her anniversary rather than today's. This is the whole rule, and
        // it is a date in his pot that predates every deposit he has ever made.
        assertThat(app.pointsExpiringNextOf(BRAM)).isEqualTo(earnedThem.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM))
                .as("twelve months after she earned them, not twelve months after he was given them")
                .isEqualTo(theirTwelveMonthsAreUpOn);
        assertThat(ChronoUnit.DAYS.between(sheGaveThemOn, theirTwelveMonthsAreUpOn))
                .as("their remaining life in his pot is a couple of months, not another twelve")
                .isPositive()
                .isLessThan(DAYS_IN_A_YEAR);

        // The day before that anniversary, and a sweep run then leaves them exactly where they are.
        // Without this half, a sweep that expired everything it could see would pass the next one.
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), theirTwelveMonthsAreUpOn) - 1);
        app.runJob(THE_EXPIRY_SWEEP);
        assertThat(app.pointsBalanceOf(BRAM))
                .as("a sweep run inside their twelve months takes nothing, gift or no gift")
                .isEqualTo(earnedThem.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isEqualTo(theirTwelveMonthsAreUpOn);

        // The far side of it, and the same sweep takes the whole of them.
        app.daysPass(2);
        app.runJob(THE_EXPIRY_SWEEP);
        assertThat(app.pointsBalanceOf(BRAM))
                .as("their twelve months were up, and being in somebody else's pot did not extend them")
                .isZero();
        assertThat(app.pointsExpiringNextOf(BRAM)).isNull();
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isNull();
        assertThat(ChronoUnit.DAYS.between(sheGaveThemOn, app.theDateTheClockReads()))
                .as("gone well inside a year of being given, which a restarted clock could not do")
                .isLessThan(DAYS_IN_A_YEAR);

        // And again, because a nightly job runs nightly: a batch that has gone stays gone and is
        // never expired twice.
        app.runJob(THE_EXPIRY_SWEEP);
        assertThat(app.pointsBalanceOf(BRAM)).isZero();
        assertThat(app.pointsBalanceOf(ANKE)).isZero();
    }
}
