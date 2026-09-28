package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bill ended before its day arrives is not taken.
 *
 * <p>User story 6. Ending a bill is the customer saying they no longer pay it, and the only way to
 * see that it took is that the money stops leaving: a list it disappeared from while the nightly run
 * kept debiting it would be the page and the application disagreeing about what the customer
 * decided.
 *
 * <p>The other half of the same story is asserted with it — that ending erases nothing. The bill is
 * still readable among the ended ones and its history is still answered, because the whole point of
 * ending rather than deleting is that the months it <em>was</em> taken for stay explained.
 *
 * <p>A second bill is left standing beside it, and it is the assertion that matters most here: a run
 * that had quietly done nothing at all would satisfy every claim about the ended one. The standing
 * bill going out on the same morning is what says the run happened.
 */
class ABillEndedBeforeItsDayArrivesIsNotTakenApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "takeBillsDue";
    private static final String THE_GYM = "40.00";
    private static final String THE_INTERNET = "30.00";
    private static final int THE_EIGHTH = 8;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-ended-bill-is-not-taken"));
        theCustomer = app.aCustomerOfItsOwn("an ended bill is not taken");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_bill_ended_before_its_day_takes_nothing_while_the_one_beside_it_still_goes_out() {
        RecurringBillView gym = app.declareABillFor(theCustomer, "Gym", THE_EIGHTH, THE_GYM);
        RecurringBillView internet = app.declareABillFor(theCustomer, "Internet", THE_EIGHTH, THE_INTERNET);
        BigDecimal before = app.currentAccountBalanceOf(theCustomer);

        RecurringBillView ended = app.endTheBill(theCustomer, gym.billId());
        assertThat(ended.state())
                .as("a bill this test did not actually end would leave every assertion below "
                        + "about a bill that never stopped standing")
                .isEqualTo("ENDED");

        LocalDate theDayBothFallDue = theNextEighthAfter(app.theDateTheClockReads());
        windTo(theDayBothFallDue);
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("the internet went out and the gym did not, so the balance fell by one of the "
                        + "two and not by both")
                .isEqualByComparingTo(before.subtract(new BigDecimal(THE_INTERNET)));

        assertThat(app.historyOfBill(theCustomer, gym.billId()))
                .as("an ended bill is a record rather than an instruction, and the night has "
                        + "nothing to present for it")
                .isEmpty();
        assertThat(app.historyOfBill(theCustomer, internet.billId()))
                .as("while the bill beside it went out that same morning, which is what says the "
                        + "run happened at all")
                .singleElement()
                .satisfies(taken -> {
                    assertThat(taken.dueOn()).isEqualTo(theDayBothFallDue);
                    assertThat(taken.outcome()).isEqualTo("PAID");
                });

        assertThat(app.billsOf(theCustomer).stream().map(RecurringBillView::billId))
                .as("and the ended bill has left the list of what is about to go out")
                .doesNotContain(gym.billId())
                .contains(internet.billId());
    }

    /**
     * Winds the clock to that day, insisting on a move forwards: the clock only goes one way and
     * refuses a move of no days at all.
     */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days)
                .as("this test has to reach " + day + " and the clock cannot be wound backwards")
                .isPositive();
        app.daysPass(days);
    }

    /** The first eighth that begins after the given day. */
    private static LocalDate theNextEighthAfter(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonths = month.atDay(THE_EIGHTH);
        return thisMonths.isAfter(today) ? thisMonths : month.plusMonths(1).atDay(THE_EIGHTH);
    }
}
