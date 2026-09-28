package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An account whose bills, arrears and budgets together claim more of this month than the month has
 * is told once, on the night it crossed, and is told again only in a month that crosses afresh.
 *
 * <p>User stories 31 and 32. A customer wants to see a bad month coming while there is still time to
 * do something about it, and wants that warning to keep meaning something — which it stops doing the
 * moment it arrives every night for the rest of the month.
 *
 * <p><strong>Six nightly sweeps is the shape of the claim</strong>, exactly as it is for the
 * category warnings: the account stands over the line on every one of them, so a rule asking "is it
 * over tonight" would answer yes six times.
 *
 * <p><strong>Climbing out and falling back in inside one month says nothing, and that is the
 * deliberate answer.</strong> This warning is about a month, and a month is the unit it is raised in
 * — so a customer who cuts a budget, comes back under the line and then puts the figure back where
 * it was has not earned a second copy of a warning about the same month. The record's own index over
 * the account and the day that month began is what makes that a guarantee rather than an intention.
 * The next month is a new promise, and a month that crosses afresh is worth its own line.
 *
 * <p>No bills and no income in this test, deliberately: a budget larger than the balance is the
 * shortest true version of the condition, and bringing a billing run into it would be asserting the
 * bill calendar a dozen other tests already assert. What this test is about is the comparison and
 * the once-in-a-month rule.
 *
 * <p>An application of its own, because months pass by winding the clock and that cannot be undone.
 */
class AMonthPromisedToMoreThanItHoldsIsAnnouncedOnceInItApiTest extends ApiIntegrationTest {

    private static final String OVER_COMMITTED = "THE_MONTH_IS_OVER_COMMITTED";

    /** What a customer opens with, and what the promise below is measured against. */
    private static final String WHAT_THE_ACCOUNT_HOLDS = "1500.00";

    /** A plan larger than the account: the whole of the condition, in one figure. */
    private static final String A_PLAN_BIGGER_THAN_THE_MONTH = "2000.00";

    /** And one the month covers comfortably, which is how the customer climbs back under. */
    private static final String A_PLAN_THE_MONTH_COVERS = "500.00";

    /** How many nights the warning is held against, which is the whole of the once-only claim. */
    private static final int HOW_MANY_NIGHTS_IT_IS_HELD_AGAINST = 6;

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-over-committed-month"));
        theCustomer = app.aCustomerOfItsOwn("told when a month is over-committed");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void one_warning_a_month_however_often_the_account_crosses_the_line_inside_it() {
        long currentAccount = app.currentAccountOf(theCustomer);
        // Onto the first of a month before anything is declared, so that the six nights below are
        // six nights of one month rather than a stretch that steps over a boundary in the middle of
        // the claim.
        windToTheFirstOfTheNextMonth();
        LocalDate theFirstMonthBegan = app.theDateTheClockReads();

        SpendingCategoryView everything = app.declareACategoryFor(theCustomer, "Everything");

        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(whatWasSaidAbout(OVER_COMMITTED))
                .as("an account whose holder has put a figure on nothing has made no plan for the "
                        + "month to be smaller than")
                .isEmpty();

        app.declareABudgetFor(theCustomer, everything.categoryId(), A_PLAN_BIGGER_THAN_THE_MONTH);
        app.runJob(TheNotificationSweep.THE_JOB);

        List<NotificationView> afterTheCrossing = whatWasSaidAbout(OVER_COMMITTED);
        assertThat(afterTheCrossing)
                .as("two thousand euros of budget against fifteen hundred in the account and "
                        + "nothing due to arrive is a month promised to more than it holds")
                .singleElement()
                .satisfies(said -> {
                    assertThat(said.amount())
                            .as("what the month is promised to: the bills and arrears still to be "
                                    + "met, plus what the budgets still allow")
                            .isEqualByComparingTo(new BigDecimal(A_PLAN_BIGGER_THAN_THE_MONTH));
                    assertThat(said.balance())
                            .as("against what it has: the balance and the income still due in it")
                            .isEqualByComparingTo(new BigDecimal(WHAT_THE_ACCOUNT_HOLDS));
                    assertThat(said.occursOn())
                            .as("the month it is about, carried as the day that month began")
                            .isEqualTo(theFirstMonthBegan.withDayOfMonth(1));
                    assertThat(said.currentAccountId()).isEqualTo(currentAccount);
                    assertThat(said.categoryId())
                            .as("it is about the whole promise and blames no single category for "
                                    + "there being more of them than the month can pay for")
                            .isNull();
                    assertThat(said.savingsAccountId()).isNull();
                    assertThat(said.billId()).isNull();
                });

        for (int night = 0; night < HOW_MANY_NIGHTS_IT_IS_HELD_AGAINST; night++) {
            app.daysPass(1);
            app.runJob(TheNotificationSweep.THE_JOB);
        }
        assertThat(whatWasSaidAbout(OVER_COMMITTED))
                .as("six nights over the line is one month over the line, and the customer is told "
                        + "about it once")
                .isEqualTo(afterTheCrossing);

        // Climbing out inside the month, and falling straight back in: the customer cuts the figure
        // to one the month covers and then puts it back where it was.
        app.declareABudgetFor(theCustomer, everything.categoryId(), A_PLAN_THE_MONTH_COVERS);
        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(whatWasSaidAbout(OVER_COMMITTED))
                .as("coming back under says nothing new, and takes nothing back either")
                .isEqualTo(afterTheCrossing);

        app.declareABudgetFor(theCustomer, everything.categoryId(), A_PLAN_BIGGER_THAN_THE_MONTH);
        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(whatWasSaidAbout(OVER_COMMITTED))
                .as("and crossing again inside the same month still says nothing: this warning is "
                        + "about a month, and it is the same month")
                .isEqualTo(afterTheCrossing);

        // A month that crosses afresh. The budget standing is the larger one again and nothing has
        // been spent, so the new month is promised to more than it holds from its first day.
        YearMonth theSecondMonth = YearMonth.from(theFirstMonthBegan).plusMonths(1);
        windTo(theSecondMonth.atDay(1));
        app.runJob(TheNotificationSweep.THE_JOB);

        assertThat(whatWasSaidAbout(OVER_COMMITTED))
                .as("a new month is a new promise, and one bigger than the month it is made in is "
                        + "worth saying a second time")
                .hasSize(2)
                .satisfies(said -> {
                    assertThat(said.get(0).occursOn())
                            .as("newest first, as the panel reads them, and about the new month")
                            .isEqualTo(theSecondMonth.atDay(1));
                    assertThat(said.get(0).raisedAt()).isAfter(said.get(1).raisedAt());
                });

        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(whatWasSaidAbout(OVER_COMMITTED))
                .as("and the new month is itself said only once")
                .hasSize(2);
    }

    /** Only what has been said to this customer under one reason, newest first. */
    private static List<NotificationView> whatWasSaidAbout(String reason) {
        return Arrays.stream(app.notificationsOf(theCustomer))
                .filter(said -> reason.equals(said.reason()))
                .toList();
    }

    /** Winds the clock to that day, insisting on a move forwards: the clock only goes one way. */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days)
                .as("this test has to reach " + day + " and the clock cannot be wound backwards")
                .isPositive();
        app.daysPass(days);
    }

    /** Onto the first of the next month, which is where this test's narrative starts from. */
    private static void windToTheFirstOfTheNextMonth() {
        windTo(YearMonth.from(app.theDateTheClockReads()).plusMonths(1).atDay(1));
    }
}
