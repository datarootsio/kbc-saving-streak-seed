package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.sharedpots.SharedPotsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Changing what somebody is to a shared pot.
 *
 * <p>Its own class rather than another method on {@code SharedPotController}, for the reason
 * {@code PotInvitationController} is its own class: a membership is a thing rather than a property
 * of a pot — it is identified by the pot and the customer together, it is acted on rather than only
 * read, and the acts on it are going to grow. Reading who is in a pot stays with the pot, because
 * that read is a description of the pot and belongs beside its balance and its name; changing
 * somebody's role is a change to one membership, and it belongs here.
 *
 * <p>A {@code PUT} on the role rather than on the membership, because the role is the whole of what
 * can be changed: a request that replaced the membership would have to say when somebody joined, and
 * when they joined is the pot's history rather than anybody's to send. Sent twice it means what it
 * meant once, which is what {@code PUT} promises and what an owner's double click needs.
 *
 * <p><strong>Nothing is judged here.</strong> Whether the pot exists, whether the caller is its
 * owner, whether the customer named is in the pot, whether the role is a role and whether the pot
 * would be left without an owner are all rules, and every one of them belongs to Shared Pots, which
 * refuses on its own in words a person can act on and is reported by {@code RefusalsAsHttp}. What is
 * checked here is only whether the body carried the one thing the domain cannot ask about — who is
 * making the change — which is the same line every other endpoint in this application draws.
 *
 * <p>The answer is the membership as it now reads, so that the page that sent it can redraw the row
 * it just changed without going back for the list.
 */
@RestController
@RequestMapping("/api/shared-pots/{potId}/members")
class PotMemberController {

    private static final Logger log = LoggerFactory.getLogger(PotMemberController.class);

    private final SharedPotsService pots;

    PotMemberController(SharedPotsService pots) {
        this.pots = pots;
    }

    /**
     * An owner changes a member's role, and the membership comes back with the role it now holds.
     *
     * <p>The member is named in the path and the owner in the body, which is the contract this
     * feature carries: the thing being changed is identified by where it is, and the person acting
     * travels with the request because there is nothing else in this application to say who they
     * are.
     */
    @PutMapping("/{customerId}/role")
    PotMemberResponse changeTheRole(@PathVariable long potId, @PathVariable long customerId,
                                    @RequestBody(required = false) PotRoleChangeRequest request) {
        // Reading the request, not judging it. Whether the role is a role, whether the caller may
        // change one and whether the pot would keep an owner are rules, and all three belong to
        // Shared Pots. What cannot be left to the rule is a body that never said who is making the
        // change, because there is then nobody to refuse on behalf of.
        if (request == null || request.customerId() == null) {
            throw refusingTheChange("NOT_A_ROLE_CHANGE",
                    "Changing a role needs the customer making the change.");
        }
        return PotMemberResponse.of(
                pots.changeTheRoleOf(potId, customerId, request.customerId(), request.role()));
    }

    /**
     * A member leaves the pot, or an owner removes them, and what was still theirs comes back to a
     * current account of theirs.
     *
     * <p>A {@code DELETE} on the membership, because the membership is what ends. The money coming
     * back is not a second act to be asked for separately: nobody loses money in this design, so
     * there is no departure without a settlement and no endpoint that could offer one.
     *
     * <p><strong>Three identifiers and no body.</strong> The member is in the path, because their
     * membership is the thing being deleted; who is doing it and where the money goes travel as
     * query parameters, because a {@code DELETE} has no body to carry them in and that is the
     * contract every other {@code DELETE} in this feature already carries. The two customers are
     * deliberately different parameters with different names — the one in the path is who is
     * leaving, and the one in the query is who decided — which is the whole difference between
     * leaving and being removed.
     *
     * <p><strong>Nothing is judged here.</strong> Whether the pot exists, whether the caller may
     * remove somebody else, whether the member is in the pot, whether the pot would be left without
     * an owner, whose current account the money would go to and how much of the pot is theirs are
     * all rules, and every one of them belongs to Shared Pots, which refuses on its own in words a
     * person can act on and is reported by {@code RefusalsAsHttp}. What is read here is only what
     * the domain cannot be asked about: whether the request said who is acting and where the money
     * should land.
     *
     * <p>The answer is the settlement, so that the page which sent it can say what came back without
     * going to look for a movement it would have to match up by hand.
     */
    @DeleteMapping("/{customerId}")
    PotSettlementResponse leaveThePot(@PathVariable long potId,
                                      @PathVariable("customerId") long memberCustomerId,
                                      @RequestParam(required = false) Long customerId,
                                      @RequestParam(required = false) Long toCurrentAccountId) {
        if (customerId == null) {
            throw refusingTheChange("NOT_A_DEPARTURE",
                    "Leaving a shared pot needs the customer doing it.");
        }
        if (toCurrentAccountId == null) {
            throw refusingTheChange("NOT_A_DEPARTURE",
                    "Leaving a shared pot needs the current account to settle what is still theirs "
                            + "into.");
        }
        return PotSettlementResponse.of(
                pots.leaveThePot(potId, memberCustomerId, customerId, toCurrentAccountId));
    }

    /**
     * A change to a membership refused before the domain ever sees it, because the request did not
     * carry something the domain cannot ask about — who is making it, or where a settlement should
     * land. Said out loud as well as answered, for the reason every other refusal here is: the
     * reason reaches whoever asked and nowhere else, and the log is the only copy a reviewer tracing
     * somebody's complaint can read.
     *
     * <p>Kept in the words Shared Pots warns in, so that {@code grep "shared pot rejected"} finds
     * every refusal whichever side of the domain boundary turned the request down.
     *
     * <p>A bad request rather than one of the module's kinds, and the same line every other endpoint
     * in this feature draws: a field that was never filled in is a malformed request, where a
     * customer who is named and does not exist is a 404.
     */
    private ResponseStatusException refusingTheChange(String kind, String reason) {
        log.warn("shared pot rejected kind={} reason={}", kind, reason);
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
