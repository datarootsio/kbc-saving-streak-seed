package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.sharedpots.SharedPotsService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who has put what into a shared pot.
 *
 * <p>Its own class rather than another method on {@code SharedPotController}, for the reason
 * {@code PotMemberController} is its own class: what the pot is — its name, its balance, who is in
 * it — is one answer, and what each of them has paid into it is a different and much larger one,
 * read by a screen of its own and about to be read by every later screen that argues about money.
 * Putting it on the pot would make every read of a pot's name work out three figures per member.
 *
 * <p><strong>Nothing is judged here and nothing is assembled here.</strong> Whether the pot exists
 * and whether the caller may see the figures are rules and both belong to Shared Pots, which refuses
 * on its own in words a person can act on and is reported by {@code RefusalsAsHttp}. The arithmetic
 * belongs there too — it is the pot's own honesty about who has carried it — so this class reads the
 * request and renders the answer.
 *
 * <p>Who is asking travels as {@code ?customerId=}, the way it does on every read of a pot's goals:
 * this is a members-only read, and a {@code GET} has no body to carry it in. A request that names
 * nobody is nobody, and is refused in the same words a stranger is.
 */
@RestController
@RequestMapping("/api/shared-pots/{potId}/contributions")
class PotContributionController {

    private final SharedPotsService pots;

    PotContributionController(SharedPotsService pots) {
        this.pots = pots;
    }

    /**
     * Every member of the pot with what they have paid in, what is still theirs, and what it has
     * earned them, in the order they joined — which puts the owner who opened it first.
     *
     * <p>In one read transaction, so that every figure on the page describes the same instant of the
     * ledger. Two reads a moment apart is how a screen comes to show a member's contribution from
     * before a withdrawal beside a total from after it, which is exactly the page nobody could
     * check.
     */
    @Transactional(readOnly = true)
    @GetMapping
    List<PotContributionResponse> contributionsTo(@PathVariable long potId,
                                                  @RequestParam(required = false) Long customerId) {
        return pots.contributionsTo(potId, customerId).stream()
                .map(PotContributionResponse::of)
                .toList();
    }
}
