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
 * A gift is drawn from the sender's oldest points first — the same draw a reward claim makes — and
 * each slice it draws arrives in the recipient's pot as a batch of its own, dated at the moment the
 * batch it came out of was earned.
 *
 * <p>This is the one decision in the feature a customer would not have guessed, and it is what keeps
 * "a point lasts twelve months" true rather than nearly true: a gift that arrived as a single fresh
 * batch would restart the clock on every hop, and with nothing limiting how often people may give,
 * two customers passing the same points back and forth would keep a balance alive indefinitely.
 *
 * <p>Asserted through what each customer is told expires next, which is the only window this
 * application opens onto the age of anybody's points — and the right one, because the age is only
 * interesting for what it costs them later. Three deposits a hundred days apart give the sender
 * three ages to draw from; one gift larger than the two oldest of them together then says everything
 * at once: it empties the oldest, empties the middle one, takes a single point off the youngest, and
 * arrives at the other end as three batches whose soonest anniversary is the oldest deposit's rather
 * than today's.
 *
 * <p>One test rather than several, and its own application, for the reason every clock-moving test
 * in this repository gives: the clock only goes forward, so a class whose tests each wound it on
 * would be asserting against whatever order they happened to run in.
 */
class AGiftIsDrawnFromTheSendersOldestPointsFirstApiTest extends ApiIntegrationTest {

    /**
     * Long enough apart that the three deposits have three different anniversaries, and short enough
     * that all three are still inside their twelve months when the gift is made. A hundred days is
     * also nowhere near a week, so no deposit here secures a week the previous one started and the
     * points are paid at the plain rate.
     */
    private static final int DAYS_BETWEEN_THE_DEPOSITS = 100;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-gift-is-drawn-oldest-first"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_gift_takes_the_oldest_points_first_and_each_slice_keeps_the_age_it_was_earned_at() {
        long ankesSavings = app.savingsAccountOf(ANKE);

        LocalDate theOldestWasEarnedOn = app.theDateTheClockReads();
        DepositView oldest = app.deposit(ankesSavings, ANKE, "6.00");
        app.daysPass(DAYS_BETWEEN_THE_DEPOSITS);
        LocalDate theMiddleOneWasEarnedOn = app.theDateTheClockReads();
        DepositView middle = app.deposit(ankesSavings, ANKE, "9.00");
        app.daysPass(DAYS_BETWEEN_THE_DEPOSITS);
        LocalDate theYoungestWasEarnedOn = app.theDateTheClockReads();
        DepositView youngest = app.deposit(ankesSavings, ANKE, "20.00");

        // Three ages, and the oldest is at the front of the queue: what she stands to lose next is
        // the first deposit's points, on the anniversary of the day it landed.
        assertThat(app.pointsExpiringNextOf(ANKE)).isEqualTo(oldest.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(ANKE)).isEqualTo(theOldestWasEarnedOn.plusYears(1));
        long ankeHeld = app.pointsBalanceOf(ANKE);
        assertThat(ankeHeld)
                .isEqualTo(oldest.pointsEarned() + middle.pointsEarned() + youngest.pointsEarned());
        assertThat(app.pointsBalanceOf(BRAM))
                .as("nothing has ever been paid into his account in this application")
                .isZero();

        // One point more than the two oldest deposits are worth together, so the gift cannot come out
        // of any one batch: it needs all of the oldest, all of the middle one, and a single point off
        // the youngest.
        long givenAway = oldest.pointsEarned() + middle.pointsEarned() + 1;
        GiftView gift = app.give(ANKE, BRAM, String.valueOf(givenAway));

        assertThat(gift.points()).isEqualTo(givenAway);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(ankeHeld - givenAway);
        assertThat(app.pointsBalanceOf(BRAM)).isEqualTo(givenAway);

        // Oldest first, and drawn from as many batches as it needed: the two older deposits are gone
        // from her pot entirely, so what she now stands to lose next is the youngest deposit, one
        // point lighter than it was earned.
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("the oldest two batches were emptied and the youngest is one point short")
                .isEqualTo(youngest.pointsEarned() - 1);
        assertThat(app.pointsExpiringNextOnOf(ANKE))
                .isEqualTo(theYoungestWasEarnedOn.plusYears(1));

        // And at the other end the gift did not arrive as one batch of anything. What he stands to
        // lose next is exactly the oldest slice, on the anniversary of the day *she* earned it —
        // months before he was given it, and a pot holding points older than any deposit he has ever
        // made. Had the whole gift arrived dated at any single moment, this figure would be the whole
        // gift on one day.
        assertThat(app.pointsExpiringNextOf(BRAM))
                .as("only the oldest slice goes on the oldest slice's day")
                .isEqualTo(oldest.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM))
                .as("twelve months after she earned them, not twelve months after he was given them")
                .isEqualTo(theOldestWasEarnedOn.plusYears(1));

        // Wound past that anniversary and swept, the oldest slice goes and the next one along
        // surfaces — which is the second age proved rather than inferred: the middle slice kept the
        // middle deposit's date, and the single point off the youngest kept the youngest's.
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(),
                theOldestWasEarnedOn.plusYears(1)) + 1);
        app.runJob("expireOldPoints");

        assertThat(app.pointsBalanceOf(BRAM))
                .as("the oldest slice reached its twelve months in his pot")
                .isEqualTo(givenAway - oldest.pointsEarned());
        assertThat(app.pointsExpiringNextOf(BRAM)).isEqualTo(middle.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM))
                .as("the middle slice carries the middle deposit's own anniversary")
                .isEqualTo(theMiddleOneWasEarnedOn.plusYears(1));
        assertThat(app.pointsBalanceOf(ANKE))
                .as("her own remaining points are younger and the sweep left them alone")
                .isEqualTo(ankeHeld - givenAway);
    }
}
