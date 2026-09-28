package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a rule nobody has saved would do if it fired this minute, as the API reports it — which is
 * exactly as a test reads it.
 *
 * <p>{@code outcome} is read as the word the API sends rather than mapped onto an enumeration of the
 * test's own, for the reason {@link GoalView} gives. It is the same word the occurrence would carry,
 * which is what a test asserting "this rule would be short" is really asserting.
 *
 * <p>{@code shortfall} is boxed because it is filled on one outcome only, and a test that could not
 * see the absence could not tell "short of nothing" from "short of EUR 0,00".
 */
public record DryRunView(LocalDate asAt, BigDecimal balance, String outcome,
                         WhatWouldMoveView wouldMove, BigDecimal shortfall) {
}
