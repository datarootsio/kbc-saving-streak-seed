package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BillToComeView;
import io.dataroots.savingstreak.support.MoneyMovementView;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bill somebody ends disappears from the forecast from the day they end it, and every month it was
 * actually taken for stays exactly where it was.
 *
 * <p>User stories 6 and 8. Ending a bill is a closing rather than a deletion, and the two halves of
 * that are the whole of this test: nothing is promised for a bill nobody pays any more, and nothing
 * is erased about the months they did. An application that took the dates out of the forecast and
 * the debits out of the history would be rewriting the customer's own past, and an application that
 * left the dates in would be forecasting money nobody is going to spend.
 *
 * <p>The year-ahead bar is where this is read, because that is the forecast an ended bill could
 * linger in for eleven more months — a month-ahead figure would say the same thing about one date.
 *
 * <p>An application whose clock this test winds, because the claim is about a bill that has been
 * taken at least once: a bill ended before it was ever presented proves only that nothing was
 * promised, which is the easy half.
 */
class AnEndedBillLeavesTheForecastWhileItsPastMovementsStayApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";

    /** The day of the month this test stands on before the bill's own day arrives. */
    private static final int THE_THIRD = 3;
    private static final int THE_FIFTH = 5;
    private static final String THE_GYM = "20.00";

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationStandingJustBeforeTheBillsDay() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-ended-bill-leaves-the-forecast"));
        theCustomer = app.aCustomerOfItsOwn("an ended bill leaves the forecast");
        LocalDate today = app.theDateTheClockReads();
        LocalDate theThird = today.withDayOfMonth(1).plusMonths(1).plusDays(THE_THIRD - 1);
        app.daysPass(ChronoUnit.DAYS.between(today, theThird));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_dates_ahead_of_it_go_and_the_month_it_was_taken_for_stays() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        BigDecimal held = app.currentAccountBalanceOf(theCustomer);
        RecurringBillView gym = app.declareABillFor(theCustomer, "Gym", THE_FIFTH, THE_GYM);

        assertThat(app.previewOn(savingsAccount).bills())
                .as("a standing bill is on the year ahead on every date the shared calendar says it "
                        + "falls on, which over twelve months is twelve of them")
                .hasSizeGreaterThanOrEqualTo(12)
                .allSatisfy(date -> {
                    assertThat(date.billId()).isEqualTo(gym.billId());
                    assertThat(date.billName()).isEqualTo("Gym");
                    assertThat(date.dueOn().getDayOfMonth()).isEqualTo(THE_FIFTH);
                    assertThat(date.amount()).isEqualByComparingTo(new BigDecimal(THE_GYM));
                });

        app.daysPass(2);
        LocalDate theDayItWasTaken = app.theDateTheClockReads();
        app.runJob(THE_BILLS_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("the day arrived and the money left, which is what makes the second half of "
                        + "this test about a month that really happened")
                .isEqualByComparingTo(held.subtract(new BigDecimal(THE_GYM)));

        app.endTheBill(theCustomer, gym.billId());

        assertThat(app.previewOn(savingsAccount).bills())
                .as("ended, so nothing is promised for it again — not next month and not in eleven "
                        + "months' time")
                .isEmpty();
        assertThat(app.theCurrentAccountOf(theCustomer).monthAhead().billsDue())
                .as("and the month ahead has nothing left to cover either")
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(app.historyOfBill(theCustomer, gym.billId()))
                .as("while the month it was taken for is still explained, which is the whole reason "
                        + "ending is a closing rather than a deletion")
                .singleElement()
                .satisfies(taken -> {
                    assertThat(taken.dueOn()).isEqualTo(theDayItWasTaken);
                    assertThat(taken.outcome()).isEqualTo("PAID");
                    assertThat(taken.amount()).isEqualByComparingTo(new BigDecimal(THE_GYM));
                });

        List<MoneyMovementView> ledger = List.of(app.moneyMovementsOf(theCustomer));
        assertThat(ledger)
                .as("and the euros that left are still in the one list that says where the money "
                        + "went, under the name the customer knew the bill by")
                .anySatisfy(moved -> {
                    assertThat(moved.billName()).isEqualTo("Gym");
                    assertThat(moved.dueOn()).isEqualTo(theDayItWasTaken);
                    assertThat(moved.outcome()).isEqualTo("PAID");
                    assertThat(moved.amount()).isEqualByComparingTo(new BigDecimal(THE_GYM));
                });
        assertThat(app.previewOn(savingsAccount).bills())
                .as("the forecast and the ledger are different questions: nothing that has already "
                        + "happened is in the first, and nothing that has not is in the second")
                .extracting(BillToComeView::billId)
                .doesNotContain(gym.billId());
    }
}
