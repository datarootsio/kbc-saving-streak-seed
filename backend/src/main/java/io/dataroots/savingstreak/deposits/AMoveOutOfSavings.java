package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;

/**
 * What the source half of a move produced: which row the money left as, and how much of it arrives
 * on the other side already paid for.
 *
 * <p><strong>Two figures that have to travel together, which is why this is a record and not a
 * pair of return values.</strong> The row is what the record of money that moved is assembled from;
 * the figure is what the arriving deposit is written with, and it is the whole of why a move costs
 * the customer no points and pays them none. Handed back separately — or worked out again on the
 * other side — the two would be free to disagree, and the one that drifted would be the high-water
 * mark, which nobody looks at until the day a deposit earns nothing for no visible reason.
 *
 * <p>{@code earnedOnCarriedAcross} is euros rather than points, like every earned-on figure in this
 * module: what a euro is worth is the Points module's rule and this one has no opinion about it.
 * It is the sum of what the rows the move emptied had already been paid for, plus every euro of a
 * month's interest it took, which was never anybody's to be paid for.
 *
 * <p>Package-private, because it is one half of an operation nothing outside this package may take
 * half of. What the rest of the application is told about a move is
 * {@link AMoveBetweenSavingsAccounts}.
 */
record AMoveOutOfSavings(long withdrawalId, BigDecimal earnedOnCarriedAcross) {
}
