package io.dataroots.savingstreak.pointsexpiry;

import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer is told what they stand to lose next, and told nothing where there is nothing to lose.
 *
 * <p>Points that vanish with no warning are indistinguishable from points that have gone missing, so
 * the figure is on the resources a customer's screen is drawn from — the overview and every savings
 * account they hold, the same two figures in both places, because the twelve months run against their
 * points rather than against any one account's saving.
 *
 * <p>The moment reported is the anniversary rather than the sweep that will act on it. A customer
 * asked to trust "these go on the 14th" and then shown them go at three the following morning would
 * be right to distrust the next such promise; the anniversary is the promise, and the job is how it
 * is kept.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class WhatExpiresNextIsReportedApiTest extends ApiIntegrationTest {

    /** Comfortably the far side of any anniversary the clock could land near. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    /** Ten months, which leaves the first batch's anniversary the nearest thing on the horizon. */
    private static final int DAYS_WELL_SHORT_OF_A_YEAR = 300;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-what-expires-next"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_points_that_go_next_and_the_day_they_go_are_reported_until_there_are_none() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // Nothing earned, so nothing expires next — and that is not the same statement as nothing
        // expiring on some particular day, which is why both figures are absent rather than zero.
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("a customer who has never earned anything has nothing to lose next")
                .isNull();
        assertThat(app.pointsExpiringNextOnOf(ANKE)).isNull();
        BalancesView withNothingEarned = app.balancesOf(savingsAccount);
        assertThat(withNothingEarned.pointsExpiringNext()).isNull();
        assertThat(withNothingEarned.pointsExpiringNextOn()).isNull();

        // Two deposits on one afternoon, which is two batches expiring on one date. Both are counted,
        // because a figure that named only the first would understate what the day costs.
        LocalDate paidInOn = app.theDateTheClockReads();
        DepositView first = app.deposit(savingsAccount, ANKE, "40.00");
        DepositView second = app.deposit(savingsAccount, ANKE, "22.00");
        long earnedThatAfternoon = first.pointsEarned() + second.pointsEarned();
        assertThat(earnedThatAfternoon).isEqualTo(62);

        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("both batches earned that afternoon expire together, so both are in the figure")
                .isEqualTo(earnedThatAfternoon);
        LocalDate expiringOn = app.pointsExpiringNextOnOf(ANKE);
        // Exactly a year on to the day, which is the promise. Asserted against the date the
        // application's own clock reads rather than against today's, because a trainer may have
        // wound it — and the day of the month is what says twelve calendar months rather than a
        // count of days. The rule's own boundary cases are pinned in PointsExpiryTest, which is the
        // one thing in this repo the HTTP seam cannot reach: the clock endpoint moves whole days.
        assertThat(expiringOn)
                .as("a batch earned today reaches its twelve months on this day next year")
                .isEqualTo(paidInOn.plusYears(1));

        // The savings account reports the holder's figures, not the account's, so they read the same.
        BalancesView beside = app.balancesOf(savingsAccount);
        assertThat(beside.pointsExpiringNext()).isEqualTo(earnedThatAfternoon);
        assertThat(beside.pointsExpiringNextOn()).isEqualTo(expiringOn);

        // A younger batch is behind the old ones in the queue and does not change what goes next.
        app.daysPass(DAYS_WELL_SHORT_OF_A_YEAR);
        app.deposit(savingsAccount, ANKE, "11.00");
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("the ten-month-old batches are still the next to go")
                .isEqualTo(earnedThatAfternoon);
        assertThat(app.pointsExpiringNextOnOf(ANKE)).isEqualTo(expiringOn);

        app.daysPass(DAYS_WELL_PAST_A_YEAR - DAYS_WELL_SHORT_OF_A_YEAR);
        app.runJob("expireOldPoints");

        // The sweep took the batch the figure was naming, so the figure now names the next one along
        // — a smaller number, on a later day.
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("what goes next is now the batch that was behind them")
                .isEqualTo(11);
        assertThat(app.pointsExpiringNextOnOf(ANKE))
                .as("on a later day, because the batch behind them was earned later")
                .isAfter(expiringOn);
    }
}
