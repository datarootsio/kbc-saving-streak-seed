package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.savingsproducts.ACoreSaverSomebodyHolds.ACoreSaver;
import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.InterestPostingView;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.savingsproducts.ACoreSaverSomebodyHolds.THE_CORE_SAVER;
import static io.dataroots.savingstreak.savingsproducts.ACoreSaverSomebodyHolds.THE_INTEREST_SWEEP;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The core saver earns what it promises: a month that kept the floor is paid the headline rate and
 * the bonus, a month that went under it for a single day is paid the headline rate alone, and the
 * month after that is paid in full again — while nothing anywhere refuses a withdrawal or keeps a
 * cent of anybody's money back.
 *
 * <p><strong>The last clause is the one this class exists to nail down.</strong> A minimum balance
 * is the one condition in this catalogue that withholds rather than refuses, and the careless
 * reading of it does both: a floor arm in the withdrawal rules and a bonus withheld by the sweep.
 * That reading cannot even be self-consistent, because a refused withdrawal never takes a balance
 * under a floor, so the bonus would never be withheld by anything and the second half of the rule
 * would be dead code that looked alive. {@link #emptying_a_core_saver_completely_is_refused_by_nothing}
 * is the test that keeps it out: a customer takes every euro of a floored account and the
 * application hands it over.
 *
 * <p><strong>Nothing here is written into the test except the amounts it pays in.</strong> The day
 * the periods are counted from, the version the account is on, the headline rate, the bonus rate
 * and the floor are all read back through the API — because the assertion worth making is that the
 * two rates are <em>added together</em> on a month that earned it, and a test that typed 1.50% would
 * pass against an application that had simply published a higher headline rate.
 *
 * <p><strong>And the arithmetic is restated rather than read off the posting.</strong>
 * {@link AMonthOfInterest} is the spec's sentence written out once — the average daily balance at a
 * twelfth of the annual rate, floored to the cent — and what these tests compare is what the
 * application paid against what that sentence says at the rate it should have used. The average
 * itself is taken off the posting, because whether an average is averaged correctly is the subject
 * of ticket 04's tests and not of these; what is under test here is which rate that average was
 * multiplied by.
 *
 * <p>Its own application, for the reason {@link ACoreSaverSomebodyHolds} gives: it winds the clock
 * two whole months, and a clock cannot be wound back.
 */
class ABonusIsPaidForKeepingTheFloorWithheldForDippingUnderItAndRefusesNothingApiTest
        extends ApiIntegrationTest {

    private static ACoreSaverSomebodyHolds app;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new ACoreSaverSomebodyHolds(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-bonus-and-the-floor"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * A month whose balance never went under the floor is paid the headline rate and the bonus
     * rate, and the posting says the bonus was earned.
     *
     * <p>The two rates added together is the whole assertion, and it is made against the figures
     * the account's own version publishes rather than against a total. The posting is the other
     * half of it: a customer asking why this month paid nearly twice what free savings would have
     * paid reads "the bonus was earned" and the lowest balance that earned it, on the row.
     */
    @Test
    void a_month_that_kept_the_floor_is_paid_the_headline_rate_and_the_bonus() {
        ACoreSaver theirs = app.aCoreSaverOpenedToday("somebody keeping the floor");
        AnAgreementView agreement = app.theAgreementOf(theirs.savingsAccountId());
        TermsVersionView version = app.theVersionOf(THE_CORE_SAVER, agreement.version());
        assertThat(agreement.minimumBalance())
                .describedAs("a core saver has a floor to keep, or there is no rule here to test")
                .isGreaterThan(BigDecimal.ZERO);
        assertThat(version.bonusRatePercent())
                .describedAs("and a bonus to earn for keeping it")
                .isGreaterThan(BigDecimal.ZERO);

        // Comfortably over the floor on the day the period begins, and never touched again.
        theirs.payIn("600.00");
        app.aWholePeriodPassesFor(agreement.openedOn(), 1);
        app.runJob(THE_INTEREST_SWEEP);

        BigDecimal withTheBonus = version.annualRatePercent().add(version.bonusRatePercent());
        assertThat(app.interestPaidInto(theirs.savingsAccountId()))
                .singleElement()
                .satisfies(month -> {
                    assertThat(month.lowestDailyBalance())
                            .describedAs("it held six hundred euros on every day of the month")
                            .isEqualByComparingTo("600.00");
                    assertThat(month.lowestDailyBalance())
                            .describedAs("which never went under the floor")
                            .isGreaterThanOrEqualTo(agreement.minimumBalance());
                    assertThat(month.bonusEarned()).isTrue();
                    assertThat(month.annualRatePercent())
                            .describedAs("the headline rate and the bonus, added")
                            .isEqualByComparingTo(withTheBonus);
                    assertThat(month.interest()).isEqualByComparingTo(
                            AMonthOfInterest.onABalanceOf(month.averageDailyBalance(),
                                    withTheBonus));
                    assertThat(month.interest())
                            .describedAs("which is more than the headline rate alone would have "
                                    + "paid, or the bonus is not worth keeping a floor for")
                            .isGreaterThan(AMonthOfInterest.onABalanceOf(
                                    month.averageDailyBalance(), version.annualRatePercent()));
                });
    }

    /**
     * A balance that dipped under the floor on one day of the month costs that month's bonus, and
     * nothing else at all: the money is untouched and the month afterwards is paid in full.
     *
     * <p><strong>Two months in one test on purpose.</strong> "The next month starts clean" is not a
     * fact about a month, it is a fact about the boundary between two of them, and the only way to
     * say it is to wind the clock across both and put the two payments side by side. The dip lasts
     * a single day and the money is back before the month ends — which is exactly the case the
     * lowest daily balance exists to catch, and which a rule judging the closing balance, or the
     * average, would have paid the bonus on.
     */
    @Test
    void a_single_days_dip_costs_that_months_bonus_and_the_month_after_it_is_paid_in_full() {
        ACoreSaver theirs = app.aCoreSaverOpenedToday("somebody who dips for a day");
        AnAgreementView agreement = app.theAgreementOf(theirs.savingsAccountId());
        TermsVersionView version = app.theVersionOf(THE_CORE_SAVER, agreement.version());
        BigDecimal withTheBonus = version.annualRatePercent().add(version.bonusRatePercent());
        LocalDate openedOn = agreement.openedOn();

        theirs.payIn("600.00");
        // Down to four hundred for one day, in the middle of the month, and back again.
        app.daysPass(5);
        theirs.takeOut("200.00");
        app.daysPass(1);
        theirs.payIn("200.00");
        app.aWholePeriodPassesFor(openedOn, 1);
        app.runJob(THE_INTEREST_SWEEP);
        // And a whole month more, with nothing moving in it at all.
        app.aWholePeriodPassesFor(openedOn, 2);
        app.runJob(THE_INTEREST_SWEEP);

        List<InterestPostingView> months = app.interestPaidInto(theirs.savingsAccountId());
        assertThat(months).hasSize(2);
        InterestPostingView dipped = months.get(0);
        InterestPostingView kept = months.get(1);

        assertThat(dipped.lowestDailyBalance())
                .describedAs("the one day it went down to four hundred is what the month is judged "
                        + "on, although it closed on six hundred")
                .isEqualByComparingTo("400.00");
        assertThat(dipped.lowestDailyBalance()).isLessThan(agreement.minimumBalance());
        assertThat(dipped.bonusEarned()).isFalse();
        assertThat(dipped.annualRatePercent())
                .describedAs("the headline rate alone")
                .isEqualByComparingTo(version.annualRatePercent());
        assertThat(dipped.interest()).isEqualByComparingTo(AMonthOfInterest.onABalanceOf(
                dipped.averageDailyBalance(), version.annualRatePercent()));

        assertThat(kept.periodOrdinal()).isEqualTo(dipped.periodOrdinal() + 1);
        assertThat(kept.lowestDailyBalance())
                .describedAs("nothing moved in the month after it, so it stayed where the first "
                        + "month left it")
                .isGreaterThanOrEqualTo(agreement.minimumBalance());
        assertThat(kept.bonusEarned())
                .describedAs("the following month starts clean: a dip is not carried forward")
                .isTrue();
        assertThat(kept.annualRatePercent()).isEqualByComparingTo(withTheBonus);
        assertThat(kept.interest()).isEqualByComparingTo(AMonthOfInterest.onABalanceOf(
                kept.averageDailyBalance(), withTheBonus));

        assertThat(kept.interest())
                .describedAs("two months of the same account, wound through on the same clock, "
                        + "paying two different amounts because one of them dipped")
                .isGreaterThan(dipped.interest());
        assertThat(app.balanceOf(theirs.savingsAccountId()))
                .describedAs("and the dip cost the bonus and not one cent of their own money: "
                        + "everything paid in is still there, with both months' interest on top")
                .isEqualByComparingTo(new BigDecimal("600.00")
                        .add(dipped.interest()).add(kept.interest()));
    }

    /**
     * A withdrawal that empties a core saver to nothing at all is refused by nothing and takes
     * nothing.
     *
     * <p><strong>The characteristic bug this test exists to fail on.</strong> A floor is the one
     * condition in this catalogue that stands between nobody and their money: it withholds a
     * bonus. An implementation that also refused the withdrawal would be refusing this request,
     * and would additionally have made its own withholding unreachable — a balance that is never
     * allowed under the floor never goes under the floor.
     *
     * <p>Emptied completely rather than merely taken under the floor, because the whole balance is
     * the strongest version of the request and the one a customer closing an account makes. No
     * charge, no fee, no rounding: the account holds nought afterwards and every cent of it is in
     * the current account it came from.
     */
    @Test
    void emptying_a_core_saver_completely_is_refused_by_nothing() {
        ACoreSaver theirs = app.aCoreSaverOpenedToday("somebody taking it all back");
        AnAgreementView agreement = app.theAgreementOf(theirs.savingsAccountId());
        assertThat(agreement.productCode()).isEqualTo(THE_CORE_SAVER);
        assertThat(agreement.minimumBalance())
                .describedAs("the account under test has a floor, or this proves nothing")
                .isGreaterThan(BigDecimal.ZERO);

        theirs.payIn("600.00");
        BigDecimal everything = app.balanceOf(theirs.savingsAccountId());

        assertThat(app.tryToTakeOut(theirs, everything.toPlainString()).getStatusCode().value())
                .describedAs("taking every euro out of an account with a five hundred euro floor: "
                        + "a minimum balance withholds a bonus and refuses nothing")
                .isEqualTo(201);
        assertThat(app.balanceOf(theirs.savingsAccountId()))
                .describedAs("and it took nothing on the way: the account is empty, not overdrawn "
                        + "and not short of a penalty")
                .isEqualByComparingTo("0.00");
    }

    /**
     * An account on a product with no bonus rate is paid exactly what it was paid before this rule
     * existed: the headline rate, and a posting saying no bonus was earned.
     *
     * <p>Three of the four products have no bonus and no floor, and the reading that decides the
     * rate has to leave every one of them alone — a condition nobody set must not be vacuously met
     * and quietly paid for. The account is the one this customer was opened with, which is on free
     * savings, so the comparison is between two accounts of one person on the same clock.
     *
     * <p>It says {@code bonusEarned} is false rather than true, and that is the deliberate reading:
     * there was no bonus to earn here, so nothing was earned. A flag that said true whenever a
     * condition that did not exist was not broken would be true on every free savings month ever
     * posted and would tell a reader nothing at all.
     */
    @Test
    void a_product_with_no_bonus_rate_is_paid_the_headline_rate_and_claims_no_bonus() {
        ACoreSaver theirs = app.aCoreSaverOpenedToday("somebody on free savings as well");
        long freeSavings = theirs.theirFreeSavingsAccount();
        AnAgreementView agreement = app.theAgreementOf(freeSavings);
        TermsVersionView version = app.theVersionOf(agreement.productCode(), agreement.version());
        assertThat(version.bonusRatePercent())
                .describedAs("free savings offers no bonus, which is what this test is about")
                .isEqualByComparingTo("0.00");
        assertThat(agreement.minimumBalance())
                .describedAs("and asks for no floor")
                .isEqualByComparingTo("0.00");

        theirs.saver().payIn(freeSavings, "600.00");
        app.aWholePeriodPassesFor(agreement.openedOn(), 1);
        app.runJob(THE_INTEREST_SWEEP);

        assertThat(app.interestPaidInto(freeSavings))
                .singleElement()
                .satisfies(month -> {
                    assertThat(month.bonusEarned()).isFalse();
                    assertThat(month.annualRatePercent())
                            .isEqualByComparingTo(version.annualRatePercent());
                    assertThat(month.interest()).isEqualByComparingTo(
                            AMonthOfInterest.onABalanceOf(month.averageDailyBalance(),
                                    version.annualRatePercent()));
                });
    }
}
