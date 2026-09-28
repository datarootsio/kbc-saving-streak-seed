package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ArrearView;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rent that could not be paid is not waived: it stays owed, it is presented again the month after,
 * and by then the account owes two rents.
 *
 * <p>User stories 17 and 21, and the consequence the whole feature exists to create. A bill that
 * cannot be paid is not waived, not partially taken and not forgotten — it accumulates, and the
 * balance has less and less room until the customer does something about it. Miss the rent in March
 * and April presents two rents.
 *
 * <p><strong>The arrear is reached the way the lab says to reach one</strong>: a sweep with a floor
 * of nothing left standing on the savings account, and the clock wound past the day the rent falls
 * due. The rules run at two and empty the current account; the bills run at half past and find
 * nothing there. That ordering is the feature, and this test is what it is for.
 *
 * <p><strong>The second month runs the bills alone, deliberately.</strong> Nothing arrives and
 * nothing is swept, so the only thing that could change the account is the run itself — which means
 * every figure asserted afterwards is that run's doing and not a side effect of a salary or a rule.
 *
 * <p>It also asserts the absence at the start: an account that owes nothing answers with an empty
 * list rather than with a figure of nought, which is what lets the page draw no section at all. A
 * panel that reads nought every day is a panel people learn to ignore.
 *
 * <p>One test, because the clock only goes forward and the narrative is a sequence of months. The
 * claim is asserted after every step instead, which is what carrying a debt looks like.
 */
class ABillMissedInMarchIsStillOwedInAprilAndAprilPresentsTwoRentsApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final String THE_SAVING_RULES_JOB = "fireSavingRulesDue";
    private static final String THE_RENT = "900.00";
    private static final int THE_TWELFTH = 12;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-unpaid-bill-stays-owed"));
        theCustomer = app.aCustomerOfItsOwn("an unpaid bill stays owed");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_rent_that_could_not_be_paid_is_presented_again_and_the_month_after_owes_two() {
        long savingsAccount = app.savingsAccountOf(theCustomer);

        assertThat(app.arrearsOf(theCustomer))
                .as("an account that has never missed anything owes nothing, and says so with an "
                        + "empty list rather than a panel reading nought")
                .isEmpty();

        RecurringBillView rent = app.declareABillFor(theCustomer, "Rent", THE_TWELFTH, THE_RENT);
        app.leaveARuleStanding(savingsAccount,
                aWeeklySweepDownToNothingFrom(app.currentAccountOf(theCustomer)));
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("there has to be enough to pay the rent before the sweep runs, or the bill "
                        + "would go unpaid for a reason that has nothing to do with saving too hard")
                .isGreaterThan(new BigDecimal(THE_RENT));

        LocalDate theRentsFirstDay = theNextTwelfthAfter(app.theDateTheClockReads());
        windTo(theRentsFirstDay);

        // The night, in the order the night runs it: the rules at two, the bills at half past.
        app.runJob(THE_SAVING_RULES_JOB);
        app.runJob(THE_BILLS_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("the sweep emptied the account and the rent met nothing at all, so nothing was "
                        + "taken and the balance never went below zero")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(app.arrearsOf(theCustomer))
                .as("the rent is not waived and not forgotten: it stays owed")
                .singleElement()
                .satisfies(owed -> {
                    assertThat(owed.billId()).isEqualTo(rent.billId());
                    assertThat(owed.billName()).isEqualTo("Rent");
                    assertThat(owed.dueOn()).isEqualTo(theRentsFirstDay);
                    assertThat(owed.amount()).isEqualByComparingTo(new BigDecimal(THE_RENT));
                    assertThat(owed.daysLate())
                            .as("owed since this morning, which is nought whole days")
                            .isZero();
                });

        // A month on, with the rules deliberately left alone: nothing arrives, nothing is swept, and
        // the only thing that touches the account is the bills run itself.
        LocalDate theRentsSecondDay = theNextTwelfthAfter(app.theDateTheClockReads());
        windTo(theRentsSecondDay);
        app.runJob(THE_BILLS_JOB);

        long aMonthInDays = ChronoUnit.DAYS.between(theRentsFirstDay, theRentsSecondDay);
        assertThat(app.arrearsOf(theCustomer))
                .as("April presents two rents: the one that was missed and the one newly due")
                .hasSize(2)
                .satisfies(owed -> {
                    assertThat(owed.get(0).dueOn())
                            .as("oldest first, because that is the order the money will go into "
                                    + "them")
                            .isEqualTo(theRentsFirstDay);
                    assertThat(owed.get(0).daysLate())
                            .as("and how long it has been carried is counted to today rather than "
                                    + "to the night it was refused")
                            .isEqualTo(aMonthInDays);
                    assertThat(owed.get(1).dueOn()).isEqualTo(theRentsSecondDay);
                    assertThat(owed.get(1).daysLate()).isZero();
                });
        assertThat(totalOwedBy(theCustomer))
                .as("two rents, and not a cent more than two rents: no interest, no fee, no charge "
                        + "of any kind is ever added to an arrear")
                .isEqualByComparingTo(new BigDecimal(THE_RENT).multiply(new BigDecimal("2")));

        assertThat(app.arrearsTheAccountItselfReports(theCustomer))
                .as("the account's own read carries the same list the arrears' own path answers "
                        + "with, because the page draws what is owed in the same breath as the "
                        + "balance it is claimed against")
                .containsExactlyElementsOf(app.arrearsOf(theCustomer));

        assertThat(app.historyOfBill(theCustomer, rent.billId()))
                .as("two dates presented and two rows, one per due date — the cursor moved past "
                        + "the missed date rather than re-deriving it, which is what keeps the run "
                        + "idempotent")
                .hasSize(2)
                .allSatisfy(presented -> assertThat(presented.outcome()).isEqualTo("UNPAID"));

        // Run twice over the same night, which is the idempotence check and is cheap.
        app.runJob(THE_BILLS_JOB);
        assertThat(app.arrearsOf(theCustomer))
                .as("a second run over the same night owes exactly what the first one left")
                .hasSize(2);
        assertThat(app.historyOfBill(theCustomer, rent.billId()))
                .as("and writes no second row for a date already presented")
                .hasSize(2);
    }

    /** What the account owes altogether, which is the figure the still-owed panel leads with. */
    private static BigDecimal totalOwedBy(String customerName) {
        return app.arrearsOf(customerName).stream()
                .map(ArrearView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * A weekly sweep taking everything above nothing, as a customer's form would send it — the
     * sharpest version of "I saved too hard": everything in the account goes, and the rent afterwards
     * meets nothing at all.
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
