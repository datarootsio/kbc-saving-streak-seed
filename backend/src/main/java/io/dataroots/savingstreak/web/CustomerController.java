package io.dataroots.savingstreak.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.dataroots.savingstreak.accounts.ABillOnTheLedger;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.CurrentAccount;
import io.dataroots.savingstreak.accounts.CustomerAccounts;
import io.dataroots.savingstreak.accounts.SavingsAccount;
import io.dataroots.savingstreak.automation.AutomationService;
import io.dataroots.savingstreak.budgets.ARecordedSpend;
import io.dataroots.savingstreak.budgets.SpendsService;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.MoneyMovement;
import io.dataroots.savingstreak.deposits.MoneyMovementDirection;
import io.dataroots.savingstreak.deposits.MoneyMovementsService;
import io.dataroots.savingstreak.gifting.GiftingService;
import io.dataroots.savingstreak.notifications.NotificationsService;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.products.ProductsService;
import io.dataroots.savingstreak.products.TheAgreementAnAccountIsOn;
import io.dataroots.savingstreak.rewards.CustomerStanding;
import io.dataroots.savingstreak.rewards.RewardsService;
import io.dataroots.savingstreak.sharedpots.SharedPotsService;
import io.dataroots.savingstreak.streaks.StreaksService;
import io.dataroots.savingstreak.web.CustomerAccountsResponse.CurrentAccountResponse;
import io.dataroots.savingstreak.web.CustomerAccountsResponse.SavingsAccountResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final AutomationService automation;
    private final DepositsService deposits;
    private final GiftingService gifting;
    private final MoneyMovementsService movements;
    private final NotificationsService notifications;
    private final PointsService points;
    /**
     * What each of the customer's savings accounts is living under, so that the overview can say
     * which product each one is on. Asked rather than assumed: two accounts one person holds can be
     * on two different agreements.
     */
    private final ProductsService products;
    private final RewardsService rewards;
    private final SharedPotsService sharedPots;
    private final SpendsService spends;
    /**
     * Where a customer stands, assembled once and in one place, because the rewards module's
     * own sweep now reads the same assembly through an interface Rewards declares.
     */
    private final WhereEveryCustomerStands standings;
    private final StreaksService streaks;

    CustomerController(AccountsService accounts, AutomationService automation,
                       DepositsService deposits, GiftingService gifting,
                       MoneyMovementsService movements, NotificationsService notifications,
                       PointsService points, ProductsService products, RewardsService rewards,
                       SharedPotsService sharedPots,
                       SpendsService spends, WhereEveryCustomerStands standings,
                       StreaksService streaks) {
        this.accounts = accounts;
        this.automation = automation;
        this.deposits = deposits;
        this.gifting = gifting;
        this.movements = movements;
        this.notifications = notifications;
        this.points = points;
        this.products = products;
        this.rewards = rewards;
        this.sharedPots = sharedPots;
        this.spends = spends;
        this.standings = standings;
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
     * Opens a customer, so that there is somebody new to bank as and somebody new to give points
     * to.
     *
     * <p>It exists because the people who bank here were until now only the two the application
     * seeds, and a gift can only be addressed to somebody who banks here — which left a training
     * session able to demonstrate gifting in exactly one direction. A bank does not let anyone who
     * can reach it open a customer, and this endpoint is the second thing an authentication slice
     * would take away, right behind the directory above it.
     *
     * <p>Created, with the customer that now exists: the caller asked for somebody to be there and
     * needs to be able to name them straight away — the gift page selects them the moment they
     * arrive — and the identifier is not something the caller could have known.
     *
     * <p>Reading the request, not judging it. Whether a name is a name, whether an address is one
     * somebody already banks under, and what a customer opens with are all rules, and every one of
     * them belongs to Accounts, which refuses on its own. Nothing is checked here at all, not even
     * for the fields being absent: a missing name and a blank one are the same mistake with the
     * same sentence, and having the rule answer both is one fewer place for the wording to drift.
     * A body missing altogether is the exception, because there is then nothing to ask the rule
     * about.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CustomerResponse addCustomer(@RequestBody NewCustomerRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fill in a name and an email address for the person to add.");
        }
        return CustomerResponse.of(accounts.addCustomer(request.name(), request.contactDetails()));
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
                // And the mark their next deposit is judged against, beside what they hold, because
                // the two together are what say whether paying in will earn anything.
                deposits.mostEverSavedBy(customerId),
                // And what they stand to lose next, beside the balance because it is the same
                // figure read from the other end: what they can spend, and how long they have.
                points.whatExpiresNextFor(customerId),
                streaks.weekAndStreakOf(customerId),
                held.currentAccounts().stream().map(CurrentAccountResponse::of).toList(),
                held.savingsAccounts().stream().map(this::worthOf).toList());
    }

    /**
     * One savings account on the overview: what it holds, and which product it is on.
     *
     * <p>Two modules put side by side and nothing worked out here, like everything else on this
     * page. The product is asked for per account rather than for the customer, because what an
     * account is living under is the account's own answer — two accounts one person holds may be on
     * two different products, and that is precisely the thing this card exists to show.
     *
     * <p>An account with no agreement on record reads as no product at all rather than as free
     * savings by assumption: a database that has not been through the start-up migration should
     * show a gap, which somebody can act on, instead of a name nobody wrote.
     */
    private SavingsAccountResponse worthOf(SavingsAccount account) {
        Optional<TheAgreementAnAccountIsOn> agreement = products.theAgreementOf(account.getId());
        return new SavingsAccountResponse(account.getId(), deposits.moneyBalanceOf(account.getId()),
                agreement.map(TheAgreementAnAccountIsOn::productCode).orElse(null),
                agreement.map(TheAgreementAnAccountIsOn::productName).orElse(null),
                agreement.map(TheAgreementAnAccountIsOn::closedOn).orElse(null));
    }

    /**
     * Opens another savings account for this customer, on the savings product they chose, and
     * answers with it exactly as its own page would report it.
     *
     * <p><strong>The card the overview will draw for it, rather than a confirmation of its
     * own.</strong> What a page does next is show the customer the account they now hold, and the
     * shape it shows accounts in is the one on the list it came from — so this answers with exactly
     * that row, read back through the same two modules the overview reads it through. A
     * confirmation shape would be a third reading of a savings account to keep in agreement with
     * the other two, for a message nobody would look at twice. What the account is <em>living
     * under</em> — the version, the condition, the day — is on the account's own page, where a
     * whole panel is already given to it.
     *
     * <p><strong>Which product is the customer's answer and is sent as text.</strong> Nothing is
     * checked here — not that it is one of the four, not that it is not blank, not that the product
     * is still open. Whether the bank sells such a thing and whether it is still opening accounts on
     * it are the catalogue's answers and come back as sentences this class passes on untouched; a
     * controller that knew the list of codes would be a second place it was written down, and the
     * second copy is the one that is out of date the morning a fifth product is added.
     *
     * <p><strong>A 201 and no Location header</strong>, like every other creation in this
     * application: the thing that was made is in the body, and the client that asked for it is
     * holding it rather than being told where to go and look.
     *
     * <p>A customer nobody has heard of is a 404 in the words Accounts owns for an absent customer,
     * which is the same sentence the overview above answers with for the same identifier.
     */
    @Transactional
    @PostMapping("/{customerId}/savings-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    SavingsAccountResponse openASavingsAccount(@PathVariable long customerId,
                                               @RequestBody NewSavingsAccountRequest request) {
        long savingsAccountId = accounts.openASavingsAccountFor(customerId, request.product())
                .orElseThrow(() -> noSuchCustomer(customerId));
        log.info("a savings account was opened over HTTP customerId={} savingsAccountId={} "
                + "product={}", customerId, savingsAccountId, request.product());
        return worthOf(savingsAccountId);
    }

    /**
     * One savings account by its identifier, in the shape the overview lists them in.
     *
     * <p>Beside {@link #worthOf(SavingsAccount)} rather than folded into it, because the two start
     * from different things: the overview already holds the account rows it listed, and an account
     * just opened is a number. Reading it back through the same two modules is what makes the answer
     * to opening one identical to the card the next overview will draw for it.
     */
    private SavingsAccountResponse worthOf(long savingsAccountId) {
        Optional<TheAgreementAnAccountIsOn> agreement = products.theAgreementOf(savingsAccountId);
        return new SavingsAccountResponse(savingsAccountId, deposits.moneyBalanceOf(savingsAccountId),
                agreement.map(TheAgreementAnAccountIsOn::productCode).orElse(null),
                agreement.map(TheAgreementAnAccountIsOn::productName).orElse(null),
                agreement.map(TheAgreementAnAccountIsOn::closedOn).orElse(null));
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
     *
     * <p><strong>Which of them a saving rule made is asked of Automation here, and decorated on
     * the way out.</strong> It is the one thing that lets a customer tell what they did from what
     * the application did for them, and this is the only place the two modules can be put side by
     * side: the ledger is Deposits' answer and Deposits does not know automation exists, so a
     * deposit cannot say it for itself without the cycle the module boundary is built to avoid.
     * One question for the whole page rather than one per row, and only about the movements into
     * savings — nothing in this application takes money back out automatically.
     *
     * <p><strong>And the bills, which is what stops this list lying by omission.</strong> A customer
     * reading back over their money was being shown every euro they chose to save and none of the
     * euros their landlord took, which is precisely how somebody ends up unable to account for a
     * balance. The dates their bills fell due on are Accounts' answer and are asked for here, beside
     * the savings movements, for the reason the automatic flag is: this is the only place the two
     * modules can be put side by side, and neither of them has any business knowing the other's read
     * models. Unpaid attempts come with them — the money not moving is the information.
     *
     * <p><strong>And the spends, which is the same omission again in the part of a balance people
     * are actually wrong about.</strong> A customer was being shown the euros their landlord took
     * and none of the euros they spent on groceries, and groceries are the larger half of most
     * months. What they spent is the budgets module's answer and is asked for here, beside the bills
     * and the savings movements, for the reason both of those are: this is the only place the
     * modules can be put side by side, and none of them has any business knowing another's read
     * models. Budgets reads Accounts and Accounts learns nothing, and neither of them learns that
     * the other is on this page.
     *
     * <p>The four kinds are then one list, newest first, sorted once by
     * {@link MoneyMovementResponse#NEWEST_FIRST}. A bill sits at the moment it was <em>settled</em>
     * rather than the day it was owed from, which is what puts an arrear owed in March and paid in
     * June where the money actually left; both dates go out on the row. A spend sits at the moment
     * it was <em>recorded</em>, which is the only moment it has — a spend is not backdated in this
     * application — and it stays there when its split is corrected, because the money left when it
     * left and only the opinion about what it was for has changed.
     */
    @Transactional(readOnly = true)
    @GetMapping("/{customerId}/money-movements")
    List<MoneyMovementResponse> moneyMovementsOf(@PathVariable long customerId) {
        CustomerAccounts held = accounts.accountsOf(customerId)
                .orElseThrow(() -> noSuchCustomer(customerId));
        List<Long> savingsAccounts = held.savingsAccounts().stream().map(SavingsAccount::getId).toList();
        List<Long> currentAccounts = held.currentAccounts().stream().map(CurrentAccount::getId).toList();
        List<MoneyMovement> ledger = movements.movementsAcross(savingsAccounts);
        Set<Long> madeByARule = automation.whichOfTheseDepositsWereAutomatic(ledger.stream()
                .filter(moved -> moved.direction() == MoneyMovementDirection.INTO_SAVINGS)
                .map(MoneyMovement::id)
                .toList());
        List<ABillOnTheLedger> bills = accounts.billsPresentedOn(currentAccounts);
        List<ARecordedSpend> spent = spends.spendsRecordedOn(currentAccounts);

        List<MoneyMovementResponse> entries =
                new ArrayList<>(ledger.size() + bills.size() + spent.size());
        ledger.stream()
                .map(moved -> MoneyMovementResponse.of(moved,
                        moved.direction() == MoneyMovementDirection.INTO_SAVINGS
                                && madeByARule.contains(moved.id())))
                .forEach(entries::add);
        bills.stream().map(MoneyMovementResponse::ofABill).forEach(entries::add);
        spent.stream().map(MoneyMovementResponse::ofASpend).forEach(entries::add);
        entries.sort(MoneyMovementResponse.NEWEST_FIRST);

        // The stretch of time the assembled ledger covers and what each kind put into it, so that a
        // page somebody says is missing their rent or their groceries can be checked against what
        // was merged: the window says which moments were in scope, and each count says how many of
        // them that kind contributed. Counts and two moments rather than a line per row, because
        // this runs on every read of the history page and one line per movement would bury the
        // business events in a page load.
        log.debug("money movements read customerId={} entries={} assembledOver={}..{} "
                        + "savingsMovements={} madeByARule={} billsOnTheLedger={} "
                        + "spendsOnTheLedger={}",
                customerId, entries.size(),
                entries.isEmpty() ? null : entries.get(entries.size() - 1).movedAt(),
                entries.isEmpty() ? null : entries.get(0).movedAt(),
                ledger.size(), madeByARule.size(), bills.size(), spent.size());
        return entries;
    }

    /**
     * The catalogue as it stands for this customer: every offer on sale, each one saying whether
     * they can claim it right now and, when they cannot, the single reason why.
     *
     * <p><strong>A second address rather than a customer on the catalogue's own.</strong>
     * {@code /api/rewards} is the catalogue with nobody in it — the same entries at the same
     * prices for everybody — and it keeps saying exactly what it has always said, because the test
     * that pins it to four entries at four prices is the rail this whole feature is built not to
     * break. What a page needs in order to draw a card is a different question with a different
     * answer per person and per day, and it gets a path where the person is in it.
     *
     * <p><strong>Locked, never missing.</strong> An offer somebody cannot claim yet is still on
     * the list, with the reason, because that is the only thing on the screen that tells a
     * customer what the scheme wants from them. A draft and a withdrawn offer are the exception
     * and are not here at all: those are not locked, they are not being sold.
     *
     * <p><strong>This is where the customer's standing is assembled and handed in.</strong>
     * Rewards can lock an offer against a run of weeks, a badge or a lifetime of points earned,
     * and it reads none of the three modules that own those facts. This layer does, because this
     * layer already does — it is the same thing {@link #accountsOf} does when it asks Accounts
     * which accounts somebody holds and puts a balance from Deposits beside each one. One
     * standing for the whole list, assembled once here and read once per offer over there.
     *
     * <p>Asked of the customer before the catalogue is, so that somebody nobody has heard of is
     * refused rather than handed the perfectly good catalogue of a person who does not exist —
     * the same order and the same reason the claims below are read in, and the reason the
     * standing below it is never assembled for somebody who is not there.
     */
    @GetMapping("/{customerId}/rewards")
    List<RewardForACustomerResponse> rewardsFor(@PathVariable long customerId) {
        if (!accounts.customerExists(customerId)) {
            throw noSuchCustomer(customerId);
        }
        return rewards.catalogueFor(customerId, theStandingOf(customerId)).stream()
                .map(RewardForACustomerResponse::of).toList();
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
        // Reading the request, not judging it. Whether the catalogue has ever heard of the code
        // and whether the customer can afford what it names are both rules, and both belong to
        // Rewards, which refuses on its own and is reported by RefusalsAsHttp. What is checked here
        // is only whether a reward was named at all — this endpoint used to parse the code into a
        // catalogue constant and refuse it itself, which was the web layer deciding what is in the
        // catalogue.
        if (request == null || request.reward() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A claim needs to name the reward being claimed.");
        }
        // The standing goes up with the claim for the reason it goes up with the reading: an
        // offer may be restricted to a run of weeks, a badge or a lifetime of points earned, and
        // Rewards reads none of the three modules that know. Assembled before the module has
        // said whether the customer exists, and deliberately not guarded here — a standing for
        // somebody nobody has heard of says nothing is known, is never read, and the sentence
        // that request gets back is the module's own "there is no such customer". Answering it
        // here instead would move a refusal out of the module that owns it in order to save one
        // query.
        return ClaimedRewardResponse.of(
                rewards.claim(customerId, request.reward().trim(), theStandingOf(customerId)));
    }

    /**
     * Puts the last of something aside for this customer for seventy-two hours.
     *
     * <p>Beside the claim above rather than folded into it, because they are different requests
     * with different answers. A claim spends points and hands over a voucher; a hold spends
     * nothing at all and hands over three days to decide in. The customer's balance does not
     * move and no batch in their ledger is touched — the argument for taking stock rather than
     * points is in the module, on the method this calls.
     *
     * <p>The standing goes up with it for the same reason it goes up with a claim: an offer may
     * be restricted to a run of weeks, a badge or a lifetime of points earned, and Rewards reads
     * none of the three modules that know. Taking a hold runs the same gauntlet a claim runs,
     * so it needs the same facts.
     *
     * <p>Created rather than accepted, and the hold comes back with the moment it lapses on it,
     * because a deadline the customer never saw is the one thing a hold must not be.
     */
    @PostMapping("/{customerId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    HoldResponse takeAHold(@PathVariable long customerId, @RequestBody HoldRequest request) {
        // Reading the request, not judging it — the same division the claim above draws. Whether
        // the catalogue has heard of the code, whether the offer is open, whether it is for this
        // customer and whether there is one left are all rules, and all of them are Rewards'.
        if (request == null || request.reward() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A hold needs to name the offer being held.");
        }
        return HoldResponse.of(rewards.takeAHold(customerId, request.reward().trim(),
                theStandingOf(customerId)));
    }

    /**
     * Turns a hold into the claim it was being kept for: the points go now, at the price in
     * force now, and the voucher is issued.
     *
     * <p><strong>An address of its own rather than the ordinary claim noticing the hold</strong>,
     * and the reason is what happens when the hold is not there. An ordinary claim of something
     * the customer used to hold is a perfectly good request that succeeds if there is stock;
     * converting a hold that ran out is not, and it has to be told so in those words rather than
     * be quietly served or refused as sold out. The two requests mean different things, so they
     * are two addresses. The module makes the same argument at more length.
     *
     * <p>Under the hold's own path rather than under the claims one, because the thing being
     * acted on is the hold — it is the hold that has to exist, and the hold that is spent.
     * What comes back is the claim, because what the customer has afterwards is a voucher.
     */
    @PostMapping("/{customerId}/holds/{code}/claim")
    @ResponseStatus(HttpStatus.CREATED)
    ClaimedRewardResponse convertAHold(@PathVariable long customerId,
                                       @PathVariable String code) {
        return ClaimedRewardResponse.of(
                rewards.convertAHold(customerId, code.trim(), theStandingOf(customerId)));
    }

    /**
     * Gives a hold up, which puts the thing back in the window at once.
     *
     * <p>A DELETE, because that is what it is: the hold stops existing as a live thing and the
     * address stops having anything behind it. Nothing is deleted in the database — the row
     * keeps its history, like every other one-way door in this application — but what the
     * customer is doing is removing their claim on the last one, and any other verb would be
     * dressing that up.
     *
     * <p>No standing is assembled and none is needed. Giving a hold up is the one thing here
     * that stays possible when everything else about an offer has stopped being: an offer
     * withdrawn while they held it, or a rule they no longer meet, are reasons they cannot
     * convert and are no reason at all to make them go on holding stock nobody can have. The
     * module says the same where it refuses.
     *
     * <p>It answers with the hold as it now reads rather than with nothing, so that the page can
     * show what happened without a second request and so that pressing twice is answered
     * honestly rather than silently.
     */
    @DeleteMapping("/{customerId}/holds/{code}")
    HoldResponse giveUpAHold(@PathVariable long customerId, @PathVariable String code) {
        return HoldResponse.of(rewards.giveUpAHold(customerId, code.trim()));
    }

    /**
     * Where one customer stands, for the paths on this controller that need one.
     *
     * <p><strong>The assembly moved out and this is what is left of it.</strong> It used to be
     * written here, because this controller was the only thing that needed it; the waiting-list
     * slice gave it a second reader that is not a request at all — the rewards module's nightly
     * sweep, which has to know whether the person at the front of a queue still qualifies — and
     * two copies of one assembly would be two answers to one question. So the four facts and
     * the arguments for each of them live on {@link WhereEveryCustomerStands}, Rewards receives
     * that through an interface it declares itself, and this is the one-line adaptation back to
     * what a request wants.
     *
     * <p><strong>A customer nobody has heard of gets a standing in which nothing is known, and
     * never an exception.</strong> The assembly answers with nothing for them, deliberately,
     * because the sweep must not read a standing it invented. A request is the other case: the
     * answer to a bad identifier is "there is no such customer", said by whichever module was
     * actually asked to do something, so the absence is turned back into the empty standing —
     * which is never read, because every path here checks the customer first or hands the
     * refusal to the module that will. Answering it here instead would move a refusal out of
     * the module that owns it in order to save one query.
     */
    private CustomerStanding theStandingOf(long customerId) {
        return standings.theStandingOf(customerId).orElseGet(CustomerStanding::nothingIsKnown);
    }

    /**
     * Puts this customer in the queue for something that has run out, so that running out is
     * not the end of it.
     *
     * <p>Beside the hold above rather than folded into it, because they are opposite requests
     * about opposite states of one offer: a hold is taken on something that is still there, and
     * a place in a queue is asked for precisely because it is not. Pressing the wrong one has
     * to be refused in words rather than quietly turned into the other — a customer who meant
     * to claim the last one and was put in a queue instead would have been given something
     * they did not ask for.
     *
     * <p>The standing goes up with it for the reason it goes up with a claim and with a hold:
     * joining runs the same gauntlet those two run, so it needs the same facts, and Rewards
     * reads none of the three modules that own them.
     *
     * <p>Created, with the place that now exists and the position in it, because "you are
     * third" is the whole of what the customer came here to find out and a second request to
     * discover it would be a page that could show them a queue they were not in.
     */
    @PostMapping("/{customerId}/waiting-lists")
    @ResponseStatus(HttpStatus.CREATED)
    PlaceInTheQueueResponse joinTheQueue(@PathVariable long customerId,
                                         @RequestBody JoinTheQueueRequest request) {
        // Reading the request, not judging it — the same division the claim and the hold above
        // draw. Whether the catalogue has heard of the code, whether the offer has genuinely
        // run out and whether this customer could ever have one are all rules, and all of them
        // are Rewards'.
        if (request == null || request.reward() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Joining a queue needs to name the offer being waited for.");
        }
        return PlaceInTheQueueResponse.of(rewards.joinTheQueue(customerId,
                request.reward().trim(), theStandingOf(customerId)));
    }

    /**
     * Takes this customer out of a queue, which closes the gap behind them at once.
     *
     * <p>A DELETE, for the reason giving up a hold is one: their place stops existing as a live
     * thing and the address stops having anything behind it. Nothing is deleted in the
     * database — the row keeps its history, like every other one-way door here — but what the
     * customer is doing is withdrawing their claim on a turn.
     *
     * <p>No standing is assembled and none is needed, exactly as giving up a hold needs none.
     * An offer withdrawn while they waited, or a rule they no longer meet, are reasons they
     * cannot have the thing and no reason at all to keep them standing in a line for it.
     *
     * <p>It answers with the place as it stood, position and all, so that the page can say
     * what happened without a second request and so that pressing twice is answered honestly
     * rather than silently.
     */
    @DeleteMapping("/{customerId}/waiting-lists/{code}")
    PlaceInTheQueueResponse leaveTheQueue(@PathVariable long customerId,
                                          @PathVariable String code) {
        return PlaceInTheQueueResponse.of(rewards.leaveTheQueue(customerId, code.trim()));
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
     * Everything the rules have decided was worth saying to this customer, newest first, read and
     * unread together.
     *
     * <p>Customer-scoped, matching the money-movement ledger, the claimed rewards and the gifts. A
     * notification is addressed to the person who reads it: balance rungs and coming anniversaries
     * have exactly one person they concern, and somebody holding two savings accounts has one panel
     * and not two. Which pot each row is about travels in it, so the panel can name it and an
     * account's own page can pick out the notice that concerns it.
     *
     * <p>Read and unread together, because the panel is a record rather than an inbox that empties.
     *
     * <p>Nothing is judged here. Whether the customer exists is a rule and it belongs to
     * Notifications, which refuses on its own and is reported by {@code RefusalsAsHttp} — this
     * endpoint turns the module's answer into the shape the API sends and decides nothing else.
     */
    @GetMapping("/{customerId}/notifications")
    List<NotificationResponse> notificationsOf(@PathVariable long customerId) {
        return notifications.notificationsOf(customerId).stream()
                .map(NotificationResponse::of)
                .toList();
    }

    /**
     * Marks everything this customer has not yet looked at as looked at, and answers the same list
     * back.
     *
     * <p>No body. There is nothing to say: the request is "I have looked at my notifications", the
     * customer is in the path, and when they looked is the application's own clock rather than
     * anything a browser can claim.
     *
     * <p>The list comes back so that the caller needs one round trip rather than two — opening the
     * panel is one action, and a page that had to read again afterwards would go on showing the
     * count it just cleared for as long as the second request took.
     *
     * <p>A plain 200 and not a 201: nothing was created. A moment was written onto rows that already
     * existed, and the answer is the record as it now reads.
     *
     * <p>Safe to send twice, because the rule underneath is: a notification keeps the moment it was
     * first read at, so a second call marks nothing and answers the same list.
     */
    @PostMapping("/{customerId}/notifications/read")
    List<NotificationResponse> markNotificationsRead(@PathVariable long customerId) {
        return notifications.markEverythingReadFor(customerId).stream()
                .map(NotificationResponse::of)
                .toList();
    }

    /**
     * Every shared pot this customer belongs to, oldest first, each with what it holds and who else
     * is in it with what role.
     *
     * <p>Customer-scoped, matching the gifts, the claimed rewards and the notifications: a pot is
     * not this customer's, but "which of them am I in" is a question only they can ask, and it is
     * answered where every other per-customer read of this application is. Which role they hold in
     * each travels in the membership on each pot rather than beside it, so that one shape answers
     * this list and the pot's own page alike.
     *
     * <p>Asked before the pots are, so that a customer nobody has heard of is refused rather than
     * answered with the empty list of somebody who simply belongs to none. The same line every other
     * per-customer read here draws.
     *
     * <p>In one read transaction with the balances put beside them, so that every figure on the page
     * describes the same instant of the ledger. What a pot holds is the deposits module's answer and
     * is assembled here for the reason {@code SharedPotController} gives: the module that owns the
     * pot owns no money.
     */
    @Transactional(readOnly = true)
    @GetMapping("/{customerId}/shared-pots")
    List<SharedPotResponse> sharedPotsOf(@PathVariable long customerId) {
        if (!accounts.customerExists(customerId)) {
            throw noSuchCustomer(customerId);
        }
        return sharedPots.potsOf(customerId).stream()
                .map(pot -> SharedPotResponse.of(pot, deposits.moneyBalanceOf(pot.savingsAccountId())))
                .toList();
    }

    /**
     * The invitations to shared pots waiting for this customer to answer, oldest first, each naming
     * the pot, who is asking and what they are being offered.
     *
     * <p>Customer-scoped, beside the pots they belong to and for the same reason: an invitation is
     * not this customer's pot, but "who is asking me to join something" is a question only they can
     * ask, and it is answered where every other per-customer read of this application is.
     *
     * <p>Waiting ones only, because this is a panel rather than a record: an invitation they have
     * already answered is not waiting for them, and what became of it is the pot's own list. That is
     * the one way this read differs from {@code GET /api/shared-pots/{potId}/invitations}, which
     * answers every invitation a pot ever issued.
     *
     * <p>Asked before the invitations are, so that a customer nobody has heard of is refused rather
     * than answered with the empty list of somebody nobody happens to be asking anything. The same
     * line every other per-customer read here draws.
     */
    @GetMapping("/{customerId}/pot-invitations")
    List<PotInvitationResponse> potInvitationsWaitingFor(@PathVariable long customerId) {
        if (!accounts.customerExists(customerId)) {
            throw noSuchCustomer(customerId);
        }
        return sharedPots.invitationsWaitingFor(customerId).stream()
                .map(PotInvitationResponse::of)
                .toList();
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
