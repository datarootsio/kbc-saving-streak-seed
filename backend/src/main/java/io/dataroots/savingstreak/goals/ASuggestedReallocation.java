package io.dataroots.savingstreak.goals;

import java.util.List;

/**
 * What an account has to say about moving money between its goals: whether there is anything worth
 * suggesting, the whole of it in one sentence, and the moves themselves.
 *
 * <p><strong>{@code worthSuggesting} is a field and not the emptiness of {@code moves}.</strong>
 * "There is nothing to suggest" has four different reasons behind it — no goals at all, nobody has
 * said what they can save in a week, every goal is arriving in time, or a goal is late and no goal
 * below it is holding anything — and a caller handed an empty list would have to invent one of them.
 * {@code inWords} is the sentence, and a page prints it instead of drawing an empty table.
 *
 * <p>When there is something to suggest, {@code inWords} is the same sentence doing the other job:
 * how many moves, how much altogether and which goals they are for, which is what somebody repeats
 * back to themselves before deciding.
 *
 * <p><strong>Nothing in here is stored anywhere.</strong> It is derived on the read that answers with
 * it — see {@link AReallocationWorthSuggesting} — so it carries no identifier of its own and cannot
 * be fetched again. Accepting it does not accept <em>this</em>: the moves are worked out afresh at the
 * moment of acceptance, so advice that went stale between reading and pressing the button is never
 * what gets applied.
 */
public record ASuggestedReallocation(long savingsAccountId, boolean worthSuggesting, String inWords,
                                     List<ASuggestedMove> moves) {
}
