package io.dataroots.savingstreak.giftingpoints;

import java.time.LocalDate;

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
 * Gifted points are worth exactly what every other point is worth: they are in the balance the
 * moment they arrive, they are quoted back in a refusal, they buy a reward on their own, and they
 * are spent in the one oldest-first order the ledger has — ahead of the recipient's own points when
 * they are older, with no regard to which reason earned them.
 *
 * <p>The arrangement makes the spending order visible, which the balance alone cannot do. Anke earns
 * her points and holds them for a hundred days before Bram earns any of his own, so the gift she
 * then sends him is older than everything in his pot — and the two ages expire on different days,
 * which is the only window this application opens onto which batch a claim came out of. Three claims
 * then walk the whole of the gift down to nothing: what he is told expires next drops 60, 20, 10 and
 * then jumps to his own batch on his own later day. Points spent from his own end first would have
 * left the figure at 60 on her day throughout and then jumped the other way.
 *
 * <p>The refusal is asked before any of that, and it is the plainest statement that a gift is in the
 * balance: he holds thirty points of his own and sixty of hers, and the sentence he gets back for a
 * hundred-point reward quotes ninety. A ledger that had credited the gift somewhere unspendable
 * would tell him he had thirty.
 *
 * <p>Its own application, on a database nothing has ever been written to. It moves the clock, which
 * the shared one cannot have done to it, and it needs points in Bram's pot, which two tests on the
 * shared database assert he has never had.
 */
class GiftedPointsAreSpentLikeAnyOtherPointsApiTest extends ApiIntegrationTest {

    /** What the cinema ticket costs: more than the two pots together, which is the refusal. */
    private static final long CINEMA_TICKET_COSTS = 100;

    /** What the snack voucher costs, paid entirely out of the gift and with some of it left over. */
    private static final long SNACK_VOUCHER_COSTS = 40;

    /** What the cheapest reward costs, which the rest of the gift is walked down by. */
    private static final long CHARITY_DONATION_COSTS = 10;

    /**
     * A hundred days between her saving and his. Long enough that the two pots have two different
     * anniversaries, which is what makes the spending order readable at all, and short enough that
     * both are alive throughout.
     */
    private static final int DAYS_BETWEEN_THE_TWO_POTS = 100;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-gifted-points-are-spent"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_gift_is_in_the_balance_a_refusal_quotes_it_and_a_claim_spends_it_in_its_turn() {
        LocalDate sheEarnedHersOn = app.theDateTheClockReads();
        DepositView hers = app.deposit(app.savingsAccountOf(ANKE), ANKE, "60.00");
        assertThat(hers.pointsEarned()).isEqualTo(60);

        // A hundred days later he earns his own, so hers are the older of the two ages in his pot
        // once the gift arrives.
        app.daysPass(DAYS_BETWEEN_THE_TWO_POTS);
        LocalDate heEarnedHisOn = app.theDateTheClockReads();
        DepositView his = app.deposit(app.savingsAccountOf(BRAM), BRAM, "30.00");
        assertThat(his.pointsEarned()).isEqualTo(30);
        assertThat(app.pointsBalanceOf(BRAM)).isEqualTo(his.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM))
                .as("all he holds is his own, on his own day")
                .isEqualTo(heEarnedHisOn.plusYears(1));

        app.give(ANKE, BRAM, String.valueOf(hers.pointsEarned()));

        assertThat(app.pointsBalanceOf(BRAM))
                .as("hers count towards his balance from the moment the gift goes through")
                .isEqualTo(his.pointsEarned() + hers.pointsEarned());
        assertThat(app.pointsBalanceOf(ANKE)).isZero();

        // A refusal quotes the whole of what he holds, gift included. Told he had only his own
        // thirty, a customer would go and save for points already sitting in their pot.
        assertThat(app.whyTheClaimWasRefused(BRAM, "CINEMA_TICKET"))
                .as("the balance a refusal quotes includes whatever gifted points he holds")
                .isEqualTo("Cinema ticket costs " + CINEMA_TICKET_COSTS + " points, and you have "
                        + (his.pointsEarned() + hers.pointsEarned()) + ".");

        // The gift is at the front of the queue, because it is the older of the two ages — and on a
        // day months before any deposit he ever made.
        assertThat(app.pointsExpiringNextOf(BRAM)).isEqualTo(hers.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isEqualTo(sheEarnedHersOn.plusYears(1));

        // A reward he can afford, paid for out of the gift and nothing else.
        ClaimedRewardView snack = app.claim(BRAM, "SNACK_VOUCHER");
        assertThat(snack.pointsSpent()).isEqualTo(SNACK_VOUCHER_COSTS);
        assertThat(snack.voucherCode())
                .as("a gift buys a real voucher, like any other points")
                .isNotBlank();
        assertThat(app.pointsBalanceOf(BRAM))
                .isEqualTo(his.pointsEarned() + hers.pointsEarned() - SNACK_VOUCHER_COSTS);
        assertThat(app.pointsExpiringNextOf(BRAM))
                .as("the forty came off the gift, so what goes next is what is left of it")
                .isEqualTo(hers.pointsEarned() - SNACK_VOUCHER_COSTS);
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isEqualTo(sheEarnedHersOn.plusYears(1));

        // The rest of the gift, walked down ten at a time. The figure that goes next drops with each
        // claim while the day stays hers: oldest first, with no regard to which reason earned it.
        ClaimedRewardView firstDonation = app.claim(BRAM, "CHARITY_DONATION");
        assertThat(firstDonation.pointsSpent()).isEqualTo(CHARITY_DONATION_COSTS);
        assertThat(app.pointsExpiringNextOf(BRAM))
                .isEqualTo(hers.pointsEarned() - SNACK_VOUCHER_COSTS - CHARITY_DONATION_COSTS);
        assertThat(app.pointsExpiringNextOnOf(BRAM)).isEqualTo(sheEarnedHersOn.plusYears(1));

        // And the claim that empties it. Only now does his own batch surface, on his own later day,
        // which is the boundary between the two pots crossed in the right direction.
        app.claim(BRAM, "CHARITY_DONATION");
        assertThat(app.pointsBalanceOf(BRAM))
                .as("the whole of the gift has been spent and his own points are untouched")
                .isEqualTo(his.pointsEarned());
        assertThat(app.pointsExpiringNextOf(BRAM)).isEqualTo(his.pointsEarned());
        assertThat(app.pointsExpiringNextOnOf(BRAM))
                .as("the gift is gone, so what goes next is his own batch on his own day")
                .isEqualTo(heEarnedHisOn.plusYears(1));
        assertThat(app.pointsBalanceOf(ANKE))
                .as("nothing came back to her: the gift was spent by the person she gave it to")
                .isZero();
    }
}
