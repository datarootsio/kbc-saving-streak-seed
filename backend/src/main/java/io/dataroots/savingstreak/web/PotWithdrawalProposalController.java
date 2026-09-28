package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
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
 * Asking a shared pot to let money out, which is the only way any of it ever does.
 *
 * <p>Its own class rather than more methods on {@code SharedPotController}, for the reason
 * {@code PotInvitationController} is its own: a proposal is a thing rather than a property of a pot
 * — it has an identifier, a life of its own and a record that outlives it. Under the pot's path,
 * though, because a proposal belongs to exactly one pot and the pot is part of what identifies it.
 *
 * <p><strong>The ordinary withdrawal endpoint refuses a pot's savings account outright</strong>, in
 * a sentence promising that a pot's money leaves it by proposal. This is the route that sentence
 * promises. There is deliberately no other: a member who could take money out of the account
 * directly would be spending the other members' euros, and their points along with them.
 *
 * <p><strong>Nothing is judged here.</strong> Whether the pot exists, whether the caller is a member
 * who may propose, whose current account the money would go to, whether the figure is an amount this
 * application will move and whether the pot holds it are all rules, and every one of them belongs to
 * Shared Pots, which refuses on its own in words a person can act on and is reported by
 * {@code RefusalsAsHttp}. What is read here is only what the domain cannot ask about: whether the
 * request carried who is acting, and whether the characters that arrived are a number at all.
 *
 * <p>The acting customer arrives in the body of the POST and as a query parameter on the DELETE,
 * which is the contract this feature carries throughout: a pot belongs to no customer, so there is
 * no customer in its path to act under, and a DELETE has no body to put one in.
 *
 * <p>An answer comes back as the proposal in its new state rather than as an empty 204, including
 * from the DELETE — the same shape an invitation's revocation, an ended saving rule and an abandoned
 * goal already answer with. The page that sent it is showing a list of proposals and needs the row
 * it just changed.
 */
@RestController
@RequestMapping("/api/shared-pots/{potId}/withdrawal-proposals")
class PotWithdrawalProposalController {

    private static final Logger log = LoggerFactory.getLogger(PotWithdrawalProposalController.class);

    private final SharedPotsService pots;

    PotWithdrawalProposalController(SharedPotsService pots) {
        this.pots = pots;
    }

    /**
     * Proposes taking an amount out of the pot, and answers with the proposal that is now waiting.
     *
     * <p>Created, and nothing has moved: the thing that now exists is the question, not the
     * movement. The caller needs to be able to name it straight away — the row they are about to
     * show has a "take it back" button on it and a list of the people still to answer — and neither
     * the identifier nor the moment it was made is something they could have known.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WithdrawalProposalResponse propose(@PathVariable long potId,
                                       @RequestBody NewWithdrawalProposalRequest request) {
        // Reading the request, not judging it. Whether the member may propose at all, whose current
        // account that is and whether the pot holds the figure are rules, and all three belong to
        // Shared Pots. What cannot be left to the rule is a body that never said who is proposing,
        // because there is then nobody to refuse on behalf of — and one that named no amount or no
        // account, because there is then no movement to be asking about.
        if (request == null || request.customerId() == null) {
            throw refusingTheProposal(potId, "A withdrawal proposal needs the customer making it.");
        }
        if (request.amount() == null || request.toCurrentAccountId() == null) {
            throw refusingTheProposal(potId, "A withdrawal proposal needs an amount and the current "
                    + "account it would come back to.");
        }
        return WithdrawalProposalResponse.of(pots.proposeAWithdrawal(potId, request.customerId(),
                amountIn(potId, request.amount()), request.toCurrentAccountId()));
    }

    /**
     * Every proposal the pot has ever had, in any state, oldest first — who asked, when, for how
     * much, where it would go, what became of it, and who has still to answer.
     */
    @GetMapping
    List<WithdrawalProposalResponse> proposalsOf(@PathVariable long potId) {
        return pots.withdrawalProposalsOf(potId).stream()
                .map(WithdrawalProposalResponse::of)
                .toList();
    }

    /**
     * A member whose money is at stake approves the proposal, and answers with it as it now reads.
     *
     * <p><strong>The last approval is also the withdrawal.</strong> There is no second call to make
     * the money move: if this is the approval everybody was waiting for, the pot's balance has
     * fallen and the proposer's current account has risen by the time this answers, and the
     * proposal reads {@code APPROVED}. The page that sent it refreshes the row and finds it done.
     *
     * <p>200 rather than 201, and rather than an empty 204. Nothing was created — the proposal
     * already existed and what changed is where it stands — and the caller is showing a list of
     * proposals with the people still to answer beside each one, which is exactly what comes back.
     *
     * <p>Nothing is judged here. Whether this member may answer at all, whether they have answered
     * already, whether the proposal is still waiting and whether the pot still holds the money are
     * all rules, and every one of them belongs to Shared Pots. What is read here is only what the
     * domain cannot ask about: whether the request said who is answering.
     */
    @PostMapping("/{proposalId}/approve")
    WithdrawalProposalResponse approve(@PathVariable long potId, @PathVariable long proposalId,
                                       @RequestBody(required = false) AnswerToAProposalRequest request) {
        return WithdrawalProposalResponse.of(
                pots.approve(potId, proposalId, whoIsAnswering(potId, request, "Approving")));
    }

    /**
     * A member whose money is at stake says no, which ends the proposal. Nothing moves.
     *
     * <p>Its own path rather than the same one with a word in the body, because the two are
     * different acts with different consequences and a page has two buttons: a request that said
     * {@code {"answer": "REJECTED"}} would put the difference between "yes" and "no" in a string
     * that could be misspelled, and a misspelling that fell through to the wrong branch would spend
     * somebody's euros. The same reason accepting and declining an invitation are two paths.
     */
    @PostMapping("/{proposalId}/reject")
    WithdrawalProposalResponse reject(@PathVariable long potId, @PathVariable long proposalId,
                                      @RequestBody(required = false) AnswerToAProposalRequest request) {
        return WithdrawalProposalResponse.of(
                pots.reject(potId, proposalId, whoIsAnswering(potId, request, "Rejecting")));
    }

    /**
     * Who is answering, or the refusal that says the form never said.
     *
     * <p>The one thing a rule cannot be asked about: with nobody named there is nobody to refuse on
     * behalf of, and nobody whose money could be at stake. A bad request rather than a 403, in the
     * line this controller already draws for a proposal that named no amount.
     *
     * @param whatTheyTried the act, so that one reading answers for approving and for rejecting and
     *                      the person is told which form they left a box empty on
     */
    private long whoIsAnswering(long potId, AnswerToAProposalRequest request, String whatTheyTried) {
        if (request == null || request.customerId() == null) {
            throw refusingTheProposal(potId, whatTheyTried
                    + " a withdrawal proposal needs the customer doing it.");
        }
        return request.customerId();
    }

    /**
     * The member who proposed a withdrawal takes it back.
     *
     * <p>A DELETE because the caller is undoing something they asked for, and the proposal stays in
     * the pot's record all the same — withdrawn is a state rather than an absence, for the reason
     * {@code ProposalState} gives. The customer doing it travels as a query parameter, because a
     * DELETE has no body to carry them in.
     */
    @DeleteMapping("/{proposalId}")
    WithdrawalProposalResponse takeBack(@PathVariable long potId, @PathVariable long proposalId,
                                        @RequestParam(required = false) Long customerId) {
        if (customerId == null) {
            throw refusingTheProposal(potId,
                    "Taking back a withdrawal proposal needs the customer doing it.");
        }
        return WithdrawalProposalResponse.of(pots.takeBackTheProposal(potId, proposalId, customerId));
    }

    /**
     * The amount as a figure, or a refusal naming what could not be read as one — in the words the
     * deposit and withdrawal endpoints already use, because it is the same mistake about the same
     * kind of figure. Named back to whoever sent it, because a person who typed a comma has to see
     * the comma to see the mistake.
     */
    private BigDecimal amountIn(long potId, String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException notANumber) {
            throw refusingTheProposal(potId, "\"" + amount + "\" is not an amount of money. Write "
                    + "it in digits with a full stop, like 25.00.");
        }
    }

    /**
     * A proposal refused before the domain ever sees it, because the request did not carry something
     * the domain cannot ask about. Said out loud as well as answered, for the reason every other
     * refusal here is: the reason reaches whoever asked and nowhere else, and the log is the only
     * copy a reviewer tracing somebody's complaint can read.
     *
     * <p>Kept in the words Shared Pots warns in, so that {@code grep "shared pot rejected"} finds
     * every refusal whichever side of the domain boundary turned the request down.
     *
     * <p>A bad request rather than one of the module's kinds, and the same line the gift endpoint
     * draws: a field that was never filled in, or a figure that is not a number at all, is a
     * malformed request, where a member who is named and may not propose is a 403.
     */
    private ResponseStatusException refusingTheProposal(long potId, String reason) {
        log.warn("shared pot rejected potId={} kind=NOT_A_PROPOSAL reason={}", potId, reason);
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
