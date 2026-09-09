package io.dataroots.savingstreak.giftingpoints;

import java.time.LocalDate;

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
 * Giving away points whose anniversary has already passed hands over points the next sweep will
 * take. The gift goes through, the points are credited, and then they are swept — both rules holding
 * at once, and neither of them misbehaving.
 *
 * <p>This is the honest consequence of inheriting the date rather than a defect, and it is worth a
 * test of its own precisely because it looks like one. Nothing here is a special case: the sweep runs
 * once a night and judges every batch against the day it was earned, so a batch that passed its
 * anniversary this morning is still in its owner's balance and still spendable and still giftable
 * until the sweep gets to it. What the gift moves is the batch's date along with its points, so the
 * arriving batch is a day past its own twelve months from the moment it lands and the very next sweep
 * ends it — in the recipient's pot rather than in the sender's, which is the only thing the gift
 * changed.
 *
 * <p>Both events are one line each in the log. The credit is the ledger's own {@code points credited}
 * line with {@code reason=GIFT_RECEIVED} and the {@code oldestEarnedAt} the batch came with; the
 * ending is {@code points batch expired} naming the same reason and the same earned-at, under the
 * sweep's {@code points expired} total. A reviewer grepping either phrase sees the whole of what
 * happened here without having to reconstruct it from two balances.
 *
 * <p>Its own application, on a database nothing has ever been written to: it winds the clock more
 * than a year forward without ever running the sweep, which is the one arrangement in this repository
 * that could not survive being shared, and it needs points in Bram's pot.
 */
class AGiftOfPointsPastTheirAnniversaryIsSweptTheSameNightApiTest extends ApiIntegrationTest {

    private static final String THE_EXPIRY_SWEEP = "expireOldPoints";

    /**
     * A fortnight past a year. Comfortably the far side of any anniversary the clock could land near,
     * so the test is about the rule rather than about which day of which month the run happens on.
     */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-gift-past-its-anniversary"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void points_given_after_their_anniversary_are_credited_and_then_swept_away() {
        LocalDate sheEarnedThemOn = app.theDateTheClockReads();
        DepositView earnedThem = app.deposit(app.savingsAccountOf(ANKE), ANKE, "40.00");
        assertThat(earnedThem.pointsEarned()).isEqualTo(40);
        LocalDate theirTwelveMonthsWereUpOn = sheEarnedThemOn.plusYears(1);

        // A year and a fortnight, and no sweep has run in it. Their day has been and gone, and they
        // are still hers: the sweep is what ends a batch, not the calendar on its own.
        app.daysPass(DAYS_WELL_PAST_A_YEAR);
        assertThat(theirTwelveMonthsWereUpOn).isBefore(app.theDateTheClockReads());
        assertThat(app.pointsBalanceOf(ANKE))
                .as("nothing has swept them yet, so they are still in her balance")
                .isEqualTo(earnedThem.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(ANKE))
                .as("the day she is told they go is already behind her")
                .isEqualTo(theirTwelveMonthsWereUpOn);

        // The gift is not refused for their age — nothing in this feature refuses on age — and the
        // points are credited to him in full.
        GiftView gift = app.give(ANKE, BRAM, String.valueOf(earnedThem.pointsEarned()));
        assertThat(gift.points()).isEqualTo(earnedThem.pointsEarned());
        assertThat(app.pointsBalanceOf(BRAM))
                .as("credited in full: a gift of overdue points is still a gift that went through")
                .isEqualTo(earnedThem.pointsEarned());
        assertThat(app.pointsBalanceOf(ANKE)).isZero();
        assertThat(app.pointsExpiringNextOf(BRAM)).isEqualTo(earnedThem.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM))
                .as("he was handed points on a day already past, because the date came with them")
                .isEqualTo(theirTwelveMonthsWereUpOn);

        // And that night's sweep takes them, out of his pot, without his having done anything.
        app.runJob(THE_EXPIRY_SWEEP);
        assertThat(app.pointsBalanceOf(BRAM))
                .as("the sweep that would have taken them from her takes them from him instead")
                .isZero();
        assertThat(app.pointsExpiringNextOf(BRAM)).isNull();
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isNull();
        assertThat(app.pointsBalanceOf(ANKE))
                .as("and nothing came back to her: the gift was final")
                .isZero();
    }
}
