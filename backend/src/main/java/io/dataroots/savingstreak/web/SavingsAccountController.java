package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.RecordedDeposit;
import io.dataroots.savingstreak.deposits.WithdrawalsService;
import io.dataroots.savingstreak.loyalty.LoyaltyService;
import io.dataroots.savingstreak.loyalty.NextAnniversaryOfADeposit;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.products.ProductsService;
import io.dataroots.savingstreak.streaks.StreaksService;
import io.dataroots.savingstreak.timeline.TimelineService;
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
 * A savings account, what is in it, what its holder has to spend, and the money that has moved in
 * and out of it.
 *
 * <p>The figures come from five modules that do not know about each other — who holds the account,
 * what has been paid into it, what its holder has earned, how their week and their run of weeks are
 * going and what that run pays, and when each deposit next pays for the money still sitting in it —
 * and are assembled here. Assembling an answer is not a rule: no decision about money, points,
 * weeks, rates or anniversaries is taken in this class.
 *
 * <p>Only the money is the account's. The points, the week and the run of weeks all belong to the
 * customer who holds it and read the same beside every account they hold; what paying in <em>here</em>
 * earned is on each deposit in the history.
 *
 * <p>Rewards are claimed against the customer and not here. The points that pay for one are the
 * holder's rather than this account's, so there is no account for a claim to come out of and no
 * account-shaped list of what has been claimed.
 */
@RestController
@RequestMapping("/api/savings-accounts")
class SavingsAccountController {

    private static final Logger log = LoggerFactory.getLogger(SavingsAccountController.class);

    private final AccountsService accounts;
    private final DepositsService deposits;
    private final WithdrawalsService withdrawals;
    private final PointsService points;
    private final StreaksService streaks;
    private final LoyaltyService loyalty;
    /**
     * What each savings account is living under, for the one part of this reading that is about the
     * account's rules rather than its figures. A sixth module beside the five, and asked the same
     * way: it answers what this account is on, and nothing here decides any of it.
     */
    private final ProductsService products;
    private final TimelineService timeline;

    SavingsAccountController(AccountsService accounts, DepositsService deposits, WithdrawalsService withdrawals,
                             PointsService points,
                             StreaksService streaks,
                             LoyaltyService loyalty, ProductsService products,
                             TimelineService timeline) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.points = points;
        this.streaks = streaks;
        this.loyalty = loyalty;
        this.products = products;
        this.timeline = timeline;
    }

    /**
     * The figures in one read transaction, so that they describe the same instant of the ledger. Each
     * module opens a read of its own otherwise, and a deposit committing between two of them would
     * have the page state a balance of EUR 0,00 beside a week that has taken EUR 60,00 in — an answer
     * that contradicts itself and that neither module is wrong about.
     *
     * <p>The week and the streak come back together from one call for the same reason one step
     * further in: they are one derivation off one reading of the clock, and asking for them
     * separately is what would have them describe two different weeks.
     */
    @Transactional(readOnly = true)
    @GetMapping("/{savingsAccountId}")
    SavingsAccountResponse savingsAccount(@PathVariable long savingsAccountId) {
        AccountHolder holder = accounts.holderOfSavingsAccount(savingsAccountId)
                .orElseThrow(() -> noSuchSavingsAccount(savingsAccountId));
        return SavingsAccountResponse.of(
                savingsAccountId,
                holder.name(),
                deposits.moneyBalanceOf(savingsAccountId),
                // The holder's points, not the account's. Paying in here earns them and they are
                // reported beside this balance because that is the connection the page is about —
                // but they are the same figure whichever of the customer's accounts is open.
                points.balanceOf(holder.customerId()),
                // And the most they have ever had in savings, which is also theirs rather than this
                // account's: it is the mark a deposit into any of their accounts is judged against,
                // and beside the balance it says whether paying in here will earn anything.
                deposits.mostEverSavedBy(holder.customerId()),
                // And what the holder stands to lose next, for the same reason: the twelve months
                // run against their points rather than against this account's saving.
                points.whatExpiresNextFor(holder.customerId()),
                // And their week and their run of weeks, for the same reason: a week counts what
                // they put away, wherever they put it.
                streaks.weekAndStreakOf(holder.customerId()),
                // And the agreement the account itself is living under, which is the one figure
                // here that is the account's rather than the holder's and is not a figure at all.
                // Empty for an account nothing has recorded one for, which is a database that has
                // not been through the start-up migration: the panel is then simply absent, rather
                // than filled in with an agreement nobody wrote.
                products.theAgreementOf(savingsAccountId),
                // And whether that product has published anything newer than the version above,
                // with what the difference would be. Read here rather than on a door of its own so
                // that the two arrive together: an agreement panel drawn a moment before the
                // comparison beside it would say "version 1" without saying that version 2 exists.
                // Nothing about it moves anything — taking the newer terms is a separate press, on
                // a separate door, because nothing in this application adopts terms on anybody's
                // behalf.
                products.theNewerTermsFor(savingsAccountId));
    }

    /**
     * Every deposit into the account, what each earned, and when each next pays.
     *
     * <p>In one read transaction, for the reason the overview above gives one step further out: what
     * a deposit has earned and what its next anniversary is worth are read from two modules, and a
     * withdrawal committing between the two would have a row reporting a bonus worked out on euros
     * that had already left. One transaction, and the two halves of every row describe the same
     * instant of the ledger.
     *
     * <p>Package-private like the rest of this class, and the annotation above is honoured all the
     * same. It is worth saying so, because {@link LoyaltyService#payLoyaltyBonuses} and
     * {@code PointsService} both carry a comment claiming a proxy cannot advise a method that is not
     * public and are declared public on the strength of it. That was true of older Spring: the
     * transaction attribute source used to read public methods only. Since Spring Framework 6.0 the
     * one this application runs on is built as {@code AnnotationTransactionAttributeSource(false)}
     * by {@code AbstractTransactionManagementConfiguration}, so that a CGLIB proxy — which can
     * override a package-private method of a class in its own package — advises this handler exactly
     * as it advises the overview above it. A DEBUG log from {@code JpaTransactionManager} names the
     * transaction after this method, which is how to check the claim rather than take it on trust.
     */
    @Transactional(readOnly = true)
    @GetMapping("/{savingsAccountId}/deposits")
    List<DepositResponse> depositsInto(@PathVariable long savingsAccountId) {
        // Asked before the deposits are, so that an account nobody has heard of is refused rather
        // than answered with the empty history of an account that has simply never been paid into.
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            throw noSuchSavingsAccount(savingsAccountId);
        }
        List<RecordedDeposit> history = deposits.depositsInto(savingsAccountId);
        // Absent for every deposit that has been emptied, which is how a row comes to report no
        // next anniversary: the money has gone and there is no promise left to make about it.
        Map<Long, NextAnniversaryOfADeposit> nextAnniversaries =
                loyalty.whenTheDepositsInAnAccountNextPay(savingsAccountId);
        return history.stream()
                .map(deposit -> DepositResponse.of(deposit, nextAnniversaries.get(deposit.id())))
                .toList();
    }

    /**
     * The year this account has ahead of it: the days its points go, the days its deposits pay, and
     * the window they are drawn in.
     *
     * <p>Its own resource rather than more fields on the overview above. The overview is read on
     * every visit to every screen and is already assembling five modules; this is wanted by one
     * screen, it is the only caller of two module reads that exist for it, and a resource that can be
     * asked for on its own is one a trainer can curl while demonstrating what a wound-forward clock
     * does to a bar.
     *
     * <p>Asked whether the account exists first, so that an account nobody has heard of is refused
     * rather than answered with the empty year of an account that has simply never been paid into —
     * the same order, and the same refusal, the deposit history above uses. The Timeline module
     * cannot draw that distinction itself: both are a list of no deposits, and who holds which
     * account is the Accounts module's answer.
     *
     * <p>No read transaction is opened here, because the module opens its own around the three reads
     * that have to describe one instant of the ledger. That is a rule about the answer's consistency
     * rather than about this endpoint, so it lives with the rule.
     */
    @GetMapping("/{savingsAccountId}/timeline")
    SavingsAccountTimelineResponse timelineOf(@PathVariable long savingsAccountId) {
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            log.warn("timeline rejected savingsAccountId={} reason={}", savingsAccountId,
                    AccountsService.noSuchSavingsAccount(savingsAccountId));
            throw noSuchSavingsAccount(savingsAccountId);
        }
        return SavingsAccountTimelineResponse.of(timeline.timelineOf(savingsAccountId));
    }

    @PostMapping("/{savingsAccountId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    DepositResponse deposit(@PathVariable long savingsAccountId, @RequestBody DepositRequest request) {
        // Reading the request, not judging it. Whether a figure is a number at all is a question
        // about the characters that arrived; whether the number is an amount this application will
        // move is a rule, and it belongs to Deposits, which refuses on its own.
        if (request.amount() == null || request.fromCurrentAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A deposit needs an amount and the current account it comes from.");
        }
        RecordedDeposit made = deposits.deposit(
                savingsAccountId, request.fromCurrentAccountId(), amountIn(request));
        // The anniversary the money has just started counting towards, read the same way the
        // history reads it rather than worked out here: a deposit just made and the same deposit
        // looked back at have to say the same thing, and one code path is what makes that true
        // instead of hoped for. It holds all of its money, so it is always in this answer.
        return DepositResponse.of(
                made, loyalty.whenTheDepositsInAnAccountNextPay(savingsAccountId).get(made.id()));
    }

    @PostMapping("/{savingsAccountId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    WithdrawalResponse withdraw(@PathVariable long savingsAccountId, @RequestBody WithdrawalRequest request) {
        if (request == null || request.amount() == null || request.toCurrentAccountId() == null) {
            String reason = "A withdrawal needs an amount and the current account it returns to.";
            log.warn("withdrawal rejected savingsAccountId={} reason={}", savingsAccountId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return WithdrawalResponse.of(withdrawals.withdraw(
                savingsAccountId, request.toCurrentAccountId(), withdrawalAmountIn(savingsAccountId, request.amount())));
    }

    @GetMapping("/{savingsAccountId}/withdrawals")
    List<WithdrawalResponse> withdrawalsFrom(@PathVariable long savingsAccountId) {
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            log.warn("withdrawal history rejected savingsAccountId={} reason={}", savingsAccountId,
                    AccountsService.noSuchSavingsAccount(savingsAccountId));
            throw noSuchSavingsAccount(savingsAccountId);
        }
        return withdrawals.withdrawalsFrom(savingsAccountId).stream().map(WithdrawalResponse::of).toList();
    }

    /**
     * Closes a savings account its holder has emptied, and answers with the agreement as it now
     * reads — the product, the version, the day it began, and the day it ended.
     *
     * <p><strong>A POST to a door, and deliberately not a DELETE.</strong> Nothing goes away.
     * Every deposit, every withdrawal, every goal and every allocation on this account stays
     * readable afterwards, and so does the agreement itself: a closed account still names the
     * product and the version every one of those rows was decided under. A DELETE would promise the
     * opposite of what happens, and this application has already drawn that line once — withdrawing
     * a reward offer is a press on a door for the same reason, because vouchers point at it.
     *
     * <p><strong>The account has to exist and has to be held by somebody</strong>, both settled
     * here before anything is asked of the catalogue. An account nobody holds belongs to a shared
     * pot, and a pot's account is closed by closing the pot — which settles the members' money
     * first. Letting this door reach one would be a way to close a pot's account out from under the
     * pot, so it answers what every other reading in this class answers for an account nobody
     * holds: there is no such savings account here.
     *
     * <p>Whether it may be closed is the catalogue's rule and is refused in the catalogue's words:
     * an account with money still in it, and an account closed already, are both conflicts rather
     * than anything to correct on a form. Nothing about that judgement is repeated here.
     */
    @PostMapping("/{savingsAccountId}/close")
    AnAgreementResponse close(@PathVariable long savingsAccountId) {
        AccountHolder holder = accounts.holderOfSavingsAccount(savingsAccountId)
                .orElseThrow(() -> {
                    log.warn("closing a savings account rejected savingsAccountId={} reason={}",
                            savingsAccountId, AccountsService.noSuchSavingsAccount(savingsAccountId));
                    return noSuchSavingsAccount(savingsAccountId);
                });
        AnAgreementResponse closed =
                AnAgreementResponse.of(products.closeTheSavingsAccount(savingsAccountId));
        log.info("a savings account was closed over HTTP savingsAccountId={} customerId={} "
                        + "product={} closedOn={}",
                savingsAccountId, holder.customerId(), closed.productCode(), closed.closedOn());
        return closed;
    }

    /**
     * The amount as a figure, or a refusal naming what could not be read as one. Named back to
     * whoever sent it, because a person who typed a comma has to see the comma to see the mistake.
     */
    private BigDecimal amountIn(DepositRequest request) {
        return amountIn(request.amount());
    }

    private BigDecimal amountIn(String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException notANumber) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\"" + amount
                    + "\" is not an amount of money. Write it in digits with a full stop, like 25.00.");
        }
    }

    /**
     * The same reading a deposit gets, logged on the way out. Every other way a withdrawal can be
     * refused says so in the Withdrawals module's own log; this one is decided here, before the
     * module is called at all, and would otherwise be the single refusal a reviewer reading the
     * application's log could not find. The sentence is logged rather than a summary of it, so that
     * what the log says and what the customer was told are the same words.
     */
    private BigDecimal withdrawalAmountIn(long savingsAccountId, String amount) {
        try {
            return amountIn(amount);
        } catch (ResponseStatusException notAnAmountOfMoney) {
            log.warn("withdrawal rejected savingsAccountId={} amount={} reason={}",
                    savingsAccountId, amount, notAnAmountOfMoney.getReason());
            throw notAnAmountOfMoney;
        }
    }

    /** Worded for whoever reads it: a refusal reaches the screen with its reason unchanged. */
    private ResponseStatusException noSuchSavingsAccount(long savingsAccountId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, AccountsService.noSuchSavingsAccount(savingsAccountId));
    }
}
