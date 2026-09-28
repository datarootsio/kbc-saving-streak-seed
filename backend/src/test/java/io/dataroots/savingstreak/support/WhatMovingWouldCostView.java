package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the API says moving money to another savings account would cost, read before anything has
 * moved.
 *
 * <p>The whole of the price of a move is in here, which is the point of the reading existing: a
 * move earns nothing and loses nothing, secures no week and breaks no streak, and leaves the most
 * its holder has ever saved exactly where it was — so the loyalty clock is the only thing a
 * customer is actually being asked to weigh, and a test asserting that they were told before they
 * pressed is asserting the honesty of the feature.
 *
 * <p>{@code soonestAnniversaryGivenUp} is null when the move would give up no anniversary at all,
 * which a test reads as the real answer it is rather than as a missing figure.
 */
public record WhatMovingWouldCostView(long fromSavingsAccountId, long toSavingsAccountId,
                                      BigDecimal amount, LocalDate newAnniversary,
                                      long pointsOnTheNewAnniversary,
                                      LocalDate soonestAnniversaryGivenUp, long pointsGivenUp,
                                      int depositsItWouldDrawDown) {
}
