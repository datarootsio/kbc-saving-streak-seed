package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * One move worth making as the API reports it, which is exactly as a test reads it: out of that goal,
 * into this one, how much, and the reason it is being suggested.
 *
 * <p>Both ends carry a name beside their identifier, and neither identifier is ever null: this
 * suggestion only ever moves money between goals, so a test that read a null here would be reading a
 * rule that had changed.
 *
 * <p>{@code reason} is asserted on rather than skipped over, because "each suggested move carries the
 * reason it is suggested, naming the goal it helps" is an acceptance criterion and a sentence nobody
 * checks is a sentence that quietly stops naming anything.
 */
public record SuggestedMoveView(Long outOfGoalId, String outOfGoalName, Long intoGoalId,
                                String intoGoalName, BigDecimal amount, String reason) {
}
