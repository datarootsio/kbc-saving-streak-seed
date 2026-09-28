package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One deposit a move would come out of: which row, how much of it would leave, what would be left
 * in it, and when it landed.
 *
 * <p><strong>What a move would do, not what it has done.</strong> Nothing here has happened. It is
 * the answer to the only question a customer weighing a move actually has — which of my euros are
 * about to have their loyalty clock restarted, and what is left behind counting towards the old one
 * — and it is answered before anything is confirmed rather than explained afterwards.
 *
 * <p><strong>The amount left behind is here because a forfeit is worked out from it.</strong> What
 * an anniversary pays is a fraction of what is still in the deposit, so the cost of moving half a
 * deposit is what its anniversary pays now less what it would pay on the half that stayed. A record
 * carrying only what leaves would make the caller subtract it from a remaining amount it would have
 * to fetch separately — and the two readings would be a moment apart, which is exactly how the
 * figure quoted and the figure charged come to differ.
 *
 * <p>The moment it landed is here for the same caller: an anniversary falls on the deposit's own
 * date, and whoever prices a forfeit has to be able to say <em>which day</em> is being given up
 * rather than only how many points.
 *
 * <p>Both amounts are quoted to the cent, so a caller can add these up or print one without
 * deciding again how many places money has.
 *
 * <p>Public because it crosses the boundary to the Loyalty module, which owns what an anniversary
 * pays and knows nothing about how a savings ledger is drawn down. This module answers which euros;
 * that one answers what they were worth.
 */
public record MoneyAMoveWouldTake(long depositId, BigDecimal taken, BigDecimal leftInItAfterwards,
                                  Instant depositedAt) {
}
