package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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
 * The euros start growing: a month pays a twelfth of the annual rate on the average balance the
 * account held across it, and money that arrived on the last day is paid for one day.
 *
 * <p><strong>The average daily balance is the whole of this test.</strong> A closing balance would
 * pay a full month on money that arrived yesterday and an opening balance would pay nothing on it,
 * so the second method below — three thousand one hundred euros arriving on the last day of a
 * thirty-one day month, earning one day out of thirty-one — is the one that tells the three rules
 * apart. The first method is the ordinary case underneath it: money that was there all month is
 * paid for all month.
 *
 * <p><strong>Nothing here is written into the test except the amounts it pays in.</strong> The day
 * the periods are counted from, the version the account is on and the rate that version names are
 * all read back through the API, because an account is paid at the rate <em>its own</em> terms name
 * and free savings has published two versions at two different rates. A test that hard-coded either
 * would be asserting what the seed happens to write this month.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: it winds the
 * clock a month and more, twice, and a clock cannot be wound back.
 */
class AMonthsInterestIsATwelfthOfTheAnnualRateOnTheAverageDailyBalanceApiTest
        extends ApiIntegrationTest {

    private static final String THE_INTEREST_SWEEP = "postMonthlyInterest";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-month-of-interest"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * Money that is there for the whole of a period earns the whole of the period's interest, and
     * the posting says the period, the balance, the rate and the version it was paid under.
     *
     * <p>The six figures together are the point: a customer who can read the period it covers, the
     * balance it was worked out on and the rate it was paid at can redo the multiplication by hand,
     * which is the difference between a reward and a number on a screen.
     */
    @Test
    void a_month_pays_a_twelfth_of_the_annual_rate_on_the_balance_that_was_there_all_month() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        AnAgreementView agreement = app.theAgreementOf(savingsAccount);
        BigDecimal rate = app.theVersionOf(agreement.productCode(), agreement.version())
                .annualRatePercent();

        app.deposit(savingsAccount, ANKE, "1200.00");
        aWholePeriodPasses(agreement.openedOn(), 1);
        app.runJob(THE_INTEREST_SWEEP);

        BigDecimal expected = AMonthOfInterest.onABalanceOf(new BigDecimal("1200.00"), rate);
        assertThat(expected)
                .as("a month on twelve hundred euros is worth asserting about at all")
                .isGreaterThan(BigDecimal.ZERO);
        assertThat(app.interestPaidInto(savingsAccount))
                .singleElement()
                .satisfies(month -> {
                    assertThat(month.periodOrdinal()).isEqualTo(1);
                    assertThat(month.from()).isEqualTo(agreement.openedOn());
                    assertThat(month.until()).isEqualTo(agreement.openedOn().plusMonths(1));
                    assertThat(month.averageDailyBalance()).isEqualByComparingTo("1200.00");
                    assertThat(month.annualRatePercent()).isEqualByComparingTo(rate);
                    assertThat(month.termsVersion()).isEqualTo(agreement.version());
                    assertThat(month.interest()).isEqualByComparingTo(expected);
                    // Nothing pays for keeping a floor yet, so no posting claims a bonus was
                    // earned — and the lowest balance is reported all the same, because the walk
                    // that found the average found it too.
                    assertThat(month.bonusEarned()).isFalse();
                    assertThat(month.lowestDailyBalance()).isEqualByComparingTo("1200.00");
                });
        assertThat(app.moneyBalanceOf(savingsAccount))
                .as("the interest is in the account, not beside it")
                .isEqualByComparingTo(new BigDecimal("1200.00").add(expected));
    }

    /**
     * Money that arrives on the last day of a period is paid for one day of it.
     *
     * <p>The sentence the average daily balance exists to make true, and the one case that
     * separates it from every simpler rule. The account closes the month holding twelve hundred
     * euros and is averaged at a thirty-first of that, because a thirty-first of that is what it
     * actually held across the month — so the month pays nothing at all, while a rule that paid on
     * the closing balance would have handed over a month's interest on money that had been there
     * since the previous evening.
     *
     * <p>Nothing is the honest answer here rather than a shortfall: a twelfth of a rate on
     * thirty-odd euros is a fraction of a cent, and this application floors to the cent everywhere.
     * The figure that carries the rule is the average the posting reports, and the test says what
     * the other reading would have paid so that the difference is on the page rather than implied.
     *
     * <p>The account is the holder's other one rather than the one above, because that one has
     * already been paid a month and its balance would be part of this arithmetic.
     */
    @Test
    void money_that_arrives_on_the_last_day_of_a_period_is_paid_for_one_day() {
        long savingsAccount = app.otherSavingsAccountOf(ANKE);
        AnAgreementView agreement = app.theAgreementOf(savingsAccount);
        BigDecimal rate = app.theVersionOf(agreement.productCode(), agreement.version())
                .annualRatePercent();
        LocalDate from = theFirstPeriodNotYetOver(agreement.openedOn());
        LocalDate until = from.plusMonths(1);
        long days = ChronoUnit.DAYS.between(from, until);

        // Up to the last day of the period, then the money, then the day that ends it.
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), until.minusDays(1)));
        app.deposit(savingsAccount, ANKE, "1200.00");
        app.daysPass(1);
        app.runJob(THE_INTEREST_SWEEP);

        BigDecimal averaged = new BigDecimal("1200.00")
                .divide(BigDecimal.valueOf(days), 2, RoundingMode.FLOOR);
        assertThat(app.interestPaidInto(savingsAccount))
                .filteredOn(month -> month.from().equals(from))
                .singleElement()
                .satisfies(month -> {
                    assertThat(month.averageDailyBalance())
                            .as("one day out of the " + days + " the period covered")
                            .isEqualByComparingTo(averaged);
                    assertThat(month.interest())
                            .isEqualByComparingTo(AMonthOfInterest.onABalanceOf(averaged, rate));
                    assertThat(month.lowestDailyBalance())
                            .as("it held nothing at all on every day but the last")
                            .isEqualByComparingTo("0.00");
                });
        assertThat(AMonthOfInterest.onABalanceOf(new BigDecimal("1200.00"), rate))
                .as("a rule that paid on the closing balance would have paid a whole month on "
                        + "money that arrived the evening before")
                .isGreaterThan(AMonthOfInterest.onABalanceOf(averaged, rate));
        assertThat(app.moneyBalanceOf(savingsAccount))
                .as("and what is in the account is the deposit and whatever the month paid")
                .isEqualByComparingTo(new BigDecimal("1200.00")
                        .add(AMonthOfInterest.onABalanceOf(averaged, rate)));
    }

    /**
     * Winds the clock to the day the account's <em>n</em>-th period ends, which is the day the
     * sweep can first pay for it.
     *
     * <p>Counted to the day rather than "thirty-one days on", because a period is a calendar month
     * from the day the account was opened and the tests must be right whichever month the run
     * happens in. The clock only moves forwards and only in whole days, which is exactly what this
     * arithmetic is for.
     */
    private static void aWholePeriodPasses(LocalDate openedOn, int ordinal) {
        LocalDate until = openedOn.plusMonths(ordinal);
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), until));
    }

    /**
     * The first of this account's periods that has not finished yet, which is the one this test can
     * still arrange a last-day deposit inside.
     *
     * <p>Asked rather than assumed, because the method above has already wound the clock a month on
     * and the two methods may run in either order.
     */
    private static LocalDate theFirstPeriodNotYetOver(LocalDate openedOn) {
        LocalDate today = app.theDateTheClockReads();
        LocalDate from = openedOn;
        while (!from.plusMonths(1).isAfter(today)) {
            from = from.plusMonths(1);
        }
        return from;
    }
}
