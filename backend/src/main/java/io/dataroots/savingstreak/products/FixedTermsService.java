package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.WithdrawalsService;
import io.dataroots.savingstreak.products.TheTermAnAccountIsLockedInto.ATermInForce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;

/**
 * The two things a customer holding a fixed term can do about it: read what it says today, and
 * break it.
 *
 * <p><strong>Breaking is a deliberate operation and not a withdrawal that happens to go
 * through.</strong> That is the decision this class exists to make, and the ticket states it as a
 * criterion of its own. A withdrawal from an unmatured term is refused — by
 * {@link TheTermAnAccountIsLockedInto}, through the one question Deposits asks on the way out — in
 * a sentence naming the day it matures and how long is left. Breaking is a second press, on a
 * second door, after a reading that quotes the price. The alternative the spec sketches, where any
 * withdrawal before maturity quietly charges the penalty and moves the account, would mean a
 * customer taking fifty euros out of what they thought was an instant-access account and paying
 * ninety days of interest for the privilege of finding out it was not. A price nobody was shown is
 * not a price anybody agreed to.
 *
 * <p><strong>Breaking and taking the money out are two presses, and that is the same decision
 * again.</strong> Breaking ends the term and moves the account to free savings; what the customer
 * then does with the money is an ordinary withdrawal, refused by nothing. Folding the withdrawal
 * into the break would make the charge depend on how much was taken — and the figure quoted before
 * the button was pressed would stop being the figure charged the moment the balance moved, which is
 * the one promise this operation is built around.
 *
 * <p><strong>The charge is money leaving the account, written as its own movement.</strong> The
 * ledger's balance is the sum of what its rows still hold, so a charge that did not draw rows down
 * would not change the balance at all — and a balance that fell with no row to point at would be
 * the one figure in this application nobody could explain. {@code WithdrawalsService} writes it,
 * because that module owns how money leaves a savings account and this one owns what it costs.
 *
 * <p><strong>What it costs is priced by the same arithmetic the monthly sweep is priced by.</strong>
 * {@link WhatAnAnnualRateIsWorth} turns a rate in basis points into euros over a slice of a year,
 * once, for both — because a second place a rate is priced is the first place two rates disagree,
 * and the disagreement would be a customer charged at a rate they were never paid at.
 *
 * <p><strong>Nothing here settles a maturity.</strong> What happens on the morning a term is up —
 * rolling over into another term at the day's rates, moving to instant access, or sitting where it
 * is — is {@link MaturitiesService}'s business. This class answers only whether the day has come:
 * before it, the money is locked and breaking is the way out; on it and after it, the money leaves
 * with nothing in the way and nothing to pay. What it does now say is which of the three endings
 * this account agreed to, because that is a fact about the terms in force and somebody deciding
 * whether to break a term early is deciding against it.
 */
@Service
public class FixedTermsService {

    private static final Logger log = LoggerFactory.getLogger(FixedTermsService.class);

    /** Nought euros, quoted as money, which is what a term worth nothing to break costs. */
    private static final BigDecimal NOTHING = AmountOfMoney.quotedToTheCent(BigDecimal.ZERO);

    private final TheTermAnAccountIsLockedInto lock;
    /**
     * The record of what each account is living under, asked for the one thing breaking changes:
     * the account moves onto free savings at the version being sold today.
     */
    private final WhatEachSavingsAccountIsOn agreements;
    /**
     * The ledger, asked one question: what the account still holds. The charge is a fraction of
     * that figure, read at the moment the question is put and handed straight to the rule that
     * prices it. This module owns no money and adds nothing up — the same bargain
     * {@link ProductsService} makes when it closes an account.
     */
    private final DepositsService deposits;
    /**
     * Where the charge is written, and the only thing this class asks of the withdrawal side of
     * Deposits. It hands over an amount already worked out: that module has no opinion about rates,
     * and this one has none about how a savings ledger is drawn down.
     *
     * <p>There is no cycle here to be careful of, and it is worth saying once. Deposits' own
     * withdrawal service holds {@code WhatAnAgreementSaysAboutMoneyLeaving}, which this module
     * answers — so nothing on that path may hold this class, and nothing does:
     * {@link TheTermAnAccountIsLockedInto} is the class the gate reaches, and it holds two
     * repositories and a clock. This one is reached only by the door a customer presses.
     */
    private final WithdrawalsService withdrawals;
    private final Clock clock;

    FixedTermsService(TheTermAnAccountIsLockedInto lock, WhatEachSavingsAccountIsOn agreements,
                      DepositsService deposits, WithdrawalsService withdrawals, Clock clock) {
        this.lock = lock;
        this.agreements = agreements;
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.clock = clock;
    }

    /**
     * What this account's term says today: the months, the maturity date, the days left and what
     * breaking would cost.
     *
     * <p>Answered for every savings account, including the three products in four that have no term
     * at all: a screen asking about an account it has just opened is entitled to an answer rather
     * than a refusal, and nought months with no maturity date and nothing to pay is the true one.
     * The same reading, for the same reason, that the notice door gives every account.
     *
     * <p>The price is worked out here rather than by whoever is drawing the screen, and it is worked
     * out by the same function that charges it. That is what makes "what breaking would cost" and
     * "what breaking cost" one number instead of two that agree most of the time.
     */
    @Transactional(readOnly = true)
    public TheTermOnAnAccount termOn(long savingsAccountId) {
        Optional<ATermInForce> locked = lock.theTermOn(savingsAccountId);
        if (locked.isEmpty()) {
            log.debug("the term on a savings account was read savingsAccountId={} termMonths=0 "
                    + "reason=it is not on a term", savingsAccountId);
            return new TheTermOnAnAccount(savingsAccountId, 0, null, false, 0,
                    AmountOfMoney.quotedToTheCent(deposits.moneyBalanceOf(savingsAccountId)), 0,
                    NOTHING, null, null);
        }
        ATermInForce term = locked.get();
        LocalDate today = lock.today();
        BigDecimal balance = AmountOfMoney.quotedToTheCent(
                deposits.moneyBalanceOf(savingsAccountId));
        boolean matured = TheTermAnAccountIsLockedInto.hasMatured(term.maturesOn(), today);
        // Nothing to pay once the day has come, which is the whole of "a withdrawal after maturity
        // is allowed with no charge" said as a figure rather than as a rule somewhere else.
        BigDecimal cost = matured ? NOTHING : asEuros(priceOfBreaking(term, balance));
        long daysLeft = TheTermAnAccountIsLockedInto.daysLeftOn(term.maturesOn(), today);
        String atTheEnd = whatTheTermsSayHappensAtTheEnd(term, matured);
        log.debug("the term on a savings account was read savingsAccountId={} product={} "
                        + "termMonths={} openedOn={} termRunsFrom={} maturesOn={} on={} "
                        + "matured={} daysLeft={} balance={} penaltyDays={} "
                        + "whatBreakingWouldCost={} maturityAction={}",
                savingsAccountId, term.productCode(), term.termMonths(), term.openedOn(),
                term.termRunsFrom(), term.maturesOn(), today, matured, daysLeft, asMoney(balance),
                term.penaltyDays(), asMoney(cost), term.maturityAction());
        return new TheTermOnAnAccount(savingsAccountId, term.termMonths(), term.maturesOn(),
                matured, daysLeft, balance, term.penaltyDays(), cost, term.maturityAction(),
                atTheEnd);
    }

    /**
     * What this account's own terms say happens on the day the term is up, as a sentence naming the
     * day.
     *
     * <p><strong>The three endings in one place, in the module that owns the vocabulary.</strong>
     * {@link MaturityAction} is three words; what each of them means to somebody holding the account
     * is three sentences, and this is where they are written. A screen that turned the word into
     * prose of its own would be a second statement of what the agreement says — and the ticket asks
     * for the ending "in the words the terms use", which are these.
     *
     * <p><strong>Two tenses, because the same account reads this on both sides of the day.</strong>
     * A term whose maturity is ahead is told what will happen; one whose maturity has gone — which
     * is any matured term before the nightly sweep reaches it, and a waiting term for ever after —
     * is told what did. The alternative, one tense for both, would have an account that came free a
     * fortnight ago promising its holder something for next week.
     *
     * <p><strong>It says what the ending does, not what the account is on now.</strong> A rolled-over
     * term reads this off the version it rolled <em>into</em>, because that is what the account is
     * living under from the morning it rolled, and what happens at the end of <em>this</em> term is
     * what its holder is asking about.
     */
    private static String whatTheTermsSayHappensAtTheEnd(ATermInForce term, boolean matured) {
        String when = matured
                ? "It matured on " + term.maturesOn() + " and "
                : "When it matures on " + term.maturesOn() + ", ";
        return when + switch (term.maturityAction()) {
            case ROLL_OVER -> "the money goes straight into another " + term.termMonths()
                    + "-month term at the rates on offer that day — a new agreement, not this one "
                    + "extended. Break it or take the money out before then if you would rather it "
                    + "did not.";
            case MOVE_TO_INSTANT -> "the account moves to free savings at the rates on offer that "
                    + "day, and the money is yours to take out from that morning.";
            case HOLD -> "the money stays exactly where it is, earning what free savings earns, "
                    + "until you do something about it. Nothing moves on your behalf.";
        };
    }

    /**
     * Breaks a fixed term: charges the stated price, ends the term, and moves the account onto free
     * savings at the version being sold today.
     *
     * <p><strong>The charge first, the move second, and in one transaction.</strong> The price is a
     * fraction of what the account holds, and the account holds what it holds under the agreement
     * being broken — so it is worked out and taken while that agreement is still the one in force.
     * Moving first and charging afterwards would price the penalty of a fixed term against an
     * instant-access account, which is a sentence that means nothing. Both in one transaction,
     * because a charge without a move is a customer who paid to break a term that is still locked,
     * and a move without a charge is a term broken for free.
     *
     * <p><strong>Nothing is withdrawn on the customer's behalf.</strong> The money is theirs from
     * this moment and where it goes decides a week, a streak and a loyalty clock — three decisions
     * this application does not take for anybody. What breaking does is take the lock off; the
     * withdrawal that follows is an ordinary withdrawal, refused by nothing.
     *
     * <p><strong>A term broken cannot be broken twice, and nothing here remembers that.</strong>
     * Breaking moves the account onto a product with no term, so a second press finds an account
     * that is not on a term and is refused in exactly those words. The rule is a consequence of what
     * breaking does rather than a flag anything has to check.
     *
     * <p><strong>An account with nothing in it is broken for nothing.</strong> The charge is a
     * fraction of the balance, the balance is nought, and no movement is written — because a
     * movement of nought euros is a row that says nothing happened. The term still ends, because
     * what the customer asked for was to be free of it.
     *
     * @throws TermRefused when the account is not on a term at all — which includes a term broken
     *                     already — or when the term has already matured, in which case the money
     *                     was free without anybody paying for it
     */
    @Transactional
    public ATermBroken breakTheTerm(long savingsAccountId) {
        LocalDate today = lock.today();
        ATermInForce term = lock.theTermOn(savingsAccountId).orElseThrow(() -> refusing(
                TermRefused.Kind.NOT_A_TERM_ACCOUNT,
                "That savings account is not locked into a fixed term, so there is nothing to "
                        + "break — the money leaves it whenever you like.",
                "savingsAccountId=" + savingsAccountId));
        if (TheTermAnAccountIsLockedInto.hasMatured(term.maturesOn(), today)) {
            throw refusing(TermRefused.Kind.A_TERM_THAT_HAS_ALREADY_MATURED,
                    "That term matured on " + term.maturesOn() + ", so there is nothing to break "
                            + "and nothing to pay. The money has been yours since then.",
                    "savingsAccountId=" + savingsAccountId + " product=" + term.productCode()
                            + " maturedOn=" + term.maturesOn());
        }
        BigDecimal balance = AmountOfMoney.quotedToTheCent(
                deposits.moneyBalanceOf(savingsAccountId));
        long cents = priceOfBreaking(term, balance);
        BigDecimal charge = asEuros(cents);
        log.debug("breaking a fixed term was asked for savingsAccountId={} product={} version={} "
                        + "termMonths={} openedOn={} maturesOn={} on={} daysLeft={} balance={} "
                        + "rateBasisPoints={} penaltyDays={} charge={}",
                savingsAccountId, term.productCode(), term.itsTerms().version(), term.termMonths(),
                term.openedOn(), term.maturesOn(), today,
                TheTermAnAccountIsLockedInto.daysLeftOn(term.maturesOn(), today), asMoney(balance),
                term.annualRateBasisPoints(), term.penaltyDays(), asMoney(charge));
        Long chargedAs = cents == 0 ? null
                : withdrawals.chargeAnEarlyExitFrom(savingsAccountId, charge, now());
        TheAgreementAnAccountIsOn nowOn = agreements.moveToFreeSavings(savingsAccountId,
                "a fixed term was broken early, so the account is no longer locked into one");
        // One INFO line with every figure that decided it, because this is the business event the
        // ticket is about: a customer gave up a rate and paid for it, and "what did breaking my
        // term actually cost me" has to be answerable from one line. The days, the charge and the
        // account are named, which is what the ticket asks for in as many words.
        log.info("a fixed term was broken early savingsAccountId={} wasOn={} wasOnVersion={} "
                        + "termMonths={} brokenOn={} wouldHaveMaturedOn={} daysLeft={} "
                        + "rateBasisPoints={} penaltyDays={} balance={} charge={} "
                        + "chargedAsWithdrawalId={} nowOn={} nowOnVersion={} maturesOn={}",
                savingsAccountId, term.productCode(), term.itsTerms().version(), term.termMonths(),
                today, term.maturesOn(),
                TheTermAnAccountIsLockedInto.daysLeftOn(term.maturesOn(), today),
                term.annualRateBasisPoints(), term.penaltyDays(), asMoney(balance),
                asMoney(charge), chargedAs, nowOn.productCode(), nowOn.version(),
                nowOn.maturesOn());
        return new ATermBroken(savingsAccountId, today, term.maturesOn(), term.termMonths(),
                term.penaltyDays(), balance, charge, nowOn);
    }

    /**
     * The price of breaking this term on this balance, in cents.
     *
     * <p>One private line with two callers, which is the whole point of it: the figure a customer
     * is shown before they confirm and the figure they are charged when they do are produced by the
     * same call on the same balance, so they cannot come out different. The arithmetic itself is
     * {@link TheTermAnAccountIsLockedInto#whatBreakingWouldCost}, which is where the decisions about
     * the rate, the days in a year and the direction of the flooring are argued.
     */
    private static long priceOfBreaking(ATermInForce term, BigDecimal balance) {
        return TheTermAnAccountIsLockedInto.whatBreakingWouldCost(
                balance.movePointRight(2).longValueExact(), term.annualRateBasisPoints(),
                term.penaltyDays());
    }

    /**
     * Every refusal this class makes says why in the log as well as to whoever asked, because only
     * one of the two is kept: the reason reaches the person at the keyboard and nowhere else.
     *
     * <p>The values that decided it travel as a fragment the caller assembles rather than as a fixed
     * set of columns — the same arrangement {@link WhatEachSavingsAccountIsOn} makes of its four
     * refusals, and for the same reason: a line with three empty fields on it is a line a reviewer
     * has to read twice.
     */
    private TermRefused refusing(TermRefused.Kind kind, String reason, String what) {
        log.warn("breaking a fixed term was refused {} kind={} reason={}", what, kind, reason);
        return new TermRefused(kind, reason);
    }

    /**
     * The cents as the euros everything outside this module speaks, through {@code AmountOfMoney}
     * like every other figure that leaves here: how many places a euro amount has is a decision this
     * application made once, and this module has no business making it again.
     */
    private static BigDecimal asEuros(long cents) {
        return AmountOfMoney.quotedToTheCent(BigDecimal.valueOf(cents, 2));
    }

    /**
     * The moment the charge is dated at, truncated the way every other movement this application
     * records is, so that a ledger sorted by moment puts it exactly where the break happened.
     */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
