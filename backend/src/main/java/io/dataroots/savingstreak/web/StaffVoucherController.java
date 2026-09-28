package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.rewards.AVoucherAtTheCounter;
import io.dataroots.savingstreak.rewards.RewardsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The counter: looking a voucher up, and marking it handed over.
 *
 * <p><strong>Nothing here checks that the caller is staff.</strong> There is no Spring Security in
 * this application, no role, no session and no password, and nothing anywhere asks who is sending
 * a request. The sign-in endpoint next door is honest about exactly this — "nothing checks that
 * the browser was ever told about that account. This is a sign-in screen and not authentication"
 * — and the same sentence is true here with a different noun: this is a counter screen and not
 * authorisation, and the difference is the whole of what a later slice would add. Anybody who can
 * reach this can mark anybody's voucher used. That is written down rather than left for somebody
 * to discover, because a surface that quietly looks official is worse than one that says what it
 * is.
 *
 * <p><strong>{@code /api/staff} rather than under the customer.</strong> A voucher is addressed by
 * the code printed on it and by nothing else, because the code is all the person at the till has:
 * they are holding a phone and a piece of paper, not a customer identifier. Hanging these under
 * {@code /api/customers/{id}} would have meant the counter looking a customer up before it could
 * look a voucher up, which is a step that cannot be performed with the information in the room.
 *
 * <p>Two endpoints and not one, deliberately. Reading a voucher and spending it are different acts
 * and the till does them in that order — check what it is, tell the person, then hand it over —
 * and a single call that did both would make "let me just see" impossible to offer.
 */
@RestController
@RequestMapping("/api/staff/vouchers")
class StaffVoucherController {

    private static final Logger log = LoggerFactory.getLogger(StaffVoucherController.class);

    private final RewardsService rewards;
    private final AccountsService accounts;

    StaffVoucherController(RewardsService rewards, AccountsService accounts) {
        this.rewards = rewards;
        this.accounts = accounts;
    }

    /**
     * What a voucher is, whose it is, and whether it is good. Writes nothing, so it can be asked
     * as often as somebody at a till likes.
     */
    @GetMapping("/{voucherCode}")
    VoucherAtTheCounterResponse voucher(@PathVariable String voucherCode) {
        return withTheHoldersName(rewards.voucherReading(asTypedAtACounter(voucherCode)));
    }

    /**
     * Marks the voucher handed over, naming the counter.
     *
     * <p>Reading the request, not judging it. Whether the code names a voucher and whether that
     * voucher can still be handed over are both rules, and both belong to Rewards, which refuses
     * on its own and is reported by {@link RefusalsAsHttp}. What is checked here is only whether a
     * counter was named at all — the same line the claim endpoint draws about the reward it names,
     * and for the same reason: a field that was not filled in is a fact about the form rather than
     * about the voucher.
     *
     * <p>Blank counts as not filled in. A form that accepted a space would record a redemption
     * attributed to a name nobody can read out, which is the one thing requiring the field was
     * supposed to prevent.
     *
     * <p>An OK rather than a created: nothing new exists afterwards. The voucher that was already
     * there has moved to the end of its life, and what comes back is that same voucher read again,
     * so the screen shows the state it has just written rather than the state it had.
     */
    @PostMapping("/{voucherCode}/use")
    VoucherAtTheCounterResponse use(@PathVariable String voucherCode,
                                    @RequestBody UseVoucherRequest request) {
        if (request == null || request.counter() == null || request.counter().isBlank()) {
            String reason = "Say which counter is handing this voucher over.";
            // Logged here rather than left to the advice, because a refusal raised at the edge
            // never reaches a module that would have logged it — and this one is the difference
            // between a redemption somebody can be asked about and one nobody can.
            log.warn("voucher rejected voucher={} kind=NO_COUNTER_NAMED reason={}",
                    voucherCode, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return withTheHoldersName(rewards.handOverTheVoucher(
                asTypedAtACounter(voucherCode), request.counter().trim()));
    }

    /**
     * The code as the voucher is stored under, from the code somebody typed.
     *
     * <p>Trimmed and upper-cased here rather than in the module, because this is where the typing
     * is: a voucher code is six characters read off a screen and tapped into a phone at a till,
     * and a keyboard that helpfully lower-cases the first letter is not the customer's mistake.
     * The alphabet a voucher is built from has no lower case in it and no ambiguous characters
     * either, so upper-casing can only ever turn a code into the code it was meant to be. What to
     * forgive about a keyboard is a question about this surface and not about what a voucher is.
     */
    private static String asTypedAtACounter(String voucherCode) {
        return voucherCode == null ? "" : voucherCode.trim().toUpperCase();
    }

    /**
     * Puts the holder's name beside the voucher, which is what somebody at a counter checks
     * against the person in front of them — and, when the voucher is a bundle's, the list of
     * what to actually put on the counter.
     *
     * <p>Asked of Accounts here rather than carried out of Rewards, because who a customer is
     * belongs to Accounts and Rewards has spent this whole feature not reading other modules for
     * the sake of a screen. It is the shape {@code CustomerController} already uses.
     *
     * <p><strong>The contents are a second question to Rewards, asked in exactly the same
     * spirit.</strong> A voucher is a claim, and what a claim keeps is its code, its title and
     * what it cost; what a bundle is made of is a fact about the catalogue, and the catalogue is
     * where it is read from. Joining the two here rather than inside the module's own reading is
     * this layer doing what it already does with the name: assembling one screen out of two
     * answers, neither of which had to learn about the other. Empty for every voucher that is
     * not a bundle's, which is every voucher this application has ever issued, and the till
     * draws nothing for it.
     */
    private VoucherAtTheCounterResponse withTheHoldersName(AVoucherAtTheCounter voucher) {
        String name = accounts.customerWith(voucher.customerId())
                .map(customer -> customer.getName())
                .orElse(null);
        return VoucherAtTheCounterResponse.of(voucher, name,
                rewards.whatIsInside(voucher.rewardCode()));
    }
}
