package io.dataroots.savingstreak.loyaltybonus;

import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A loyalty bonus is worth exactly what every other point is worth: it is in the balance from the
 * moment it is paid, it is quoted back in a refusal, it buys a reward on its own, and it is spent in
 * its turn in the one oldest-first order the ledger has.
 *
 * <p>The arrangement is built so that no other reading of the ledger could pass it. Two weeks are
 * secured — EUR 100 and then EUR 50 — so three batches are earned in the ordinary way: a hundred
 * euros' base points, fifty euros' base points, and the five the second week's rate paid on top of
 * them. A year later both deposits pay an anniversary, and the expiry sweep then takes all three of
 * the original batches, because twelve months after the money moved is the same day as that
 * deposit's first anniversary. What is left in the pot at that point is nothing but loyalty bonus:
 * 10 from the older deposit and 5 from the newer.
 *
 * <p>So the balance, the refusal and the first claim that follow are all statements about loyalty
 * bonus and about nothing else. A ledger that had credited a bonus somewhere unspendable would show
 * a balance of nothing here, and every assertion below would fail rather than merely read oddly.
 *
 * <p>The spending order is then pinned by what the customer is told expires next rather than by the
 * balance, because the balance cannot tell the orders apart: taking 40 points out of 5 points of
 * bonus and 90 of fresh base points leaves 55 whichever end they come off. Which batch those 55 are
 * in is the question, and the day they go is the answer — the bonus was earned a fortnight before
 * the deposit that followed it, so the two batches expire on different days.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: a year passes
 * in it, and a run of weeks needs a clock nobody else is moving.
 */
class ALoyaltyBonusIsSpentLikeAnyOtherPointsApiTest extends ApiIntegrationTest {

    private static final String THE_LOYALTY_SWEEP = "payLoyaltyBonuses";

    private static final String THE_EXPIRY_SWEEP = "expireOldPoints";

    /** What the cheapest reward in the catalogue costs, which a loyalty bonus alone has to cover. */
    private static final long CHARITY_DONATION_COSTS = 10;

    /**
     * What the snack voucher costs: more than the bonuses alone, which is the refusal, and less than
     * the bonus plus a fresh deposit, which is the claim that has to reach across both.
     */
    private static final long SNACK_VOUCHER_COSTS = 40;

    /**
     * A fortnight past a year, counted from the second of the two deposits. Both deposits are then
     * comfortably the far side of their first anniversary and neither is anywhere near its second,
     * so the test is about the rule rather than about which day of which month the run happens on.
     */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-bonus-is-spent-like-the-rest"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_bonus_is_in_the_balance_buys_a_reward_and_is_spent_in_its_turn() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // Two secured weeks, which is three batches of points earned the ordinary way.
        DepositView theFirstWeek = app.deposit(savingsAccount, ANKE, "100.00");
        assertThat(theFirstWeek.basePoints()).isEqualTo(100);
        assertThat(theFirstWeek.streakBonusPoints()).isZero();
        app.aWeekPasses();
        LocalDate theSecondWeekPaidInOn = app.theDateTheClockReads();
        DepositView theSecondWeek = app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(theSecondWeek.multiplierApplied()).isEqualByComparingTo("1.10");
        assertThat(theSecondWeek.basePoints()).isEqualTo(50);
        assertThat(theSecondWeek.streakBonusPoints()).isEqualTo(5);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(100 + 50 + 5);

        // A year on. Neither deposit was touched, so each pays a tenth of the euros still sitting in
        // it — and the tenth is in the balance the moment it is paid, which is what the balance read
        // straight after the sweep says.
        app.daysPass(DAYS_WELL_PAST_A_YEAR);
        app.runJob(THE_LOYALTY_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("both deposits paid a tenth of their euros, into the same pot as the rest")
                .isEqualTo(100 + 50 + 5 + 10 + 5);

        // The expiry sweep now takes all three of the original batches, because twelve months after
        // the money moved is the same day as that deposit's first anniversary. Nothing is left in
        // the pot but loyalty bonus, which is what makes everything below a statement about loyalty
        // bonus alone.
        app.runJob(THE_EXPIRY_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("what the two deposits earned when they landed has gone, and the anniversary "
                        + "bonuses they have just been paid have not")
                .isEqualTo(10 + 5);

        // A refusal quotes what the customer has, and what they have is bonus. A ledger that did not
        // count a bonus towards the balance would refuse this claim by telling them they had nothing.
        assertThat(app.whyTheClaimWasRefused(ANKE, "SNACK_VOUCHER"))
                .as("the balance a refusal quotes is the whole of what they hold, bonus included")
                .isEqualTo("Coffee or snack voucher costs " + SNACK_VOUCHER_COSTS
                        + " points, and you have " + (10 + 5) + ".");

        // And a reward they can afford is handed over, paid for out of bonus and nothing else.
        ClaimedRewardView boughtWithBonus = app.claim(ANKE, "CHARITY_DONATION");
        assertThat(boughtWithBonus.pointsSpent()).isEqualTo(CHARITY_DONATION_COSTS);
        assertThat(boughtWithBonus.voucherCode()).isNotBlank();
        assertThat(app.pointsBalanceOf(ANKE))
                .as("a bonus buys a reward like any other points, so the 10 have gone")
                .isEqualTo(5);

        // The 10 that went were the older deposit's bonus, dated a week before the newer one's:
        // oldest first, among the bonuses themselves. What is left goes twelve months after the
        // second deposit's anniversary, which is two years to the day after that deposit landed.
        assertThat(app.pointsExpiringNextOf(ANKE)).isEqualTo(5);
        assertThat(app.pointsExpiringNextOnOf(ANKE))
                .as("the claim took the older of the two bonuses, so what is left is the newer "
                        + "deposit's, dated at its own later anniversary")
                .isEqualTo(theSecondWeekPaidInOn.plusYears(2));

        // A fresh deposit behind that surviving bonus in the queue. A year of not saving means the
        // ladder starts again, so this is ninety euros' worth of ordinary base points and no bonus.
        LocalDate paidInAfterTheBonus = app.theDateTheClockReads();
        DepositView afterTheBonus = app.deposit(savingsAccount, ANKE, "90.00");
        assertThat(afterTheBonus.multiplierApplied()).isEqualByComparingTo("1.00");
        assertThat(afterTheBonus.pointsEarned()).isEqualTo(90);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(5 + 90);

        // Forty points: five out of the bonus that was already there, and thirty-five out of the
        // deposit that landed after it.
        ClaimedRewardView spanningTheBonus = app.claim(ANKE, "SNACK_VOUCHER");
        assertThat(spanningTheBonus.pointsSpent()).isEqualTo(SNACK_VOUCHER_COSTS);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(5 + 90 - SNACK_VOUCHER_COSTS);

        // Which end the 40 came off is the whole question, and the balance cannot answer it: 55 are
        // left either way. What the customer is told goes next can, because the two batches expire
        // on different days. All 55 in the fresh deposit's batch says the bonus was drawn on first;
        // a bonus held back to the end would have left its 5 standing, and what went next would be
        // those 5, on the earlier day of the two.
        assertThat(app.pointsExpiringNextOf(ANKE))
                .as("the bonus was spent in its turn, so the whole of what is left is the fresh "
                        + "deposit's batch")
                .isEqualTo(5 + 90 - SNACK_VOUCHER_COSTS);
        assertThat(app.pointsExpiringNextOnOf(ANKE))
                .as("on the fresh deposit's own day, not on the earlier day the bonus would have gone")
                .isEqualTo(paidInAfterTheBonus.plusYears(1));
    }
}
