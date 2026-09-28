package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.InterestPostingView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interest is money rather than a number on a screen: it raises the balance, it appears in the
 * record of money that moved, it is in the next month's average, and it can be taken back out like
 * any other euro.
 *
 * <p><strong>All four are one decision.</strong> An interest payment is written into the savings
 * ledger the deposits live in, as a row of another origin, and every one of these follows from that
 * without being arranged: the balance is the sum of what the rows hold, the ledger of movements is
 * a reading of those rows, next month's average is a walk over them, and a withdrawal draws them
 * down. A design that kept interest in a table of its own would have had to implement four things
 * and would have got one of them wrong.
 *
 * <p>The second month is the one worth watching. Its average daily balance is the first month's
 * balance <em>including</em> the interest paid at the end of the first, which is what "interest
 * compounds monthly" means here — and it is true because the payment is dated at the day the period
 * ended, which is the first day of the next one.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: two months
 * pass in it.
 */
class InterestIsMoneyInTheAccountApiTest extends ApiIntegrationTest {

    private static final String THE_INTEREST_SWEEP = "postMonthlyInterest";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-interest-is-money"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void interest_raises_the_balance_is_in_the_ledger_compounds_and_can_be_withdrawn() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        AnAgreementView agreement = app.theAgreementOf(savingsAccount);
        app.deposit(savingsAccount, ANKE, "1200.00");

        // The first month, in full.
        windTo(agreement.openedOn().plusMonths(1));
        app.runJob(THE_INTEREST_SWEEP);
        BigDecimal firstMonth = theMonth(savingsAccount, 1).interest();
        assertThat(firstMonth)
                .as("a month on twelve hundred euros paid something, or nothing below is a test")
                .isGreaterThan(BigDecimal.ZERO);
        assertThat(app.moneyBalanceOf(savingsAccount))
                .as("it is in the account rather than beside it")
                .isEqualByComparingTo(new BigDecimal("1200.00").add(firstMonth));

        // And in the record of money that moved, under a direction of its own, earning nothing and
        // naming no current account — because nothing was debited to pay it.
        assertThat(app.moneyMovementsOf(ANKE))
                .filteredOn(moved -> "INTEREST_INTO_SAVINGS".equals(moved.direction()))
                .singleElement()
                .satisfies(interest -> {
                    assertThat(interest.amount()).isEqualByComparingTo(firstMonth);
                    assertThat(interest.savingsAccountId()).isEqualTo(savingsAccount);
                    assertThat(interest.pointsEarned()).isZero();
                    assertThat(interest.currentAccountId()).isNull();
                    assertThat(interest.automatic()).isFalse();
                });

        // The second month is worked out on a balance that includes the first month's payment,
        // which is the whole of how monthly interest compounds here.
        windTo(agreement.openedOn().plusMonths(2));
        app.runJob(THE_INTEREST_SWEEP);
        InterestPostingView secondMonth = theMonth(savingsAccount, 2);
        assertThat(secondMonth.averageDailyBalance())
                .as("the money the bank added last month is in this month's average")
                .isEqualByComparingTo(new BigDecimal("1200.00").add(firstMonth));

        // And it is money like any other: all of it leaves when the customer asks for it.
        BigDecimal whatItHolds = app.moneyBalanceOf(savingsAccount);
        assertThat(whatItHolds)
                .isEqualByComparingTo(new BigDecimal("1200.00").add(firstMonth)
                        .add(secondMonth.interest()));
        app.withdraw(savingsAccount, ANKE, whatItHolds.toPlainString());
        assertThat(app.moneyBalanceOf(savingsAccount))
                .as("the interest was withdrawable, so nothing of it is stranded")
                .isEqualByComparingTo("0.00");
    }

    private static InterestPostingView theMonth(long savingsAccount, int ordinal) {
        List<InterestPostingView> paid = app.interestPaidInto(savingsAccount);
        return paid.stream()
                .filter(month -> month.periodOrdinal() == ordinal)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "savings account " + savingsAccount + " has not been paid its month "
                                + ordinal + ", only " + paid.size() + " months"));
    }

    /**
     * Winds the clock to a named day, counted to the day rather than in a round number of them,
     * because a period is a calendar month from the day the account was opened and this test has to
     * be right whichever month the run happens in.
     */
    private static void windTo(LocalDate day) {
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), day));
    }
}
