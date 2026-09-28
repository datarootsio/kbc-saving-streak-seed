package io.dataroots.savingstreak.moneymovements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove.WithdrawalView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.MoneyMovementView;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bills a customer declared are in the same list as the money they saved: paid ones, unpaid
 * attempts and an arrear settled months late, interleaved with the deposits and withdrawals in one
 * newest-first ledger.
 *
 * <p>User stories 26, 27 and 28. Until this slice the history tab lied by omission — it knew every
 * euro a customer chose to move into savings and none of the euros their landlord moved out of their
 * current account, which is exactly how somebody ends up unable to account for a balance. An unpaid
 * attempt is in here for the same reason and it is the sharper half of it: a ledger that recorded
 * only successes leaves a customer wondering where the rent went, and the answer they need is that
 * it never moved.
 *
 * <p><strong>The arrear settled months late is the case most likely to be got wrong.</strong> A bill
 * has two dates and every other movement in this ledger has one. An arrear owed in one month and
 * paid in another belongs at its settlement, because that is when the money actually left the
 * account — filing it under the day it was owed would put it beneath a balance it never touched.
 * Both dates are on the row, so the history does not rewrite itself: it still says which day it was
 * owed from.
 *
 * <p><strong>A customer of its own, and its own application.</strong> "Newest first" and "every
 * account" are claims about the whole of one customer's ledger, and the seeded pair carry households
 * whose rent the nightly runs this test drives would take as well. Bram is read too, and only to
 * check the other half of the promise: his bills are in his ledger and in nobody else's.
 *
 * <p>Gifts are deliberately not asserted here, because a gift is not in this ledger and must not be:
 * {@code AGiftMovesPointsAndNothingElseApiTest} holds that line already. Points are not euros, and
 * the list this feature adds bills to is a list of money that moved.
 */
class TheLedgerShowsBillsPaidAndUnpaidBesideTheDepositsApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final String INTO_SAVINGS = "INTO_SAVINGS";
    private static final String OUT_OF_SAVINGS = "OUT_OF_SAVINGS";
    private static final String A_BILL = "OUT_OF_CURRENT_ACCOUNT";

    /** Long enough that "settled months late" is not a figure of speech. */
    private static final long A_MONTH_AND_A_HALF = 45;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithALedgerOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-bills-in-the-ledger"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_paid_bill_an_unpaid_attempt_and_an_arrear_settled_late_read_in_one_list_with_the_deposits() {
        String customer = app.aCustomerOfItsOwn("a ledger with bills in it");
        long savings = app.savingsAccountOf(customer);
        long everyday = app.currentAccountOf(customer);

        // Money into savings, which is both the first row of the ledger and what leaves the current
        // account too thin to pay the second bill below.
        DepositView saved = app.deposit(savings, customer, "1000.00");
        BigDecimal afterSaving = app.currentAccountBalanceOf(customer);

        // A bill the account can cover, taken on the day it falls due.
        LocalDate theDayTheBroadbandIsTaken = app.theDateTheClockReads().plusDays(2);
        RecurringBillView broadband = app.declareABillFor(customer, "Broadband",
                theDayTheBroadbandIsTaken.getDayOfMonth(), "35.00");
        // And one it cannot, falling due three days later. Declared now rather than later so that
        // the run that takes the broadband bill has already looked at this one and found nothing due.
        LocalDate theDayTheTuitionIsOwedFrom = app.theDateTheClockReads().plusDays(5);
        RecurringBillView tuition = app.declareABillFor(customer, "Tuition",
                theDayTheTuitionIsOwedFrom.getDayOfMonth(), "900.00");

        windTo(theDayTheBroadbandIsTaken);
        app.runJob(THE_BILLS_JOB);
        assertThat(app.currentAccountBalanceOf(customer))
                .as("the broadband bill was affordable, so it went out")
                .isEqualByComparingTo(afterSaving.subtract(new BigDecimal("35.00")));
        // Ended the moment it has been taken once, so that the months this test winds through do not
        // fill the ledger with broadband bills nobody is asserting about. Ending it leaves the date it
        // was already taken on exactly where it is, which is the whole point of ending rather than
        // deleting.
        app.endTheBill(customer, broadband.billId());

        BigDecimal beforeTheTuitionWasPresented = app.currentAccountBalanceOf(customer);
        windTo(theDayTheTuitionIsOwedFrom);
        app.runJob(THE_BILLS_JOB);

        // The unpaid attempt: in the ledger, marked, and it moved nothing.
        assertThat(app.currentAccountBalanceOf(customer))
                .as("a bill that cannot be paid takes nothing at all, so no balance fell")
                .isEqualByComparingTo(beforeTheTuitionWasPresented);

        MoneyMovementView[] afterTheRefusal = app.moneyMovementsOf(customer);
        assertThat(Arrays.stream(afterTheRefusal).map(MoneyMovementView::direction).toList())
                .as("the refused bill is the newest thing that happened, above the bill that was "
                        + "paid and the deposit before it")
                .containsExactly(A_BILL, A_BILL, INTO_SAVINGS);

        MoneyMovementView refused = afterTheRefusal[0];
        assertThat(refused.billName())
                .as("the name the customer recognises, not an identifier")
                .isEqualTo("Tuition");
        assertThat(refused.outcome())
                .as("marked unpaid, because the information the customer needs is that the money "
                        + "never moved")
                .isEqualTo("UNPAID");
        assertThat(refused.amount())
                .as("what was owed, because that is the figure they still have to find")
                .isEqualByComparingTo("900.00");
        assertThat(refused.dueOn()).isEqualTo(theDayTheTuitionIsOwedFrom);
        assertThat(refused.savingsAccountId())
                .as("money leaving a current account for a bill never goes near savings")
                .isNull();
        assertThat(refused.currentAccountId()).isEqualTo(everyday);
        assertThat(refused.pointsEarned())
                .as("a bill has earned a point in this application exactly as often as a "
                        + "withdrawal has")
                .isZero();
        assertThat(refused.automatic())
                .as("a bill is not a saving rule, and the word on this row means the rule")
                .isFalse();

        MoneyMovementView paid = afterTheRefusal[1];
        assertThat(paid.billName()).isEqualTo("Broadband");
        assertThat(paid.outcome()).isEqualTo("PAID");
        assertThat(paid.amount()).isEqualByComparingTo("35.00");
        assertThat(paid.dueOn()).isEqualTo(theDayTheBroadbandIsTaken);
        assertThat(paid.daysLate())
                .as("taken on the day it fell due, which is nought days late rather than no answer")
                .isZero();

        assertThat(afterTheRefusal[2].id())
                .as("and the deposit is still where it was, at the bottom")
                .isEqualTo(saved.id());

        // Now the way out of the hole, and the months it takes. The bill is ended first, so that
        // nothing new falls due while the clock is wound and the only thing left to settle is the
        // debt itself — ending a bill does not waive what was already owed.
        app.endTheBill(customer, tuition.billId());
        WithdrawalView back = app.withdraw(savings, customer, "900.00");
        app.daysPass(A_MONTH_AND_A_HALF);
        app.runJob(THE_BILLS_JOB);

        assertThat(app.currentAccountBalanceOf(customer))
                .as("the arrear was settled out of the money brought back from savings")
                .isEqualByComparingTo(beforeTheTuitionWasPresented);

        MoneyMovementView[] ledger = app.moneyMovementsOf(customer);
        assertThat(Arrays.stream(ledger).map(MoneyMovementView::direction).toList())
                .as("four things happened and they read in the order they happened in, newest "
                        + "first: the arrear settled, the withdrawal that paid for it, the "
                        + "broadband bill, the deposit")
                .containsExactly(A_BILL, OUT_OF_SAVINGS, A_BILL, INTO_SAVINGS);

        MoneyMovementView settledLate = ledger[0];
        assertThat(settledLate.billName()).isEqualTo("Tuition");
        assertThat(settledLate.outcome())
                .as("the same date, paid at last: one row per due date, updated in place")
                .isEqualTo("PAID");
        assertThat(settledLate.dueOn())
                .as("and it still says which day it was owed from, months after the fact")
                .isEqualTo(theDayTheTuitionIsOwedFrom);
        assertThat(settledLate.movedAt())
                .as("it sorts by when it was taken, which is after the withdrawal that paid for it")
                .isAfter(back.withdrawnAt());
        assertThat(settledLate.daysLate())
                .as("roughly the month and a half the debt was carried for")
                .isGreaterThanOrEqualTo(A_MONTH_AND_A_HALF);
        assertThat(settledLate.id())
                .as("the same row that was in the ledger unpaid, rather than a second one")
                .isEqualTo(refused.id());

        assertThat(ledger[1].id()).isEqualTo(back.id());
        assertThat(ledger[2].billName()).isEqualTo("Broadband");
        assertThat(ledger[3].id()).isEqualTo(saved.id());

        // Every moment in the order the list claims, which is the assertion a merge of two sorted
        // lists most easily gets wrong.
        assertThat(Arrays.stream(ledger).map(MoneyMovementView::movedAt).toList())
                .isSortedAccordingTo((one, another) -> another.compareTo(one));

        // And a bill on one customer's account is never in another's. Bram is a real customer with
        // a real household of his own, and the runs above presented his bills too — so this is two
        // populated ledgers being checked against each other rather than an empty one that would
        // pass however badly the question were asked.
        MoneyMovementView[] brams = app.moneyMovementsOf(BRAM);
        assertThat(Arrays.stream(brams).filter(row -> A_BILL.equals(row.direction())))
                .as("Bram's bills were presented by the same runs, so there is something to confuse")
                .isNotEmpty();
        assertThat(Arrays.stream(brams).map(MoneyMovementView::currentAccountId).distinct())
                .as("and none of them is this test customer's account")
                .doesNotContain(everyday);
        assertThat(Arrays.stream(brams).map(MoneyMovementView::billName))
                .doesNotContain("Broadband", "Tuition");
        assertThat(Arrays.stream(ledger).map(MoneyMovementView::currentAccountId).distinct())
                .as("and this customer's ledger is entirely their own account's")
                .containsExactly(everyday);
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
}
