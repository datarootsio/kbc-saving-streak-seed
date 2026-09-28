package io.dataroots.savingstreak.simulation;

import java.util.List;

/**
 * Everything one asking comes back with: the present every branch was folded from, and one
 * turned-out future per branch.
 *
 * <p><strong>The two travel together because they were read together.</strong> The snapshot is taken
 * in one read transaction so that every figure in it describes one instant of the ledger, and the
 * branches are folded from that one snapshot; handing a caller the branches and making them ask for
 * the present separately would be a second transaction, a second instant, and a screen whose "where
 * you are now" column could disagree with the twelve months beside it. One question, one answer.
 *
 * <p><strong>The do-nothing future is always the first of them, and it is always there.</strong> A
 * projection with nothing to compare against answers no question — "you will have EUR 4 210 in
 * September" is a number, and "EUR 4 210 rather than the EUR 3 890 you were heading for" is an
 * argument. So it is computed whether or not anybody asked about anything, which also means a
 * request carrying no branch at all still comes back with the one branch a customer is actually
 * living in.
 *
 * <p>Nothing here was written down. The whole of this answer is derived on every asking and thrown
 * away, which is the promise the feature rests on and the reason there is no identifier on a branch
 * to ask about again later.
 */
public record TheFuturesOfThisAccount(TheStartingPoint standing,
                                      List<HowAScenarioTurnsOut> scenarios) {

    /** Copied on the way in, so that an answer cannot be edited after it was worked out. */
    public TheFuturesOfThisAccount {
        scenarios = List.copyOf(scenarios);
    }
}
