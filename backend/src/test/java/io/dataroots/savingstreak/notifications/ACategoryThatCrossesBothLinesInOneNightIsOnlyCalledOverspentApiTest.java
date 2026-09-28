package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove.PartAsTyped;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import io.dataroots.savingstreak.support.SpendView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A category that passes four fifths of its allowance and the whole of it between two sweeps is
 * called overspent and is never called running low — not on the night it crossed, and not afterwards
 * either.
 *
 * <p>User stories 28, 29 and 30, and the one case in this half of the feature that has two defensible
 * answers and therefore needs a deliberate one. The quieter reason is defined as the crossing of four
 * fifths <em>without</em> the crossing of all of it, so the two are mutually exclusive by their own
 * definitions rather than by a rule somebody has to remember: raising both would put a line saying
 * "running low" beside a line saying "you are already over" about one category on one night, and the
 * first of those is false the moment the second is true.
 *
 * <p><strong>The second half is the half worth writing down.</strong> Everything in the budgets
 * module is derived on every read, so a correction can move a spend's euros out of the category and
 * leave the very month this warning was about sitting comfortably at eighty-five per cent — at which
 * point a sweep judging only where the month stands would raise the quieter line, and the customer
 * would be warned loudly and then warned softly about one month, in that order. That is a warning
 * walking backwards and nobody could act on it. So the louder line stands for the month: the panel is
 * a log of what was said on the nights it was said, and the budget screen is what is true now.
 *
 * <p>That the correction was heard at all is asserted through the budget read, so this test is not
 * quietly passing because nothing moved.
 *
 * <p>An application of its own, for the reason the other tests of this feature have one: the sweep is
 * fired by name and the record it writes is the subject.
 */
class ACategoryThatCrossesBothLinesInOneNightIsOnlyCalledOverspentApiTest
        extends ApiIntegrationTest {

    private static final String RUNNING_LOW = "A_BUDGET_IS_RUNNING_LOW";

    private static final String OVERSPENT = "A_BUDGET_HAS_BEEN_OVERSPENT";

    /** What the category is allowed in the month. */
    private static final String THE_MONTHLY_BUDGET = "100.00";

    /** One spend that goes past both lines at once, which is the whole of the first half. */
    private static final String WHAT_CROSSES_BOTH_LINES = "250.00";

    /** What the correction leaves filed here: eighty-five per cent, which is running low. */
    private static final String WHAT_THE_CORRECTION_LEAVES_HERE = "85.00";

    /** And what it moves to a category nobody has put a figure on. */
    private static final String WHAT_THE_CORRECTION_MOVES_AWAY = "165.00";

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationOfThisTestsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-budget-both-lines"));
        theCustomer = app.aCustomerOfItsOwn("crossing both budget lines at once");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_louder_line_is_the_one_said_and_it_stands_for_the_month() {
        SpendingCategoryView holiday = app.declareACategoryFor(theCustomer, "Holiday");
        SpendingCategoryView elsewhere = app.declareACategoryFor(theCustomer, "Everything else");
        app.declareABudgetFor(theCustomer, holiday.categoryId(), THE_MONTHLY_BUDGET);

        SpendView flights = app.spendFor(theCustomer, "Flights", WHAT_CROSSES_BOTH_LINES,
                holiday.categoryId());
        app.runJob(TheNotificationSweep.THE_JOB);

        List<NotificationView> overspent = whatWasSaidAbout(OVERSPENT);
        assertThat(overspent)
                .as("two hundred and fifty euros against a hundred passes four fifths and the "
                        + "whole of it between two sweeps, and the louder line is the one said")
                .singleElement()
                .satisfies(said -> {
                    assertThat(said.categoryId()).isEqualTo(holiday.categoryId());
                    assertThat(said.categoryName()).isEqualTo("Holiday");
                    assertThat(said.amount())
                            .as("what the month allowed")
                            .isEqualByComparingTo(new BigDecimal(THE_MONTHLY_BUDGET));
                    assertThat(said.balance())
                            .as("against what it cost")
                            .isEqualByComparingTo(new BigDecimal(WHAT_CROSSES_BOTH_LINES));
                });
        assertThat(whatWasSaidAbout(RUNNING_LOW))
                .as("and the quieter line is not said beside it: \"running low\" is the crossing of "
                        + "four fifths without the crossing of all of it, and it would be false")
                .isEmpty();

        // The correction, which is the case this test exists for: the euros move out of the
        // category and leave the very month the warning was about at eighty-five per cent.
        app.correctTheSplitFor(theCustomer, flights.spendId(),
                PartAsTyped.filedUnder(holiday.categoryId(), WHAT_THE_CORRECTION_LEAVES_HERE),
                PartAsTyped.filedUnder(elsewhere.categoryId(), WHAT_THE_CORRECTION_MOVES_AWAY));

        assertThat(app.spendingThisMonthOf(theCustomer).theCategory(holiday.categoryId()))
                .as("the correction really was heard, and the month it was about now reads as a "
                        + "category comfortably inside its budget")
                .satisfies(row -> {
                    assertThat(row.spent())
                            .isEqualByComparingTo(new BigDecimal(WHAT_THE_CORRECTION_LEAVES_HERE));
                    assertThat(row.overspent()).isFalse();
                });

        app.runJob(TheNotificationSweep.THE_JOB);

        assertThat(whatWasSaidAbout(RUNNING_LOW))
                .as("and still nothing quieter is said: a customer warned that a month had gone "
                        + "over is not told afterwards that the same month is merely running low, "
                        + "because a warning that got softer is one nobody could act on")
                .isEmpty();
        assertThat(whatWasSaidAbout(OVERSPENT))
                .as("nor is the louder one taken back. A notification is a record of what was said "
                        + "on the night it was said, and the budget screen is what is true now")
                .isEqualTo(overspent);
    }

    /** Only what has been said to this customer under one reason, newest first. */
    private static List<NotificationView> whatWasSaidAbout(String reason) {
        return Arrays.stream(app.notificationsOf(theCustomer))
                .filter(said -> reason.equals(said.reason()))
                .toList();
    }
}
