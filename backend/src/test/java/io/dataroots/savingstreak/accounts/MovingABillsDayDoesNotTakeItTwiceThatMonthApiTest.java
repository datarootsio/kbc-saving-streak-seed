package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BillOccurrenceView;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer who corrects the day a bill goes out on is charged once that month, whichever way the
 * day moved.
 *
 * <p>The outbound mirror of {@link ChangingAPaydayDoesNotPayASecondSalaryThatMonthApiTest}, and the
 * more expensive of the two to get wrong: a salary paid twice is a gift, a rent taken twice is money
 * out of a balance the customer is about to decide how much to save from. Correcting the day is a
 * correction, not a second rent — but a month is not a day, and the two are easy to confuse. A bill
 * taken on the 10th of March whose holder then says their landlord takes it on the 20th has a 20th
 * of March that no cursor sitting at the 11th excludes and no row under that day denies; before this
 * test existed the nightly run debited it, and the customer paid twice in March for having told the
 * application the truth. The rule is one settlement a calendar month, and it is kept by asking the
 * record which <em>months</em> a bill has been presented for rather than which days.
 *
 * <p><strong>Both directions, and that is the whole point of the class.</strong> Moving a bill
 * earlier was already safe — the new day falls behind a cursor that has passed the old one — and
 * that is exactly why the defect survived a suite and a walk over the API: every test happened to
 * move the day the safe way, or not at all. A test that proves only the direction that worked is the
 * test that lets this through.
 *
 * <p>The month after is asserted on both, because "never taken again" and "taken once a month" look
 * identical for as long as a test stops at the correction. A guard that simply refused every date in
 * a month the bill had ever been settled in would pass the first half and quietly cost the customer
 * their next month's electricity.
 *
 * <p>Two customers rather than one, so that neither direction depends on the order JUnit runs the
 * two methods in, and each method winds the clock to the first of a fresh month before it starts — a
 * debit demonstrated inside one named month is the only way to say "twice in one month" at all.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test winds
 * the clock, and the file the rest of the run shares is not one to leave wound forward.
 */
class MovingABillsDayDoesNotTakeItTwiceThatMonthApiTest extends ApiIntegrationTest {

    /** The name a trainer types into the development jobs endpoint. */
    private static final String THE_JOB = "takeBillsDue";

    /** Small enough that a seeded current account covers several of them without ever falling short:
     *  this class is about being charged twice, and a balance that ran out would hide that. */
    private static final String THE_BILL = "10.00";

    /** Early in the month, and a day every month has. */
    private static final int THE_TENTH = 10;

    /** Late in the month, and a day every month has — so this test is not about February's clamp. */
    private static final int THE_TWENTIETH = 20;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;
    private static String somebodyElse;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-moving-a-bills-day"));
        theCustomer = app.aCustomerOfItsOwn("moving a bills day");
        somebodyElse = app.aCustomerOfItsOwn("moving a bills day somebody else");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void moving_a_bill_later_into_a_month_it_has_already_been_taken_in_does_not_take_it_twice() {
        LocalDate theFirstOfTheMonth = theClockWoundToTheFirstOfANewMonth();
        RecurringBillView bill = app.declareABillFor(theCustomer, "Rent", THE_TENTH, THE_BILL);
        BigDecimal beforeAnyDebit = app.currentAccountBalanceOf(theCustomer);

        windTo(theFirstOfTheMonth.withDayOfMonth(THE_TENTH + 1));
        app.runJob(THE_JOB);
        BigDecimal afterTheTenth = app.currentAccountBalanceOf(theCustomer);
        assertThat(afterTheTenth)
                .as("the debit this test is about to make sure does not happen a second time")
                .isEqualByComparingTo(beforeAnyDebit.subtract(new BigDecimal(THE_BILL)));

        // The correction, made on the 11th: the landlord takes it later in the month than the
        // customer said, in a month whose rent has already left the account.
        RecurringBillView corrected = app.changeTheBill(theCustomer, bill.billId(),
                Map.of("dayOfMonth", String.valueOf(THE_TWENTIETH)));
        assertThat(corrected.dayOfMonth())
                .as("the correction has to have been made, or this test is watching an unchanged "
                        + "bill and proving nothing at all")
                .isEqualTo(THE_TWENTIETH);

        windTo(theFirstOfTheMonth.withDayOfMonth(THE_TWENTIETH + 1));
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("correcting the day a bill goes out on is not a second rent: the 10th and the "
                        + "20th are two different days and one single March")
                .isEqualByComparingTo(afterTheTenth);
        assertThat(app.historyOfBill(theCustomer, bill.billId()))
                .as("and the record says the month was settled once rather than twice")
                .hasSize(1);

        windTo(theFirstOfTheMonth.plusMonths(1).withDayOfMonth(THE_TWENTIETH + 1));
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("and the correction does take: next month's rent leaves on the new day, once, "
                        + "because a month skipped is not a bill that stops being owed")
                .isEqualByComparingTo(afterTheTenth.subtract(new BigDecimal(THE_BILL)));

        List<BillOccurrenceView> history = app.historyOfBill(theCustomer, bill.billId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).dueOn())
                .as("newest first, and the newest is the new day in the new month")
                .isEqualTo(theFirstOfTheMonth.plusMonths(1).withDayOfMonth(THE_TWENTIETH));
        assertThat(history.get(0).outcome()).isEqualTo("PAID");
        assertThat(history.get(1).dueOn())
                .as("and the month that was corrected keeps the date it was actually taken on")
                .isEqualTo(theFirstOfTheMonth.withDayOfMonth(THE_TENTH));
    }

    @Test
    void moving_a_bill_earlier_into_a_month_it_has_already_been_taken_in_does_not_take_it_twice() {
        LocalDate theFirstOfTheMonth = theClockWoundToTheFirstOfANewMonth();
        RecurringBillView bill = app.declareABillFor(somebodyElse, "Energy", THE_TWENTIETH, THE_BILL);
        BigDecimal beforeAnyDebit = app.currentAccountBalanceOf(somebodyElse);

        windTo(theFirstOfTheMonth.withDayOfMonth(THE_TWENTIETH + 1));
        app.runJob(THE_JOB);
        BigDecimal afterTheTwentieth = app.currentAccountBalanceOf(somebodyElse);
        assertThat(afterTheTwentieth)
                .as("the debit this test is about to make sure does not happen a second time")
                .isEqualByComparingTo(beforeAnyDebit.subtract(new BigDecimal(THE_BILL)));

        // The mirror correction, made on the 21st: earlier in the month than the customer said, in a
        // month the bill has already been taken in.
        RecurringBillView corrected = app.changeTheBill(somebodyElse, bill.billId(),
                Map.of("dayOfMonth", String.valueOf(THE_TENTH)));
        assertThat(corrected.dayOfMonth()).isEqualTo(THE_TENTH);

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(somebodyElse))
                .as("the 10th of this month is behind the money that already left on the 20th, and "
                        + "a correction never reaches backwards for a month already settled")
                .isEqualByComparingTo(afterTheTwentieth);
        assertThat(app.historyOfBill(somebodyElse, bill.billId())).hasSize(1);

        windTo(theFirstOfTheMonth.plusMonths(1).withDayOfMonth(THE_TENTH + 1));
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(somebodyElse))
                .as("and next month's bill leaves on the new day, once")
                .isEqualByComparingTo(afterTheTwentieth.subtract(new BigDecimal(THE_BILL)));
        assertThat(app.historyOfBill(somebodyElse, bill.billId()).get(0).dueOn())
                .isEqualTo(theFirstOfTheMonth.plusMonths(1).withDayOfMonth(THE_TENTH));
    }

    /**
     * Winds the clock to the first of the month after the one it is reading, and says which day that
     * is.
     *
     * <p>Both tests in this class start here, so that each one owns a whole named month whatever the
     * day the run happens on and whatever the other test left the clock reading. A claim about being
     * charged twice "in one month" cannot be made from a stretch of days that straddles two.
     */
    private static LocalDate theClockWoundToTheFirstOfANewMonth() {
        LocalDate theFirst = app.theDateTheClockReads().plusMonths(1).withDayOfMonth(1);
        windTo(theFirst);
        return theFirst;
    }

    /**
     * The clock moved on to a named day, through the endpoint a trainer would use, insisting on a
     * move forwards: the clock only goes one way, so a test that has already passed the day it wants
     * is a test asserting about the wrong month.
     */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days)
                .as("this test has to reach " + day + " and the clock cannot be wound backwards")
                .isPositive();
        app.daysPass(days);
        assertThat(app.theDateTheClockReads())
                .as("a test that asserts about a due date has to be standing on the day it means")
                .isEqualTo(day);
    }
}
