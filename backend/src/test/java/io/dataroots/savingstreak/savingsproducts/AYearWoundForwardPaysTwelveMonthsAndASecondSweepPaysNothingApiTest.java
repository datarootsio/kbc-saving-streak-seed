package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
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
 * A trainer winds the clock a year forward, runs the sweep, and sees twelve payments — and running
 * it again pays nothing.
 *
 * <p><strong>The first half is what makes a monthly scheme demonstrable in an afternoon.</strong>
 * The sweep pays every period an account has passed and not been paid for, rather than the most
 * recent one, so a year that went by while nothing was running arrives in one go. It is the same
 * arrangement that covers an application switched off for a fortnight, and the twelve periods
 * arrive as twelve separate postings — each naming the days it covered, the balance it was worked
 * out on, the rate and the version — rather than as one payment nobody could check.
 *
 * <p><strong>The second half is idempotence, and it is a rule the database keeps rather than one
 * the sweep remembers.</strong> A posting names the account and the period ordinal and the pair is
 * unique, so a second run finds every month already judged and writes nothing. The test runs the
 * job twice in a row, which is exactly what a trainer does when they are not sure the first one
 * worked.
 *
 * <p>The periods are asserted to meet end to end as well as to be twelve. A month that ended on the
 * day the next began is a month whose balance was counted once; an overlap would pay some days
 * twice and a gap would lose them, and neither would show up in a total.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: a year passes
 * in it.
 */
class AYearWoundForwardPaysTwelveMonthsAndASecondSweepPaysNothingApiTest extends ApiIntegrationTest {

    private static final String THE_INTEREST_SWEEP = "postMonthlyInterest";

    private static final int MONTHS_IN_A_YEAR = 12;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-year-of-interest"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_year_wound_forward_pays_twelve_months_and_running_the_sweep_again_pays_nothing() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        AnAgreementView agreement = app.theAgreementOf(savingsAccount);
        app.deposit(savingsAccount, ANKE, "1200.00");

        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(),
                agreement.openedOn().plusMonths(MONTHS_IN_A_YEAR)));
        app.runJob(THE_INTEREST_SWEEP);

        List<InterestPostingView> aYear = app.interestPaidInto(savingsAccount);
        assertThat(aYear).hasSize(MONTHS_IN_A_YEAR);
        for (int month = 0; month < MONTHS_IN_A_YEAR; month++) {
            InterestPostingView paid = aYear.get(month);
            assertThat(paid.periodOrdinal())
                    .as("the months are numbered from the first and arrive in order")
                    .isEqualTo(month + 1);
            assertThat(paid.from())
                    .as("each month begins a month after the one before it, counted from the day "
                            + "the account was opened")
                    .isEqualTo(agreement.openedOn().plusMonths(month));
            assertThat(paid.until()).isEqualTo(agreement.openedOn().plusMonths(month + 1L));
            assertThat(paid.termsVersion()).isEqualTo(agreement.version());
        }
        BigDecimal balanceAfterTheYear = app.moneyBalanceOf(savingsAccount);
        assertThat(balanceAfterTheYear)
                .as("a year of interest is in the account")
                .isGreaterThan(new BigDecimal("1200.00"));

        // Again, the way a trainer does when they are not sure the first one worked.
        app.runJob(THE_INTEREST_SWEEP);

        assertThat(app.interestPaidInto(savingsAccount))
                .as("every month has already been judged, so nothing is judged again")
                .hasSize(MONTHS_IN_A_YEAR);
        assertThat(app.moneyBalanceOf(savingsAccount))
                .as("and not a cent moved the second time")
                .isEqualByComparingTo(balanceAfterTheYear);
    }
}
