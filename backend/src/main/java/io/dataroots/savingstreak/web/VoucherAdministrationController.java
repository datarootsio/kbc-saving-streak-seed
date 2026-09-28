package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.rewards.AVoucherAtTheCounter;
import io.dataroots.savingstreak.rewards.RewardsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The one thing whoever runs the scheme can do to a voucher that is already out there: revoke it,
 * with a reason, which gives the customer their points back and puts the thing back in the window.
 *
 * <p><strong>Nothing here checks that the caller runs the scheme.</strong> There is no Spring
 * Security in this application, no role, no session and no password, and the sign-in endpoint's
 * javadoc is honest about exactly that: nothing checks that the browser was ever told about the
 * account it names. The same sentence is true of the handler below and it is worth saying in the
 * same words rather than in softer ones — <em>this is an administration screen and not
 * authorisation</em>. Anybody who can reach this application can cancel anybody's voucher and
 * credit them the points. Building the thing that would stop them is a feature of its own, and a
 * comment implying it half-exists would be worse than this paragraph.
 *
 * <p><strong>A controller of its own rather than another handler on the catalogue's.</strong>
 * {@code RewardAdministrationController} is addressed by an offer's code and every one of its
 * handlers is about a row of the catalogue; a voucher is addressed by the code printed on it and
 * has nothing to do with any of that path's variables. Hanging this off {@code /api/admin/rewards}
 * would have meant an offer code in the URL of a request that does not name an offer.
 *
 * <p><strong>And deliberately not on the counter's controller either</strong>, although both act
 * on a voucher by its code. {@code /api/staff} is a till: it checks a voucher and hands it over,
 * and the two things it can do are the two things a person standing at a counter is allowed to do.
 * Cancelling is not one of them — it moves points — and putting it behind the same prefix would
 * mean that the day somebody puts authentication in front of these surfaces, "staff" would have to
 * be split into two before it could be locked.
 *
 * <p>Beyond reading the request it judges nothing. Whether the code names a voucher and whether
 * that voucher can still be cancelled are rules, and both belong to Rewards, which refuses on its
 * own and is reported by {@link RefusalsAsHttp}. What is checked here is only whether a reason was
 * given at all — the same line the counter draws about its own required field, and for the same
 * reason: a box nobody filled in is a fact about the form rather than about the voucher.
 */
@RestController
@RequestMapping("/api/admin/vouchers")
class VoucherAdministrationController {

    private static final Logger log =
            LoggerFactory.getLogger(VoucherAdministrationController.class);

    private final RewardsService rewards;
    private final AccountsService accounts;

    VoucherAdministrationController(RewardsService rewards, AccountsService accounts) {
        this.rewards = rewards;
        this.accounts = accounts;
    }

    /**
     * Revokes the voucher, naming the reason, and answers with the voucher as it now reads.
     *
     * <p>An OK rather than a created: nothing new exists afterwards that anybody asked for. The
     * voucher that was already there has reached the end of its life, and what comes back is that
     * same voucher read again — so the screen shows the state it has just written, with the reason
     * on it, rather than the state it had a moment ago.
     *
     * <p>The voucher reading is the shape that comes back rather than something of this surface's
     * own, and that is the point: what an administrator needs to see after cancelling one is
     * exactly what a till would see if the customer turned up with it, which is the only way to be
     * sure the two agree. What is not on it is the refund, deliberately — how many points somebody
     * has is the Points module's answer and this response has never carried a balance.
     */
    @PostMapping("/{voucherCode}/cancel")
    VoucherAtTheCounterResponse cancel(@PathVariable String voucherCode,
                                       @RequestBody(required = false) CancelVoucherRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            String reason = "Say why this voucher is being cancelled.";
            // Logged here rather than left to the advice, because a refusal raised at the edge
            // never reaches a module that would have logged it — and this one is the difference
            // between a revoked claim somebody can be asked about and one nobody can.
            log.warn("voucher rejected voucher={} kind=NO_REASON_GIVEN reason={}",
                    voucherCode, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        AVoucherAtTheCounter cancelled = rewards.cancelTheVoucher(
                asTypedByWhoeverRunsTheScheme(voucherCode), request.reason().trim());
        return VoucherAtTheCounterResponse.of(cancelled, holderOf(cancelled),
                rewards.whatIsInside(cancelled.rewardCode()));
    }

    /**
     * The code as the voucher is stored under, from the code somebody pasted or typed.
     *
     * <p>Trimmed and upper-cased in the same words the counter surface uses, because the typing is
     * the same typing: a voucher code is six characters read off a screen, and the alphabet one is
     * built from has no lower case and no ambiguous characters in it, so upper-casing can only
     * turn a code into the code it was meant to be. It is done here rather than in the module for
     * the reason it is done there — what to forgive about a keyboard is a question about a
     * surface — and doing it identically in both is what stops one of them finding a voucher the
     * other cannot.
     */
    private static String asTypedByWhoeverRunsTheScheme(String voucherCode) {
        return voucherCode == null ? "" : voucherCode.trim().toUpperCase();
    }

    /**
     * Puts the holder's name beside the voucher, which is who the points have just gone back to.
     *
     * <p>Asked of Accounts here rather than carried out of Rewards, because who a customer is
     * belongs to Accounts and Rewards has spent this whole feature not reading other modules for
     * the sake of a screen. It is the shape {@code CustomerController} already uses, and the same
     * shape the counter surface uses for the same record. Null when the customer behind the claim
     * is no longer on file, which is honest rather than a failure: the cancellation happened.
     */
    private String holderOf(AVoucherAtTheCounter voucher) {
        return accounts.customerWith(voucher.customerId())
                .map(customer -> customer.getName())
                .orElse(null);
    }
}
