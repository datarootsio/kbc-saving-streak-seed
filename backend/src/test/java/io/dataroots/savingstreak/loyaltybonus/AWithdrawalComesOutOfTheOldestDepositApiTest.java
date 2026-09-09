package io.dataroots.savingstreak.loyaltybonus;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A withdrawal that only partly covers the savings comes out of the oldest deposit, so the forfeit
 * falls on the money nearest its anniversary and the newer deposits' clocks run on whole.
 *
 * <p>Withdrawals have always drawn the oldest deposit down first; this is where that ordering is
 * pinned down as loyalty behaviour rather than left as an accident of how withdrawals happen to be
 * recorded. It is what makes the forfeit fall where a customer would expect it — on the money they
 * have held longest and are about to be paid for, rather than scattered across everything they hold.
 *
 * <p>Which deposit the money came out of cannot be read off the final total: the rule is a tenth of
 * what is left, so any allocation of one withdrawal across a set of deposits leaves the same euros
 * in the account and pays the same tenth of them in the end. Under newest-first these six hundred
 * points would reach the same 650 by another road — 10 for the older deposit and 40 for the newer,
 * instead of nothing and 50. What tells the allocations apart is therefore <em>which sweep</em> pays
 * and <em>how much that sweep pays</em>. So the two deposits here land months apart, the sweep is run
 * twice — once after the older deposit's anniversary and before the newer one's, and once after both
 * — and each run is asserted on what it paid rather than only on where the balance ended up.
 *
 * <ul>
 *   <li>EUR 100 lands, and EUR 500 lands a little over half a year later. EUR 100 is then withdrawn
 *       — a sixth of the savings, which the oldest deposit covers exactly.
 *   <li>The far side of the older deposit's first anniversary, the sweep pays nothing: the money
 *       that would have been paid for is the money that left. Under any other allocation the
 *       hundred euros would still be sitting in that deposit and would have paid 10 here.
 *   <li>The far side of the newer deposit's own anniversary, months later, that sweep pays 50 — a
 *       tenth of the whole 500, because the withdrawal never reached it. Under a newest-first
 *       allocation the deposit would be holding 400 and this sweep would pay 40.
 * </ul>
 *
 * <p>Its own application and its own database, for the reason {@link AnApplicationWithAClockToMove}
 * gives and then some: this test winds the clock past a year and a half.
 */
class AWithdrawalComesOutOfTheOldestDepositApiTest extends ApiIntegrationTest {

    private static final String THE_SWEEP = "payLoyaltyBonuses";

    /**
     * A little over half a year between the two deposits. Far enough apart that their anniversaries
     * are more than six months apart, which is what lets one be judged without the other, and far
     * enough that no run of consecutive weeks connects them — so both are paid at the ordinary rate
     * and the euros are the whole of the arithmetic.
     */
    private static final int DAYS_BETWEEN_THE_TWO_DEPOSITS = 200;

    /** A few days later, so the money is out well before either deposit's anniversary. */
    private static final int DAYS_UNTIL_THE_WITHDRAWAL = 210;

    /** A fortnight past a year from the older deposit, and nowhere near the newer one's year. */
    private static final int DAYS_PAST_THE_OLDER_DEPOSITS_ANNIVERSARY = 379;

    /** A fortnight past a year from the newer deposit, which landed on day 200. */
    private static final int DAYS_PAST_THE_NEWER_DEPOSITS_ANNIVERSARY = 580;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-oldest-deposit-first"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_oldest_deposit_takes_the_withdrawal_and_the_newer_ones_anniversary_pays_in_full() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        DepositView older = app.deposit(savingsAccount, ANKE, "100.00");
        assertThat(older.pointsEarned()).isEqualTo(100);
        assertThat(older.multiplierApplied())
                .as("no run of weeks behind it, so its euros are its points")
                .isEqualByComparingTo("1.00");

        app.daysPass(DAYS_BETWEEN_THE_TWO_DEPOSITS);

        DepositView newer = app.deposit(savingsAccount, ANKE, "500.00");
        assertThat(newer.pointsEarned()).isEqualTo(500);
        assertThat(newer.multiplierApplied())
                .as("over six months after the last deposit is nobody's streak")
                .isEqualByComparingTo("1.00");
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(600);

        // A sixth of the savings, which is exactly what the oldest deposit holds.
        app.daysPass(DAYS_UNTIL_THE_WITHDRAWAL - DAYS_BETWEEN_THE_TWO_DEPOSITS);
        app.withdraw(savingsAccount, ANKE, "100.00");
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("the withdrawal covered only part of the savings, so most of it is still there")
                .isEqualByComparingTo("500.00");

        // The older deposit's first anniversary, which its money did not reach.
        app.daysPass(DAYS_PAST_THE_OLDER_DEPOSITS_ANNIVERSARY - DAYS_UNTIL_THE_WITHDRAWAL);

        app.runJob(THE_SWEEP);

        long afterTheOlderDepositsAnniversary = app.pointsBalanceOf(ANKE);
        assertThat(afterTheOlderDepositsAnniversary)
                .as("the withdrawal came out of the oldest deposit and emptied it, so the "
                        + "anniversary it has just passed pays nothing — had the hundred euros been "
                        + "taken from the newer deposit instead, this deposit would still hold them "
                        + "and would have paid 10")
                .isEqualTo(600);

        // Months later, the newer deposit's own anniversary. Its clock ran on untouched.
        app.daysPass(DAYS_PAST_THE_NEWER_DEPOSITS_ANNIVERSARY
                - DAYS_PAST_THE_OLDER_DEPOSITS_ANNIVERSARY);

        app.runJob(THE_SWEEP);

        // What this second sweep paid, rather than where the balance ended up. The end total cannot
        // tell the allocations apart — newest-first pays 10 here and 40 there, and 600 + 10 + 40 is
        // the same 650 — so a total asserted alone would pass under the very allocation this class
        // exists to rule out. The difference between the two sweeps is what discriminates.
        assertThat(app.pointsBalanceOf(ANKE) - afterTheOlderDepositsAnniversary)
                .as("this sweep pays a tenth of the whole 500 the newer deposit still holds, "
                        + "because the withdrawal never reached it — under a newest-first "
                        + "allocation it would be holding 400 and this sweep would pay 40")
                .isEqualTo(50);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("the 600 the two deposits earned as they landed, plus the one anniversary the "
                        + "money was still there for")
                .isEqualTo(600 + 50);
    }
}
