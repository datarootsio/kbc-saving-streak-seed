package io.dataroots.savingstreak.giftingpoints;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two customers passing the same points back and forth do not keep them alive: on the anniversary of
 * the day they were originally earned the points go, however many hands they went through in
 * between.
 *
 * <p>This is the attack the inherited dating exists to close, and the only test that actually runs
 * it. Nothing in this feature limits how often a customer may give, so if a gift dated the arriving
 * points at the moment it was made, a pair of customers could refresh the twelve months on every hop
 * and hold a balance for ever — the twelve-month rule would hold for everybody who never gifted and
 * be void for everybody who did. Six hops two months apart cover the whole year, so every one of
 * them lands while the points are comfortably alive and each is a chance for the clock to be reset.
 * The day the points were earned is asserted as the day they go, after all six.
 *
 * <p>A sweep is run after every hop as well, and takes nothing each time. That is the other half:
 * points that vanished early would make the ending assertion pass for the wrong reason, and a rule
 * that expired a batch the moment it changed hands would look identical at the end of the year. It
 * is worth being exact about what those in-loop sweeps do, because every hop is inside the twelve
 * months and the sweep only gathers batches old enough to be worth judging: they find nothing to
 * consider at all and so never reach the anniversary comparison. Nothing taken is still the answer
 * the year needs, and the balance read beside each one is what would catch points that went early.
 *
 * <p>What each hop asserts is the balance moving whole — a gift of everything the holder has, so
 * nothing is left behind to muddy the next hop — and the day the holder is told their points go,
 * which stays the original anniversary from the first hop to the last.
 *
 * <p>Its own application, on a database nothing has ever been written to: a year passes in it, and
 * it needs points in both customers' pots, which two tests on the shared database assert Bram has
 * never had.
 */
class PointsGivenBackAndForthDoNotOutliveTheirTwelveMonthsApiTest extends ApiIntegrationTest {

    private static final String THE_EXPIRY_SWEEP = "expireOldPoints";

    /**
     * Six hands the same points pass through. Six times {@link #DAYS_BETWEEN_THE_HOPS} is 360 days,
     * so the hops cover all but the last few days of the twelve months and the final one still lands
     * while the points are alive — a hop after the anniversary would be a different rule, and it has
     * a test of its own.
     */
    private static final int HOPS = 6;

    /**
     * Two months between hops. Long enough that no hop could be mistaken for the moment before it,
     * and nowhere near a week, so no gift is anywhere near the saving that earned the points.
     */
    private static final int DAYS_BETWEEN_THE_HOPS = 60;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-points-given-back-and-forth"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void points_passed_between_two_pots_all_year_still_go_on_the_day_they_were_earned() {
        LocalDate theyWereEarnedOn = app.theDateTheClockReads();
        DepositView earnedThem = app.deposit(app.savingsAccountOf(ANKE), ANKE, "30.00");
        long thePoints = earnedThem.pointsEarned();
        assertThat(thePoints).isEqualTo(30);
        LocalDate theirTwelveMonthsAreUpOn = theyWereEarnedOn.plusYears(1);
        assertThat(app.pointsExpiringNextOnOf(ANKE)).isEqualTo(theirTwelveMonthsAreUpOn);

        String holder = ANKE;
        for (int hop = 1; hop <= HOPS; hop++) {
            app.daysPass(DAYS_BETWEEN_THE_HOPS);
            String whoGaveThem = holder;
            String whoHasThemNow = ANKE.equals(whoGaveThem) ? BRAM : ANKE;

            app.give(whoGaveThem, whoHasThemNow, String.valueOf(thePoints));
            holder = whoHasThemNow;

            // The whole of them moved, and nothing was created or left behind on the way.
            assertThat(app.pointsBalanceOf(whoHasThemNow))
                    .as("hop %d put the whole of them in %s's pot", hop, whoHasThemNow)
                    .isEqualTo(thePoints);
            assertThat(app.pointsBalanceOf(whoGaveThem))
                    .as("hop %d emptied %s's pot", hop, whoGaveThem)
                    .isZero();

            // And the hop did not touch the clock they came with. This is the assertion the
            // spend-and-credit alternative would fail, on the very first hop.
            assertThat(app.pointsExpiringNextOnOf(whoHasThemNow))
                    .as("after hop %d they still go on the day they were earned, not a year on "
                            + "from the hop", hop)
                    .isEqualTo(theirTwelveMonthsAreUpOn);

            // Nor did it shorten it: a sweep run now takes nothing. It does not even judge these
            // points — every hop lands well inside the twelve months, so the batch is younger than
            // the cut-off the sweep gathers candidates by and the sweep never looks at it, which is
            // itself the statement that a gift did not backdate them into range. What the assertion
            // catches is points ending early by any route at all, hop included: without it the
            // ending below would pass on a pot that had been empty since the first hop.
            app.runJob(THE_EXPIRY_SWEEP);
            assertThat(app.pointsBalanceOf(whoHasThemNow))
                    .as("a sweep after hop %d takes nothing, because their year is not up", hop)
                    .isEqualTo(thePoints);
        }

        assertThat(ChronoUnit.DAYS.between(theyWereEarnedOn, app.theDateTheClockReads()))
                .as("the hops covered the year and the last of them landed inside it")
                .isEqualTo((long) HOPS * DAYS_BETWEEN_THE_HOPS)
                .isLessThan(ChronoUnit.DAYS.between(theyWereEarnedOn, theirTwelveMonthsAreUpOn));

        // The anniversary of the day they were earned arrives, and they go — out of whichever pot
        // the last hop left them in.
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), theirTwelveMonthsAreUpOn) + 1);
        app.runJob(THE_EXPIRY_SWEEP);

        assertThat(app.pointsBalanceOf(holder))
                .as("%d hops bought them no extra time at all", HOPS)
                .isZero();
        assertThat(app.pointsBalanceOf(ANKE)).isZero();
        assertThat(app.pointsBalanceOf(BRAM)).isZero();
        assertThat(app.pointsExpiringNextOf(ANKE)).isNull();
        assertThat(app.pointsExpiringNextOf(BRAM)).isNull();
    }
}
