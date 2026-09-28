package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;

/**
 * How much of a deposit is new saving: the part of it that takes the customer above the most they
 * have ever had in savings.
 *
 * <p>A point is paid for a euro saved, and a euro saved twice is one euro. Without this rule the
 * same hundred euros could be paid in, earned on, taken back out and paid in again for as long as
 * somebody had the patience — the money ending exactly where it started and the points climbing
 * every lap. This is the one place that says which euros of a deposit have already been paid for.
 *
 * <p>The mark it is measured against is the most the customer has ever held, which is the sum of
 * what every deposit of theirs has earned on. Those two are the same figure by construction: a
 * deposit earns on whatever it adds above the mark, so the mark afterwards is the higher of what it
 * was and what the customer now holds, and it never falls. Taking money out therefore leaves it
 * exactly where it was, which is the whole of why a withdrawal can leave the points ledger alone.
 *
 * <p>Nothing is ever taken back. Points earned are the customer's, a balance cannot go negative, and
 * money coming back out costs them nothing — it simply does not earn a second time on the way back
 * in. The alternative was to take the points back when the money left and, for anybody who had
 * already spent them on a reward, to leave them owing points they could not pay. A savings
 * application should not put somebody in debt for using their savings.
 *
 * <p>Whose mark it is: the customer's, across every savings account they hold, because their points
 * are one pot. A mark kept per account would let the same euros be walked from one account to the
 * next and earn once in each.
 *
 * <p><strong>Only the money the customer put there counts, on both sides of the subtraction.</strong>
 * A savings account now holds euros the bank added as well — a month's interest, written into the
 * same ledger — and not one of them is money anybody saved. Counted into the mark they would raise
 * it and quietly reduce what the next real deposit earns; counted into what is held they would fill
 * in part of the gap an earlier withdrawal left and pay for the same euros twice. The figures handed
 * in are both asked of the customer's own rows, in {@code DepositRepository}, where each of those
 * two sentences is written beside the query that keeps it.
 *
 * <p>Everything here is euros rather than points. What a euro is worth is the Points module's rule
 * and this has no opinion about it; which euros earn at all is this module's, because it is the one
 * that knows what has been saved and what has since left.
 *
 * <p><strong>Public for the simulator's fold, and this is the rule it most needed.</strong> A branch
 * that takes five hundred euros out and pays them back in earns <em>nothing</em> on the way back,
 * and a customer who believes a withdrawal is a loan against their own points is wrong in a way the
 * application could not tell them until afterwards. The fold cannot call {@code
 * DepositsService.deposit} — that method reads the clock and writes the ledger — so it restates the
 * three steps that method is made of, and this is the first of them. Restating <em>this</em> one as
 * well would have put the high-water mark in two places, and the projection would have kept paying
 * on euros the ledger had already paid for: the exact mistake the whole feature exists to warn a
 * customer about. The figures it is judged against come off the snapshot the fold was handed; what
 * counts as new saving is decided here and nowhere else.
 */
public final class TheMostEverSaved {

    private TheMostEverSaved() {
    }

    /**
     * The part of {@code amount} that is new saving: what it adds above the mark, never more than
     * the deposit itself and never less than nothing.
     *
     * <p>Three cases, and the arithmetic gives all three without a branch. A customer whose savings
     * are at their peak has a mark equal to what they hold, so the whole deposit is new. One who has
     * taken money out has a mark above what they hold, so the deposit fills the gap back up first
     * and only the excess earns. One who has taken out more than this deposit puts back is still
     * below the mark afterwards, and the deposit earns nothing at all.
     *
     * @param amount        what is being paid in now
     * @param stillSaved    what the customer holds across their savings accounts before this deposit
     * @param everEarnedOn  the mark: everything their deposits have earned on so far, which is the
     *                      most they have ever held
     */
    public static BigDecimal newSavingIn(BigDecimal amount, BigDecimal stillSaved,
                                         BigDecimal everEarnedOn) {
        BigDecimal aboveTheMark = stillSaved.add(amount).subtract(everEarnedOn);
        return aboveTheMark.max(BigDecimal.ZERO).min(amount);
    }
}
