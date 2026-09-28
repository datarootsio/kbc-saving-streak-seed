package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.goals.ASuggestedReallocation;

/**
 * What an account has to say about moving money between its goals, as the API reports it: whether
 * there is anything worth suggesting, the whole of it in one sentence, and the moves.
 *
 * <p><strong>{@code worthSuggesting} is a field, and a page reads it rather than the length of
 * {@code moves}.</strong> "Nothing to suggest" has four different reasons behind it — no goals at all,
 * nobody has said what they can put away in a week, every goal is arriving in time, or a goal is late
 * and nothing below it is holding anything — and a page handed an empty array would have to invent
 * one. {@code inWords} is the sentence to print instead of an empty table; when there is something to
 * suggest it is the same sentence doing the other job, saying how many moves, how much altogether and
 * which goals they are for.
 *
 * <p>One shape for the suggestion read and for what accepting says it applied, so that a page which
 * has just taken the advice is reading the same fields it drew the list from. What it is <em>not</em>
 * is a plan the client hands back: accepting names no move and carries no identifier, because the
 * moves are worked out again at the moment of acceptance and yesterday's advice is never what gets
 * applied.
 */
record SuggestedReallocationResponse(long savingsAccountId, boolean worthSuggesting, String inWords,
                                     List<SuggestedMoveResponse> moves) {

    static SuggestedReallocationResponse of(ASuggestedReallocation suggestion) {
        return new SuggestedReallocationResponse(
                suggestion.savingsAccountId(),
                suggestion.worthSuggesting(),
                suggestion.inWords(),
                suggestion.moves().stream().map(SuggestedMoveResponse::of).toList());
    }
}
