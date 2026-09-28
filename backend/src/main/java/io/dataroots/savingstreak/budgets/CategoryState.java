package io.dataroots.savingstreak.budgets;

import java.util.Arrays;
import java.util.List;

/**
 * Whether a spending category is still one of the things a customer's money goes on, or has been
 * ended for good.
 *
 * <p>Two values rather than three, and the missing one is deliberate: there is no pause. A saving
 * rule has one because stopping your own saving for a month is a thing a customer decides; a
 * category is a word for money that leaves, and a month in which nothing went on groceries is a
 * month with nothing recorded against Groceries rather than a category that was asleep. Inventing a
 * pause here would invent a state the arithmetic below would then have to have an opinion about.
 *
 * <p>{@link #ENDED} is a closing rather than a deletion — the same shape {@code BillState},
 * {@code RuleState} and {@code GoalState} use, and for the same reason: what was spent under a
 * category is the explanation for money that has already left a current account, and "what did
 * March go on" has to stay answerable after the customer has stopped using the word. An ended
 * category stops existing for every purpose except its history.
 *
 * <p><strong>Ending is one-way.</strong> A category brought back from ended would have a gap in the
 * middle of it that no month in this module could describe — was the budget running in those months
 * or not? — and the honest way to start describing that spending again is to declare it again,
 * which is a new category with its own identifier and its own first month.
 *
 * <p>Public, because {@link ADeclaredCategory} carries it out of the module: whoever draws a
 * category has to be able to tell one that is standing from one that was ended, and a page that had
 * to infer it from the presence of an ending moment would be reading the absence of a column.
 */
public enum CategoryState {

    /** Standing: it is on the account's list, it can be renamed, and money can be filed under it. */
    STANDING,

    /** Ended. It has left the list, and its name and everything spent under it remain readable. */
    ENDED;

    /**
     * Whether a category in this state is still a word the customer is using rather than only a
     * record of one they used to.
     *
     * <p>A switch <em>expression</em> rather than a comparison against {@link #ENDED}, for the
     * reason {@code BillState.isStillStanding} gives: "is this still standing" is asked by the list
     * a customer reads, by the cap on how many one account may carry, by the name that may stand
     * only once, and by every refusal about a category that is over — four copies of it would be
     * four chances for a value added later to be quietly counted as standing.
     */
    boolean isStillStanding() {
        return switch (this) {
            case STANDING -> true;
            case ENDED -> false;
        };
    }

    /**
     * The states a category that is still standing can be in, derived rather than written out
     * twice.
     */
    static List<CategoryState> theOnesStillStanding() {
        return Arrays.stream(values()).filter(CategoryState::isStillStanding).toList();
    }
}
