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
 * A gift is not a dead end: points somebody was given can be given on again, and they carry the age
 * they were originally earned at when they go — not the age of the hand that passed them on.
 *
 * <p>The onward gift here is a gift of points its sender never earned, drawn from a pot he has never
 * deposited a euro into, made sixty days after they arrived and a hundred and sixty days after
 * anybody earned anything. It is the second hop that could plausibly have lost the original date:
 * the first hop reads it off a deposit's own batch, and the second has to read it off a batch that
 * was itself created by a gift.
 *
 * <p>Two ages are earned so that the onward gift has an oldest to draw from and a younger one to
 * leave behind, which is what makes both ends readable. He gives on exactly the older slice's worth,
 * so his pot is left holding the younger slice on the younger day, and what she is told goes next is
 * the older slice on the day she herself earned it. Then the older anniversary arrives, the sweep
 * runs once, and it takes her returned points and leaves his — two batches that were one gift,
 * ending on the two days they were earned on.
 *
 * <p>The onward hop returns to the customer who first earned them because this application banks two
 * customers and there is no third to hand them to; that is a limit of the seeded data, not of the
 * rule. What the case is actually about is asserted either way: the sender of the onward gift is
 * giving away points that were given to him, and the date that travels is the date of the deposit
 * that earned them rather than the date of either hop. A third pot would be a third name in the
 * assertions and nothing else.
 *
 * <p>Its own application, on a database nothing has ever been written to: it winds the clock past an
 * anniversary, and it needs points in Bram's pot, which two tests on the shared database assert he
 * has never had.
 */
class ReceivedPointsCanBeGivenOnwardApiTest extends ApiIntegrationTest {

    private static final String THE_EXPIRY_SWEEP = "expireOldPoints";

    /**
     * A hundred days between her two deposits, so the gift she makes carries two ages and the onward
     * gift has an oldest to take. Nowhere near a week, so neither deposit secures the other's week
     * and both are paid at the plain rate.
     */
    private static final int DAYS_BETWEEN_HER_TWO_DEPOSITS = 100;

    /** Two months of him holding them before he passes them on, so the onward hop is its own moment. */
    private static final int DAYS_HE_HELD_THEM_FOR = 60;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-received-points-go-onward"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void points_given_on_a_second_time_still_carry_the_age_they_were_earned_at() {
        long ankesSavings = app.savingsAccountOf(ANKE);

        LocalDate theOlderWasEarnedOn = app.theDateTheClockReads();
        DepositView older = app.deposit(ankesSavings, ANKE, "12.00");
        app.daysPass(DAYS_BETWEEN_HER_TWO_DEPOSITS);
        LocalDate theYoungerWasEarnedOn = app.theDateTheClockReads();
        DepositView younger = app.deposit(ankesSavings, ANKE, "20.00");
        assertThat(older.pointsEarned()).isEqualTo(12);
        assertThat(younger.pointsEarned()).isEqualTo(20);

        // The first hop: everything she holds, which is two ages and so arrives as two batches.
        long everythingSheHeld = older.pointsEarned() + younger.pointsEarned();
        app.give(ANKE, BRAM, String.valueOf(everythingSheHeld));
        assertThat(app.pointsBalanceOf(BRAM)).isEqualTo(everythingSheHeld);
        assertThat(app.pointsBalanceOf(ANKE)).isZero();
        assertThat(app.pointsExpiringNextOf(BRAM)).isEqualTo(older.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isEqualTo(theOlderWasEarnedOn.plusYears(1));

        app.daysPass(DAYS_HE_HELD_THEM_FOR);

        // The second hop: he gives on exactly the older slice's worth of points he never earned.
        GiftView onward = app.give(BRAM, ANKE, String.valueOf(older.pointsEarned()));
        assertThat(onward.points()).isEqualTo(older.pointsEarned());
        assertThat(onward.senderId()).isEqualTo(app.customerIdOf(BRAM));
        assertThat(onward.recipientId()).isEqualTo(app.customerIdOf(ANKE));

        // Oldest first out of his pot too, so what he is left holding is the younger slice on the
        // younger day — the age of that batch survived being gifted once and drawn on twice.
        assertThat(app.pointsBalanceOf(BRAM)).isEqualTo(younger.pointsEarned());
        assertThat(app.pointsExpiringNextOf(BRAM)).isEqualTo(younger.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isEqualTo(theYoungerWasEarnedOn.plusYears(1));

        // And what arrived on the far side of the second hop is dated at the deposit that earned it,
        // a hundred and sixty days and two gifts ago. Dated at either hop it would have five months
        // or a year still to run.
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(older.pointsEarned());
        assertThat(app.pointsExpiringNextOf(ANKE)).isEqualTo(older.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(ANKE))
                .as("dated at the moment they were originally earned, not at either gift")
                .isEqualTo(theOlderWasEarnedOn.plusYears(1));

        // The older anniversary arrives, and one sweep separates the two: it takes what was given
        // onward and leaves what was not, because the two were always two ages.
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(),
                theOlderWasEarnedOn.plusYears(1)) + 1);
        app.runJob(THE_EXPIRY_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("twelve months after she earned them, whichever pots they toured in between")
                .isZero();
        assertThat(app.pointsBalanceOf(BRAM))
                .as("the younger slice's own twelve months are not up")
                .isEqualTo(younger.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isEqualTo(theYoungerWasEarnedOn.plusYears(1));
    }
}
