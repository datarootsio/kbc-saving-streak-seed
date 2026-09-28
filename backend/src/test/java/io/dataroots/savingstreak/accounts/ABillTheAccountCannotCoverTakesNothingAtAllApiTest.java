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
 * A bill due against a balance that cannot cover it is recorded unpaid, takes nothing at all, and
 * leaves the balance exactly where it was.
 *
 * <p>User stories 15 and 16. All-or-nothing is the whole of it: a bill of nine hundred against a
 * balance of six hundred takes nothing and leaves six hundred, because a half-paid rent is neither a
 * lesson nor a thing that happens — and because a balance that could go below zero would teach a
 * customer that overdrafts are free, which is the opposite of what this application exists to teach.
 *
 * <p><strong>Short by one cent, deliberately.</strong> It is the case most likely to be got wrong:
 * an implementation that took what it could would take all but a cent here and the balance would
 * look very nearly right, while an implementation that refused anything it could not cover in full
 * leaves the balance untouched to the cent. A bill twice the size of the account would be satisfied
 * by both.
 *
 * <p><strong>A customer of this test's own, rather than a seeded one.</strong> It used to drive
 * Bram, because his current account was the shallower of the seeded two. That stopped being safe
 * once the seed gained declared bills of its own: this test winds to the next twentieth, and the
 * single catch-up run that follows also takes every seeded due date the wind passed over, so the
 * balance moved for reasons that have nothing to do with the bill under test and the assertion
 * failed on every day of the month except the 12th to the 19th. A customer nobody else has lived
 * in owes nothing, so the only thing the run can find is the bill this test declared. The figure is
 * still read off the account rather than written down.
 *
 * <p>What becomes of the date afterwards — that it stays owed and is presented again — is the next
 * slice's promise. What is asserted here is that it was recorded, that it names the whole amount,
 * and that no money moved.
 */
class ABillTheAccountCannotCoverTakesNothingAtAllApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "takeBillsDue";
    private static final int THE_TWENTIETH = 20;

    private static AnApplicationWithAClockToMove app;

    /** Nobody else's bills, so the catch-up run finds only the one this test declares. */
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-bill-that-cannot-be-paid"));
        theCustomer = app.aCustomerOfItsOwn("a bill that cannot be paid");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_bill_one_cent_more_than_the_balance_takes_nothing_and_the_balance_never_goes_negative() {
        BigDecimal balance = app.currentAccountBalanceOf(theCustomer);
        BigDecimal oneCentMoreThanHeHas = balance.add(new BigDecimal("0.01"));

        RecurringBillView rent = app.declareABillFor(theCustomer, "Rent", THE_TWENTIETH,
                oneCentMoreThanHeHas.toPlainString());
        windTo(theNextTwentiethAfter(app.theDateTheClockReads()));

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("nothing at all was taken: not the balance, not all but a cent of it, nothing")
                .isEqualByComparingTo(balance);
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("and a balance never goes below zero, because an application that let one "
                        + "would be teaching that overdrafts are free")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);

        assertThat(app.historyOfBill(theCustomer, rent.billId()))
                .singleElement()
                .satisfies(presented -> {
                    assertThat(presented.dueOn())
                            .isEqualTo(app.theDateTheClockReads());
                    assertThat(presented.outcome())
                            .as("the date was presented and refused, which is a fact worth having: "
                                    + "a balance that did not move says nothing about why")
                            .isEqualTo("UNPAID");
                    assertThat(presented.amount())
                            .as("the whole rent, because that is what is still owed — a nought "
                                    + "here would lose the one figure the customer needs")
                            .isEqualByComparingTo(oneCentMoreThanHeHas);
                });

        assertThat(theBill(rent.billId()).lastTakenOn())
                .as("a date that was presented and refused is not a date the rent was taken, and "
                        + "the page must not say it was")
                .isNull();
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
     * refuses a move of no days at all.
     */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days)
                .as("this test has to reach " + day + " and the clock cannot be wound backwards")
                .isPositive();
        app.daysPass(days);
    }

    /** The first twentieth that begins after the given day, clamped the way the application clamps. */
    private static LocalDate theNextTwentiethAfter(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonths = month.atDay(Math.min(THE_TWENTIETH, month.lengthOfMonth()));
        if (thisMonths.isAfter(today)) {
            return thisMonths;
        }
        YearMonth next = month.plusMonths(1);
        return next.atDay(Math.min(THE_TWENTIETH, next.lengthOfMonth()));
    }
}
