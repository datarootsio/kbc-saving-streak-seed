package io.dataroots.savingstreak.streakbonus;

import java.util.Arrays;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bonus is points like any other: it can be spent on a reward, it is spent in its turn behind the
 * points earned before it, and taking the money back out of the account does not take it back.
 *
 * <p>The spending is arranged so that it has to reach into the bonus. Two weeks are secured, EUR 60
 * and then EUR 110, so the account has three batches of points in this order: sixty euros, a hundred
 * and ten euros, and the eleven the second week's rate paid on top of them. A claim costing 180 has
 * to take all of the first, all of the second and ten of the eleven — so what is left says both that
 * the batches went in the order they were earned and that a bonus is drawn on exactly as the euros
 * are.
 *
 * <p>Its own application and one test, for the reasons {@link AnApplicationWithAClockToMove} gives: a
 * bonus needs a run of weeks, and a run of weeks needs a clock nobody else is moving.
 */
class AStreakBonusIsSpentAndKeptApiTest extends ApiIntegrationTest {

    /** What the family cinema pack costs, which is the claim this test is built around. */
    private static final long FAMILY_CINEMA_PACK_COSTS = 180;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-bonus-spent-and-kept"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_bonus_is_spent_in_its_turn_and_a_withdrawal_takes_none_of_it_back() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        DepositView theFirstWeek = app.deposit(savingsAccount, ANKE, "60.00");
        assertThat(theFirstWeek.pointsEarned()).isEqualTo(60);
        app.aWeekPasses();
        DepositView theSecondWeek = app.deposit(savingsAccount, ANKE, "110.00");
        assertThat(theSecondWeek.multiplierApplied()).isEqualByComparingTo("1.10");
        assertThat(theSecondWeek.basePoints()).isEqualTo(110);
        assertThat(theSecondWeek.streakBonusPoints()).isEqualTo(11);
        assertThat(theSecondWeek.pointsEarned()).isEqualTo(121);

        BalancesView earned = app.balancesOf(savingsAccount);
        assertThat(earned.pointsBalance()).isEqualTo(60 + 121);
        // The history accounts for every point in the balance, which is what says the bonus is
        // reported against the deposit that earned it rather than credited somewhere nothing lists.
        assertThat(pointsInTheHistoryOf(savingsAccount)).isEqualTo(earned.pointsBalance());

        // The whole EUR 170 goes straight back to the current account. Nothing in this scheme is
        // contingent on the money staying put: the base points and the bonus are both already
        // earned, and using savings is not something the application punishes.
        app.withdraw(savingsAccount, ANKE, "170.00");

        BalancesView emptied = app.balancesOf(savingsAccount);
        assertThat(emptied.moneyBalance()).isEqualByComparingTo("0.00");
        assertThat(emptied.pointsBalance()).isEqualTo(60 + 121);
        assertThat(pointsInTheHistoryOf(savingsAccount)).isEqualTo(emptied.pointsBalance());
        assertThat(theSameDepositInTheHistory(savingsAccount, theSecondWeek))
                .satisfies(listed -> {
                    assertThat(listed.basePoints()).isEqualTo(110);
                    assertThat(listed.streakBonusPoints()).isEqualTo(11);
                    assertThat(listed.multiplierApplied()).isEqualByComparingTo("1.10");
                });

        // 60 + 110 of the 180 come out of the two batches of euros, oldest first, and the last ten
        // come out of the bonus — which the ledger treats as a batch like any other.
        ClaimedRewardView claimed = app.claim(savingsAccount, "FAMILY_CINEMA_PACK");
        assertThat(claimed.pointsSpent()).isEqualTo(FAMILY_CINEMA_PACK_COSTS);
        assertThat(app.balancesOf(savingsAccount).pointsBalance()).isEqualTo(60 + 121 - 180);

        // And the one point left is genuinely there and genuinely only one: the refusal reads the
        // same balance back out.
        assertThat(app.whyTheClaimWasRefused(savingsAccount, "CHARITY_DONATION"))
                .isEqualTo("Charity donation costs 10 points, and this account has 1.");
    }

    /** Everything the history says this account's deposits earned, however they earned it. */
    private static long pointsInTheHistoryOf(long savingsAccountId) {
        return Arrays.stream(app.depositsInto(savingsAccountId))
                .mapToLong(DepositView::pointsEarned)
                .sum();
    }

    /** The same deposit read back out of the history, found by the identifier it was given. */
    private static DepositView theSameDepositInTheHistory(long savingsAccountId, DepositView made) {
        return Arrays.stream(app.depositsInto(savingsAccountId))
                .filter(listed -> listed.id().equals(made.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("deposit " + made.id() + " is not in the history"));
    }
}
