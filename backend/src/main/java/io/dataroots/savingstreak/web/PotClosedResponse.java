package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.dataroots.savingstreak.sharedpots.APotClosed;

/**
 * A shared pot brought to an end as the API reports it: which pot, when it closed, what it held at
 * that moment, what it holds now, what each member was given back, and what closing did to the
 * goals and the proposals it left behind.
 *
 * <p><strong>Everything the one request did, in one answer</strong>, because closing is the one
 * request in this feature that does several things at once. A page that was told only "closed"
 * would have to go and read the pot, the contributions, the goals and the proposals to find out
 * what became of the money — and the person who clicked it wants to see what each of them got back
 * before they navigate anywhere.
 *
 * <p>{@code thePotNowHolds} is nought and says so as 0.00 rather than as an absence, the same way a
 * pot just opened reports its balance. The two figures are quoted side by side because their
 * difference is the whole of what moved, and because the settlements add up to it exactly: every
 * figure here is summed from the very same deposits.
 *
 * <p>The settlements are the shape a departure already reports, so nothing rendering "what came
 * back to you" has to know whether it is looking at somebody who left or at a pot that ended. A
 * member settled nothing is here with 0.00 and no withdrawal against them, which is an answer.
 *
 * <p>The goals and the proposals travel as identifiers, because what a reader wants of them here is
 * that they were dealt with: each is readable in full on the screen that owns it, and a closed pot's
 * goals and proposals both go on answering.
 *
 * <p>Both money figures are quoted to the cent, for the reason a pot's balance is: a figure that has
 * been through SQLite comes back as 500.5 — it has no decimal type and keeps an amount as a float —
 * and a page that had to decide how many places money has would be deciding it again.
 */
record PotClosedResponse(long potId, String potName, Long savingsAccountId, Instant closedAt,
                         BigDecimal thePotHeld, BigDecimal thePotNowHolds,
                         List<PotSettlementResponse> settledTo, List<Long> goalsAbandoned,
                         List<Long> proposalsEnded) {

    static PotClosedResponse of(APotClosed closed) {
        return new PotClosedResponse(closed.potId(), closed.potName(), closed.savingsAccountId(),
                closed.closedAt(), closed.thePotHeld(), closed.thePotNowHolds(),
                closed.settledTo().stream().map(PotSettlementResponse::of).toList(),
                closed.goalsAbandoned(), closed.proposalsEnded());
    }
}
