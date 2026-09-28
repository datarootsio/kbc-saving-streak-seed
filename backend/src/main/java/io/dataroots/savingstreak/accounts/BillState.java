package io.dataroots.savingstreak.accounts;

import java.util.Arrays;
import java.util.List;

/**
 * Whether a recurring bill is still leaving the account every month, or has been ended for good.
 *
 * <p>Two values rather than three, and the missing one is deliberate: there is no pause. A saving
 * rule has one because stopping your own saving for a month is a thing a customer decides; a rent
 * is not, and a customer who has stopped paying one has ended it. Inventing a pause here would be
 * inventing a sentence nobody says about a direct debit.
 *
 * <p>{@link #ENDED} is a closing rather than a deletion — the same shape {@code RuleState} and
 * {@code GoalState} use, and for the same reason: a bill that has been taken is the explanation for
 * money that has already left a current account, and "what happened to my money
 * last year" has to stay answerable after the instruction that did it is gone. An ended bill stops
 * existing for every purpose except its history.
 *
 * <p><strong>Ending is one-way.</strong> A bill brought back from ended would have a gap in the
 * middle of it that nothing here could describe — was the rent owed for those months or not? — and
 * the honest way to start paying something again is to declare it again.
 *
 * <p>Public, because {@link ADeclaredBill} carries it out of the module: whoever draws a bill has
 * to be able to tell one that is standing from one that was ended, and a page that had to infer it
 * from the presence of an ending moment would be reading the absence of a column.
 */
public enum BillState {

    /** Standing: it is on the account's list of what goes out, and it is what the night will take. */
    STANDING,

    /** Ended. It has left the list, and its name, its day and its amount remain readable. */
    ENDED;

    /**
     * Whether a bill in this state is still an instruction rather than only a record.
     *
     * <p>A switch <em>expression</em> rather than a comparison against {@link #ENDED}, for the
     * reason {@code RuleState.isStillStanding} gives: "is this still standing" is asked by the list
     * a customer reads, by the cap on how many one account may carry, and by every refusal about a
     * bill that is over, and three copies of it would be three chances for a value added later to
     * be quietly counted as standing.
     */
    boolean isStillStanding() {
        return switch (this) {
            case STANDING -> true;
            case ENDED -> false;
        };
    }

    /** The states a bill that is still standing can be in, derived rather than written out twice. */
    static List<BillState> theOnesStillStanding() {
        return Arrays.stream(values()).filter(BillState::isStillStanding).toList();
    }
}
