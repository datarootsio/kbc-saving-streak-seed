package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A withdrawal takes the interest the bank paid before it takes anything the customer put in, and
 * the deposits it does reach are still the oldest first.
 *
 * <p><strong>Interest is the cheapest money in the account, which is why it goes first.</strong> A
 * euro of interest carries no loyalty clock and has earned no points, so spending it costs the
 * customer nothing at all; a euro of a deposit is counting towards an anniversary that pays a tenth
 * of whatever is still in that deposit when it falls. Taking the bank's money first is the ordering
 * that costs the customer least, and it is a rule rather than a kindness — so it is asserted rather
 * than left to the order rows happen to come back in.
 *
 * <p><strong>Which euros left cannot be read off the balance</strong>, because any allocation of
 * one withdrawal leaves the same total behind. What tells them apart is what each deposit is still
 * worth on its next anniversary, which is a tenth of what is left in it — so that figure is what
 * this test reads, deposit by deposit, exactly as {@code AWithdrawalComesOutOfTheOldestDeposit}
 * does for the promise this one extends.
 *
 * <p>The two halves are one story on one account, in the order a customer would live it. First a
 * withdrawal of exactly the month's interest: both deposits are untouched, which is only possible
 * if the interest went first. Then a withdrawal of four hundred euros with the interest already
 * spent: it comes out of the older deposit and the newer one is untouched, which is the promise
 * that was already there and is unchanged by the new kind of row in front of it.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: a month
 * passes in it.
 */
class AWithdrawalTakesInterestBeforeItTakesDepositsApiTest extends ApiIntegrationTest {

    private static final String THE_INTEREST_SWEEP = "postMonthlyInterest";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMonthThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-interest-leaves-first"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_interest_goes_first_and_then_the_oldest_deposit() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        AnAgreementView agreement = app.theAgreementOf(savingsAccount);
        DepositView older = app.deposit(savingsAccount, ANKE, "1200.00");
        DepositView newer = app.deposit(savingsAccount, ANKE, "1200.00");

        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(),
                agreement.openedOn().plusMonths(1)));
        app.runJob(THE_INTEREST_SWEEP);
        BigDecimal aMonthsInterest = app.interestPaidInto(savingsAccount).get(0).interest();
        assertThat(aMonthsInterest)
                .as("a month on two thousand four hundred euros paid something, without which "
                        + "there is nothing to take first")
                .isGreaterThan(BigDecimal.ZERO);

        // Exactly the interest, and nothing else. If the deposits went first this would take a
        // euro or so out of the older one and its next anniversary would be worth a point less.
        app.withdraw(savingsAccount, ANKE, aMonthsInterest.toPlainString());

        assertThat(whatTheNextAnniversaryOfIsWorth(savingsAccount, older))
                .as("the oldest deposit is untouched, because the bank's own money went first")
                .isEqualTo(120);
        assertThat(whatTheNextAnniversaryOfIsWorth(savingsAccount, newer)).isEqualTo(120);
        assertThat(app.moneyBalanceOf(savingsAccount))
                .as("and what is left is exactly the two deposits")
                .isEqualByComparingTo("2400.00");

        // And with the interest spent, the next withdrawal falls where it always has: on the
        // deposit that has been there longest.
        app.withdraw(savingsAccount, ANKE, "400.00");

        assertThat(whatTheNextAnniversaryOfIsWorth(savingsAccount, older))
                .as("eight hundred euros left in the oldest deposit")
                .isEqualTo(80);
        assertThat(whatTheNextAnniversaryOfIsWorth(savingsAccount, newer))
                .as("and the newer one untouched, which is the promise that was already here")
                .isEqualTo(120);
    }

    /**
     * What one deposit's next anniversary is worth at what it holds now — a tenth of its whole
     * euros — which is the only thing on a screen that says which deposit a withdrawal came out of.
     */
    private static long whatTheNextAnniversaryOfIsWorth(long savingsAccount, DepositView deposit) {
        List<DepositView> history = List.of(app.depositsInto(savingsAccount));
        return history.stream()
                .filter(row -> row.id().equals(deposit.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "deposit " + deposit.id() + " is no longer in the history of savings "
                                + "account " + savingsAccount))
                .nextAnniversaryPoints();
    }
}
