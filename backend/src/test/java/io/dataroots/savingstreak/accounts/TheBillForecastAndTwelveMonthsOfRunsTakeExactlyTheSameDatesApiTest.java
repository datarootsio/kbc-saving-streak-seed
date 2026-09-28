package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BillOccurrenceView;
import io.dataroots.savingstreak.support.BillToComeView;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.RulePreviewView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The twelve months the forecast draws are the twelve months the nightly run takes: wind the clock
 * to the far edge of the bar, run the job, and the dates actually presented are the dates the
 * forecast showed — to the day, with nothing extra and nothing missing.
 *
 * <p>User story 30, and the promise the whole forecast is under. A timeline a customer cannot trust
 * is worse than no timeline: they would decide how much to save against a rent the run then takes on
 * a different day. The two agree by construction — the forecast calls the same
 * {@code WhenABillIsDue} the run calls and asks the record the same question about which months are
 * already settled — and this is the assertion that says so out loud rather than leaving it to a
 * reading of the code.
 *
 * <p><strong>The window is read rather than assumed.</strong> How many days to wind is the distance
 * between the two days the forecast itself names, so this test cannot pass by agreeing with its own
 * arithmetic about how long twelve months is — and it stays true in a leap year.
 *
 * <p><strong>A bill on the thirty-first is one of the two</strong>, because the month-end clamp is
 * where a forecast and a run are most likely to part company: a forecast that named the 31st of
 * February would promise a debit on a day nobody is ever charged on. The other is the twelfth, an
 * ordinary day that every month has.
 *
 * <p>The job is run twice over the same stretch, because that is the idempotence check and it is
 * cheap: a second run must find every date already settled and take nothing more.
 *
 * <p>The bills are small enough that the account covers all of them, so the balance is a second,
 * independent reading of the same claim — the money that left is the money the forecast said would.
 */
class TheBillForecastAndTwelveMonthsOfRunsTakeExactlyTheSameDatesApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";

    private static final int THE_LAST_DAY_OF_THE_MONTH = 31;
    private static final int AN_ORDINARY_DAY = 12;
    private static final String THE_SUBSCRIPTION = "10.00";
    private static final String THE_PHONE = "15.00";

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestWinds() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-bill-forecast-agrees"));
        theCustomer = app.aCustomerOfItsOwn("the bill forecast agrees");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void winding_the_clock_twelve_months_takes_exactly_the_dates_the_forecast_showed() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        BigDecimal held = app.currentAccountBalanceOf(theCustomer);
        RecurringBillView subscription = app.declareABillFor(theCustomer, "Subscription",
                THE_LAST_DAY_OF_THE_MONTH, THE_SUBSCRIPTION);
        RecurringBillView phone =
                app.declareABillFor(theCustomer, "Phone", AN_ORDINARY_DAY, THE_PHONE);

        RulePreviewView theYearAhead = app.previewOn(savingsAccount);
        List<LocalDate> forecast = theYearAhead.bills().stream()
                .map(BillToComeView::dueOn)
                .sorted()
                .toList();
        BigDecimal forecastTotal = theYearAhead.bills().stream()
                .map(BillToComeView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(forecast)
                .as("two bills over twelve months is a couple of dozen dates, and a forecast that "
                        + "came back empty would satisfy every comparison below")
                .hasSizeGreaterThanOrEqualTo(24);
        assertThat(theYearAhead.bills())
                .as("none of them has fallen yet: both bills were declared a moment ago, so every "
                        + "date is still to come rather than owed by the next run")
                .allSatisfy(date -> assertThat(date.owedRatherThanStillToCome()).isFalse());
        assertThat(forecast)
                .as("the month-end clamp is in the forecast and not only in the run: a date on the "
                        + "31st of a short month is a day nobody is ever charged on")
                .allSatisfy(date -> assertThat(date.getDayOfMonth())
                        .isIn(THE_LAST_DAY_OF_THE_MONTH, AN_ORDINARY_DAY,
                                date.lengthOfMonth()));

        // As far as the bar looks, counted out of the bar's own two days rather than out of this
        // test's idea of how long twelve months is.
        app.daysPass(ChronoUnit.DAYS.between(theYearAhead.from(), theYearAhead.until()));
        assertThat(app.theDateTheClockReads()).isEqualTo(theYearAhead.until());

        app.runJob(THE_BILLS_JOB);
        app.runJob(THE_BILLS_JOB);

        List<LocalDate> presented = Stream.concat(
                        app.historyOfBill(theCustomer, subscription.billId()).stream(),
                        app.historyOfBill(theCustomer, phone.billId()).stream())
                .map(BillOccurrenceView::dueOn)
                .sorted()
                .toList();

        assertThat(presented)
                .as("the dates the run actually presented are the dates the forecast showed — the "
                        + "same days, the same number of them, and the second run of the job added "
                        + "none of them a second time")
                .isEqualTo(forecast);
        assertThat(Stream.concat(
                        app.historyOfBill(theCustomer, subscription.billId()).stream(),
                        app.historyOfBill(theCustomer, phone.billId()).stream()))
                .as("and the account covered every one of them, so nothing is owed and the balance "
                        + "below is a second reading of the same claim")
                .allSatisfy(taken -> assertThat(taken.outcome()).isEqualTo("PAID"));
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("the money that left is the money the forecast said would leave")
                .isEqualByComparingTo(held.subtract(forecastTotal));
        assertThat(app.previewOn(savingsAccount).bills())
                .as("and the year drawn from the far edge is a fresh twelve months rather than the "
                        + "one just settled: derived on every read, stored nowhere")
                .allSatisfy(date -> assertThat(date.dueOn()).isAfter(theYearAhead.until()));
    }
}
