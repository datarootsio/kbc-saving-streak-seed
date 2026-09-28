package io.dataroots.savingstreak.notifications;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The share at which a budget is called running low comes from the scheme in force on the night the
 * sweep runs.
 *
 * <p>One month, one category, one figure spent, and two nights either side of a published Monday.
 * A hundred and forty of two hundred is seven tenths of the month gone: under four fifths, which is
 * what this application has always called running low, and over three fifths, which is what the bank
 * publishes in the middle of this test. Nothing about the category changes between the two sweeps —
 * not the budget, not the carry, not a euro of the spending — so the second warning is the published
 * share and nothing else.
 *
 * <p><strong>Deliberately the same month and the same category on both nights.</strong> A budget is
 * only ever about a month, so a test that let one roll over would be comparing two allowances rather
 * than two shares, and the second night would find a category nothing had been spent against. It
 * starts on the first so that the Monday it winds to — never more than seven days off — is a Monday
 * of the same month.
 *
 * <p>The sweep is run again afterwards because the uniqueness the record keeps over budget warnings
 * is not a thing a published figure may touch: once a category has been called running low in a
 * month, it is called running low once.
 *
 * <p>An application of its own, because both the clock and a published version of the scheme move
 * one way only.
 */
class AChangeToTheShareABudgetIsCalledLowAtIsUsedByTheNextSweepApiTest extends ApiIntegrationTest {

    private static final String RUNNING_LOW = "A_BUDGET_IS_RUNNING_LOW";

    /** What the category is allowed each month, and the figure the share is taken of. */
    private static final String THE_MONTHLY_BUDGET = "200.00";

    /** Seven tenths of it: under four fifths of the month and over three fifths of it. */
    private static final String SEVEN_TENTHS_OF_THE_MONTH = "140.00";

    /** What the bank publishes, as a percentage, because that is the unit the scheme speaks in. */
    private static final String THREE_FIFTHS_AS_A_PERCENTAGE = "60.00";

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseSchemeThisTestMayPublishVersionsOf() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-published-share-of-a-budget"));
        theCustomer = app.aCustomerOfItsOwn("told when a published share says a budget is low");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_same_month_is_called_running_low_only_once_the_published_share_says_it_is() {
        windToTheFirstOfTheNextMonth();
        YearMonth theMonthThisTestIsAbout = YearMonth.from(app.theDateTheClockReads());
        SpendingCategoryView groceries = app.declareACategoryFor(theCustomer, "Groceries");
        app.declareABudgetFor(theCustomer, groceries.categoryId(), THE_MONTHLY_BUDGET);
        app.spendFor(theCustomer, "The big shop", SEVEN_TENTHS_OF_THE_MONTH,
                groceries.categoryId());

        app.runJob(TheNotificationSweep.THE_JOB);

        assertThat(whatWasSaidAbout(RUNNING_LOW))
                .as("a hundred and forty of two hundred is not four fifths of the month, which is "
                        + "the share the scheme is seeded at")
                .isEmpty();

        LocalDate theMondayTheShareMoves = app.theNextMondayStillToCome();
        assertThat(YearMonth.from(theMondayTheShareMoves))
                .as("the Monday the share moves on is a Monday of the month this test is about, or "
                        + "the second sweep would be comparing two allowances rather than two "
                        + "shares — which is why it starts on the first")
                .isEqualTo(theMonthThisTestIsAbout);
        Map<String, Object> runningLowSooner = ASchemeSomebodyAdministers.theSameSchemeAgain(
                app.theSchemeInForce(), theMondayTheShareMoves,
                "A budget is called running low at three fifths of what the month allows rather "
                        + "than at four, so that there is more of the month left to act in.");
        runningLowSooner.put("whatShareOfABudgetIsRunningLow", THREE_FIFTHS_AS_A_PERCENTAGE);
        app.publishAVersionOfTheScheme(runningLowSooner);
        app.theClockReaches(theMondayTheShareMoves);

        app.runJob(TheNotificationSweep.THE_JOB);

        List<NotificationView> afterTheShareMoved = whatWasSaidAbout(RUNNING_LOW);
        assertThat(afterTheShareMoved)
                .as("the same hundred and forty of the same two hundred is over three fifths, and "
                        + "the sweep is judging by the share the bank published on Monday")
                .singleElement()
                .satisfies(said -> {
                    assertThat(said.categoryId()).isEqualTo(groceries.categoryId());
                    assertThat(said.amount())
                            .as("what the month allows, which the share is taken of")
                            .isEqualByComparingTo(THE_MONTHLY_BUDGET);
                    assertThat(said.balance())
                            .as("and what it has cost, which crossed it")
                            .isEqualByComparingTo(SEVEN_TENTHS_OF_THE_MONTH);
                });

        app.runJob(TheNotificationSweep.THE_JOB);

        assertThat(whatWasSaidAbout(RUNNING_LOW))
                .as("once per category per month, which a published figure does not loosen")
                .isEqualTo(afterTheShareMoved);
    }

    /** Only what has been said to this customer under one reason, newest first. */
    private static List<NotificationView> whatWasSaidAbout(String reason) {
        return Arrays.stream(app.notificationsOf(theCustomer))
                .filter(said -> reason.equals(said.reason()))
                .toList();
    }

    /**
     * Onto the first of the next month, which is where this test's one month starts — and what makes
     * the Monday it winds to a Monday of that same month.
     */
    private static void windToTheFirstOfTheNextMonth() {
        LocalDate theFirst = YearMonth.from(app.theDateTheClockReads()).plusMonths(1).atDay(1);
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), theFirst));
    }
}
