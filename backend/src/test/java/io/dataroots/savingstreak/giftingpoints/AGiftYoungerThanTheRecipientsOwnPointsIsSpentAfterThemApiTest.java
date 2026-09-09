package io.dataroots.savingstreak.giftingpoints;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A gift waits its turn: points somebody was given are spent after points that customer already
 * held and earned earlier, because the one order the ledger has is oldest first and being a gift
 * buys no place in the queue.
 *
 * <p>This is {@link GiftedPointsAreSpentLikeAnyOtherPointsApiTest} run in the other direction, and
 * it exists because that direction cannot tell the rule from a special case. There the gift is the
 * older of the two ages, so "oldest first" and "gifts first" predict the same figure in every
 * assertion — a ledger that put gifted batches at the front of the queue would pass it unchanged.
 * Here the recipient's own points are the older, so the two readings disagree from the first claim
 * onwards: every figure below is what oldest-first spends and none of them is what gifts-first
 * would spend. "With no regard to which reason earned them" needs both directions asserted or it is
 * only half said.
 *
 * <p>He saves first and she saves a hundred days later, so the gift she sends him is the younger
 * batch in his pot and the two ages fall due on two different days. Which batch a claim came out of
 * is not a question this application answers directly; the day and the figure it says go next are,
 * and with two ages in the pot they are enough. Two claims walk his own older batch down and then
 * empty it — the figure due on <em>his</em> day falls 50, 10 and then vanishes, leaving the whole of
 * the gift untouched on <em>her</em> later day. Gifts-first would have left his own fifty sitting
 * there on his own day throughout, and drawn the claims out of hers.
 *
 * <p>Then his own anniversary arrives and the sweep runs. It takes nothing: the batch whose year was
 * up is the one he spent, and the gift is a hundred days short of its own anniversary — young enough
 * that the sweep does not even look at it. So the pot survives the day its owner's own points would
 * have gone on, still holding the gift on the gift's own day. Spent gifts-first, that same sweep
 * would have found fifty of his own unspent points a year old and taken them.
 *
 * <p>Its own application, on a database nothing has ever been written to. It moves the clock past a
 * year, which nothing sharing a clock could survive, and it needs points in Bram's pot, which two
 * tests on the shared database assert he has never had.
 */
class AGiftYoungerThanTheRecipientsOwnPointsIsSpentAfterThemApiTest extends ApiIntegrationTest {

    private static final String THE_EXPIRY_SWEEP = "expireOldPoints";

    /** What the snack voucher costs: less than his own batch, so the first claim comes out of it. */
    private static final long SNACK_VOUCHER_COSTS = 40;

    /**
     * What the cheapest reward costs, which is exactly what the first claim left of his own batch —
     * so the second claim empties it to the point and takes nothing out of the gift.
     */
    private static final long CHARITY_DONATION_COSTS = 10;

    /**
     * A hundred days between his saving and hers. Long enough that his batch and her gift fall due
     * on two different days, which is what makes the spending order readable at all, and short
     * enough that both are alive throughout the claims.
     */
    private static final int DAYS_BETWEEN_THE_TWO_POTS = 100;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-younger-gift-waits-its-turn"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_gift_younger_than_what_the_recipient_already_held_is_spent_after_it() {
        LocalDate heEarnedHisOn = app.theDateTheClockReads();
        DepositView his = app.deposit(app.savingsAccountOf(BRAM), BRAM, "50.00");
        assertThat(his.pointsEarned()).isEqualTo(50);
        LocalDate hisTwelveMonthsAreUpOn = heEarnedHisOn.plusYears(1);
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isEqualTo(hisTwelveMonthsAreUpOn);

        // A hundred days later she earns hers and gives him the lot, so what arrives is the younger
        // of the two ages in his pot rather than the older.
        app.daysPass(DAYS_BETWEEN_THE_TWO_POTS);
        LocalDate sheEarnedHersOn = app.theDateTheClockReads();
        DepositView hers = app.deposit(app.savingsAccountOf(ANKE), ANKE, "60.00");
        assertThat(hers.pointsEarned()).isEqualTo(60);
        LocalDate herTwelveMonthsAreUpOn = sheEarnedHersOn.plusYears(1);
        assertThat(herTwelveMonthsAreUpOn)
                .as("the gift falls due after his own points do, which is the whole arrangement")
                .isAfter(hisTwelveMonthsAreUpOn);

        app.give(ANKE, BRAM, String.valueOf(hers.pointsEarned()));

        assertThat(app.pointsBalanceOf(BRAM))
                .as("a younger gift counts towards his balance the moment it goes through, like any other")
                .isEqualTo(his.pointsEarned() + hers.pointsEarned());
        assertThat(app.pointsBalanceOf(ANKE)).isZero();
        assertThat(app.pointsExpiringNextOf(BRAM))
                .as("what goes next is his own batch, because the gift is the younger of the two")
                .isEqualTo(his.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isEqualTo(hisTwelveMonthsAreUpOn);

        // The first claim. Oldest first means his own batch pays for it, so the figure due on his
        // own day falls by exactly what the voucher cost. A ledger that spent gifts first would have
        // left it at fifty and taken the forty out of hers.
        ClaimedRewardView snack = app.claim(BRAM, "SNACK_VOUCHER");
        assertThat(snack.pointsSpent()).isEqualTo(SNACK_VOUCHER_COSTS);
        assertThat(app.pointsBalanceOf(BRAM))
                .isEqualTo(his.pointsEarned() + hers.pointsEarned() - SNACK_VOUCHER_COSTS);
        assertThat(app.pointsExpiringNextOf(BRAM))
                .as("the claim came out of his own older batch, not out of the younger gift")
                .isEqualTo(his.pointsEarned() - SNACK_VOUCHER_COSTS);
        assertThat(app.pointsExpiringNextOnOf(BRAM))
                .as("so the day that goes next is still his own")
                .isEqualTo(hisTwelveMonthsAreUpOn);

        // The second claim costs exactly what is left of his own batch, so it empties that batch and
        // stops there: the gift is still whole, and now it is the only thing he has left to lose.
        ClaimedRewardView donation = app.claim(BRAM, "CHARITY_DONATION");
        assertThat(donation.pointsSpent()).isEqualTo(CHARITY_DONATION_COSTS);
        assertThat(app.pointsBalanceOf(BRAM))
                .as("two claims spent his own fifty and not a point of the gift")
                .isEqualTo(hers.pointsEarned());
        assertThat(app.pointsExpiringNextOf(BRAM))
                .as("the whole of the gift is untouched, and it is all he has left")
                .isEqualTo(hers.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM))
                .as("on her day, which is the day the points she gave him were always going to go")
                .isEqualTo(herTwelveMonthsAreUpOn);

        // His own anniversary, and a sweep run on the far side of it. It takes nothing: the batch
        // whose year is up is the one he spent, and the gift is a hundred days short of its own
        // anniversary. Had the claims come out of the gift instead, this sweep would have found
        // fifty unspent points of his a year old and emptied his pot.
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), hisTwelveMonthsAreUpOn) + 1);
        app.runJob(THE_EXPIRY_SWEEP);

        assertThat(app.pointsBalanceOf(BRAM))
                .as("his own day came and went and the pot still holds the whole of the gift")
                .isEqualTo(hers.pointsEarned());
        assertThat(app.pointsExpiringNextOf(BRAM)).isEqualTo(hers.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isEqualTo(herTwelveMonthsAreUpOn);
        assertThat(ChronoUnit.DAYS.between(app.theDateTheClockReads(), herTwelveMonthsAreUpOn))
                .as("still to run, in a pot whose owner's own points have already had their year")
                .isPositive();
    }
}
