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
 * A bill whose day has arrived is taken overnight, the balance falls by exactly its amount, and
 * running the job a second time over the same date takes nothing more.
 *
 * <p>User stories 10 and 14. The first half is the feature existing at all: a declared bill that
 * sits and waits is a sentence about money that never becomes money, and the balance a customer
 * decides to save from is only honest once what they said leaves the account actually leaves it.
 *
 * <p>The second half is what makes the demonstration safe to give. A trainer runs the nightly job by
 * hand — that is the whole point of the jobs endpoint — and pressing it twice is a thing people do.
 * A run that took the rent twice would halve the customer's balance for no reason anybody could
 * explain, so the record is unique over the bill and the date due and the run asks it before
 * presenting anything.
 *
 * <p>The day before is asserted as well as the day itself. Either claim alone would be satisfied by
 * a job that took the rent on whatever day it happened to be run, which is the defect most easily
 * written and the hardest to see in a passing test.
 */
class ABillIsTakenOnTheDayItFallsDueAndOnlyOnceApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "takeBillsDue";
    private static final String THE_RENT = "900.00";
    private static final int THE_FIFTEENTH = 15;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-bill-is-taken"));
        theCustomer = app.aCustomerOfItsOwn("a bill is taken");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_rent_leaves_on_its_day_and_leaves_once_however_often_the_job_runs() {
        RecurringBillView rent = app.declareABillFor(theCustomer, "Rent", THE_FIFTEENTH, THE_RENT);
        assertThat(rent.lastTakenOn())
                .as("a bill declared this minute has never been taken, and a date here would be "
                        + "the page claiming a debit that never happened")
                .isNull();

        LocalDate theDayItFallsDue = theNextFifteenthAfter(app.theDateTheClockReads());
        BigDecimal before = app.currentAccountBalanceOf(theCustomer);

        windTo(theDayItFallsDue.minusDays(1));
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("the fourteenth is not the fifteenth, and a bill taken early is a debit on a "
                        + "day its holder never named")
                .isEqualByComparingTo(before);
        assertThat(app.historyOfBill(theCustomer, rent.billId()))
                .as("nothing has fallen due, so there is nothing to have a record of")
                .isEmpty();

        app.daysPass(1);
        assertThat(app.theDateTheClockReads()).isEqualTo(theDayItFallsDue);
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("the balance falls by exactly what was declared — not a rounded figure, not a "
                        + "part of one")
                .isEqualByComparingTo(before.subtract(new BigDecimal(THE_RENT)));

        List<BillOccurrenceView> history = app.historyOfBill(theCustomer, rent.billId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).dueOn()).isEqualTo(theDayItFallsDue);
        assertThat(history.get(0).outcome()).isEqualTo("PAID");
        assertThat(history.get(0).amount()).isEqualByComparingTo(new BigDecimal(THE_RENT));
        assertThat(history.get(0).daysLate())
                .as("taken on the morning it was owed, which is what nought days late means")
                .isZero();

        assertThat(theBill(rent.billId()).lastTakenOn())
                .as("and the page can say when the rent last went out, which a balance alone can "
                        + "never tell anybody")
                .isEqualTo(theDayItFallsDue);

        BigDecimal afterTheFirstRun = app.currentAccountBalanceOf(theCustomer);
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("a trainer pressing the button twice is not a second rent; the record is "
                        + "unique over the bill and the date due")
                .isEqualByComparingTo(afterTheFirstRun);
        assertThat(app.historyOfBill(theCustomer, rent.billId()))
                .as("and the history says the date was settled once rather than twice")
                .hasSize(1);
    }

    /** The bill as the account's own read reports it, which is the list the page draws. */
    private static RecurringBillView theBill(long billId) {
        return app.billsOf(theCustomer).stream()
                .filter(bill -> bill.billId() == billId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the bill this test declared is not on the "
                        + "account it was declared against"));
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
     * The first fifteenth that begins after the given day. Written out as the clamp the application
     * makes rather than as a plain {@code withDayOfMonth}, so that a later reader changing the day
     * above does not quietly get an answer the application disagrees with.
     */
    private static LocalDate theNextFifteenthAfter(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonths = month.atDay(Math.min(THE_FIFTEENTH, month.lengthOfMonth()));
        if (thisMonths.isAfter(today)) {
            return thisMonths;
        }
        YearMonth next = month.plusMonths(1);
        return next.atDay(Math.min(THE_FIFTEENTH, next.lengthOfMonth()));
    }
}
