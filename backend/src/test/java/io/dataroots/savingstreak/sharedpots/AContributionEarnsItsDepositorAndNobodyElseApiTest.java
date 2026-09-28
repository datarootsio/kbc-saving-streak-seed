package io.dataroots.savingstreak.sharedpots;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A euro paid into a shared pot earns points for the person who paid it in, at their own streak
 * multiplier, against their own high-water mark, and it counts towards their own week — and the
 * other member of the pot earns nothing from it, secures nothing by it and has their mark left
 * exactly where it was.
 *
 * <p><strong>This is the test the whole feature turns on.</strong> The money is shared and the habit
 * is not: two people saving together keep two records, and the only thing about a contribution that
 * is joint is where the euros went. Every rule that prices a deposit is keyed by a customer, and the
 * customer a contribution is priced for is the one whose current account it came out of — so the
 * proof is not that the arithmetic is right but that it happened to the right person, which is why
 * the second member is read back after every step.
 *
 * <p>Its own application and its own clock, for the reason {@link AnApplicationWithAClockToMove}
 * documents: a run of secured weeks can only be counted from zero, so it needs accounts nothing has
 * ever landed in and a clock nobody else moves. That is doubly true here — a pot on the run's shared
 * database would hand five other test classes a savings account identifier that exists and that
 * nobody holds.
 *
 * <p>One test method, because the clock only goes forward: a second method would find the weeks
 * already moved on and would be asserting against whatever order the two happened to run in. The
 * narrative is asserted on after every step instead, which is what a run of weeks is.
 *
 * <p>The second member is put in the pot by {@link SomebodyElseInThePot},
 * which says why: somebody joins a pot by being invited and accepting, and invitations are the next
 * slice. Nothing is asserted through it; every claim below is read back through the API.
 */
class AContributionEarnsItsDepositorAndNobodyElseApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-contribution-earns-its-depositor"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_points_the_week_and_the_mark_are_the_depositors_and_the_other_members_are_untouched() {
        // Each member's own savings account, which is where their own figures are read: the points,
        // the mark, the week and the run are the customer's, and every account they hold reports
        // them. The pot's account reports nobody's, because nobody holds it.
        long herOwnAccount = app.savingsAccountOf(ANKE);
        long hisOwnAccount = app.savingsAccountOf(BRAM);
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        SomebodyElseInThePot.joins(app, pot.id(), ANKE, BRAM, PotRole.CONTRIBUTOR);

        BalancesView hersBefore = app.balancesOf(herOwnAccount);
        BalancesView hisBefore = app.balancesOf(hisOwnAccount);
        assertThat(hersBefore.currentStreakWeeks())
                .as("a run can only be counted from zero, and this is the zero")
                .isZero();
        assertThat(hisBefore.currentStreakWeeks()).isZero();
        assertThat(hersBefore.pointsBalance()).isZero();
        assertThat(hisBefore.pointsBalance()).isZero();

        // Her first contribution: exactly what a week asks for, paid into the pot rather than into
        // anything of hers.
        DepositView hers = app.deposit(pot.savingsAccountId(), ANKE, "50.00");

        // Priced as a personal deposit of the same size would have been. She has never saved
        // before, so every euro of it is above the mark and earns, and the run it has just made one
        // week long pays the ordinary rate.
        assertThat(hers.newSavings()).isEqualByComparingTo("50.00");
        assertThat(hers.multiplierApplied()).isEqualByComparingTo("1.00");
        assertThat(hers.pointsEarned()).isEqualTo(50);

        BalancesView hersAfter = app.balancesOf(herOwnAccount);
        assertThat(hersAfter.pointsBalance())
                .as("the points are hers, reported beside every account she holds")
                .isEqualTo(50);
        assertThat(hersAfter.mostEverSaved())
                .as("and the mark her next deposit is judged against has moved with them")
                .isEqualByComparingTo("50.00");
        assertThat(hersAfter.newSavingsThisWeek())
                .as("and the week she is in counts what she put away, wherever she put it")
                .isEqualByComparingTo("50.00");
        assertThat(hersAfter.stillNeededThisWeek()).isEqualByComparingTo("0.00");
        assertThat(hersAfter.currentStreakWeeks())
                .as("a week saved jointly is still a week she saved")
                .isEqualTo(1);
        assertThat(hersAfter.moneyBalance())
                .as("and none of the euros are in an account of hers: the pot has them")
                .isEqualByComparingTo("0.00");

        BalancesView hisAfterHers = app.balancesOf(hisOwnAccount);
        assertThat(hisAfterHers.pointsBalance())
                .as("the other member earns nothing from somebody else's contribution")
                .isEqualTo(hisBefore.pointsBalance());
        assertThat(hisAfterHers.mostEverSaved())
                .as("and his mark is where it was, so his own first euro will still earn")
                .isEqualByComparingTo(hisBefore.mostEverSaved());
        assertThat(hisAfterHers.newSavingsThisWeek())
                .as("and her contribution did nothing for his week")
                .isEqualByComparingTo("0.00");
        assertThat(hisAfterHers.currentStreakWeeks()).isZero();

        app.aWeekPasses();

        // A new week, and she secures it out of the pot alone — which is story 30: a week in which
        // she only saved jointly is still a week she saved.
        assertThat(app.balancesOf(herOwnAccount).newSavingsThisWeek()).isEqualByComparingTo("0.00");
        DepositView hersAgain = app.deposit(pot.savingsAccountId(), ANKE, "50.00");

        BalancesView herSecondWeek = app.balancesOf(herOwnAccount);
        assertThat(herSecondWeek.currentStreakWeeks())
                .as("two weeks running, both of them secured from the shared pot")
                .isEqualTo(2);
        // And the rate is her own run's rate, climbed because the deposit that secured the second
        // week lengthened the run before it was priced: EUR 50 at 1.10 is 55 points.
        assertThat(hersAgain.multiplierApplied()).isEqualByComparingTo("1.10");
        assertThat(hersAgain.pointsEarned()).isEqualTo(55);
        assertThat(herSecondWeek.pointsBalance()).isEqualTo(105);

        BalancesView hisSecondWeek = app.balancesOf(hisOwnAccount);
        assertThat(hisSecondWeek.currentStreakWeeks())
                .as("a week of hers is not a week of his, however much the pot holds")
                .isZero();
        assertThat(hisSecondWeek.pointsBalance()).isEqualTo(hisBefore.pointsBalance());

        // And now he pays into the same pot, and it is his week and his run that move — at his own
        // rate, which is the ordinary one, because this is his first week and not his second.
        DepositView his = app.deposit(pot.savingsAccountId(), BRAM, "50.00");

        assertThat(his.multiplierApplied())
                .as("his own multiplier, not the one the pot's other member has climbed to")
                .isEqualByComparingTo("1.00");
        assertThat(his.pointsEarned()).isEqualTo(50);
        assertThat(app.balancesOf(hisOwnAccount).currentStreakWeeks()).isEqualTo(1);
        assertThat(app.balancesOf(hisOwnAccount).pointsBalance()).isEqualTo(50);
        assertThat(app.balancesOf(herOwnAccount).currentStreakWeeks())
                .as("and his contribution leaves her run exactly where she left it")
                .isEqualTo(2);
        assertThat(app.balancesOf(herOwnAccount).pointsBalance()).isEqualTo(105);

        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("the money, and only the money, is shared")
                .isEqualByComparingTo("150.00");
        assertThat(app.potWith(pot.id()).members())
                .as("two people saving into one pot, each keeping their own habit")
                .hasSize(2);
    }
}
