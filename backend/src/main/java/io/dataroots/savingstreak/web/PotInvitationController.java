package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.sharedpots.SharedPotsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Asking somebody to join a shared pot, and the four things that can become of the question.
 *
 * <p>Its own class rather than four more methods on {@code SharedPotController}, because an
 * invitation is a thing rather than a property of a pot: it has an identifier, a life of its own,
 * two lists it appears in, and three endpoints that act on one of them. Under the pot's path, though
 * — {@code /api/shared-pots/{potId}/invitations} — because an invitation belongs to exactly one pot
 * and the pot is part of what identifies it. The one read that is <em>not</em> under a pot is the
 * panel an invited customer reads, which lives with that customer's other per-customer reads for the
 * reason all of those are there.
 *
 * <p><strong>Nothing is judged here.</strong> Whether the pot exists, whether the caller is its
 * owner, whether anybody banks under the address, whether the role is a role and whether the
 * invitation has been answered already are all rules, and every one of them belongs to Shared Pots,
 * which refuses on its own in words a person can act on and is reported by {@code RefusalsAsHttp}.
 * What is checked here is only whether the request carried the things the domain cannot ask about —
 * who is acting, and the address an invitation is going to — which is the same line every other
 * endpoint in this application draws.
 *
 * <p>The acting customer arrives in the body of each POST and as a query parameter on the DELETE,
 * which is the contract this feature carries throughout: a pot belongs to no customer, so there is
 * no customer in its path to act under, and a DELETE has no body to put one in.
 *
 * <p>An answer comes back as the invitation in its new state rather than as an empty 204, including
 * from the DELETE — the same shape ending a saving rule and abandoning a goal already answer with.
 * The page that sent it is showing a list of invitations and needs the row it just changed.
 */
@RestController
@RequestMapping("/api/shared-pots/{potId}/invitations")
class PotInvitationController {

    private static final Logger log = LoggerFactory.getLogger(PotInvitationController.class);

    private final SharedPotsService pots;

    PotInvitationController(SharedPotsService pots) {
        this.pots = pots;
    }

    /**
     * Invites a customer into the pot by the address they bank under, with the role the invitation
     * grants.
     *
     * <p>Created, with the invitation that is now waiting: the caller needs to be able to name it
     * straight away — the row they are about to show has a revoke button on it — and neither the
     * identifier nor the moment it was sent is something they could have known.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PotInvitationResponse invite(@PathVariable long potId,
                                 @RequestBody NewPotInvitationRequest request) {
        // Reading the request, not judging it. Whether anybody banks under the address, whether the
        // caller may invite at all and whether the role is a role are rules, and all three belong to
        // Shared Pots. What cannot be left to the rule is a body that never said who is asking,
        // because there is then nobody to refuse on behalf of.
        if (request == null || request.customerId() == null) {
            throw refusingTheInvitation("An invitation needs the customer sending it.");
        }
        if (request.contactDetails() == null || request.contactDetails().isBlank()) {
            throw refusingTheInvitation(
                    "An invitation needs the email address of the customer it is going to.");
        }
        return PotInvitationResponse.of(
                pots.invite(potId, request.customerId(), request.contactDetails(), request.role()));
    }

    /**
     * Every invitation the pot has issued, in any state, oldest first — the owner's record of who has
     * been asked and what came of it.
     */
    @GetMapping
    List<PotInvitationResponse> invitationsIssuedBy(@PathVariable long potId) {
        return pots.invitationsIssuedBy(potId).stream().map(PotInvitationResponse::of).toList();
    }

    /**
     * The invited customer accepts, which makes them a member of the pot with the role the invitation
     * named.
     *
     * <p>A plain 200 and not a 201, although a membership was written: what the caller asked to
     * change is the invitation, and the answer is that invitation as it now reads. The membership is
     * a consequence, and it is readable where memberships are read.
     */
    @PostMapping("/{invitationId}/accept")
    PotInvitationResponse accept(@PathVariable long potId, @PathVariable long invitationId,
                                 @RequestBody PotInvitationAnswerRequest request) {
        return PotInvitationResponse.of(
                pots.accept(potId, invitationId, whoIsAnswering(request)));
    }

    /** The invited customer declines, which closes the invitation and makes them a member of nothing. */
    @PostMapping("/{invitationId}/decline")
    PotInvitationResponse decline(@PathVariable long potId, @PathVariable long invitationId,
                                  @RequestBody PotInvitationAnswerRequest request) {
        return PotInvitationResponse.of(
                pots.decline(potId, invitationId, whoIsAnswering(request)));
    }

    /**
     * An owner takes back an invitation nobody has answered yet.
     *
     * <p>A DELETE because the caller is undoing something they sent, and the invitation stays in the
     * pot's record all the same — revoked is a state rather than an absence, for the reason
     * {@code InvitationState} gives. The customer doing it travels as a query parameter, because a
     * DELETE has no body to carry them in.
     */
    @DeleteMapping("/{invitationId}")
    PotInvitationResponse revoke(@PathVariable long potId, @PathVariable long invitationId,
                                 @RequestParam(required = false) Long customerId) {
        if (customerId == null) {
            throw refusingTheInvitation("Taking back an invitation needs the customer doing it.");
        }
        return PotInvitationResponse.of(pots.revoke(potId, invitationId, customerId));
    }

    /**
     * Who is answering, insisted on before the domain is asked: an answer with nobody answering it
     * has nobody to refuse on behalf of, and nobody to make a member either.
     */
    private long whoIsAnswering(PotInvitationAnswerRequest request) {
        if (request == null || request.customerId() == null) {
            throw refusingTheInvitation("Answering an invitation needs the customer answering it.");
        }
        return request.customerId();
    }

    /**
     * An invitation refused before the domain ever sees it, because the request did not carry
     * something the domain cannot ask about. Said out loud as well as answered, for the reason every
     * other refusal here is: the reason reaches whoever asked and nowhere else, and the log is the
     * only copy a reviewer tracing somebody's complaint can read.
     *
     * <p>Kept in the words Shared Pots warns in, so that {@code grep "shared pot rejected"} finds
     * every refusal whichever side of the domain boundary turned the request down.
     *
     * <p>A bad request rather than one of the module's kinds, and the same line the gift endpoint
     * draws: a field that was never filled in is a malformed request, where a customer who is named
     * and does not exist is a 404.
     */
    private ResponseStatusException refusingTheInvitation(String reason) {
        log.warn("shared pot rejected kind=NOT_AN_INVITATION reason={}", reason);
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
