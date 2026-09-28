package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.InterestPostingView;
import io.dataroots.savingstreak.support.WhatAYearInAProductWouldPayView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.savingsproducts.AShelfSomebodyIsComparing.FREE_SAVINGS;
import static io.dataroots.savingstreak.savingsproducts.AShelfSomebodyIsComparing.THE_CORE_SAVER;
import static io.dataroots.savingstreak.savingsproducts.AShelfSomebodyIsComparing.THE_INTEREST_SWEEP;
import static io.dataroots.savingstreak.savingsproducts.AShelfSomebodyIsComparing.theCardFor;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The figure the comparison screen promises is the figure the bank actually pays: a customer is
 * shown what twelve months would earn, the money goes in, the clock is wound a year, the sweep is
 * run, and the two agree to the cent.
 *
 * <p><strong>This is the test the whole ticket exists for.</strong> A comparison screen is the
 * second place a rate gets priced, and the second place a rate is priced is the first place two
 * rates disagree. Every other test here asserts that the projection says something sensible; only
 * this one asserts that what it says is <em>true</em> — and it does so by playing the year out
 * through the doors a trainer would use rather than by comparing two pieces of arithmetic with each
 * other. Nothing in it restates a rate, a twelfth or a flooring: both figures come out of the
 * application, one before the year and one after it.
 *
 * <p><strong>Two products rather than one, and the core saver is the interesting one.</strong> Free
 * savings is the simple case — one rate, no condition — and it is here so that a failure can be
 * told apart from one about the bonus. The core saver is the case that would catch a projection
 * quietly pricing at the headline rate alone, or judging a floor it should have kept: the amount is
 * well above the floor, nothing ever leaves the account, so every one of the twelve months earns
 * its bonus and the projection has to have said so.
 *
 * <p><strong>Compounding is the thing most likely to be got wrong, and a year is what exposes
 * it.</strong> Interest is paid into the account that earned it and is therefore in the next
 * month's average, so a projection that took a twelfth of a year's simple interest would be a few
 * cents short after twelve months and exactly right after one. The cent comparison is the point:
 * {@code isEqualByComparingTo} on the sum of what was actually posted, not a tolerance.
 *
 * <p><strong>The projection is read before the money moves and before the clock does.</strong> That
 * is the order a customer does it in — they look at the shelf, they choose, and only then is there
 * an account — and it means the figure under test was worked out with no account in existence at
 * all, which is what a projection is.
 *
 * <p>Its own application, for the reason {@link AShelfSomebodyIsComparing} gives: a year passes in
 * it.
 */
class AProjectionMatchesWhatAWoundForwardClockActuallyPaysApiTest extends ApiIntegrationTest {

    /**
     * What is typed into the comparison and then paid in, to the cent.
     *
     * <p>Well above the core saver's five hundred euro floor, so that the bonus is earned every
     * month and the account has somewhere to fall from — a figure at the floor would be kept by an
     * account that never moved and would say nothing about whether the floor was even looked at.
     */
    private static final String THE_AMOUNT = "1200.00";

    private static final int MONTHS_IN_A_YEAR = 12;

    private static AShelfSomebodyIsComparing app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AShelfSomebodyIsComparing(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-projection-against-the-clock"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_interest_a_projection_promises_is_what_a_wound_forward_year_actually_pays() {
        ASaverChoosingAProduct onTheCoreSaver = app.aSaver("somebody weighing the core saver");
        long coreSaver = onTheCoreSaver.open(THE_CORE_SAVER).id();
        ASaverChoosingAProduct onFreeSavings = app.aSaver("somebody weighing free savings");
        long freeSavings = onFreeSavings.open(FREE_SAVINGS).id();

        // Read before a cent has moved, which is when a customer actually reads it.
        List<WhatAYearInAProductWouldPayView> promised = app.whatAYearWouldPayOn(THE_AMOUNT);
        WhatAYearInAProductWouldPayView theCoreSaverWasPromised =
                theCardFor(promised, THE_CORE_SAVER);
        WhatAYearInAProductWouldPayView freeSavingsWasPromised =
                theCardFor(promised, FREE_SAVINGS);
        assertThat(theCoreSaverWasPromised.theBonusIsInThatFigure())
                .as("the amount is above the floor, so the figure promised includes the bonus")
                .isTrue();
        assertThat(theCoreSaverWasPromised.interest())
                .as("the core saver's headline and bonus together beat free savings, which is the "
                        + "whole reason somebody would keep a floor")
                .isGreaterThan(freeSavingsWasPromised.interest());

        onTheCoreSaver.payIn(coreSaver, THE_AMOUNT);
        onFreeSavings.payIn(freeSavings, THE_AMOUNT);
        LocalDate openedOn = onTheCoreSaver.agreementOn(coreSaver).openedOn();

        app.aWholeYearPassesFrom(openedOn);
        app.runJob(THE_INTEREST_SWEEP);

        List<InterestPostingView> theCoreSaversYear = app.interestPaidInto(coreSaver);
        assertThat(theCoreSaversYear)
                .as("a year wound forward is twelve months judged")
                .hasSize(MONTHS_IN_A_YEAR);
        assertThat(theCoreSaversYear).allSatisfy(month -> assertThat(month.bonusEarned())
                .as("nothing left the account, so every month kept the floor")
                .isTrue());
        assertThat(whatWasActuallyPaidInto(coreSaver))
                .as("the core saver's projection promised EUR %s and the year has now been played "
                        + "out", theCoreSaverWasPromised.interest())
                .isEqualByComparingTo(theCoreSaverWasPromised.interest());
        assertThat(app.balanceOf(coreSaver))
                .as("and the balance the projection said the account would hold is the balance it "
                        + "holds")
                .isEqualByComparingTo(theCoreSaverWasPromised.balanceAfterTwelveMonths());

        assertThat(app.interestPaidInto(freeSavings)).hasSize(MONTHS_IN_A_YEAR);
        assertThat(whatWasActuallyPaidInto(freeSavings))
                .as("and the simple case agrees too, which is what tells a failure above apart "
                        + "from one about the bonus")
                .isEqualByComparingTo(freeSavingsWasPromised.interest());
        assertThat(app.balanceOf(freeSavings))
                .isEqualByComparingTo(freeSavingsWasPromised.balanceAfterTwelveMonths());
    }

    /**
     * What the twelve postings came to, added up from what the account's own page reports.
     *
     * <p>The postings rather than the balance, because the balance is the amount plus the interest
     * and the projection promises the interest on its own. Both are asserted all the same: a sum
     * that matched while the balance did not would mean money had arrived from somewhere nobody
     * wrote down.
     */
    private static BigDecimal whatWasActuallyPaidInto(long savingsAccountId) {
        return app.interestPaidInto(savingsAccountId).stream()
                .map(InterestPostingView::interest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
