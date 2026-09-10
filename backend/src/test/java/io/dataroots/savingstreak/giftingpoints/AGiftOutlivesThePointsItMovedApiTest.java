package io.dataroots.savingstreak.giftingpoints;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.GiftView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What somebody did is not undone by what later happened to the points they did it with. Both ways
 * a point can leave a pot are walked here — one gift is spent on a reward and the other is left to
 * the twelve-month sweep — and afterwards nobody holds a single point of either, while both gifts
 * are still in both customers' lists for exactly what they were worth.
 *
 * <p>This is the case that says the record is written down rather than derived from the ledger. A
 * list read off the batches a gift created would be complete right up until the batches were gone,
 * and would then quietly lose the gift: the customer who gave forty points away would be shown as
 * having given nothing, and the customer who spent them would have no record of where they came
 * from. The two ways of losing points are both here because they take the batch away by different
 * routes — a claim draws it down to nothing, and the sweep deletes what is past its anniversary —
 * and a record derived from the ledger would fail on either.
 *
 * <p>The first gift is exactly what the snack voucher costs, so the claim spends that gift and
 * nothing else. The second is the whole of what she had left, so once the sweep has run neither of
 * them holds anything at all and there is no batch anywhere for a list to have been derived from.
 *
 * <p>Its own application, on a database nothing has ever been written to: it winds a year on, which
 * cannot be done to the shared one, and it needs both pots empty at the end, which is only
 * assertable where nothing else has ever earned anything.
 */
class AGiftOutlivesThePointsItMovedApiTest extends ApiIntegrationTest {

    private static final String THE_EXPIRY_SWEEP = "expireOldPoints";

    /** What the snack voucher costs, which is also the size of the gift that pays for it. */
    private static final long SNACK_VOUCHER_COSTS = 40;

    /** A day between the two gifts, so the pair of them has an unarguable order in both lists. */
    private static final int DAYS_BETWEEN_THE_TWO_GIFTS = 1;

    /** A day past the anniversary, which is the first day the points she earned are no longer hers. */
    private static final int A_DAY_PAST_THE_ANNIVERSARY = 1;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-gift-outlives-its-points"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_gift_stays_in_both_lists_after_its_points_have_been_spent_and_after_they_have_expired() {
        LocalDate sheEarnedThemOn = app.theDateTheClockReads();
        DepositView hers = app.deposit(app.savingsAccountOf(ANKE), ANKE, "50.00");
        assertThat(hers.pointsEarned()).isEqualTo(50);

        // The gift that gets spent: exactly the price of the voucher he buys with it.
        GiftView theOneHeSpends = app.give(ANKE, BRAM, String.valueOf(SNACK_VOUCHER_COSTS));
        ClaimedRewardView voucher = app.claim(BRAM, "SNACK_VOUCHER");
        assertThat(voucher.pointsSpent()).isEqualTo(SNACK_VOUCHER_COSTS);
        assertThat(app.pointsBalanceOf(BRAM))
                .as("the whole of that gift has been spent")
                .isZero();

        assertThat(app.giftsOf(ANKE))
                .as("the gift is still hers to read the moment its points have been spent")
                .hasSize(1);
        assertThat(app.giftsOf(BRAM)).hasSize(1);

        // The gift that gets swept: the rest of what she earned that day, left alone until its
        // twelve months are up.
        app.daysPass(DAYS_BETWEEN_THE_TWO_GIFTS);
        long whatSheHasLeft = hers.pointsEarned() - SNACK_VOUCHER_COSTS;
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(whatSheHasLeft);
        GiftView theOneThatExpires = app.give(ANKE, BRAM, String.valueOf(whatSheHasLeft));
        assertThat(app.pointsBalanceOf(BRAM)).isEqualTo(whatSheHasLeft);

        // Twelve months after she earned them — the day they go is hers and not the day of the
        // gift, and one sweep on the far side of it clears the last point either of them holds.
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(),
                sheEarnedThemOn.plusYears(1)) + A_DAY_PAST_THE_ANNIVERSARY);
        app.runJob(THE_EXPIRY_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("she gave away everything she earned and earned nothing since")
                .isZero();
        assertThat(app.pointsBalanceOf(BRAM))
                .as("what he was given is a year old, whichever pot it aged in")
                .isZero();
        assertThat(app.pointsExpiringNextOf(BRAM))
                .as("there is nothing left to lose")
                .isNull();

        // Not one point of either gift is anywhere, and both gifts are still in both lists.
        GiftView[] herList = app.giftsOf(ANKE);
        assertThat(herList).hasSize(2);
        assertThat(herList[0].id()).isEqualTo(theOneThatExpires.id());
        assertThat(herList[0].direction()).isEqualTo("SENT");
        assertThat(herList[0].points())
                .as("the record still says what the expired gift was worth")
                .isEqualTo(whatSheHasLeft);
        assertThat(herList[0].recipientName()).isEqualTo(BRAM);
        assertThat(herList[1].id()).isEqualTo(theOneHeSpends.id());
        assertThat(herList[1].direction()).isEqualTo("SENT");
        assertThat(herList[1].points())
                .as("and what the spent one was worth")
                .isEqualTo(SNACK_VOUCHER_COSTS);
        assertThat(herList[1].recipientName()).isEqualTo(BRAM);

        GiftView[] hisList = app.giftsOf(BRAM);
        assertThat(hisList).hasSize(2);
        assertThat(hisList[0].id()).isEqualTo(theOneThatExpires.id());
        assertThat(hisList[0].direction()).isEqualTo("RECEIVED");
        assertThat(hisList[0].points()).isEqualTo(whatSheHasLeft);
        assertThat(hisList[0].senderName()).isEqualTo(ANKE);
        assertThat(hisList[1].id()).isEqualTo(theOneHeSpends.id());
        assertThat(hisList[1].direction())
                .as("what he was given is still on his record after he spent it")
                .isEqualTo("RECEIVED");
        assertThat(hisList[1].points()).isEqualTo(SNACK_VOUCHER_COSTS);
        assertThat(hisList[1].senderName()).isEqualTo(ANKE);
    }
}
