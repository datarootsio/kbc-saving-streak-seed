package io.dataroots.savingstreak.newsavings;

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
 * A point is paid for a euro saved, and a euro saved twice is one euro. A deposit earns on the part
 * of it that takes its holder above the most they have ever had in savings; euros that only fill a
 * gap an earlier withdrawal left have been saved once already and were paid for then.
 *
 * <p>The flaw this closes is the round trip: pay in, earn, take it straight back out, pay the same
 * money in again. Under "every euro that moves in earns", a hundred euros and an afternoon bought
 * any reward in the catalogue.
 *
 * <p>What it deliberately does <em>not</em> do is take points back. A withdrawal leaves the ledger
 * alone, nothing a customer has earned is ever removed, and a balance can never go below nothing.
 * Using your savings is not something this application punishes — it simply does not pay twice for
 * the same money.
 *
 * <p>Its own application, with a clock nobody else is moving, because every figure here is exact and
 * an exact figure needs a known rate. Everything happens inside one week, so every deposit is paid
 * at the ordinary rate and the points are the euros themselves.
 *
 * <p>Each test starts with the customer's savings at their peak and leaves them there, and measures
 * the change it caused rather than an absolute balance — the three share one application and JUnit
 * does not promise to run them in the order they are written.
 */
class TheSameEurosOnlyEarnOnceApiTest extends ApiIntegrationTest {

    /** What the cinema ticket costs, which is the claim the last test is built around. */
    private static final long CINEMA_TICKET_COSTS = 100;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-new-savings"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** The exploit, written out as the round trip it was, and run three times over. */
    @Test
    void the_same_hundred_euros_only_earn_once_however_often_they_go_round() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        long before = app.pointsBalanceOf(ANKE);

        DepositView first = app.deposit(savingsAccount, ANKE, "100.00");
        assertThat(first.newSavings())
                .as("nobody has saved these euros before, so all of them are new saving")
                .isEqualByComparingTo("100.00");
        assertThat(first.pointsEarned()).isEqualTo(100);

        for (int lap = 1; lap <= 3; lap++) {
            app.withdraw(savingsAccount, ANKE, "100.00");
            assertThat(app.pointsBalanceOf(ANKE) - before)
                    .as("lap %d: taking money out takes nothing back — what was earned is earned", lap)
                    .isEqualTo(100);
            assertThat(app.balancesOf(savingsAccount).mostEverSaved())
                    .as("lap %d: the mark is the most ever saved, so it does not fall when money "
                            + "leaves", lap)
                    .isEqualByComparingTo("100.00");

            DepositView again = app.deposit(savingsAccount, ANKE, "100.00");
            assertThat(again.newSavings())
                    .as("lap %d: the same hundred euros going back where they were is not new "
                            + "saving", lap)
                    .isEqualByComparingTo("0.00");
            assertThat(again.pointsEarned()).as("lap %d", lap).isZero();
            assertThat(app.pointsBalanceOf(ANKE) - before)
                    .as("lap %d: three round trips, three hundred euros moved, and still the "
                            + "hundred points the money was worth the first time", lap)
                    .isEqualTo(100);
        }

        // And saving genuinely more still earns, which is the half of the rule that has to keep
        // working: the mark is a floor under what has already been paid for, not a cap on earning.
        DepositView more = app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(more.newSavings()).isEqualByComparingTo("50.00");
        assertThat(more.pointsEarned()).isEqualTo(50);
        assertThat(app.pointsBalanceOf(ANKE) - before).isEqualTo(150);
        assertThat(app.balancesOf(savingsAccount).mostEverSaved()).isEqualByComparingTo("150.00");
    }

    /**
     * A deposit that does not close the gap on its own earns nothing, and the next one earns only
     * the part above the mark. Two deposits of the same size, on either side of the line, so the
     * only thing that can be being measured is where the mark was.
     */
    @Test
    void a_deposit_that_only_partly_fills_a_gap_earns_on_what_is_above_the_mark() {
        long savingsAccount = app.otherSavingsAccountOf(ANKE);
        long before = app.pointsBalanceOf(ANKE);

        app.deposit(savingsAccount, ANKE, "40.00");
        app.withdraw(savingsAccount, ANKE, "40.00");

        DepositView fillingTheGap = app.deposit(savingsAccount, ANKE, "30.00");
        assertThat(fillingTheGap.newSavings())
                .as("thirty euros into a forty-euro gap is still below the mark")
                .isEqualByComparingTo("0.00");
        assertThat(fillingTheGap.pointsEarned()).isZero();

        DepositView crossingIt = app.deposit(savingsAccount, ANKE, "30.00");
        assertThat(crossingIt.newSavings())
                .as("ten of these thirty fill what was left of the gap and twenty are new saving")
                .isEqualByComparingTo("20.00");
        assertThat(crossingIt.pointsEarned()).isEqualTo(20);

        assertThat(app.pointsBalanceOf(ANKE) - before)
                .as("the forty the money earned the first time, and twenty for the twenty euros "
                        + "that are genuinely more than she has ever saved")
                .isEqualTo(60);
    }

    /**
     * The case the rule exists to handle without a debt: the points were spent on a reward before
     * the money left. There is nothing to take back and nothing is taken — the balance never goes
     * below nothing — and the money going back in simply does not earn a second time.
     */
    @Test
    void spending_the_points_first_leaves_nothing_owed_and_nothing_to_farm() {
        long savingsAccount = app.savingsAccountOf(BRAM);
        long before = app.pointsBalanceOf(BRAM);

        app.deposit(savingsAccount, BRAM, "100.00");
        ClaimedRewardView claimed = app.claim(BRAM, "CINEMA_TICKET");
        assertThat(claimed.pointsSpent()).isEqualTo(CINEMA_TICKET_COSTS);
        assertThat(app.pointsBalanceOf(BRAM) - before)
                .as("earned and spent, so back where he started")
                .isZero();

        app.withdraw(savingsAccount, BRAM, "100.00");
        assertThat(app.pointsBalanceOf(BRAM))
                .as("the ticket is his, the money is back in his current account, and he is not in "
                        + "debt for either — a withdrawal takes nothing back")
                .isEqualTo(before)
                .isNotNegative();

        DepositView savedAgain = app.deposit(savingsAccount, BRAM, "100.00");
        assertThat(savedAgain.newSavings())
                .as("the same hundred euros, and he has saved them before")
                .isEqualByComparingTo("0.00");
        assertThat(savedAgain.pointsEarned())
                .as("otherwise the ticket could be claimed again and again on one hundred euros")
                .isZero();
        assertThat(app.pointsBalanceOf(BRAM) - before).isZero();

        assertThat(app.whyTheClaimWasRefused(BRAM, "CHARITY_DONATION"))
                .as("and what he is told is what he has, never a figure below nothing")
                .isEqualTo("Charity donation costs 10 points, and you have 0.");
    }
}
