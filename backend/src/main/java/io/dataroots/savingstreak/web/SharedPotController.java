package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.sharedpots.ASharedPot;
import io.dataroots.savingstreak.sharedpots.SharedPotsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * A savings pot that belongs to a group of customers rather than to one of them.
 *
 * <p>Its own paths rather than paths under a customer or under a savings account, because a pot
 * belongs to neither: it is not one customer's, and the account it holds is its own. The one read
 * that <em>is</em> about a customer — the pots somebody belongs to — lives with that customer's
 * other per-customer reads, beside their gifts and their notifications, for the reason all of those
 * are there.
 *
 * <p><strong>This class puts a balance beside a pot, and decides nothing else.</strong> The Shared
 * Pots module owns no money — the euros are deposits in the pot's savings account — so the figure
 * everybody reading a pot actually wants comes from Deposits and is assembled here. That is the same
 * arrangement {@code SavingsGoalController} has with the balance goals are claims against, and
 * assembling an answer is not a rule: nothing about what a pot is, who may open one or what it is
 * called is decided in this class.
 *
 * <p>Beyond that it reads the request and judges nothing. Whether the customer exists, whether the
 * name is a name and whether the pot is there at all are rules, and every one of them belongs to
 * Shared Pots, which refuses on its own and is reported by {@code RefusalsAsHttp}. What is checked
 * here is only whether the body carried the one thing the domain cannot ask about — who is opening
 * the pot — which is the same line every other endpoint in this application draws.
 */
@RestController
@RequestMapping("/api/shared-pots")
class SharedPotController {

    private static final Logger log = LoggerFactory.getLogger(SharedPotController.class);

    private final SharedPotsService pots;

    /** Read for one figure and one only: what a pot's savings account holds. */
    private final DepositsService deposits;

    SharedPotController(SharedPotsService pots, DepositsService deposits) {
        this.pots = pots;
        this.deposits = deposits;
    }

    /**
     * Opens a pot for the customer named in the body, who becomes its owner.
     *
     * <p>Created, with the pot that now exists: the caller asked for something to be there and needs
     * to be able to name it straight away — the invitation it is about to send names the pot — and
     * neither the identifier nor the savings account underneath it is something the caller could
     * have known.
     *
     * <p>In one transaction with the balance it reads back, so the pot and its figure describe the
     * same instant of the ledger. It is nothing, on a pot opened a moment ago; it is read rather
     * than assumed because one shape answers every read of a pot, and a zero written in here would
     * be this class claiming to know something about money.
     */
    @Transactional
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SharedPotResponse openAPot(@RequestBody NewSharedPotRequest request) {
        // Reading the request, not judging it. Whether the customer exists and whether the name is a
        // name are rules, and both belong to Shared Pots, which refuses on its own in words a person
        // can act on. What cannot be left to the rule is a body that never said who is opening the
        // pot, because there is then nobody to refuse on behalf of.
        if (request == null || request.customerId() == null) {
            throw refusingThePot("A pot needs the customer opening it.");
        }
        ASharedPot opened = pots.openAPot(request.customerId(), request.name());
        return SharedPotResponse.of(opened, deposits.moneyBalanceOf(opened.savingsAccountId()));
    }

    /**
     * One pot as its members find it: what it is called, what it holds, and who is in it.
     *
     * <p>In one read transaction, so that the pot's membership and its balance describe the same
     * instant. Two reads a moment apart is how a page comes to show a balance a deposit has just
     * changed beside a membership from before it.
     */
    @Transactional(readOnly = true)
    @GetMapping("/{potId}")
    SharedPotResponse pot(@PathVariable long potId) {
        ASharedPot pot = pots.potWith(potId);
        return SharedPotResponse.of(pot, deposits.moneyBalanceOf(pot.savingsAccountId()));
    }

    /**
     * Who belongs to the pot and what each of them is to it, in the order they joined.
     *
     * <p>A path of its own beside the pot rather than a second shape of the same answer, because a
     * page about who can do what has no use for the balance — and because the membership is the part
     * of a pot that changes most. The same list either way, so the two cannot disagree.
     */
    @GetMapping("/{potId}/members")
    List<PotMemberResponse> membersOf(@PathVariable long potId) {
        return pots.membersOf(potId).stream().map(PotMemberResponse::of).toList();
    }

    /**
     * An owner closes the pot, and the answer says what became of everything in it.
     *
     * <p>A {@code POST} to a path of its own rather than a {@code DELETE} on the pot, because
     * nothing is deleted: the pot, its members, its contributions, its money movements and its
     * proposals all stay readable for ever, and every one of them goes on answering afterwards. What
     * changes is that the pot stops accepting anything new. A {@code DELETE} would promise the
     * opposite of what this does.
     *
     * <p>Not idempotent, and deliberately so: closing a pot that is already closed is refused with a
     * sentence saying it is closed, which is story 67 rather than an oversight. A second click on a
     * stale page is told what happened rather than quietly told it worked.
     *
     * <p>Answered 200 with the record of what happened, not 201 and not 204. Nothing new is there to
     * be addressed, so there is nothing to have created; and a close hands several people a figure
     * none of them typed, so an empty answer would be the one response in this feature that made a
     * page go and look up what it had just done.
     *
     * <p><strong>Nothing is judged here.</strong> Whether the pot exists, whether the caller is an
     * owner, whether it has already been closed, how much of the pot is each member's and where
     * their money goes back to are all rules, and every one of them belongs to Shared Pots, which
     * refuses on its own in words a person can act on and is reported by {@code RefusalsAsHttp}.
     * What is checked here is only whether the body carried the one thing the domain cannot ask
     * about — who is closing the pot — which is the same line every other endpoint in this
     * application draws.
     */
    @PostMapping("/{potId}/close")
    PotClosedResponse closeThePot(@PathVariable long potId,
                                  @RequestBody(required = false) CloseThePotRequest request) {
        if (request == null || request.customerId() == null) {
            throw refusingThePot("Closing a shared pot needs the customer doing it.");
        }
        return PotClosedResponse.of(pots.closeThePot(potId, request.customerId()));
    }

    /**
     * A pot refused before the domain ever sees it, because the body did not say who was opening it.
     * Said out loud as well as answered, for the reason every other refusal here is: the reason
     * reaches whoever asked and nowhere else, and the log is the only copy a reviewer tracing
     * somebody's complaint can read.
     *
     * <p>Kept in the words Shared Pots warns in, so that {@code grep "shared pot rejected"} finds
     * every refused pot whichever side of the domain boundary turned it down.
     *
     * <p>A bad request rather than one of the module's kinds, and the same line the gift endpoint
     * draws: a field that was never filled in is a malformed request, where a customer who is named
     * and does not exist is a 404.
     */
    private ResponseStatusException refusingThePot(String reason) {
        log.warn("shared pot rejected kind=NOT_A_POT reason={}", reason);
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
