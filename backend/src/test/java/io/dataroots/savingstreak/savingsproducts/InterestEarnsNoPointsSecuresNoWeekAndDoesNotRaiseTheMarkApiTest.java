package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.InterestPostingView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A euro the bank added is not a euro the customer saved: interest earns no points, secures no
 * week, and never enters the figure the most-ever-saved mark is judged against.
 *
 * <p><strong>The last of those is the one that would have been most expensive to get wrong, and it
 * is shown the only way it can be shown — by a deposit.</strong> The mark answers "how much of the
 * money you put in have you already been paid points for", so a year of interest counted into it
 * would quietly change what the customer's next real deposit earns. The symptom would not appear
 * for months, and when it did it would be a deposit that earned the wrong number of points for no
 * visible reason.
 *
 * <p>So this test arranges the one case where the two readings differ by something a person could
 * notice. Two thousand euros go in and the mark is two thousand. A thousand comes back out, so the
 * customer holds a thousand and is a thousand below their mark. A year of interest is paid on what
 * is left. Then a thousand goes back in — <em>exactly</em> the gap the withdrawal left — and it
 * earns nothing at all, because every one of those euros has been saved once already and was paid
 * for then. Had the interest counted, the account would have been a few euros above the mark and
 * that deposit would have earned a few points: the same deposit, a different answer, and nobody
 * able to say why.
 *
 * <p>The week is asserted in the same breath and on the sharpest day for it. The clock is wound to
 * the day the twelfth period ends, so the last payment lands today, inside the week in progress —
 * and the week still says nothing was saved, because a secured week measures money the customer
 * moved.
 *
 * <p>The anniversaries are asserted by absence, which is the honest way round: an interest payment
 * is not a deposit, so it is not in the deposit history, so there is no clock against it for an
 * anniversary to pay. A year of interest leaves the history exactly as long as it was.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: a year passes
 * in it.
 */
class InterestEarnsNoPointsSecuresNoWeekAndDoesNotRaiseTheMarkApiTest extends ApiIntegrationTest {

    private static final String THE_INTEREST_SWEEP = "postMonthlyInterest";

    /** A year of monthly periods, which is what the sweep is asked to pay in one go. */
    private static final int MONTHS_IN_A_YEAR = 12;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-interest-earns-nothing"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_deposit_made_after_a_year_of_interest_earns_exactly_what_it_would_have_earned_without_it() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        AnAgreementView agreement = app.theAgreementOf(savingsAccount);

        DepositView first = app.deposit(savingsAccount, ANKE, "2000.00");
        assertThat(first.pointsEarned()).isEqualTo(2000);
        app.withdraw(savingsAccount, ANKE, "1000.00");
        long pointsBeforeTheYear = app.pointsBalanceOf(ANKE);
        int depositsBeforeTheYear = app.depositsInto(savingsAccount).length;

        // A year, to the day the twelfth period ends, so the last payment lands today — inside the
        // week the assertion about weeks is about.
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(),
                agreement.openedOn().plusMonths(MONTHS_IN_A_YEAR)));
        app.runJob(THE_INTEREST_SWEEP);

        BigDecimal aYearOfInterest = whatTheYearPaid(savingsAccount);
        assertThat(aYearOfInterest)
                .as("a year of interest on a thousand euros is worth more than a euro, without "
                        + "which everything below would pass for the wrong reason")
                .isGreaterThan(BigDecimal.ONE);
        BalancesView afterTheYear = app.balancesOf(savingsAccount);
        assertThat(afterTheYear.moneyBalance())
                .as("the interest really is in the account")
                .isEqualByComparingTo(new BigDecimal("1000.00").add(aYearOfInterest));
        assertThat(afterTheYear.pointsBalance())
                .as("and it earned no points on the way in")
                .isEqualTo(pointsBeforeTheYear);
        assertThat(afterTheYear.mostEverSaved())
                .as("the mark is what the customer has put away, and they have put away two "
                        + "thousand euros however much the bank has added since")
                .isEqualByComparingTo("2000.00");
        assertThat(afterTheYear.newSavingsThisWeek())
                .as("the last month's interest landed today, and a week counts what the customer "
                        + "moved")
                .isEqualByComparingTo("0.00");
        assertThat(app.depositsInto(savingsAccount))
                .as("an interest payment is not a deposit, so nothing new has an anniversary to pay")
                .hasSize(depositsBeforeTheYear);

        // And the deposit that proves it: exactly the gap the withdrawal left, which has been saved
        // once already and earns nothing a second time.
        DepositView fillingTheGapBackIn = app.deposit(savingsAccount, ANKE, "1000.00");

        assertThat(fillingTheGapBackIn.newSavings())
                .as("every one of these euros has been saved before, and a year of interest did "
                        + "not turn any of them into new saving")
                .isEqualByComparingTo("0.00");
        assertThat(fillingTheGapBackIn.pointsEarned()).isZero();
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(pointsBeforeTheYear);
        assertThat(app.balancesOf(savingsAccount).mostEverSaved()).isEqualByComparingTo("2000.00");
    }

    /**
     * What the twelve months came to altogether, added up from the postings themselves rather than
     * from the balance, so that the figure the test reasons about is the one the application says
     * it paid.
     */
    private static BigDecimal whatTheYearPaid(long savingsAccount) {
        InterestPostingView[] months = app.interestPaidInto(savingsAccount)
                .toArray(new InterestPostingView[0]);
        assertThat(months)
                .as("a year wound forward and swept once pays every month of it")
                .hasSize(MONTHS_IN_A_YEAR);
        BigDecimal paid = BigDecimal.ZERO;
        for (InterestPostingView month : months) {
            paid = paid.add(month.interest());
        }
        return paid;
    }
}
