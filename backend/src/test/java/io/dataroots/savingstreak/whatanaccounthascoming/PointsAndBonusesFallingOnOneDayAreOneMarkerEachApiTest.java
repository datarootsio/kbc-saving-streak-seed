package io.dataroots.savingstreak.whatanaccounthascoming;

import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.TimelineView;
import io.dataroots.savingstreak.support.TimelineView.Event;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two deposits made on one afternoon are one marker of each kind, carrying what the whole day is
 * worth.
 *
 * <p>A bar has one position for a day, and what a customer reads off a marker is what that day costs
 * them or pays them — not what each lot in it was. Two markers drawn on top of each other would be
 * one marker with the wrong number on it, which is worse than either answer.
 *
 * <p>It is the grouping {@code PointsExpiringNext} has always applied to the customer's own figure,
 * for the same reason: a figure that named only the first of two batches reaching their twelve
 * months on one day would understate what the day costs. Here it is applied to the anniversaries as
 * well, which is new — two deposits paying on one day pay once, as far as a bar is concerned, and
 * which deposit paid what is the history's answer a row at a time on the same screen.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class PointsAndBonusesFallingOnOneDayAreOneMarkerEachApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationForOneBusyAfternoon() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-one-day-one-marker"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_day_two_deposits_landed_on_is_one_marker_of_each_kind_worth_both() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        LocalDate oneAfternoon = app.theDateTheClockReads();
        assertThat(app.deposit(savingsAccount, ANKE, "40.00").pointsEarned()).isEqualTo(40);
        assertThat(app.deposit(savingsAccount, ANKE, "22.00").pointsEarned()).isEqualTo(22);

        TimelineView bar = app.timelineOf(savingsAccount);

        // Two deposits, four things happening, two markers: the points from both lots go on one day
        // and both anniversaries pay on it. A tenth of EUR 40 and a tenth of EUR 22 are four points
        // and two, and the day is worth six.
        assertThat(bar.events())
                .as("one busy afternoon is one day on the bar, whatever it was made of")
                .containsExactly(
                        new Event(oneAfternoon.plusYears(1), "POINTS_EXPIRE", 62),
                        new Event(oneAfternoon.plusYears(1), "LOYALTY_BONUS", 6));

        // The same day, read the same way, on the customer's own figure: the two answers are made of
        // the same batches, so they cannot come to disagree about what that day is worth.
        assertThat(app.pointsExpiringNextOf(ANKE)).isEqualTo(62);
        assertThat(app.pointsExpiringNextOnOf(ANKE)).isEqualTo(oneAfternoon.plusYears(1));
    }
}
