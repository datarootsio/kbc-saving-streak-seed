package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BillOccurrenceView;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bill declared on the 31st is taken on the last day of February, and once — not twice — in a
 * thirty-day month.
 *
 * <p>User story 9. Clamping is the banking convention and it is the only rule that does not skip
 * February outright: a rent taken on the last day of the month goes out twelve times a year, and an
 * application that treated "the 31st" literally would take it seven times and leave the customer to
 * work out why. The other half of the same clamp is the one that is easy to get wrong in the
 * opposite direction — a month with no 31st must name its last day <em>once</em>, and a run that
 * walked days rather than months would find the 30th and then the 31st-clamped-to-the-30th and
 * charge April twice.
 *
 * <p>February is reached by winding to the day before it ends, so that the claim is made twice over:
 * a run on the day before takes nothing, and a run on the last day the month has takes the bill. One
 * of those alone would be satisfied by a job that took the money on any day it happened to be run.
 *
 * <p>The last day of February is asked of the calendar rather than written down, because it is the
 * 29th in a leap year and this test has to be true in one.
 */
class ABillDueOnTheThirtyFirstIsTakenOnTheLastDayOfAShortMonthApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "takeBillsDue";
    private static final String THE_SUBSCRIPTION = "10.00";
    private static final int THE_THIRTY_FIRST = 31;

    /** April, the thirty-day month this test finishes in. */
    private static final int APRIL = 4;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseFebruaryThisTestReaches() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-bill-clamped-to-february"));
        theCustomer = app.aCustomerOfItsOwn("a bill clamped to february");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_thirty_first_falls_on_the_last_day_february_has_and_on_the_thirtieth_of_april_once() {
        LocalDate lastDayOfFebruary = theNextFebruaryAfter(app.theDateTheClockReads()).atEndOfMonth();
        windTo(lastDayOfFebruary.minusDays(1));

        RecurringBillView subscription =
                app.declareABillFor(theCustomer, "Subscription", THE_THIRTY_FIRST, THE_SUBSCRIPTION);
        BigDecimal before = app.currentAccountBalanceOf(theCustomer);

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("the day before is not the day it is due, however short the month is")
                .isEqualByComparingTo(before);
        assertThat(app.historyOfBill(theCustomer, subscription.billId())).isEmpty();

        app.daysPass(1);
        assertThat(app.theDateTheClockReads()).isEqualTo(lastDayOfFebruary);
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("February is taken rather than skipped: a bill declared for the 31st goes out "
                        + "twelve times a year")
                .isEqualByComparingTo(before.subtract(new BigDecimal(THE_SUBSCRIPTION)));
        assertThat(app.historyOfBill(theCustomer, subscription.billId()))
                .singleElement()
                .satisfies(february -> {
                    assertThat(february.dueOn())
                            .as("which is the 29th in a leap year and the 28th otherwise, asked of "
                                    + "the calendar rather than written down here")
                            .isEqualTo(lastDayOfFebruary);
                    assertThat(february.outcome()).isEqualTo("PAID");
                });

        LocalDate theLastDayOfApril = YearMonth.of(lastDayOfFebruary.getYear(), APRIL).atEndOfMonth();
        windTo(theLastDayOfApril);
        app.runJob(THE_JOB);

        List<BillOccurrenceView> history = app.historyOfBill(theCustomer, subscription.billId());
        assertThat(history.stream().map(BillOccurrenceView::dueOn))
                .as("newest first, and the clamp is made month by month: the last day February "
                        + "has, then the 31st of March because March has one, then the 30th of "
                        + "April — which is named once and not twice")
                .containsExactly(theLastDayOfApril,
                        LocalDate.of(lastDayOfFebruary.getYear(), 3, 31),
                        lastDayOfFebruary);
        assertThat(history).allSatisfy(taken ->
                assertThat(taken.outcome()).isEqualTo("PAID"));
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("three months, three debits, and not a fourth for a day April does not have")
                .isEqualByComparingTo(
                        before.subtract(new BigDecimal(THE_SUBSCRIPTION).multiply(new BigDecimal(3))));
    }

    /**
     * Winds the clock to that day, insisting on a move forwards: the clock only goes one way and
     * refuses a move of no days at all, so a test that has already passed the day it wants is a test
     * asserting about the wrong month.
     */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days)
                .as("this test has to reach " + day + " and the clock cannot be wound backwards")
                .isPositive();
        app.daysPass(days);
    }

    /**
     * The first February that starts after the month the given day falls in, so that every wind
     * forward this test makes is a move the clock will accept.
     */
    private static YearMonth theNextFebruaryAfter(LocalDate today) {
        YearMonth february = YearMonth.of(today.getYear(), 2);
        return february.isAfter(YearMonth.from(today)) ? february : february.plusYears(1);
    }
}
