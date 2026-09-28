package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.ATermBrokenView;
import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.InterestPostingView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.dataroots.savingstreak.savingsproducts.AFixedTermSomebodyHolds.FREE_SAVINGS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer who breaks a fixed term is charged for it, and from that day on the bank pays interest
 * on what the charge left behind rather than on what the account held before it.
 *
 * <p><strong>The month a term is broken in, and every month after it.</strong> Those are two
 * different claims and both of them are here. The first is about the day the charge landed: the
 * account held the whole balance up to that day and less than it afterwards, so the average daily
 * balance the month is paid on sits between the two figures and below the opening one. The second
 * is about a charge staying taken: a month later the account is still short by the charge, and a
 * walk that had counted the charge as money arriving would say it was <em>over</em> by it, for as
 * long as the account existed.
 *
 * <p><strong>Nobody had ever asked this question, which is why it could be wrong.</strong> The
 * walk that reconstructs a day's balance reads each movement's direction and turns it into a sign;
 * a deposit and a month's interest rise, a withdrawal falls, and the early-exit charge is its own
 * fifth word. Until this test there was no test in this application that computed interest on an
 * account after a term had been broken on it, so the charge could be — and was — signed as a rise,
 * and every month afterwards paid interest on money the bank had already taken off the customer.
 *
 * <p><strong>The figures here are written down rather than derived, and that is the point of
 * them.</strong> EUR 7.10 is ninety days of 2.40% a year on EUR 1 200, and EUR 1 192,90 is what is
 * left of the twelve hundred after it — two sums a reader can do on paper. A test that asked the
 * application what the charge was and then subtracted it would agree with the application about
 * the direction as readily as about the amount, which is the one thing it must not do. The rate the
 * interest is then paid at is the exception and is read back through the API, because an account
 * that broke its term is moved onto whichever version of free savings was being sold that day, and
 * free savings has published two at two different rates.
 *
 * <p>An application and a database of its own, for the reason {@link AFixedTermSomebodyHolds}
 * gives: this class winds the clock two whole months and a clock cannot be wound back. Ordered for
 * the same reason — the second month cannot be asked about before the first has been paid.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InterestAfterABrokenTermIsPaidOnWhatTheChargeLeftBehindApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-interest-after-a-broken-term");

    /** The sweep that pays every account every month it has passed, by the name it is run under. */
    private static final String THE_INTEREST_SWEEP = "postMonthlyInterest";

    /** What the account is opened with, and the balance the charge is priced on. */
    private static final String WHAT_IT_HOLDS = "1200.00";

    /**
     * Ninety days of 2.40% a year on EUR 1 200, floored to the cent.
     *
     * <p>1 200 × 240 × 90 ÷ (10 000 × 365) is 7.1013…, and the flooring makes it EUR 7.10. Spelled
     * out rather than computed, because a test that computed it would agree with whatever the
     * application did — and this test's whole subject is a figure the application had the wrong
     * sign on.
     */
    private static final String WHAT_BREAKING_COSTS = "7.10";

    /** What the account actually holds from the day it was charged: the twelve hundred, less that. */
    private static final BigDecimal WHAT_IS_LEFT_AFTER_THE_CHARGE = new BigDecimal("1192.90");

    /**
     * What a walk that read the charge as money arriving would have thought the account held: the
     * twelve hundred with the charge added to it rather than taken off.
     *
     * <p>Named so that the assertions can say what the wrong answer looks like as well as what the
     * right one does. The gap between the two is EUR 14.20 — twice the charge — which is what makes
     * a sign error on a movement this small visible in an average at all.
     */
    private static final BigDecimal WHAT_THE_WRONG_SIGN_WOULD_HAVE_MADE_IT =
            new BigDecimal("1207.10");

    private static AFixedTermSomebodyHolds theirs;
    private static long theAccount;
    private static LocalDate theDayItWasOpened;

    /** The period the term was broken inside: the account's first month. */
    private static LocalDate theFirstMonthBegan;
    private static LocalDate theFirstMonthEnded;

    /** How many of that month's days the account still held the whole twelve hundred for. */
    private static long daysBeforeTheCharge;

    @BeforeAll
    static void openAFixedTermPutMoneyInItAndBreakItTheNextDay() {
        theirs = new AFixedTermSomebodyHolds(DATABASE, "somebody who had to have the money back");
        theDayItWasOpened = theirs.theDateTheClockReads();
        theAccount = theirs.openAFixedTerm();
        theirs.payIn(theAccount, WHAT_IT_HOLDS);
        theFirstMonthBegan = theDayItWasOpened;
        theFirstMonthEnded = theDayItWasOpened.plusMonths(1);

        // A day in, rather than on the day it was opened, so that the month has a stretch at the
        // full balance and a stretch at the lower one. Broken on the opening day the average would
        // be one figure throughout and would not tell a walk that dates its movements correctly
        // from one that does not.
        theirs.daysPass(1);
        ATermBrokenView broken = theirs.breakTheTerm(theAccount);
        daysBeforeTheCharge = ChronoUnit.DAYS.between(theFirstMonthBegan, broken.brokenOn());

        assertThat(broken.charge())
                .as("ninety days of 2.40%% a year on EUR 1 200, which is the sum this test is "
                        + "built on and not the rule it is testing")
                .isEqualByComparingTo(WHAT_BREAKING_COSTS);
        assertThat(theirs.balanceOf(theAccount))
                .as("and what the account holds from this day on")
                .isEqualByComparingTo(WHAT_IS_LEFT_AFTER_THE_CHARGE);
    }

    @AfterAll
    static void stopIt() {
        if (theirs != null) {
            theirs.close();
        }
    }

    /**
     * The month the term was broken in is paid on the balance as it actually stood each day: the
     * whole twelve hundred until the charge, and what the charge left behind afterwards.
     *
     * <p>The average is the figure that carries the rule, because it is the only one of the three
     * that mixes the two stretches — the lowest is what the account was left with and the interest
     * is a fraction of a percent of either. A walk that counted the charge as money arriving would
     * report an average <em>above</em> the twelve hundred the account was opened with, which is a
     * balance it never held on any day of its life, and the assertion below says so in those words.
     *
     * <p>The lowest daily balance is asserted beside it because it is the same claim with no
     * averaging in the way: the least this account held in its first month is what the bank left
     * it, and nothing here can make that more than what it was opened with.
     */
    @Test
    @Order(1)
    void the_month_a_term_is_broken_in_is_paid_on_the_balance_the_charge_left_behind() {
        theirs.daysPass(ChronoUnit.DAYS.between(theirs.theDateTheClockReads(), theFirstMonthEnded));
        theirs.runJob(THE_INTEREST_SWEEP);

        long days = ChronoUnit.DAYS.between(theFirstMonthBegan, theFirstMonthEnded);
        BigDecimal expectedAverage = averagedOver(days, daysBeforeTheCharge);
        BigDecimal rate = theRateTheAccountIsPaidAt();

        assertThat(expectedAverage)
                .as("the month is averaged below what the account was opened holding, because it "
                        + "stopped holding all of it on the day it was charged")
                .isLessThan(new BigDecimal(WHAT_IT_HOLDS))
                .isGreaterThan(WHAT_IS_LEFT_AFTER_THE_CHARGE);
        assertThat(theirs.interestPaidInto(theAccount))
                .filteredOn(month -> month.from().equals(theFirstMonthBegan))
                .singleElement()
                .satisfies(month -> {
                    assertThat(month.averageDailyBalance())
                            .as("EUR 1 200,00 until the charge landed and EUR 1 192,90 after it, "
                                    + "over the %d days the month covered — and never the "
                                    + "EUR 1 207,10 a charge read as money arriving would have "
                                    + "made of it", days)
                            .isEqualByComparingTo(expectedAverage)
                            .isLessThan(WHAT_THE_WRONG_SIGN_WOULD_HAVE_MADE_IT);
                    assertThat(month.lowestDailyBalance())
                            .as("the least it held all month is what the charge left it")
                            .isEqualByComparingTo(WHAT_IS_LEFT_AFTER_THE_CHARGE);
                    assertThat(month.interest())
                            .as("and the interest is a twelfth of the year's rate on that average")
                            .isEqualByComparingTo(
                                    AMonthOfInterest.onABalanceOf(expectedAverage, rate));
                    assertThat(month.interest())
                            .as("which is less than the same month would have paid on a balance "
                                    + "inflated by the charge")
                            .isLessThan(AMonthOfInterest.onABalanceOf(
                                    averagedOver(days, daysBeforeTheCharge,
                                            WHAT_THE_WRONG_SIGN_WOULD_HAVE_MADE_IT), rate));
                });
    }

    /**
     * The month after that is paid on a balance that is still short by the charge, because a charge
     * is not undone by a month passing.
     *
     * <p>This is the half of the defect that went on for the life of the account. Nothing moves in
     * or out of the account across this second month, so every one of its days holds the same
     * figure — what the charge left, plus the first month's interest, which landed on the first day
     * of this one. The average and the lowest are therefore that figure exactly, and a walk that
     * had counted the charge as money arriving would report both of them EUR 14.20 higher every
     * month from here on.
     *
     * <p>The first month's interest is read off the posting rather than written down, because it is
     * a figure the previous test has already asserted the application's answer to; restating it
     * here would be a second copy of an arithmetic this class has finished arguing about.
     */
    @Test
    @Order(2)
    void the_months_after_it_are_paid_on_a_balance_that_is_still_short_by_the_charge() {
        LocalDate theSecondMonthEnded = theFirstMonthEnded.plusMonths(1);
        theirs.daysPass(ChronoUnit.DAYS.between(theirs.theDateTheClockReads(), theSecondMonthEnded));
        theirs.runJob(THE_INTEREST_SWEEP);

        BigDecimal whatTheFirstMonthPaid = theInterestOfTheMonthBeginningOn(theFirstMonthBegan);
        BigDecimal heldEveryDay = WHAT_IS_LEFT_AFTER_THE_CHARGE.add(whatTheFirstMonthPaid);
        BigDecimal rate = theRateTheAccountIsPaidAt();

        assertThat(theirs.interestPaidInto(theAccount))
                .filteredOn(month -> month.from().equals(theFirstMonthEnded))
                .singleElement()
                .satisfies(month -> {
                    assertThat(month.averageDailyBalance())
                            .as("nothing moved all month, so every day of it held what the charge "
                                    + "left plus what the first month paid")
                            .isEqualByComparingTo(heldEveryDay);
                    assertThat(month.lowestDailyBalance()).isEqualByComparingTo(heldEveryDay);
                    assertThat(month.interest())
                            .isEqualByComparingTo(AMonthOfInterest.onABalanceOf(heldEveryDay, rate));
                });
        assertThat(heldEveryDay)
                .as("a month on and the charge is still gone: interest that had been paid into "
                        + "the account cannot make up EUR 7,10, and a charge counted the wrong way "
                        + "would have put the balance above EUR 1 207,10 instead")
                .isLessThan(new BigDecimal(WHAT_IT_HOLDS));
        assertThat(theirs.balanceOf(theAccount))
                .as("and the account itself says the same thing")
                .isEqualByComparingTo(heldEveryDay.add(
                        AMonthOfInterest.onABalanceOf(heldEveryDay, rate)));
    }

    /**
     * The average daily balance of the month the charge landed in, worked out the way the
     * specification says it rather than the way the application does it.
     *
     * <p>The account held the whole of what it was paid in for the days before the charge and what
     * the charge left it for the rest, so the average is those two stretches added up and divided
     * by the days in the month. Floored to the cent, downwards, because that is the one rounding in
     * the rule and a test that rounded the other way would pass against an implementation that did
     * too.
     */
    private static BigDecimal averagedOver(long days, long daysAtTheFullBalance) {
        return averagedOver(days, daysAtTheFullBalance, WHAT_IS_LEFT_AFTER_THE_CHARGE);
    }

    /**
     * The same arithmetic over any second stretch, so that the test can also say what the month
     * would have been averaged at had the charge been read as money arriving.
     *
     * <p>Saying the wrong answer out loud is worth the parameter: an assertion that the average is
     * EUR 1 193,13 is an assertion somebody has to check a table to understand, and one that adds
     * "and not the EUR 1 206,63 a charge with the wrong sign would give" explains itself.
     */
    private static BigDecimal averagedOver(long days, long daysAtTheFullBalance,
                                           BigDecimal heldAfterwards) {
        return new BigDecimal(WHAT_IT_HOLDS).multiply(BigDecimal.valueOf(daysAtTheFullBalance))
                .add(heldAfterwards.multiply(BigDecimal.valueOf(days - daysAtTheFullBalance)))
                .divide(BigDecimal.valueOf(days), 2, RoundingMode.FLOOR);
    }

    /**
     * The rate this account is paid at today, which is free savings' — the product breaking a term
     * moves an account onto — at the version it was moved onto.
     *
     * <p>Read rather than written down, because free savings has published two versions at two
     * different rates and which of them an account lands on is the day it broke its term, not
     * anything a test can know in advance.
     */
    private static BigDecimal theRateTheAccountIsPaidAt() {
        AnAgreementView agreement = theirs.theAccount(theAccount).agreement();
        assertThat(agreement.productCode())
                .as("breaking a term moves the account onto free savings, which is the premise "
                        + "the rate is read under")
                .isEqualTo(FREE_SAVINGS);
        return theirs.theVersionOf(agreement.productCode(), agreement.version())
                .annualRatePercent();
    }

    /** What one already-paid month paid, off the account's own page. */
    private static BigDecimal theInterestOfTheMonthBeginningOn(LocalDate from) {
        return theirs.interestPaidInto(theAccount).stream()
                .filter(month -> month.from().equals(from))
                .map(InterestPostingView::interest)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no month beginning on " + from + " has been paid"));
    }
}
