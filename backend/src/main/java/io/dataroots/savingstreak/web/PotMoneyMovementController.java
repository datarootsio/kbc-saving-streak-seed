package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.deposits.MoneyMovementsService;
import io.dataroots.savingstreak.sharedpots.ASharedPot;
import io.dataroots.savingstreak.sharedpots.SharedPotsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Everything that has moved in or out of a shared pot, listed the way a customer's own money
 * movements are.
 *
 * <p><strong>The same list, in the same shape, in the same words.</strong> A pot's history is the
 * Deposits module's ledger about the pot's savings account, and it comes back in
 * {@link MoneyMovementResponse} — the record a customer's own history already comes back in — so a
 * member who has read their own money movements has nothing new to learn. That sameness is the whole
 * of what this endpoint is for, and it is why the rows are not reshaped on the way out.
 *
 * <p><strong>Assembled here because this module owns no money.</strong> Shared Pots holds the pot,
 * its membership and the rules about who may do what to it; the euros are deposits and withdrawals
 * in the Deposits module and always were. The web layer is where the two can be put side by side,
 * which is the same arrangement that puts a balance beside a pot and a balance beside a goal.
 *
 * <p><strong>Its own class rather than another method on {@code SharedPotController}</strong>, for
 * the reason the contributions read is its own: a pot's history is a screen of its own, it grows
 * with every euro that moves, and nothing reading a pot's name wants it.
 *
 * <p>Who is asking travels as {@code ?customerId=} and is insisted on by Shared Pots: a pot's
 * history says what two other people have done with their money, so only its members may read it. A
 * request that names nobody is nobody and is refused in the same words a stranger is.
 */
@RestController
@RequestMapping("/api/shared-pots/{potId}/money-movements")
class PotMoneyMovementController {

    private static final Logger log = LoggerFactory.getLogger(PotMoneyMovementController.class);

    private final SharedPotsService pots;

    /** Read for one thing: what has moved in and out of the account the pot holds. */
    private final MoneyMovementsService movements;

    PotMoneyMovementController(SharedPotsService pots, MoneyMovementsService movements) {
        this.pots = pots;
        this.movements = movements;
    }

    /**
     * The pot's movements, newest first, as any of its members reads them.
     *
     * <p>One savings account, because a pot has exactly one and it is the pot's own. A customer's
     * ledger merges every account they hold along with their bills and their spends; a pot has
     * neither bills nor groceries — those hang off a current account and a pot has none — so this
     * list is the savings movements and nothing else, which is why it is assembled in three lines
     * rather than in thirty.
     *
     * <p><strong>Nothing here was made by a saving rule, and the {@code false} says so.</strong>
     * Saving rules count against the limit of the customer who holds the account, and a pot is held
     * by nobody — so a pot is filled by hand, by members pressing a button, and every row is
     * somebody's decision. It is a statement rather than a hedge, and the day a rule can fire into a
     * pot is the day this line has to be asked of the Automation module instead.
     *
     * <p>In one read transaction, so that the membership the request is judged against and the
     * ledger it answers with describe the same instant.
     */
    @Transactional(readOnly = true)
    @GetMapping
    List<MoneyMovementResponse> moneyMovementsOf(@PathVariable long potId,
                                                 @RequestParam(required = false) Long customerId) {
        ASharedPot pot = pots.thePotAMemberMayRead(potId, customerId, "read the money movements of");
        List<MoneyMovementResponse> entries =
                movements.movementsAcross(List.of(pot.savingsAccountId())).stream()
                        .map(moved -> MoneyMovementResponse.of(moved, false))
                        .toList();
        // The stretch of time the pot's ledger covers and how much of it there is, so that a history
        // somebody says is missing a payment can be checked against what was read. Counts and two
        // moments rather than a line per row, for the reason a customer's own ledger gives: this
        // runs on every read of the page and one line per movement would bury the business events.
        log.debug("pot money movements read potId={} savingsAccountId={} readByCustomerId={} "
                        + "movements={} assembledOver={}..{}",
                potId, pot.savingsAccountId(), customerId, entries.size(),
                entries.isEmpty() ? null : entries.get(entries.size() - 1).movedAt(),
                entries.isEmpty() ? null : entries.get(0).movedAt());
        return entries;
    }
}
