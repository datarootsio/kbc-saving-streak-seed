package io.dataroots.savingstreak.streakbonus;

import java.util.Arrays;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one rule the whole scheme is priced by, watched happening: a deposit is paid at the rate of the
 * run of weeks as it stands <em>once that deposit has been counted</em>.
 *
 * <p>Everything here falls out of that one sentence. A deposit with no run behind it earns one point
 * per whole euro, as it always did. A deposit that does not carry its week over the line is paid
 * whatever the live run was already paying. And the deposit that does carry the week over is paid at
 * the rate of the run that now includes that week — while the deposits made earlier in the same week
 * keep what they were paid, because a deposit's reward is settled when it is made and nothing is
 * topped up behind it.
 *
 * <p>Its own application on a database nothing has ever been written to, because a run counted from
 * zero needs an account nothing has landed in and a clock only this class moves — see
 * {@link AnApplicationWithAClockToMove}.
 *
 * <p>One test, because the clock only goes forward: a second method in this class would find the
 * weeks already moved on and would be asserting against whatever order the two happened to run in.
 * The narrative is asserted on after every step instead, which is what a run of weeks is.
 */
class ADepositIsPaidAtTheStreakItSecuresApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-paid-at-the-streak-it-secures"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_deposit_that_carries_a_week_over_the_line_is_itself_paid_at_the_longer_run() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // Nothing has ever landed in it. No run, and so the ordinary rate: the figure a customer has
        // been shown since before there were streaks at all.
        assertThat(app.balancesOf(savingsAccount).currentMultiplier()).isEqualByComparingTo("1.00");

        // The first week of a run pays the ordinary rate, which is the scheme working rather than an
        // oversight: a streak is something a customer builds, so securing a week for the first time
        // earns exactly what a euro has always earned.
        DepositView theFirstWeek = app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(theFirstWeek.multiplierApplied()).isEqualByComparingTo("1.00");
        assertThat(theFirstWeek.basePoints()).isEqualTo(50);
        assertThat(theFirstWeek.streakBonusPoints()).isZero();
        assertThat(theFirstWeek.pointsEarned()).isEqualTo(50);
        BalancesView weekOne = app.balancesOf(savingsAccount);
        assertThat(weekOne.currentStreakWeeks()).isEqualTo(1);
        assertThat(weekOne.currentMultiplier()).isEqualByComparingTo("1.00");
        assertThat(weekOne.pointsBalance()).isEqualTo(50);

        app.aWeekPasses();

        // Well short of what the week asks for, so it secures nothing on its own — and it is not
        // punished for that either: it earns at whatever the live run is paying, which is still the
        // one week behind it.
        DepositView shortOfTheMinimum = app.deposit(savingsAccount, ANKE, "20.00");
        assertThat(shortOfTheMinimum.multiplierApplied()).isEqualByComparingTo("1.00");
        assertThat(shortOfTheMinimum.basePoints()).isEqualTo(20);
        assertThat(shortOfTheMinimum.streakBonusPoints()).isZero();
        assertThat(app.balancesOf(savingsAccount).currentStreakWeeks()).isEqualTo(1);

        // And this is the payoff. EUR 30 carries the week from EUR 20 to EUR 50, so by the time this
        // deposit is priced the run is two weeks long — and the deposit that did it is already paid
        // at the second week's rate rather than having to wait for the next one.
        DepositView carriedTheWeekOver = app.deposit(savingsAccount, ANKE, "30.00");
        assertThat(carriedTheWeekOver.multiplierApplied()).isEqualByComparingTo("1.10");
        assertThat(carriedTheWeekOver.basePoints()).isEqualTo(30);
        // 30 euros at 1.10 is 33 points, so three of them were the run's doing.
        assertThat(carriedTheWeekOver.streakBonusPoints()).isEqualTo(3);
        assertThat(carriedTheWeekOver.pointsEarned()).isEqualTo(33);

        BalancesView weekTwo = app.balancesOf(savingsAccount);
        assertThat(weekTwo.currentStreakWeeks()).isEqualTo(2);
        assertThat(weekTwo.currentMultiplier()).isEqualByComparingTo("1.10");
        // The balance moved by the base and the bonus together, which is the total the deposit
        // reported: the two figures are what the one figure is made of, never something beside it.
        assertThat(weekTwo.pointsBalance()).isEqualTo(50 + 20 + 33);

        // Nothing was topped up behind it. The EUR 20 that landed earlier in this same week is still
        // worth the twenty points it was paid, at the rate it was paid at, however good the week
        // turned out to be.
        assertThat(theSameDepositInTheHistory(savingsAccount, shortOfTheMinimum))
                .satisfies(listed -> {
                    assertThat(listed.pointsEarned()).isEqualTo(20);
                    assertThat(listed.basePoints()).isEqualTo(20);
                    assertThat(listed.streakBonusPoints()).isZero();
                    assertThat(listed.multiplierApplied()).isEqualByComparingTo("1.00");
                });

        app.aWeekPasses();

        // A third consecutive secured week, and the rate has climbed another step.
        DepositView theThirdWeek = app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(theThirdWeek.multiplierApplied()).isEqualByComparingTo("1.20");
        assertThat(theThirdWeek.basePoints()).isEqualTo(50);
        assertThat(theThirdWeek.streakBonusPoints()).isEqualTo(10);
        assertThat(theThirdWeek.pointsEarned()).isEqualTo(60);

        BalancesView weekThree = app.balancesOf(savingsAccount);
        assertThat(weekThree.currentStreakWeeks()).isEqualTo(3);
        assertThat(weekThree.currentMultiplier()).isEqualByComparingTo("1.20");
        assertThat(weekThree.pointsBalance()).isEqualTo(50 + 20 + 33 + 60);
        // And the history adds up to the same balance, which is what says the bonus is reported
        // against the deposit that earned it rather than credited somewhere nothing lists.
        assertThat(Arrays.stream(app.depositsInto(savingsAccount))
                .mapToLong(DepositView::pointsEarned).sum())
                .isEqualTo(weekThree.pointsBalance());
    }

    /** The same deposit read back out of the history, found by the identifier it was given. */
    private static DepositView theSameDepositInTheHistory(long savingsAccountId, DepositView made) {
        return Arrays.stream(app.depositsInto(savingsAccountId))
                .filter(listed -> listed.id().equals(made.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("deposit " + made.id() + " is not in the history"));
    }
}
