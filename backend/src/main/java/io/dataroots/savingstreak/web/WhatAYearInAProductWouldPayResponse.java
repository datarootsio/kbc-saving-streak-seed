package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.products.AProjectionOverTwelveMonths;

/**
 * One card of the comparison screen: a product a customer may open an account on, and what the
 * figure they typed would be worth in it after twelve months — in euros of interest and in points.
 *
 * <p><strong>The product travels inside the projection rather than beside it</strong>, and it is
 * the same {@link SavingsProductResponse} the shelf is drawn from rather than a flattened copy of
 * its fields. One shape for "a savings product" is what stops the comparison and the catalogue from
 * drifting into disagreeing about what a rate or a condition is, and it means a page that can
 * already draw a product card can draw this one with the figures added underneath.
 *
 * <p><strong>Only products open to new accounts are in this list at all</strong>, which is the
 * backend's decision and not the page's. A closed product is still in {@code /api/savings-products}
 * because customers hold it; it is absent here because this is the screen somebody opens an account
 * from, and offering a figure for an agreement nobody will sign would be inviting them to choose
 * it. A page that drew a disabled card would have had to be told which rule it was obeying, and
 * this way there is nothing for it to obey.
 *
 * <p><strong>{@link #interestIfTheFloorIsNotKept} is null on three products out of four, and that
 * null is the message.</strong> Absent means there is no floor to fall under and therefore nothing
 * to lose: the one figure beside it is the whole answer. Present means the product pays a bonus for
 * keeping a balance, {@link #interest} is what keeping it is worth and this is what letting it slip
 * for a month costs — the headline rate of a minimum-balance product means nothing on its own, and
 * a card showing it alone would be quoting the number the customer is least likely to get.
 * {@link #theBonusIsInThatFigure} says which of the two {@link #interest} actually is, because an
 * amount smaller than the floor cannot keep it and is honestly projected without the bonus.
 *
 * <p><strong>The points are sent as three figures rather than one.</strong> What the money earns on
 * the day it lands is the product's multiple; what its first anniversary pays a year later is the
 * product's loyalty rate; the total is the two added. A card that showed only the total would hide
 * that half of a notice account's advantage arrives in twelve months' time, which is exactly the
 * thing somebody choosing between a year's lock-in and instant access is weighing.
 *
 * <p>Amounts go out as figures rather than as formatted text, the way every other money field in
 * this API does: how a euro is printed belongs to whoever is printing it, and a page that has to
 * add two of them together cannot be handed sentences.
 */
record WhatAYearInAProductWouldPayResponse(SavingsProductResponse product, BigDecimal amount,
                                           BigDecimal interest,
                                           BigDecimal balanceAfterTwelveMonths,
                                           boolean theBonusIsInThatFigure,
                                           BigDecimal interestIfTheFloorIsNotKept,
                                           long pointsWhenTheMoneyLands,
                                           long pointsOnItsFirstAnniversary, long points) {

    static WhatAYearInAProductWouldPayResponse of(AProjectionOverTwelveMonths projected) {
        return new WhatAYearInAProductWouldPayResponse(
                SavingsProductResponse.of(projected.product()),
                projected.amount(),
                projected.interest(),
                projected.balanceAfterTwelveMonths(),
                projected.theBonusIsInThatFigure(),
                projected.interestIfTheFloorIsNotKept(),
                projected.pointsWhenTheMoneyLands(),
                projected.pointsOnItsFirstAnniversary(),
                projected.points());
    }
}
