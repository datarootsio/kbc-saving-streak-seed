package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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
 * Two rents owed and enough money for exactly one: the older one is paid, the newer one stays owed,
 * and the record of the older one is the row that was already there.
 *
 * <p>User stories 18 and 19, and the case most likely to be got wrong. An implementation that walked
 * the arrears in whichever order the database handed them over would clear April's rent here and
 * pass every assertion about a balance; what is asserted instead is <em>which</em> of two identical
 * nine-hundred-euro debts moved, which is the whole promise.
 *
 * <p><strong>Two rents and the money for one, deliberately.</strong> Exactly nine hundred euros is
 * moved back from savings — not eighteen hundred, and not nine hundred and one. An application that
 * paid something towards both would leave a balance of nought and two half-settled rents, and a
 * balance assertion alone could not tell that apart from this. So the outcome of each date is
 * asserted as well as the money.
 *
 * <p><strong>The settled arrear updates the row that was already there.</strong> The outcome becomes
 * paid and the settled-at moment moves to when the money was actually taken, while the due date
 * stays the day it was owed from — so the bill's history still has one row per due date and still
 * says the rent fell due in the month it fell due in. A second row under the settlement's own day
 * would be the history rewriting when something was owed, which is the one thing a record must not
 * do.
 *
 * <p><strong>And nothing was added to it.</strong> The amount taken is the amount the row was
 * written with a month earlier, to the cent: no interest, no fee, no charge of any kind. The
 * accumulation is the whole of the consequence.
 *
 * <p>One test, because the clock only goes forward and the narrative is a sequence of months.
 */
class MoneyArrivingGoesToTheOldestArrearFirstApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final String THE_SAVING_RULES_JOB = "fireSavingRulesDue";
    private static final String THE_RENT = "900.00";
    private static final int THE_TWELFTH = 12;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-oldest-arrear-first"));
        theCustomer = app.aCustomerOfItsOwn("the oldest arrear first");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void enough_for_one_of_two_rents_pays_the_older_one_and_leaves_the_newer_one_owed() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        RecurringBillView rent = app.declareABillFor(theCustomer, "Rent", THE_TWELFTH, THE_RENT);
        app.leaveARuleStanding(savingsAccount,
                aWeeklySweepDownToNothingFrom(app.currentAccountOf(theCustomer)));

        LocalDate theOlderRentsDay = theNextTwelfthAfter(app.theDateTheClockReads());
        windTo(theOlderRentsDay);
        app.runJob(THE_SAVING_RULES_JOB);
        app.runJob(THE_BILLS_JOB);

        LocalDate theNewerRentsDay = theNextTwelfthAfter(app.theDateTheClockReads());
        windTo(theNewerRentsDay);
        app.runJob(THE_BILLS_JOB);

        assertThat(app.arrearsOf(theCustomer))
                .as("two rents owed before a cent comes back, which is the situation this test is "
                        + "about")
                .hasSize(2);

        // Exactly one rent, moved back out of savings by hand. The way out of arrears is the way
        // this application already had.
        app.withdraw(savingsAccount, theCustomer, THE_RENT);
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("enough for one of the two, and not a cent more")
                .isEqualByComparingTo(new BigDecimal(THE_RENT));

        app.runJob(THE_BILLS_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("one whole rent left the account and nothing else did: not a fee, not a part "
                        + "of the second rent, nothing")
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(app.arrearsOf(theCustomer))
                .as("the debt carried longest is the one that cleared, and the newer rent is still "
                        + "owed — a customer who scrapes together nine hundred euros pays the "
                        + "older rent, not the convenient one")
                .singleElement()
                .satisfies(stillOwed -> {
                    assertThat(stillOwed.dueOn()).isEqualTo(theNewerRentsDay);
                    assertThat(stillOwed.amount())
                            .isEqualByComparingTo(new BigDecimal(THE_RENT));
                });

        assertThat(app.historyOfBill(theCustomer, rent.billId()))
                .as("still one row per due date: the arrear was settled in place rather than "
                        + "written down a second time")
                .hasSize(2);

        BillOccurrenceView theOlderRent = thatDate(rent.billId(), theOlderRentsDay);
        assertThat(theOlderRent.outcome())
                .as("the record that was already there now says it was paid")
                .isEqualTo("PAID");
        assertThat(theOlderRent.dueOn())
                .as("and still says the day it was owed from, because the history does not rewrite "
                        + "when something was owed")
                .isEqualTo(theOlderRentsDay);
        assertThat(theOlderRent.amount())
                .as("for exactly what it asked for a month ago: no interest, no fee, no charge of "
                        + "any kind is ever added to an arrear")
                .isEqualByComparingTo(new BigDecimal(THE_RENT));
        assertThat(theOlderRent.daysLate())
                .as("and the moment it was settled at moved to when the money was actually taken, "
                        + "which is what makes the lateness a real figure")
                .isEqualTo(ChronoUnit.DAYS.between(theOlderRentsDay, theNewerRentsDay));

        assertThat(thatDate(rent.billId(), theNewerRentsDay).outcome())
                .as("and the newer rent was not touched: all-or-nothing means one rent was paid "
                        + "and not one and a half")
                .isEqualTo("UNPAID");

        assertThat(theBill(rent.billId()).lastTakenOn())
                .as("the rent was last taken for the month it was owed for, not for the month it "
                        + "was finally paid in")
                .isEqualTo(theOlderRentsDay);
    }

    /** That bill's row for that due date, insisted on: there is exactly one and this test names it. */
    private static BillOccurrenceView thatDate(long billId, LocalDate dueOn) {
        return app.historyOfBill(theCustomer, billId).stream()
                .filter(presented -> presented.dueOn().equals(dueOn))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "this bill has no record at all of the date " + dueOn));
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
     * A weekly sweep taking everything above nothing, as a customer's form would send it — the
     * sharpest version of "I saved too hard", and the reason there is anything in savings to bring
     * back later.
     */
    private static Map<String, Object> aWeeklySweepDownToNothingFrom(long fromCurrentAccountId) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", "Everything, every week");
        rule.put("fromCurrentAccountId", fromCurrentAccountId);
        rule.put("trigger", "WEEKLY");
        rule.put("dayOfWeek", app.theDateTheClockReads().getDayOfWeek().name());
        rule.put("howMuchMoves", "EVERYTHING_ABOVE");
        rule.put("floor", "0.00");
        return rule;
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

    /** The first twelfth that begins after the given day. */
    private static LocalDate theNextTwelfthAfter(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonths = month.atDay(THE_TWELFTH);
        return thisMonths.isAfter(today) ? thisMonths : month.plusMonths(1).atDay(THE_TWELFTH);
    }
}
