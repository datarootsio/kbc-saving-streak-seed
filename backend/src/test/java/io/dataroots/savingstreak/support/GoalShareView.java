package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * One line of a rule's split as a preview reports it, which is exactly as a test reads it: which
 * goal, the whole percentage its holder gave it, and what that share comes to out of the figure
 * being quoted.
 *
 * <p>{@link RuleAllocationView} is the same sentence in the past tense — what a goal actually
 * received on a night the money moved — and a test reads the two apart on purpose: a preview says
 * what the instruction comes to, and an occurrence says where the money went.
 *
 * <p>Every line of the split is here, including one whose share comes to nothing out of a small
 * figure, which is the opposite of {@link RuleAllocationView}. A test that finds a share of 0.00
 * here is seeing the contract rather than an omission.
 */
public record GoalShareView(long goalId, int share, BigDecimal amount) {
}
