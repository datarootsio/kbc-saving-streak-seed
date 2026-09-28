package io.dataroots.savingstreak.moneymovements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove.PartAsTyped;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove.WithdrawalView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.MoneyMovementView;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.SpendPartView;
import io.dataroots.savingstreak.support.SpendView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a customer spent is in the same list as what they saved, what they took back out and what
 * their bills took: four kinds, one ledger, newest first — and a spend corrected since says so out
 * loud rather than quietly reading differently from the way it read yesterday.
 *
 * <p>User stories 42 and 43. Until this slice the history tab knew every euro that left a current
 * account as a declared bill and none of the euros that left it as groceries, which is the same way
 * of lying by omission the bills themselves were added to fix. A spend is the largest part of most
 * people's outgoings and the part they are actually wrong about, so a ledger without it is a ledger
 * that cannot answer the question it exists for.
 *
 * <p><strong>The case most likely to be got wrong is where a spend sits.</strong> A bill has two
 * dates and sits at the moment it was settled; a spend has one and sits at the moment it was
 * recorded, which is also the moment the money left. Both are in here beside a deposit and a
 * withdrawal so that the merge of four sorted lists is asserted as one order rather than four.
 *
 * <p><strong>The split travels on the row, names and all.</strong> A row saying EUR 30,00 left for
 * "Supermarket" is half an answer; the customer's own words for what it went on are the other half.
 * A part filed under nothing at all is on the row too, because that is a state somebody chose in a
 * hurry rather than a gap, and a ledger that dropped it would show a spend whose parts no longer add
 * up to it.
 *
 * <p><strong>A customer of its own, and its own application.</strong> "Newest first" and "every
 * account" are claims about the whole of one customer's ledger, and the shared database carries
 * other tests' spending. A second customer of this application's own spends too, and only so that
 * the promise the other half of this makes — a spend on an account the customer does not hold is
 * never in their ledger — is checked against a populated ledger rather than an empty one.
 */
class TheLedgerShowsSpendsBesideTheDepositsAndTheBillsApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final String INTO_SAVINGS = "INTO_SAVINGS";
    private static final String OUT_OF_SAVINGS = "OUT_OF_SAVINGS";
    private static final String A_BILL = "OUT_OF_CURRENT_ACCOUNT";
    private static final String A_SPEND = "SPENT_OUT_OF_CURRENT_ACCOUNT";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithALedgerOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-spends-in-the-ledger"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_spend_reads_in_one_list_with_the_deposits_the_withdrawals_and_the_bills() {
        String customer = app.aCustomerOfItsOwn("a ledger with spends in it");
        long savings = app.savingsAccountOf(customer);
        long everyday = app.currentAccountOf(customer);

        // Oldest first, because that is the order it happens in; the ledger below is asserted the
        // other way up, which is the whole of what "newest first" means.
        DepositView saved = app.deposit(savings, customer, "300.00");
        WithdrawalView back = app.withdraw(savings, customer, "50.00");

        LocalDate theDayTheRentIsTaken = app.theDateTheClockReads().plusDays(2);
        RecurringBillView rent = app.declareABillFor(customer, "Rent",
                theDayTheRentIsTaken.getDayOfMonth(), "40.00");
        windTo(theDayTheRentIsTaken);
        app.runJob(THE_BILLS_JOB);
        // Ended the moment it has been taken once, so that nothing this test does afterwards fills
        // the ledger with rents nobody is asserting about. Ending a bill leaves the date it was
        // already taken on exactly where it is.
        app.endTheBill(customer, rent.billId());

        SpendingCategoryView groceries = app.declareACategoryFor(customer, "Groceries");
        BigDecimal beforeTheSupermarket = app.currentAccountBalanceOf(customer);
        SpendView supermarket = app.spendForSplitAcross(customer, "Supermarket", "30.00",
                PartAsTyped.filedUnder(groceries.categoryId(), "20.00"),
                PartAsTyped.unfiled("10.00"));

        assertThat(app.currentAccountBalanceOf(customer))
                .as("the money left when the spend was recorded, which is why the row sits there")
                .isEqualByComparingTo(beforeTheSupermarket.subtract(new BigDecimal("30.00")));

        MoneyMovementView[] ledger = app.moneyMovementsOf(customer);

        assertThat(Arrays.stream(ledger).map(MoneyMovementView::direction).toList())
                .as("four kinds in one list, newest first: the supermarket, the rent, the money "
                        + "brought back from savings, and the saving before it")
                .containsExactly(A_SPEND, A_BILL, OUT_OF_SAVINGS, INTO_SAVINGS);

        MoneyMovementView spent = ledger[0];
        assertThat(spent.id()).isEqualTo(supermarket.spendId());
        assertThat(spent.spendName())
                .as("the customer's own word for it, so the list reads like their week")
                .isEqualTo("Supermarket");
        assertThat(spent.amount()).isEqualByComparingTo("30.00");
        assertThat(spent.movedAt())
                .as("a spend sits at the moment it was recorded, which is when the money left")
                .isEqualTo(supermarket.recordedAt());
        assertThat(spent.currentAccountId()).isEqualTo(everyday);
        assertThat(spent.savingsAccountId())
                .as("money spent out of a current account never goes near savings")
                .isNull();
        assertThat(spent.pointsEarned())
                .as("a spend has earned a point in this application exactly as often as a bill has")
                .isZero();
        assertThat(spent.automatic())
                .as("a spend is somebody buying something, and the word on this row means a "
                        + "saving rule")
                .isFalse();
        assertThat(spent.correctedAt())
                .as("nobody has corrected it, and the null is what says so")
                .isNull();
        assertThat(spent.billName())
                .as("a spend is not a bill, and nothing on this row pretends it is")
                .isNull();
        assertThat(spent.dueOn()).isNull();
        assertThat(spent.outcome()).isNull();
        assertThat(spent.daysLate()).isNull();

        assertThat(spent.parts())
                .as("what the customer says it went on, in the order they typed it, with the "
                        + "unfiled part carried rather than dropped")
                .containsExactly(
                        new SpendPartView(groceries.categoryId(), "Groceries",
                                new BigDecimal("20.00")),
                        new SpendPartView(null, null, new BigDecimal("10.00")));

        // And the other three kinds are untouched by the fourth arriving.
        assertThat(ledger[1].billName()).isEqualTo("Rent");
        assertThat(ledger[1].parts())
                .as("a bill is not split; it is one payment for the amount its declaration fixes")
                .isNull();
        assertThat(ledger[2].id()).isEqualTo(back.id());
        assertThat(ledger[3].id()).isEqualTo(saved.id());

        // Every moment in the order the list claims, which is the assertion a merge of four sorted
        // lists most easily gets wrong.
        assertThat(Arrays.stream(ledger).map(MoneyMovementView::movedAt).toList())
                .isSortedAccordingTo((one, another) -> another.compareTo(one));

        // And somebody else's spending is in their ledger and in nobody else's. A real spend to
        // exclude rather than merely an absent one: a read that had forgotten to scope itself to the
        // customer's accounts would answer an empty question correctly and this one wrongly.
        String somebodyElse = app.aCustomerOfItsOwn("a ledger of somebody else's spending");
        SpendingCategoryView theirs = app.declareACategoryFor(somebodyElse, "Fuel");
        SpendView diesel = app.spendFor(somebodyElse, "Diesel", "60.00", theirs.categoryId());

        assertThat(Arrays.stream(app.moneyMovementsOf(somebodyElse))
                .filter(row -> A_SPEND.equals(row.direction())))
                .as("their own spend is in their own ledger, so there is something to confuse")
                .isNotEmpty();
        assertThat(Arrays.stream(app.moneyMovementsOf(customer)).map(MoneyMovementView::id))
                .as("and it is nowhere in this customer's")
                .doesNotContain(diesel.spendId());
        assertThat(Arrays.stream(app.moneyMovementsOf(customer))
                .map(MoneyMovementView::currentAccountId).distinct())
                .as("whose every row is their own account's")
                .containsExactly(everyday);
    }

    @Test
    void a_spend_corrected_afterwards_says_when_it_was_corrected_and_shows_the_split_it_now_has() {
        String customer = app.aCustomerOfItsOwn("a ledger with a corrected spend in it");
        SpendingCategoryView groceries = app.declareACategoryFor(customer, "Groceries");
        SpendingCategoryView repairs = app.declareACategoryFor(customer, "Car repairs");

        SpendView filedWrong = app.spendFor(customer, "Garage", "120.00", groceries.categoryId());

        MoneyMovementView beforeTheCorrection = theSpendIn(app.moneyMovementsOf(customer));
        assertThat(beforeTheCorrection.correctedAt())
                .as("filed once and never corrected: nothing there rather than a stand-in date")
                .isNull();
        assertThat(beforeTheCorrection.parts())
                .containsExactly(new SpendPartView(groceries.categoryId(), "Groceries",
                        new BigDecimal("120.00")));

        SpendView corrected = app.correctTheSplitFor(customer, filedWrong.spendId(),
                PartAsTyped.filedUnder(repairs.categoryId(), "120.00"));

        MoneyMovementView afterwards = theSpendIn(app.moneyMovementsOf(customer));
        assertThat(afterwards.id())
                .as("the same spend, corrected in place: a spend cannot be unmade and remade")
                .isEqualTo(filedWrong.spendId());
        assertThat(afterwards.correctedAt())
                .as("the ledger does not pretend the customer got it right the first time")
                .isEqualTo(corrected.correctedAt())
                .isNotNull();
        assertThat(afterwards.movedAt())
                .as("and it has not moved in the list: the money left when it was recorded, and "
                        + "only the opinion about what it was for has changed")
                .isEqualTo(filedWrong.recordedAt());
        assertThat(afterwards.amount())
                .as("the amount is not an opinion and a correction cannot touch it")
                .isEqualByComparingTo("120.00");
        assertThat(afterwards.parts())
                .as("the split it has now, not the one it was recorded with")
                .containsExactly(new SpendPartView(repairs.categoryId(), "Car repairs",
                        new BigDecimal("120.00")));
    }

    /** The one spend in a ledger a test has put exactly one spend into. */
    private static MoneyMovementView theSpendIn(MoneyMovementView[] ledger) {
        var spends = Arrays.stream(ledger).filter(row -> A_SPEND.equals(row.direction())).toList();
        assertThat(spends)
                .as("this test records one spend and reads the ledger back for it")
                .hasSize(1);
        return spends.get(0);
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
