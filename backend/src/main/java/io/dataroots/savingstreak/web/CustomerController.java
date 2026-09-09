package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.CustomerAccounts;
import io.dataroots.savingstreak.accounts.SavingsAccount;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.MoneyMovementsService;
import io.dataroots.savingstreak.gifting.GiftingService;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.rewards.Reward;
import io.dataroots.savingstreak.rewards.RewardsService;
import io.dataroots.savingstreak.streaks.StreaksService;
import io.dataroots.savingstreak.web.CustomerAccountsResponse.CurrentAccountResponse;
import io.dataroots.savingstreak.web.CustomerAccountsResponse.SavingsAccountResponse;
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

@RestController
@RequestMapping("/api/customers")
class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final AccountsService accounts;
    private final DepositsService deposits;
    private final GiftingService gifting;
    private final MoneyMovementsService movements;
    private final PointsService points;
    private final RewardsService rewards;
    private final StreaksService streaks;

    CustomerController(AccountsService accounts, DepositsService deposits, GiftingService gifting,
                       MoneyMovementsService movements, PointsService points,
                       RewardsService rewards, StreaksService streaks) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.gifting = gifting;
        this.movements = movements;
        this.points = points;
        this.rewards = rewards;
        this.streaks = streaks;
    }

    /**
     * Everybody this application has heard of, and the address each of them signs in with.
     *
     * <p>It exists so that a training session can sign in as somebody without first being told their
     * address, and the sign-in screen offers them as shortcuts. A bank does not publish a directory
     * of its customers to anyone who asks, and this endpoint is the first thing an authentication
     * slice would take away.
     */
    @GetMapping
    List<CustomerResponse> customers() {
        return accounts.customers().stream().map(CustomerResponse::of).toList();
    }

    /**
     * Recognises the address somebody typed and answers with the customer it belongs to.
     *
     * <p>No session is created, here or anywhere: what comes back is a customer, the browser
     * remembers it, and every request after this one still names the account it is about. Nothing
     * checks that the browser was ever told about that account. This is a sign-in screen and not
     * authentication, and the difference is the whole of what a later slice would add.
     *
     * <p>The address travels in the body rather than in the path, because a URL is written down —
     * in logs, in histories, in whatever sits between here and the browser — and a customer's
     * contact details should not be.
     */
    @PostMapping("/sign-in")
    CustomerResponse signIn(@RequestBody SignInRequest request) {
        if (request == null || request.contactDetails() == null || request.contactDetails().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fill in the email address you bank with.");
        }
        return accounts.customerIdentifiedBy(request.contactDetails())
                .map(CustomerResponse::of)
                // The same answer for an address that is not a customer's and one that is nobody's,
                // which is all this application can honestly tell apart: it knows who exists, and
                // not who is typing. Worded for someone who mistyped their own address, because in
                // a training session that is who it will be.
                // Worded by Accounts, which owns what a customer is and therefore what it sounds
                // like when nobody banks under an address. A gift addressed to a stranger has to
                // say the same thing, and two copies of the sentence are one rewording away from
                // disagreeing about what absence sounds like.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        AccountsService.noCustomerBanksUnderThoseContactDetails()));
    }

    /**
     * Everything the customer holds and what is in it: the accounts money comes from, the accounts it
     * goes to, the points all of that saving has earned them, and how the saving itself is going —
     * the week they are part-way through and the run of weeks behind it.
     *
     * <p>Those figures come from four modules that do not know about each other — who holds what,
     * what has been paid into each account, what the customer has earned, and how their week and
     * their run of weeks are going — and are assembled here. Assembling an answer is not a rule: no
     * decision about money, points, weeks or rates is taken in this class.
     *
     * <p>One read transaction, so that the figures describe the same instant of the ledger. Each
     * module opens a read of its own otherwise, and a deposit committing between two of them would
     * have the page show a savings balance without the points that deposit earned, or a week that has
     * taken EUR 60 in beside a balance of EUR 0,00 — answers that contradict themselves and that no
     * module is wrong about.
     *
     * <p>The week and the run come back together from one call for the reason the savings account's
     * own endpoint gives: they are one derivation off one reading of the clock, and asking for them
     * separately is what would have them describe two different weeks.
     */
    @Transactional(readOnly = true)
    @GetMapping("/{customerId}/accounts")
    CustomerAccountsResponse accountsOf(@PathVariable long customerId) {
        CustomerAccounts held = accounts.accountsOf(customerId)
                .orElseThrow(() -> noSuchCustomer(customerId));
        return CustomerAccountsResponse.of(
                points.balanceOf(customerId),
                // And what they stand to lose next, beside the balance because it is the same
                // figure read from the other end: what they can spend, and how long they have.
                points.whatExpiresNextFor(customerId),
                streaks.weekAndStreakOf(customerId),
                held.currentAccounts().stream().map(CurrentAccountResponse::of).toList(),
                held.savingsAccounts().stream().map(this::worthOf).toList());
    }

    private SavingsAccountResponse worthOf(SavingsAccount account) {
        return new SavingsAccountResponse(account.getId(), deposits.moneyBalanceOf(account.getId()));
    }

    /**
     * Every euro this customer has moved into or out of savings, newest first, across every savings
     * account they hold.
     *
     * <p>Which accounts those are is asked of Accounts and handed to the ledger, rather than the
     * ledger being told a customer and left to work out whose accounts are whose. A withdrawal does
     * not record whose it was, and who holds what is Accounts' answer wherever it is needed.
     *
     * <p>Asked before the movements are, so that a customer nobody has heard of is refused rather
     * than answered with the empty ledger of somebody who has simply never moved anything.
     */
    @Transactional(readOnly = true)
    @GetMapping("/{customerId}/money-movements")
    List<MoneyMovementResponse> moneyMovementsOf(@PathVariable long customerId) {
        CustomerAccounts held = accounts.accountsOf(customerId)
                .orElseThrow(() -> noSuchCustomer(customerId));
        List<Long> savingsAccounts = held.savingsAccounts().stream().map(SavingsAccount::getId).toList();
        return movements.movementsAcross(savingsAccounts).stream()
                .map(MoneyMovementResponse::of)
                .toList();
    }

    /**
     * What the customer has claimed, newest first. The other half of their points balance: the
     * deposits into every account they hold say what came in, these say what went out.
     */
    @GetMapping("/{customerId}/redemptions")
    List<ClaimedRewardResponse> claimedBy(@PathVariable long customerId) {
        // Asked before the claims are, so that a customer nobody has heard of is refused rather than
        // answered with the empty history of somebody who has simply never claimed anything.
        if (!accounts.customerExists(customerId)) {
            throw noSuchCustomer(customerId);
        }
        return rewards.claimedBy(customerId).stream().map(ClaimedRewardResponse::of).toList();
    }

    /**
     * Claims a reward out of the customer's points, wherever they earned them.
     *
     * <p>Against the customer rather than against one of their savings accounts, because that is
     * whose points these are: somebody saving towards two goals has one pot to spend and does not
     * have to pick which account pays.
     */
    @PostMapping("/{customerId}/redemptions")
    @ResponseStatus(HttpStatus.CREATED)
    ClaimedRewardResponse claim(@PathVariable long customerId, @RequestBody ClaimRequest request) {
        // Reading the request, not judging it. Whether the customer can afford the reward is a rule,
        // and it belongs to Rewards, which refuses on its own.
        if (request == null || request.reward() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A claim needs to name the reward being claimed.");
        }
        return ClaimedRewardResponse.of(rewards.claim(customerId, rewardIn(request)));
    }

    /**
     * The reward the claim named, or a refusal naming what is not in the catalogue. Named back to
     * whoever sent it, so a page that sent an old code can see which one it was.
     */
    private Reward rewardIn(ClaimRequest request) {
        String code = request.reward().trim();
        try {
            return Reward.valueOf(code);
        } catch (IllegalArgumentException notInTheCatalogue) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "There is nothing called \"" + code + "\" in the rewards catalogue.");
        }
    }

    /**
     * Gives some of the customer's points to another customer of the bank, named by the contact
     * details they bank under.
     *
     * <p>Against the customer rather than one of their accounts, for the reason a claim is: these
     * are their points, out of the one pot everything they save earns into.
     *
     * <p>Reading the request, not judging it. Whether the recipient exists, whether they are
     * somebody else, whether the figure is a number of points at all and whether the sender holds
     * that many are all rules, and every one of them belongs to Gifting, which refuses on its own.
     * What is checked here is only whether the two things a gift is made of were sent at all — the
     * same line the claim endpoint draws about the reward it names.
     */
    @PostMapping("/{customerId}/gifts")
    @ResponseStatus(HttpStatus.CREATED)
    GiftResponse give(@PathVariable long customerId, @RequestBody GiftRequest request) {
        if (request == null || request.recipientContactDetails() == null
                || request.recipientContactDetails().isBlank()) {
            throw refusingTheGift(customerId,
                    "A gift needs the email address of the customer it is going to.");
        }
        if (request.points() == null || request.points().isBlank()) {
            throw refusingTheGift(customerId, "A gift needs a number of points to give.");
        }
        return GiftResponse.of(
                gifting.give(customerId, request.recipientContactDetails(), request.points()));
    }

    /**
     * Every gift this customer was part of, sent and received together, newest first, each row marked
     * with the direction it reads in for them.
     *
     * <p>One list rather than two endpoints, because a customer's gifting reads chronologically and
     * which end of a gift they were on is a property of who is asking — the idiom the money-movement
     * ledger already set with its own direction.
     *
     * <p>Asked before the gifts are, so that a customer nobody has heard of is refused rather than
     * answered with the empty list of somebody who has simply never given or received anything. The
     * same line every other per-customer read here draws.
     */
    @GetMapping("/{customerId}/gifts")
    List<GiftResponse> giftsOf(@PathVariable long customerId) {
        if (!accounts.customerExists(customerId)) {
            throw noSuchCustomer(customerId);
        }
        return gifting.giftsOf(customerId).stream().map(GiftResponse::of).toList();
    }

    /**
     * A gift refused before the domain ever sees it, because the body did not carry the two things a
     * gift is made of. Said out loud as well as answered, for the reason every other refusal here is:
     * the reason reaches whoever asked and nowhere else, and the log is the only copy a reviewer
     * tracing somebody's complaint can read.
     *
     * <p>Kept alongside Gifting's own WARN so that {@code grep "gift rejected"} finds every refused
     * gift, whichever side of the domain boundary turned it down — a refusal that left no line saying
     * why would be indistinguishable in the log from a gift nobody ever tried to make.
     *
     * <p>A bad request rather than one of Gifting's four, and the kind says which: a field that was
     * never filled in is a malformed request, where an address that is filled in and belongs to
     * nobody is a 404. That is the line signing in already draws for itself.
     */
    private ResponseStatusException refusingTheGift(long customerId, String reason) {
        log.warn("gift rejected senderCustomerId={} kind=NOT_A_GIFT reason={}", customerId, reason);
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    /**
     * The refusal every endpoint here gives for somebody who does not bank at this application, said
     * out loud as well as answered.
     *
     * <p>Logged here rather than at each of the four call sites, which is the reason this factory
     * existed already: one place decides the words, so one place is where the WARN the house rule
     * asks for belongs. A reason only reaches whoever asked, and the log is the only copy anybody
     * reviewing the application afterwards can read.
     */
    private ResponseStatusException noSuchCustomer(long customerId) {
        String reason = AccountsService.noSuchCustomer(customerId);
        log.warn("request rejected customerId={} reason={}", customerId, reason);
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }
}
