package io.dataroots.savingstreak.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One dated thing in a branch of a future: the day, what kind of thing it is, and the one figure
 * that kind carries.
 *
 * <p><strong>The rows are for reading down; these are what the customer is actually deciding
 * about.</strong> A month row says what a month came to. It cannot say that the holiday arrives in
 * June, that the run of six weeks ends on a Sunday in March, or that the four hundred euros paid
 * back in November earn nothing at all — and those are the sentences somebody chooses a branch on.
 * Twelve balances and a list of dated events answer two different questions, which is why the answer
 * carries both and adds neither into the other.
 *
 * <p><strong>Three fields, and the third is a figure rather than a sentence.</strong> There is no
 * name here, no goal identifier and no wording. A kind and a figure are what a page turns into the
 * sentence its own language file holds, and an event carrying prose would be this module writing a
 * screen — the same bargain {@code TimelineEvent} strikes for the markers on an account's bar. The
 * cost is real and is accepted: two goals reached in one year are two events distinguishable only by
 * their day and their target, and a page that wants to name the goal has the goals themselves on the
 * snapshot beside this list. Adding a goal to the record would have made a withdrawal falling short
 * and a batch expiring carry a field that means nothing to either.
 *
 * <p><strong>{@code figure} is money on some kinds and points on others</strong>, and which is which
 * is {@link AKindOfThingThatHappens}'s to say: the amount that earned nothing, the points that went,
 * the points a bonus paid, what a short withdrawal managed to take, a goal's target, and the run of
 * weeks that ended. A {@code BigDecimal} carries all six honestly — money in this application is
 * never a double and points are whole, so a whole number in a decimal is the narrower of the two
 * losses. Two fields, one for money and one for points, would have left every event carrying a null
 * and every reader deciding which one to look at.
 *
 * <p><strong>Never worth nothing.</strong> An anniversary that pays no points is not a bonus, a
 * Sunday that ends no run is not a week lost, and a deposit that earns nothing on nothing is not
 * money arriving. That is the same reading {@code TimelineEvent} gives about its own bar, and it is
 * what keeps a year's list short enough to read.
 *
 * <p>The day can be one already gone. Points waiting on tonight's sweep and an anniversary the sweep
 * has not caught are both dated on the day the fold acts on them, which is the day the window opens
 * — the application is about to do the same thing tonight, and a branch that showed them a year out
 * would be denying something that is already owed.
 *
 * <p>A day rather than a moment, for the reason every dated answer in this application gives: the
 * day is what is promised, and which day a moment falls on depends on a zone that is the backend's
 * to settle rather than the browser's.
 */
public record AThingThatHappens(LocalDate on, AKindOfThingThatHappens kind, BigDecimal figure) {

    /** A thing that happened worth a number of points, which is the whole of what those kinds carry. */
    public static AThingThatHappens worth(LocalDate on, AKindOfThingThatHappens kind, long points) {
        return new AThingThatHappens(on, kind, BigDecimal.valueOf(points));
    }
}
