package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * What an account says about moving money between its goals, as the API reports it and so as a test
 * reads it: whether there is anything worth suggesting, the whole of it in one sentence, and the
 * moves.
 *
 * <p>{@code worthSuggesting} is read as its own field rather than inferred from an empty list, because
 * that distinction is the acceptance criterion: "no suggestion" is a real answer with a sentence
 * behind it, and a test that only counted the moves could not tell it from an empty list dressed up
 * as one.
 */
public record SuggestedReallocationView(Long savingsAccountId, boolean worthSuggesting, String inWords,
                                        List<SuggestedMoveView> moves) {

    /** The amounts added up, for the tests that assert what a whole suggestion would move. */
    public BigDecimal moving() {
        return moves.stream().map(SuggestedMoveView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** What is suggested into one goal altogether, for a test asserting on the goal being helped. */
    public BigDecimal movingInto(long goalId) {
        return moves.stream()
                .filter(move -> move.intoGoalId() == goalId)
                .map(SuggestedMoveView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
