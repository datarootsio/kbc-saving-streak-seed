package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a rule would move and where it would land, as the API reports it — which is exactly as a test
 * reads it.
 *
 * <p>{@code amount} and {@code anIllustrationRatherThanAPromise} are read together and never apart,
 * because the flag is what says whether the figure is the customer's own instruction or a worked
 * example off today's balance. A test that read the figure alone would pass against an application
 * that quoted a sweep twelve months out as a promise, which is the one thing this preview must never
 * do.
 *
 * <p>{@code floor} is boxed, and so is {@code amount}: exactly one kind of rule fills each of them,
 * and a test that read the other back as 0.00 would pass the very assertion it exists to make.
 *
 * <p>{@code intoGoals} and {@code leftUnallocated} are added up by the tests that read them: they
 * come to exactly {@code amount}, every time, which is the promise the split makes.
 */
public record WhatWouldMoveView(BigDecimal amount, BigDecimal floor,
                                boolean anIllustrationRatherThanAPromise,
                                List<GoalShareView> intoGoals, BigDecimal leftUnallocated) {
}
