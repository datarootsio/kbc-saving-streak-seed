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
 * A category four fifths of the way through what its month allows is said once, on the night it
 * crossed, and is not said again on any of the nights it goes on standing there.
 *
 * <p>User stories 28 and 30. Being told while slowing down still helps is the whole point of the
 * quieter of the two budget warnings; being told it once is what keeps the panel worth opening. A
 * rule raised on the condition rather than on the crossing would put the same sentence in front of a
 * customer on every night from the tenth to the thirty-first, and an inbox holding twenty copies of
 * one line is worse than no notification at all.
 *
 * <p><strong>Six nightly sweeps is the shape of the claim.</strong> The category is over the line on
 * every one of them and more is spent against it in between, so a sweep asking "is it over the line
 * tonight" would answer yes six times. One notification is the assertion; the sixth night is where a
 * rule that looked at the state rather than at the crossing would have been caught.
 *
 * <p><strong>Climbing out and falling back in is a month boundary, and that is deliberate.</strong>
 * A budget is only ever about a month: the allowance starts again on the first, so a category that
 * is over the line in May and under it on the first of June has climbed out, and one that crosses
 * again in June has fallen back in and is worth a second line. Inside one month it is said once
 * however the figures move, which is what the record's own index over the category, the reason and
 * the day the month began makes a guarantee rather than an intention.
 *
 * <p>The sweep is fired by the name a trainer types into the development jobs endpoint, over a clock
 * this test winds, because there is no other producer of notifications in this application and an
 * alert that arrives the next night is a button press on a movable clock.
 *
 * <p>An application of its own, because months pass by winding the clock and that cannot be undone.
 */
class ABudgetRunningLowIsSaidOnceAcrossSixNightsAndAgainOnlyOnASecondCrossingApiTest
        extends ApiIntegrationTest {

    private static final String RUNNING_LOW = "A_BUDGET_IS_RUNNING_LOW";

    private static final String OVERSPENT = "A_BUDGET_HAS_BEEN_OVERSPENT";

    /** What the category is allowed each month, and the figure four fifths is taken of. */
    private static final String THE_MONTHLY_BUDGET = "200.00";

    /** Eighty-five per cent of it: over the line the warning is about, and under the limit. */
    private static final String WHAT_CROSSES_THE_LINE = "170.00";

    /** A little more, on a later night, so the six sweeps are not six readings of one figure. */
    private static final String A_LITTLE_MORE = "5.00";

    /** What the two come to, which is what the second and later sweeps are judged against. */
    private static final String WHAT_THE_MONTH_HAS_COST_BY_THEN = "175.00";

    /** Enough to cross the line again in the next month, and still not enough to go over. */
    private static final String WHAT_CROSSES_IT_AGAIN = "190.00";

    /** How many nights the warning is held against, which is the whole of the once-only claim. */
    private static final int HOW_MANY_NIGHTS_IT_IS_HELD_AGAINST = 6;

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-budget-running-low"));
        theCustomer = app.aCustomerOfItsOwn("told when a budget runs low");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void one_warning_across_six_nights_and_a_second_only_once_a_new_month_crosses_the_line() {
        long currentAccount = app.currentAccountOf(theCustomer);
        // Onto the first of a month before anything is declared, so that the six nights below are
        // six nights of one month rather than a stretch that quietly steps over a boundary and
        // hands the category a fresh allowance in the middle of the claim.
        windToTheFirstOfTheNextMonth();
        LocalDate theFirstMonthBegan = app.theDateTheClockReads();

        SpendingCategoryView groceries = app.declareACategoryFor(theCustomer, "Groceries");
        app.declareABudgetFor(theCustomer, groceries.categoryId(), THE_MONTHLY_BUDGET);

        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(whatWasSaidAbout(RUNNING_LOW))
                .as("a budget nothing has been spent against is not running low")
                .isEmpty();

        app.spendFor(theCustomer, "The big shop", WHAT_CROSSES_THE_LINE, groceries.categoryId());
        app.runJob(TheNotificationSweep.THE_JOB);

        List<NotificationView> afterTheCrossing = whatWasSaidAbout(RUNNING_LOW);
        assertThat(afterTheCrossing)
                .as("a hundred and seventy of two hundred is four fifths of the month gone, which "
                        + "is said once, on the night it crossed")
                .singleElement()
                .satisfies(said -> {
                    assertThat(said.categoryId()).isEqualTo(groceries.categoryId());
                    assertThat(said.categoryName())
                            .as("the customer's own word for it, because that is what the sentence "
                                    + "leads with")
                            .isEqualTo("Groceries");
                    assertThat(said.occursOn())
                            .as("the month it is about, carried as the day that month began")
                            .isEqualTo(theFirstMonthBegan.withDayOfMonth(1));
                    assertThat(said.amount())
                            .as("what the month allows, which is the budget and the carry together")
                            .isEqualByComparingTo(new BigDecimal(THE_MONTHLY_BUDGET));
                    assertThat(said.balance())
                            .as("and what has been spent against it, so that both halves of the "
                                    + "decision are on the page")
                            .isEqualByComparingTo(new BigDecimal(WHAT_CROSSES_THE_LINE));
                    assertThat(said.currentAccountId()).isEqualTo(currentAccount);
                    assertThat(said.savingsAccountId())
                            .as("a budget is a fact about a current account")
                            .isNull();
                    assertThat(said.billId()).isNull();
                });

        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(whatWasSaidAbout(RUNNING_LOW))
                .as("a retry of the same night's sweep says nothing the second time")
                .isEqualTo(afterTheCrossing);

        // Six nights of the same month, with more spent against the same budget in the middle of
        // them: the category is over the line on every one of these sweeps.
        for (int night = 0; night < HOW_MANY_NIGHTS_IT_IS_HELD_AGAINST; night++) {
            app.daysPass(1);
            if (night == 2) {
                app.spendFor(theCustomer, "Another shop", A_LITTLE_MORE, groceries.categoryId());
            }
            app.runJob(TheNotificationSweep.THE_JOB);
        }
        assertThat(app.spendingThisMonthOf(theCustomer).spent())
                .as("the month really did go on costing more while those six sweeps ran")
                .isEqualByComparingTo(new BigDecimal(WHAT_THE_MONTH_HAS_COST_BY_THEN));
        assertThat(whatWasSaidAbout(RUNNING_LOW))
                .as("and the customer was told once: a category that crossed the line on the "
                        + "second is still over it on the eighth, and it is one thing that happened")
                .isEqualTo(afterTheCrossing);

        // Climbing out. A budget is about a month, so the month ending is what puts the category
        // back under its line — the allowance starts again and nothing has been spent against it.
        YearMonth theSecondMonth = YearMonth.from(theFirstMonthBegan).plusMonths(1);
        windTo(theSecondMonth.atDay(1));
        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(whatWasSaidAbout(RUNNING_LOW))
                .as("a new month is a new allowance with nothing spent against it, so there is "
                        + "nothing to say — and nothing said before is taken back")
                .isEqualTo(afterTheCrossing);

        // And falling back in.
        app.spendFor(theCustomer, "The big shop again", WHAT_CROSSES_IT_AGAIN,
                groceries.categoryId());
        app.runJob(TheNotificationSweep.THE_JOB);

        assertThat(whatWasSaidAbout(RUNNING_LOW))
                .as("having climbed out and crossed the line a second time, the warning means "
                        + "something again and is said again")
                .hasSize(2)
                .satisfies(said -> {
                    assertThat(said.get(0).occursOn())
                            .as("newest first, as the panel reads them, and about the new month")
                            .isEqualTo(theSecondMonth.atDay(1));
                    assertThat(said.get(0).balance())
                            .isEqualByComparingTo(new BigDecimal(WHAT_CROSSES_IT_AGAIN));
                    assertThat(said.get(0).raisedAt()).isAfter(said.get(1).raisedAt());
                });

        assertThat(whatWasSaidAbout(OVERSPENT))
                .as("and neither month went over what it allowed, so the louder of the two reasons "
                        + "was never raised at all")
                .isEmpty();

        assertThat(app.marksTheirNotificationsRead(theCustomer))
                .as("both stand in the customer's own list and are marked read in the one round "
                        + "trip the panel behind the bell makes")
                .isNotEmpty()
                .allSatisfy(said -> assertThat(said.readAt()).isNotNull());
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
