package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MonthAheadView;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A current account says what the month ahead has to cover: what is due in, what is due out, and
 * what that leaves against the balance it holds now — with what is already owed counted in.
 *
 * <p>User stories 33 and 21, and the figure this whole feature exists to put in front of somebody.
 * A balance on its own says "you have 2480 euros"; these four figures say "you have 2480 euros and
 * 1165 of it is spoken for", which is the sentence a customer needs <em>before</em> they decide how
 * much to sweep into savings rather than after. With nothing else wanting the money there is no
 * claim, no judgement and no lesson.
 *
 * <p><strong>The arithmetic is asserted directly.</strong> The claim is not that four plausible
 * numbers appear but that they are one sentence: the balance plus what is due in minus what is due
 * out is the figure shown. A page reading three of them and inventing the fourth is exactly the
 * defect this asserts against.
 *
 * <p><strong>And a shortfall is allowed to be one.</strong> A balance never goes below nought — the
 * run refuses rather than overdrawing — but a month can plainly cost more than there is, and
 * rounding that up to nought would hide exactly the month a customer needs to be warned about.
 *
 * <p>An application of its own whose clock this test winds, because the window is a month counted
 * from today and the figures depend on which day of the month that is. Wound to the third, so that
 * a bill on the first falls next month inside the window, one on the fifth, the twelfth and the
 * twentieth fall this month, and a salary on the twenty-fifth lands once — each of them exactly
 * once, which is the shape of window this read promises.
 */
class TheMonthAheadSaysWhatThisMonthHasToCoverApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";

    /** The day of the month this test stands on, chosen so every figure below falls in the window. */
    private static final int THE_THIRD = 3;

    private static final String THE_SALARY = "2600.00";
    private static final int PAYDAY = 25;
    private static final String THE_RENT = "950.00";
    private static final String THE_ENERGY = "120.00";
    private static final String THE_PHONE = "35.00";
    private static final String THE_INSURANCE = "60.00";

    /** What the four standing bills come to over one month, which is the figure the page shows. */
    private static final BigDecimal EVERY_BILL = new BigDecimal("1165.00");

    /** More than a new account holds, so that presenting it leaves an arrear rather than a debit. */
    private static final String A_BOILER_NOBODY_CAN_AFFORD = "4000.00";

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationStandingOnTheThirdOfAMonth() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-month-ahead"));
        theCustomer = app.aCustomerOfItsOwn("the month ahead");
        windToTheThirdOfTheNextMonth();
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void it_names_what_is_due_in_what_is_due_out_and_what_that_leaves_arrears_included() {
        BigDecimal held = app.currentAccountBalanceOf(theCustomer);
        app.declareIncomeFor(theCustomer, PAYDAY, THE_SALARY);
        app.declareABillFor(theCustomer, "Rent", 1, THE_RENT);
        app.declareABillFor(theCustomer, "Energy", 5, THE_ENERGY);
        app.declareABillFor(theCustomer, "Phone", 12, THE_PHONE);
        app.declareABillFor(theCustomer, "Insurance", 20, THE_INSURANCE);

        MonthAheadView month = app.theCurrentAccountOf(theCustomer).monthAhead();

        assertThat(month.from())
                .as("the window opens on the day the application's clock reads, never on the day "
                        + "whichever machine drew the screen thinks it is")
                .isEqualTo(app.theDateTheClockReads());
        assertThat(month.until())
                .as("and closes the day before this day next month, so that every monthly claim "
                        + "falls inside it exactly once — a window running to the same date next "
                        + "month would count a rent on today's day of the month twice")
                .isEqualTo(month.from().plusMonths(1).minusDays(1));
        assertThat(month.balance())
                .as("the balance in the account now, which is the figure the whole card is about")
                .isEqualByComparingTo(held);
        assertThat(month.incomeDue())
                .as("one salary lands inside the window, on the twenty-fifth")
                .isEqualByComparingTo(new BigDecimal(THE_SALARY));
        assertThat(month.billsDue())
                .as("the rent next month and this month's energy, phone and insurance — each of the "
                        + "four exactly once")
                .isEqualByComparingTo(EVERY_BILL);
        assertThat(month.arrearsOutstanding())
                .as("nothing has gone unpaid yet")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThatTheFiguresAddUp(month);

        // The bill nobody can afford, so that the next run leaves an arrear behind rather than a
        // debit. Declared on the fourth, which is tomorrow, so that it is the only thing the run has
        // to present and the account it meets is the one this test put the money in.
        RecurringBillView boiler =
                app.declareABillFor(theCustomer, "Boiler", 4, A_BOILER_NOBODY_CAN_AFFORD);

        assertThat(app.theCurrentAccountOf(theCustomer).monthAhead())
                .as("declared and immediately counted: the forecast is derived on every read and "
                        + "stored nowhere, so there is nothing to invalidate")
                .satisfies(withTheBoiler -> {
                    assertThat(withTheBoiler.billsDue())
                            .isEqualByComparingTo(
                                    EVERY_BILL.add(new BigDecimal(A_BOILER_NOBODY_CAN_AFFORD)));
                    assertThat(withTheBoiler.leavesYou())
                            .as("and the month now asks for more than the account will hold, which "
                                    + "is said as a figure below nothing rather than rounded up to "
                                    + "nought — it is exactly the month worth being warned about")
                            .isNegative();
                    assertThatTheFiguresAddUp(withTheBoiler);
                });

        app.daysPass(1);
        app.runJob(THE_BILLS_JOB);

        assertThat(app.historyOfBill(theCustomer, boiler.billId()))
                .singleElement()
                .satisfies(presented -> assertThat(presented.outcome()).isEqualTo("UNPAID"));
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("all or nothing: a bill the account cannot cover takes nothing at all")
                .isEqualByComparingTo(held);

        MonthAheadView carryingTheArrear = app.theCurrentAccountOf(theCustomer).monthAhead();

        assertThat(carryingTheArrear.arrearsOutstanding())
                .as("what went unpaid is still owed, and it is a claim on this balance exactly as a "
                        + "standing bill is")
                .isEqualByComparingTo(new BigDecimal(A_BOILER_NOBODY_CAN_AFFORD));
        assertThat(carryingTheArrear.billsDue())
                .as("so the month has to cover it as well as the four bills still to fall — the "
                        + "boiler's own next date is past the end of this window, and a figure "
                        + "quoting only what is still to fall would tell somebody carrying a debt "
                        + "that they had room they do not have")
                .isEqualByComparingTo(EVERY_BILL.add(new BigDecimal(A_BOILER_NOBODY_CAN_AFFORD)));
        assertThatTheFiguresAddUp(carryingTheArrear);
    }

    /**
     * The one claim that makes the card readable: the four figures are one sentence rather than four
     * plausible numbers. Asserted after every read, because a subtraction that is right once and
     * wrong the moment an arrear appears is the defect this is here to catch.
     */
    private static void assertThatTheFiguresAddUp(MonthAheadView month) {
        assertThat(month.leavesYou())
                .as("what the month leaves is the balance plus what is due in minus what is due "
                        + "out, to the cent: " + month)
                .isEqualByComparingTo(
                        month.balance().add(month.incomeDue()).subtract(month.billsDue()));
    }

    /**
     * Winds to the third of the next month, which is always a move forwards however far into a month
     * the run happens to start — the clock only goes one way and refuses a move of no days at all.
     */
    private static void windToTheThirdOfTheNextMonth() {
        LocalDate today = app.theDateTheClockReads();
        LocalDate theThird = today.withDayOfMonth(1).plusMonths(1).plusDays(THE_THIRD - 1);
        app.daysPass(ChronoUnit.DAYS.between(today, theThird));
        assertThat(app.theDateTheClockReads().getDayOfMonth()).isEqualTo(THE_THIRD);
    }
}
