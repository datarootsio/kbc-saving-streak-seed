package io.dataroots.savingstreak.whatifididthisinstead;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.SimulationView;
import io.dataroots.savingstreak.support.SimulationView.AMonthOfTheFutureView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test the whole feature stands on: a year is predicted, the clock is wound three months, the
 * nightly runs are fired in the order they are scheduled, and the balance, the points and the run of
 * weeks the application then reports are put beside the three figures the third month predicted.
 *
 * <p><strong>Why this can exist at all.</strong> A simulator in an application without a clock a
 * trainer can wind and jobs a trainer can run by name is a thing nobody can check — it is a second
 * statement of every rule, agreeing with the first only for as long as nobody edits either. Here the
 * prediction and the reality can be put side by side in about forty seconds, and the one place this
 * feature restates a rule rather than quoting it — the three steps {@code DepositsService.deposit}
 * is made of, spelled out again inside the fold — fails a test rather than misleading a customer
 * when it drifts.
 *
 * <p><strong>All five runs, in the order they are scheduled.</strong> {@code aNightPasses} fires the
 * three that move euros, which is what every other clock-moving test needs; this needs the two that
 * move points as well, because a month row carries what expired and what a bonus paid and a branch
 * predicting those against an application nobody had swept would be marked wrong for being right.
 *
 * <p><strong>The fixtures are ordinary, and that is a criterion rather than a convenience.</strong>
 * A household with a salary, rent on the first and two standing rules — and two of the days are the
 * day the account is set up on, so that the salary and one of the rules have an occurrence on the
 * very morning the window opens. The slice that wrote the fold suggested a test like this should
 * pick trigger days that <em>avoid</em> that morning, because the fold fired it a second time; the
 * answer was to fix the fold, so this aims at the boundary instead of away from it. A test that
 * needed the days chosen the other way would be saying the fix had not worked.
 *
 * <p>Three months rather than twelve because three months is enough for a rule to have fired a dozen
 * times, three salaries to have landed, three lots of rent to have gone out and a run of weeks to
 * have climbed the ladder to its cap — and it is six hundred round trips rather than two and a half
 * thousand.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: a clock cannot
 * be wound back, so nothing that shares one with another test can have three months pass in the
 * middle of it.
 */
class TheSimulatorAgreesWithTheApplicationApiTest extends ApiIntegrationTest {

    /** How far the clock is wound, which is the row the prediction is checked against. */
    private static final int MONTHS_WOUND_ON = 3;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-simulator-agrees"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_application_after_three_months_reports_the_figures_the_third_month_predicted() {
        String customer = app.aCustomerOfItsOwn("agreeing");
        long savingsAccount = app.savingsAccountOf(customer);
        LocalDate theDayTheySetItAllUp = app.theDateTheClockReads();
        // An ordinary household, set up on the day they happen to be looking at the screen: paid on
        // that day of the month, rent out on the first, sixty euros into savings every Friday and
        // twenty-five more on the weekday they are setting it up on.
        //
        // Two of those days are today's on purpose, and that is the whole of what makes this test
        // catch the thing it exists to catch rather than pass by luck. The salary's day of the month
        // is today's and the second rule's day of the week is today's, so both have an occurrence
        // that falls on the very morning the window opens — a morning the application has already
        // lived through, and one a fold that assumed nothing had been settled would live through a
        // second time. Nothing about them is contrived away from that boundary; they are aimed at it.
        app.declareIncomeFor(customer, theDayTheySetItAllUp.getDayOfMonth(), "2400.00");
        app.declareABillFor(customer, "Rent", 1, "900.00");
        app.leaveARuleStanding(savingsAccount, Map.of(
                "name", "Every Friday", "fromCurrentAccountId", app.currentAccountOf(customer),
                "trigger", "WEEKLY", "dayOfWeek", DayOfWeek.FRIDAY.name(),
                "howMuchMoves", "A_FIXED_AMOUNT", "amount", "60.00"));
        app.leaveARuleStanding(savingsAccount, Map.of(
                "name", "Every " + theDayTheySetItAllUp.getDayOfWeek(),
                "fromCurrentAccountId", app.currentAccountOf(customer),
                "trigger", "WEEKLY", "dayOfWeek", theDayTheySetItAllUp.getDayOfWeek().name(),
                "howMuchMoves", "A_FIXED_AMOUNT", "amount", "25.00"));

        SimulationView predicted = app.simulationOf(savingsAccount);
        LocalDate theThirdMonthClosesOn =
                predicted.whereThisAccountStands().asAt().plusMonths(MONTHS_WOUND_ON);
        AMonthOfTheFutureView thirdMonth =
                predicted.theYearAlreadyUnderWay().closingOn(theThirdMonthClosesOn);
        assertThat(thirdMonth)
                .as("the rows are counted from the day the window opened, so winding three months "
                        + "lands exactly on the third row's closing day — which is the whole reason "
                        + "the months are not calendar months")
                .isNotNull();

        // Night by night, all five runs, until the application's clock reads the day that row
        // closes on. Night by night rather than one wind and one run of each job, because those are
        // not the same three months: a single catch-up credits every salary first and then presents
        // every bill, so a sweep meets money that had not arrived on any morning but one.
        while (app.theDateTheClockReads().isBefore(theThirdMonthClosesOn)) {
            app.aWholeNightPasses();
        }
        assertThat(app.theDateTheClockReads())
                .as("the clock now reads the day the third row closes on, and every assertion below "
                        + "is about that one day")
                .isEqualTo(theThirdMonthClosesOn);

        BalancesView actually = app.balancesOf(savingsAccount);
        assertThat(actually.moneyBalance())
                .as("the euros the application holds are the euros the third month predicted — a "
                        + "dozen-odd Fridays of sixty and as many of the twenty-five, depending only "
                        + "on where those days fall, and neither the fold nor the run got to choose "
                        + "which")
                .isEqualByComparingTo(thirdMonth.balance());
        assertThat(actually.pointsBalance())
                .as("and the points too, which is the harder half: every one of them went through "
                        + "the high-water mark, the streak ladder and the double flooring twice "
                        + "over, once in the fold and once in the application, and the two figures "
                        + "are the only evidence that those two copies still say the same thing")
                .isEqualTo(thirdMonth.pointsStanding());
        assertThat(actually.currentStreakWeeks())
                .as("and the run of secured weeks, read the same way on both sides: the weeks behind "
                        + "this one, and this one too once enough has landed in it")
                .isEqualTo(thirdMonth.securedWeeks());
    }
}
