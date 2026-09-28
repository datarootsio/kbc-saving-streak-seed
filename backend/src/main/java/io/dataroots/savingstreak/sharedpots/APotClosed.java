package io.dataroots.savingstreak.sharedpots;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A shared pot brought to an end, as the rest of the application reads it: which pot, when it
 * closed, what it held at that moment, what each member was given back, what the group had been
 * saving for and what it was still deciding.
 *
 * <p><strong>Everything closing did, in one answer.</strong> Closing is the only act in this feature
 * that does several things at once — it settles every member, abandons every live goal and ends
 * every waiting proposal — and a caller told only that it succeeded would have to go and read four
 * screens to find out what happened to the money. The record of it is the receipt, and the pot it
 * describes is the only thing this application will never let anybody change again.
 *
 * <p><strong>A settlement per member, including the members settled nothing.</strong> A viewer who
 * only ever watched, and a contributor whose euros have all gone out in an approved withdrawal, are
 * each here with nought beside them and no withdrawal against them — an answer rather than an
 * absence, for the reason a pot's contributions list keeps them too. Dropping the rows that moved no
 * money would make a close look as though it had overlooked somebody.
 *
 * <p><strong>What the pot held, and that it now holds nothing.</strong> The two are quoted side by
 * side because their difference is the whole of what the close moved, and because "the pot holds
 * nothing once it is closed" is the claim the arithmetic has to be checkable against: what it held
 * is what the settlements add up to, to the cent, since both are summed from the very same deposits.
 *
 * <p>The goals and the proposals travel as identifiers rather than as their own records, because
 * what a reader wants of them here is that they were dealt with — and each is readable in full,
 * afterwards, on the screen that owns it. A closed pot's goals are still readable as abandoned, and
 * its proposals are still in its list.
 *
 * <p>Who closed it is deliberately not here, for the reason a settlement does not carry who decided:
 * it is in the log line and in which request arrived, and a page has nothing different to draw for
 * one owner than for another.
 */
public record APotClosed(long potId, String potName, Long savingsAccountId, Instant closedAt,
                         BigDecimal thePotHeld, BigDecimal thePotNowHolds,
                         List<ASettlement> settledTo, List<Long> goalsAbandoned,
                         List<Long> proposalsEnded) {
}
