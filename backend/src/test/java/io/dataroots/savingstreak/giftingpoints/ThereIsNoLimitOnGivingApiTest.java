package io.dataroots.savingstreak.giftingpoints;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GiftView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * There is no cap on the size of one gift, no daily total, no cooldown between gifts, no minimum and
 * no limit on how many people one customer may give to. The only bound is the one arithmetic imposes.
 *
 * <p>An absence asserted rather than assumed, because it was asked for explicitly and because it is
 * the kind of rule a later change adds by being helpful. A cooldown, a daily total or a cap would
 * each turn one of these two tests red, which is the whole reason they exist: nothing else in the
 * application would notice.
 *
 * <p>Its own application on a database nothing has ever been written to, for the reason
 * {@link AGiftMovesPointsFromOneCustomerToAnotherApiTest} gives: a gift needs two customers with
 * pots, and the run's shared database is where other tests assert that Bram has never earned a point
 * in his life.
 *
 * <p>Anke is the sender throughout, because she is the customer these tests can earn points for.
 * Every figure is read off the API rather than assumed, so that a gift is asserted to have moved
 * what it said it moved rather than what this test guessed the deposit would earn.
 */
class ThereIsNoLimitOnGivingApiTest extends ApiIntegrationTest {

    /**
     * How many gifts in a row is enough to say "no cooldown and no daily total". Twelve, sent one
     * after another as fast as the API answers, all inside the same day of the application's clock:
     * any per-day ceiling or wait between gifts would have to be absurdly generous to survive it.
     */
    private static final int GIFTS_IN_A_ROW = 12;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-no-limit-on-giving"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * Gift after gift after gift, with nothing in between them: every one goes through, the twelve
     * of them move exactly twelve points, and all twelve are written down.
     *
     * <p>{@link AnApplicationWithAClockToMove#give} insists on the 201 itself, so a gift refused
     * part-way through this run fails on the gift that was refused rather than several assertions
     * later — and each gift is asserted to be a new one, because a cooldown implemented as "answer
     * with the gift you already made" would otherwise look like success.
     */
    @Test
    void many_gifts_one_after_another_all_go_through() {
        app.deposit(app.savingsAccountOf(ANKE), ANKE, "60.00");
        long ankeHeld = app.pointsBalanceOf(ANKE);
        long bramHeld = app.pointsBalanceOf(BRAM);
        int giftsAnkeWasPartOf = app.giftsOf(ANKE).length;
        int giftsBramWasPartOf = app.giftsOf(BRAM).length;
        assertThat(ankeHeld)
                .as("the deposit earned enough to give away one point at a time")
                .isGreaterThanOrEqualTo(GIFTS_IN_A_ROW);

        for (int gift = 1; gift <= GIFTS_IN_A_ROW; gift++) {
            GiftView given = app.give(ANKE, BRAM, "1");
            assertThat(given.id()).as("gift number " + gift + " is a gift of its own").isNotNull();
            assertThat(given.points()).isEqualTo(1);
        }

        assertThat(app.pointsBalanceOf(ANKE))
                .as("twelve gifts of one point cost twelve points and not a point more")
                .isEqualTo(ankeHeld - GIFTS_IN_A_ROW);
        assertThat(app.pointsBalanceOf(BRAM))
                .as("and all twelve of them arrived")
                .isEqualTo(bramHeld + GIFTS_IN_A_ROW);
        assertThat(app.giftsOf(ANKE).length)
                .as("every one of them is in the sender's record")
                .isEqualTo(giftsAnkeWasPartOf + GIFTS_IN_A_ROW);
        assertThat(app.giftsOf(BRAM).length)
                .as("and in the recipient's")
                .isEqualTo(giftsBramWasPartOf + GIFTS_IN_A_ROW);
    }

    /**
     * One gift of everything they hold. A customer who means to clear their pot in a single act is
     * allowed to: the bound is what they have, and nothing about the size of one gift.
     *
     * <p>Left at zero, which is the assertion that says the whole balance really was the whole
     * balance rather than as much of it as some cap allowed through.
     */
    @Test
    void one_gift_of_the_senders_entire_balance_goes_through_and_leaves_them_at_zero() {
        app.deposit(app.savingsAccountOf(ANKE), ANKE, "45.00");
        long everythingAnkeHas = app.pointsBalanceOf(ANKE);
        long bramHeld = app.pointsBalanceOf(BRAM);
        assertThat(everythingAnkeHas).as("there is a pot to clear").isPositive();

        GiftView given = app.give(ANKE, BRAM, String.valueOf(everythingAnkeHas));

        assertThat(given.points()).isEqualTo(everythingAnkeHas);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the whole pot went, and the sender is left with nothing")
                .isZero();
        assertThat(app.pointsBalanceOf(BRAM))
                .as("all of it arrived: nothing was created, skimmed or destroyed on the way")
                .isEqualTo(bramHeld + everythingAnkeHas);
    }
}
