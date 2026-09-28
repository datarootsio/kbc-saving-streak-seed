package io.dataroots.savingstreak.budgets;

import java.util.Arrays;
import java.util.List;

/**
 * What became of one monthly budget: it is the figure a category is held to now, it was replaced by
 * a figure its holder preferred, or its holder stopped policing the category altogether.
 *
 * <p><strong>A budget is superseded, never mutated</strong>, which is the whole reason this enum
 * exists rather than an amount that could simply be written over. A customer who raises their
 * grocery budget in June is saying what June is allowed to cost; they are not saying that April was
 * allowed to cost it, and an amount overwritten in place would rewrite every April anybody ever
 * reads. The row that stood in April stays exactly as it was, marked {@link #SUPERSEDED}, and the
 * month read walks whichever row covered the month it was asked about.
 *
 * <p><strong>{@link #STOPPED} is not {@link #SUPERSEDED} with nothing after it.</strong> They are
 * two different things a customer does and they read differently: superseding leaves the category
 * with a figure, and stopping leaves it with none — the category is still one of the words this
 * account's money is described in, and its spending is still counted and reported, but no figure is
 * being held against it. "Not budgeted" is a state and not a nought, which is the same bargain
 * {@code SavingCapacityOnAnAccount} strikes about a capacity nobody has declared.
 *
 * <p>Three values rather than two, and the missing fourth is deliberate: there is no pause. A budget
 * paused for a month is a budget of nothing for that month, which the customer can say already by
 * stopping it and declaring it again — and a pause would be a fourth state that every month in the
 * fold, and the carry chain the next slice builds on it, would then have to have an opinion about.
 *
 * <p>Leaving {@link #STANDING} is one-way in both directions, following bills, saving rules, goals
 * and the categories themselves: nothing resumes, because what a resumed budget would mean for the
 * months it was not standing in is a question with no honest answer. Declaring a figure afresh is
 * always a new row with its own first month.
 *
 * <p>Public, because {@link ABudgetOnACategory} carries it out of the module: whoever draws a budget
 * has to be able to tell the figure in force from the record of one that used to be.
 */
public enum BudgetState {

    /** The figure this category is held to now, and the one a new month takes its allowance from. */
    STANDING,

    /**
     * Replaced by a figure its holder preferred. It still governs the months it covered, which is
     * the whole reason it was kept rather than overwritten.
     */
    SUPERSEDED,

    /**
     * Its holder stopped budgeting the category. The category stands, its spending is still
     * reported, and the months this row covered still quote it — what it no longer does is put a
     * figure on any month after them.
     */
    STOPPED;

    /** Whether this is the figure now in force, which is what every change to a budget asks first. */
    boolean isStillStanding() {
        return switch (this) {
            case STANDING -> true;
            case SUPERSEDED, STOPPED -> false;
        };
    }

    /**
     * The states a budget that is still in force can be in, derived rather than written out twice —
     * the same shape {@code CategoryState.theOnesStillStanding} has, and for the same reason: a
     * value added later must not quietly count as standing in one of the four places that ask.
     */
    static List<BudgetState> theOnesStillStanding() {
        return Arrays.stream(values()).filter(BudgetState::isStillStanding).toList();
    }
}
