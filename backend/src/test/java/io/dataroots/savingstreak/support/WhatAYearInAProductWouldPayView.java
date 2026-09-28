package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * One card of the comparison screen as the API reports it: a product somebody may open an account
 * on, and what a named amount would be worth in it after twelve months. Shared for the same reason
 * as {@link BalancesView}: a second copy of the shape can drift into disagreeing about it, and then
 * one of them is testing a contract nobody serves.
 *
 * <p>The product is the same {@link SavingsProductView} the shelf is read with, because that is
 * what the API nests here — and a test with a second shape for a product would stop noticing the
 * day the two stopped being identical.
 *
 * <p>{@code interestIfTheFloorIsNotKept} is null on every product with no bonus to lose, and a test
 * that expected a figure there would be expecting the backend to invent a choice that does not
 * exist. The tests assert the null as firmly as they assert the figure.
 *
 * <p>The points are three {@code long}s rather than one, because points are whole everywhere in
 * this application and because what the money earns when it lands and what its first anniversary
 * pays are earned on different days — a test asserting only the total could not tell a better
 * multiple from a better anniversary.
 */
public record WhatAYearInAProductWouldPayView(SavingsProductView product, BigDecimal amount,
                                              BigDecimal interest,
                                              BigDecimal balanceAfterTwelveMonths,
                                              boolean theBonusIsInThatFigure,
                                              BigDecimal interestIfTheFloorIsNotKept,
                                              long pointsWhenTheMoneyLands,
                                              long pointsOnItsFirstAnniversary, long points) {
}
