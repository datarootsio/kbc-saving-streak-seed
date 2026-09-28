package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.WhatAYearInAProductWouldPayView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsproducts.AShelfSomebodyIsComparing.FREE_SAVINGS;
import static io.dataroots.savingstreak.savingsproducts.AShelfSomebodyIsComparing.THE_CORE_SAVER;
import static io.dataroots.savingstreak.savingsproducts.AShelfSomebodyIsComparing.THE_TWELVE_MONTH_FIXED;
import static io.dataroots.savingstreak.savingsproducts.AShelfSomebodyIsComparing.THIRTY_TWO_DAY_NOTICE;
import static io.dataroots.savingstreak.savingsproducts.AShelfSomebodyIsComparing.theCardFor;
import static io.dataroots.savingstreak.savingsproducts.ASaverChoosingAProduct.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The comparison itself: a customer types a figure and every product they could open an account on
 * says what it would be worth after twelve months, in euros of interest and in points, in the order
 * the catalogue names them.
 *
 * <p><strong>What is asserted here is what the reading says; that it is true is asserted next
 * door</strong>, by winding a year and running the sweep. The two are deliberately separate tests:
 * this one would pass against a projection that was internally consistent and wrong, and that one
 * would pass against a projection that was right about one product and silent about the other
 * three.
 *
 * <p><strong>Nothing here types a rate.</strong> Every figure is either read back off the card's
 * own terms, compared with another card, or compared with what the application itself says a
 * deposit earned — because the seed's rates are the seed's, and a test that wrote 2.40% into itself
 * would pass or fail on what somebody published last Tuesday rather than on whether the comparison
 * is sound.
 *
 * <p><strong>The points half is conformance-tested without the clock.</strong> A deposit says what
 * it earned when it landed and what its first anniversary will pay, both worked out by the modules
 * that own those rules, so a projection promising different figures is caught by a deposit made a
 * second later rather than by a year of winding. That is the multiplier rule and the anniversary
 * rule quoted rather than restated, asserted as a fact rather than argued in a javadoc.
 *
 * <p>Its own application, because one of these tests closes a product to new accounts: the run's
 * shared database is pinned by two other tests to four products at four rates, and a door shut in
 * the middle of it would be shut for every test afterwards. It is put back on sale inside the test
 * that shut it, so nothing in this class depends on the order the others run in.
 */
class TheProductsComparedOnWhatEachWouldPayApiTest extends ApiIntegrationTest {

    /** Comfortably above the core saver's floor, so that the bonus is a figure and not a maybe. */
    private static final String A_FIGURE_ABOVE_THE_FLOOR = "1200.00";

    /** Under it, so that the core saver's two readings become one honest one. */
    private static final String A_FIGURE_UNDER_THE_FLOOR = "100.00";

    private static AShelfSomebodyIsComparing app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AShelfSomebodyIsComparing(
                aDatabaseFileThatDoesNotExistYet("saving-streak-products-compared"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void every_product_says_what_the_amount_would_earn_in_euros_and_in_points_in_the_catalogues_order() {
        List<WhatAYearInAProductWouldPayView> compared =
                app.whatAYearWouldPayOn(A_FIGURE_ABOVE_THE_FLOOR);

        assertThat(compared.stream().map(card -> card.product().code()))
                .as("the catalogue's own order, which is the only order this screen offers — "
                        + "nothing here ranks products by what they pay")
                .containsExactlyElementsOf(app.shelf().stream()
                        .filter(SavingsProductView::openToNewAccounts)
                        .map(SavingsProductView::code)
                        .toList());

        for (WhatAYearInAProductWouldPayView card : compared) {
            assertThat(card.amount())
                    .as("%s quotes back the figure that was typed", card.product().code())
                    .isEqualByComparingTo(A_FIGURE_ABOVE_THE_FLOOR);
            assertThat(card.interest())
                    .as("%s pays something in euros over a year", card.product().code())
                    .isGreaterThan(BigDecimal.ZERO);
            assertThat(card.balanceAfterTwelveMonths())
                    .as("%s ends the year holding the amount and its interest",
                            card.product().code())
                    .isEqualByComparingTo(
                            new BigDecimal(A_FIGURE_ABOVE_THE_FLOOR).add(card.interest()));
            assertThat(card.points())
                    .as("%s says what the money is worth in points as well as in euros, and the "
                            + "total is the two days it is earned on added up",
                            card.product().code())
                    .isEqualTo(card.pointsWhenTheMoneyLands() + card.pointsOnItsFirstAnniversary())
                    .isGreaterThan(0);
            // The card carries the rate, the condition and the loyalty benefits beside the figure,
            // because the weighing is the rate against what is asked for and not the figure alone.
            assertThat(card.product().currentTerms().annualRatePercent())
                    .as("%s says what it pays", card.product().code())
                    .isNotNull();
            assertThat(card.product().currentTerms().pointsMultiplier())
                    .as("%s says what a euro saved into it is worth", card.product().code())
                    .isNotNull();
            assertThat(card.product().currentTerms().anniversaryRatePercent())
                    .as("%s says what it pays for money staying put", card.product().code())
                    .isNotNull();
            assertThat(card.product().description())
                    .as("%s says what it asks for in words a person reads",
                            card.product().code())
                    .isNotBlank();
        }

        assertThat(theCardFor(compared, THE_TWELVE_MONTH_FIXED).interest())
                .as("the best rate on the shelf pays the most euros, which is what a year of "
                        + "lock-in buys")
                .isGreaterThan(theCardFor(compared, FREE_SAVINGS).interest());
        assertThat(theCardFor(compared, THE_TWELVE_MONTH_FIXED).points())
                .as("and it pays the most points too, on both of the days points are earned")
                .isGreaterThan(theCardFor(compared, FREE_SAVINGS).points());
    }

    @Test
    void the_minimum_balance_product_is_projected_with_the_floor_kept_and_without_it() {
        WhatAYearInAProductWouldPayView keptTheFloor = theCardFor(
                app.whatAYearWouldPayOn(A_FIGURE_ABOVE_THE_FLOOR), THE_CORE_SAVER);

        assertThat(keptTheFloor.theBonusIsInThatFigure())
                .as("a figure above the floor is projected with the bonus in it")
                .isTrue();
        assertThat(keptTheFloor.interestIfTheFloorIsNotKept())
                .as("and the headline rate alone is quoted beside it, because the core saver's "
                        + "headline figure means nothing on its own")
                .isNotNull()
                .isLessThan(keptTheFloor.interest());

        // The three products with no bonus have nothing to lose and say so with an absence rather
        // than with a second figure identical to the first.
        List<WhatAYearInAProductWouldPayView> compared =
                app.whatAYearWouldPayOn(A_FIGURE_ABOVE_THE_FLOOR);
        for (String code : List.of(FREE_SAVINGS, THIRTY_TWO_DAY_NOTICE, THE_TWELVE_MONTH_FIXED)) {
            WhatAYearInAProductWouldPayView card = theCardFor(compared, code);
            assertThat(card.interestIfTheFloorIsNotKept())
                    .as("%s has no floor to fall under, so there is no second figure", code)
                    .isNull();
            assertThat(card.theBonusIsInThatFigure())
                    .as("%s has no bonus to have in the figure", code)
                    .isFalse();
        }

        WhatAYearInAProductWouldPayView underTheFloor = theCardFor(
                app.whatAYearWouldPayOn(A_FIGURE_UNDER_THE_FLOOR), THE_CORE_SAVER);
        assertThat(underTheFloor.theBonusIsInThatFigure())
                .as("an amount smaller than the floor cannot keep it, and is projected honestly "
                        + "without the bonus rather than promised one it could never earn")
                .isFalse();
        assertThat(underTheFloor.interest())
                .as("so the two readings are one figure for this amount")
                .isEqualByComparingTo(underTheFloor.interestIfTheFloorIsNotKept());
    }

    @Test
    void the_points_a_projection_promises_are_the_points_the_application_actually_credits() {
        WhatAYearInAProductWouldPayView promised = theCardFor(
                app.whatAYearWouldPayOn(A_FIGURE_ABOVE_THE_FLOOR), THE_TWELVE_MONTH_FIXED);

        ASaverChoosingAProduct saver = app.aSaver("somebody taking the twelve-month fixed");
        long locked = saver.open(THE_TWELVE_MONTH_FIXED).id();
        DepositView landed = app.payInto(saver, locked, A_FIGURE_ABOVE_THE_FLOOR);

        assertThat(landed.multiplierApplied())
                .as("the first deposit of a run is paid the ordinary rate for the weeks, so the "
                        + "whole of the uplift is the product's — which is what the projection "
                        + "supposes, because a shelf belongs to nobody")
                .isEqualByComparingTo(landed.productMultiplierApplied());
        assertThat(landed.pointsEarned())
                .as("the points the ledger actually credited are the points the card promised")
                .isEqualTo(promised.pointsWhenTheMoneyLands());
        assertThat(landed.nextAnniversaryPoints())
                .as("and what the loyalty rule says this deposit's first anniversary will pay is "
                        + "what the card promised for it")
                .isEqualTo(promised.pointsOnItsFirstAnniversary());
    }

    @Test
    void a_product_closed_to_new_accounts_is_not_offered_as_something_to_open() {
        assertThat(app.whatAYearWouldPayOn(A_FIGURE_ABOVE_THE_FLOOR).stream()
                .map(card -> card.product().code()))
                .as("while it is on sale it is one of the cards")
                .contains(THIRTY_TWO_DAY_NOTICE);

        app.close(THIRTY_TWO_DAY_NOTICE);
        try {
            assertThat(app.whatAYearWouldPayOn(A_FIGURE_ABOVE_THE_FLOOR).stream()
                    .map(card -> card.product().code()))
                    .as("a product nobody may open an account on is not something to compare, "
                            + "because a figure beside it is an invitation to choose it")
                    .doesNotContain(THIRTY_TWO_DAY_NOTICE);
            assertThat(app.shelf().stream().map(SavingsProductView::code))
                    .as("and it is still in the catalogue, marked closed, because customers are "
                            + "holding it")
                    .contains(THIRTY_TWO_DAY_NOTICE);
        } finally {
            app.reopen(THIRTY_TWO_DAY_NOTICE);
        }

        assertThat(app.whatAYearWouldPayOn(A_FIGURE_ABOVE_THE_FLOOR).stream()
                .map(card -> card.product().code()))
                .as("put back on sale, it is back on the comparison")
                .contains(THIRTY_TWO_DAY_NOTICE);
    }

    @Test
    void a_figure_that_is_not_an_amount_of_money_is_refused_in_words() {
        ResponseEntity<JsonNode> nothing = app.tryToProjectNothingAtAll();
        assertThat(nothing.getStatusCode())
                .as("a box to go back and fill in rather than anything in an unexpected state")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(nothing)).contains("needs an amount of money");

        ResponseEntity<JsonNode> nought = app.tryToProject("0.00");
        assertThat(nought.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(nought))
                .as("the same sentence a deposit of nothing gets, because there is one rule in "
                        + "this application about what an amount of money is")
                .contains("more than zero")
                .contains("0.00");

        ResponseEntity<JsonNode> finerThanACent = app.tryToProject("10.005");
        assertThat(finerThanACent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(finerThanACent)).contains("at most two decimal places");

        ResponseEntity<JsonNode> withAComma = app.tryToProject("25,00");
        assertThat(withAComma.getStatusCode())
                .as("a question about the characters rather than about the figure, and the "
                        + "sentence quotes them back so the comma is visible")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(withAComma)).contains("25,00").contains("25.00");
    }
}
