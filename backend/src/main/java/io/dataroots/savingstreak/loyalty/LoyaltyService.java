package io.dataroots.savingstreak.loyalty;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.deposits.DepositStillHoldingMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.MoneyAMoveWouldTake;
import io.dataroots.savingstreak.loyalty.LoyaltyBonusPaidRepository.AnniversaryAlreadyPaid;
import io.dataroots.savingstreak.points.PointsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Money that stays in savings earns again: the Loyalty module's face to the rest of the
 * application, and the whole of the rule.
 *
 * <p>Every twelve months a deposit's money is still there, that deposit pays a fraction of the
 * whole euros still sitting in it. A EUR 500 deposit left alone on free savings pays 50 points a
 * year after it landed, 50 more the year after that, and 50 more the year after — because the clock
 * belongs to the deposit, recurs, and is counted from the day the money landed rather than from the
 * last payment.
 *
 * <p><strong>What fraction is the product's answer rather than this module's.</strong> It used to
 * be a tenth for everybody, written down in {@link LoyaltyRate}; accounts are now on savings
 * products and a product says what its own anniversary pays, so the rate is asked for per account
 * through {@link WhatAnAnniversaryPaysHere} and handed to the arithmetic. The same EUR 500 sitting
 * in a product that pays twelve percent is worth 60 rather than 50, and the threshold under which
 * an anniversary is worth nothing moves with it, because it is worked out from the rate rather than
 * written down beside it. Nothing else about the rule moves: when an anniversary falls, that it
 * recurs, that it is judged on what is still in the deposit, and that a payment made is never
 * rewritten are all still this module's, and it still learns nothing about products beyond a
 * number.
 *
 * <p>A module of its own because neither of the two it stands between is the right home. Deposits
 * has no opinion about points and Points cannot see deposits, and this rule needs both plus a
 * clock; so Loyalty orchestrates them the way Deposits already orchestrates Streaks and Points when
 * a deposit lands. What it asks of each is deliberately small: Deposits answers which deposits still
 * hold money and how much, Points credits a number of points against a deposit at a moment, and
 * neither learns that anniversaries exist.
 *
 * <p>It answers two things and they are the same rule read in two directions: the sweep pays the
 * anniversaries that have arrived, and the read below says when a deposit next pays and what that
 * anniversary is worth at what the deposit holds today. Both work the figure out the same way, from
 * the euros still in the deposit, and both ask the same record which anniversaries have been paid —
 * so what a customer is promised on a Tuesday is what the sweep pays them if the money is still
 * there on the Wednesday, and an anniversary the sweep has not got to yet is one the customer is
 * still shown as coming rather than one that quietly disappears for a night.
 *
 * <p>There is no rule of its own for forfeiting. What an anniversary pays is worked out from what is
 * still in the deposit <em>at that moment</em>, so a deposit drawn down to nothing is worth nothing
 * on its anniversary and one drawn halfway down is worth half. The generous reading is deliberate:
 * the strict one would let a EUR 1 withdrawal destroy a hundred points on a EUR 1,000 deposit, and
 * would turn oldest-deposit-first allocation into a trap rather than a protection. Bonuses already
 * paid are never touched, because a payment that happened is a row that is never rewritten.
 */
@Service
public class LoyaltyService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);

    private final DepositsService deposits;
    private final PointsService points;
    private final LoyaltyBonusPaidRepository paid;
    /**
     * What an anniversary pays per whole euro in a given account, asked of whoever keeps the
     * agreements. {@link WhatAnAnniversaryPaysHere} says at length why the dependency runs this way
     * round: this module multiplies by a number and learns nothing else about products.
     *
     * <p>Injected as the interface and never as the class behind it, which is what keeps a start-up
     * cycle from forming — the module that implements this reads the same ledger this one does.
     */
    private final WhatAnAnniversaryPaysHere theRate;
    private final Clock clock;

    LoyaltyService(DepositsService deposits, PointsService points, LoyaltyBonusPaidRepository paid,
                   WhatAnAnniversaryPaysHere theRate, Clock clock) {
        this.deposits = deposits;
        this.points = points;
        this.paid = paid;
        this.theRate = theRate;
        this.clock = clock;
    }

    /**
     * Pays every anniversary that has arrived by the given moment and has not been paid yet, each
     * credited as its own batch dated at its own anniversary.
     *
     * <p>Every anniversary rather than the most recent one, which is what makes a recurring clock
     * demonstrable: winding the clock three years forward and running this once pays three bonuses,
     * not one. It is the same arrangement that covers a deposit made before this scheme existed —
     * such a deposit has simply passed several anniversaries unpaid, and is paid all of them.
     *
     * <p>Answers nothing, for the reason the points sweep gives: its one caller runs on a schedule
     * with nobody waiting on it, and a figure returned to a scheduled method is a figure nothing can
     * read. What the sweep did is in the INFO line below.
     *
     * <p>Public, unlike the record and the clock, and not because anything outside wants it:
     * {@code @Transactional} is applied by a proxy and a proxy cannot advise a method that is not
     * public, so the annotation would be silently ignored and a half-finished sweep would commit.
     * The power this leaks is the power to run the nightly job early, which is idempotent and is
     * exactly what the development jobs endpoint offers anyway.
     *
     * <p>The caller says what time it is. This module has no clock of its own and reads none: a
     * sweep run against a wound-forward clock has to judge anniversaries against the moment the
     * application thinks it is, and a service that read the machine's clock instead would quietly
     * refuse to be demonstrated.
     *
     * <p>Idempotent by construction. Every anniversary that pays anything writes a row naming the
     * deposit and the ordinal, the pair is unique, and a second pass over the same deposits finds
     * every one of them already paid and pays nothing.
     */
    @Transactional
    public void payLoyaltyBonuses(Instant now) {
        Instant landedBefore = LoyaltyAnniversary.nothingLandedAfterThisCanHaveAnAnniversaryBy(now);
        List<DepositStillHoldingMoney> holding =
                deposits.depositsStillHoldingMoneyThatLandedBefore(landedBefore);
        Set<AnAnniversary> alreadyPaid = whatHasAlreadyBeenPaidFor(holding);
        // What each account's product pays, asked once per account rather than once per deposit.
        // A sweep walks everybody's deposits and several of them belong to one account, so the
        // question would otherwise be put again for every one of them and answered identically. It
        // is read afresh each sweep and never cached beyond it: an anniversary pays at the rate of
        // the product the money is sitting in tonight, and an account that took newer terms this
        // afternoon is swept at the terms it took.
        Map<Long, BigDecimal> ratesByAccount = new HashMap<>();
        long anniversariesPaid = 0;
        long pointsPaid = 0;
        for (DepositStillHoldingMoney deposit : holding) {
            int passed = LoyaltyAnniversary.anniversariesPassedBy(deposit.depositedAt(), now);
            if (passed == 0) {
                // Inside the cut-off's two days of slack rather than inside its twelve months. Said
                // out loud because it is the one place the query and the rule disagree on purpose,
                // and a reader counting deposits would otherwise be short.
                log.debug("deposit passed over for a loyalty bonus depositId={} customerId={} "
                                + "reason=still inside its first year depositedAt={} "
                                + "nextAnniversary={} remainingAmount={}",
                        deposit.id(), deposit.customerId(), deposit.depositedAt(),
                        LoyaltyAnniversary.anniversaryOf(deposit.depositedAt(), 1),
                        deposit.remainingAmount());
                continue;
            }
            BigDecimal rate = rateFor(deposit.savingsAccountId(), ratesByAccount);
            for (int ordinal = 1; ordinal <= passed; ordinal++) {
                long bonus = payIfItIsDue(deposit, ordinal, rate, alreadyPaid);
                if (bonus > 0) {
                    anniversariesPaid++;
                    pointsPaid += bonus;
                }
            }
        }
        // One line per sweep with everything that decided it: the moment it judged anniversaries
        // against, the cut-off the query used, how many deposits it looked at, how many
        // anniversaries it paid and what they came to. A balance that grew overnight is explainable
        // from this line alone, and a sweep that read forty deposits and paid none of them can be
        // told from a sweep that was handed nothing to look at.
        log.info("loyalty bonuses paid asAt={} landedBefore={} depositsConsidered={} "
                        + "accountsWhoseRateWasRead={} anniversariesPaid={} points={}",
                now, landedBefore, holding.size(), ratesByAccount.size(), anniversariesPaid,
                pointsPaid);
    }

    /**
     * When each deposit in a savings account next pays and what that anniversary is worth at what
     * the deposit holds today, by deposit.
     *
     * <p>The promise the scheme is making about money that is still there, which is why what is in
     * the answer is decided by what is in the deposit. A deposit the customer has emptied is not in
     * it at all: the money has gone, there is no anniversary left for it to reach, and a date shown
     * against it would be a promise about euros that are not in the account. A deposit holding nine
     * euros <em>is</em> in it, with a date and nothing to be earned on it, because rounding down is
     * a rule the customer is entitled to see rather than an absence they have to guess at.
     *
     * <p>The figure falls when the customer withdraws, without anything here knowing that a
     * withdrawal happened. It is the account's rate on the euros still in the deposit, so drawing a
     * deposit halfway down halves what its next anniversary is worth — the cost of a withdrawal, legible on
     * the history afterwards, which is the whole reason this is reported at what the deposit holds
     * now rather than at what landed in it.
     *
     * <p>A map by deposit rather than a list, because the caller is putting these beside the
     * account's history rows and has to be able to find the one belonging to a row — and to find
     * that there is none, which is the answer for every deposit that has been emptied.
     *
     * <p>What each deposit has already been paid is not reported here and is not asked for. Those
     * are points in the customer's pot, the ledger reports them against the deposit like every other
     * reason a deposit has earned under, and reading them a second way here would be a second answer
     * to disagree with the first. The record of <em>which</em> anniversaries have been paid is read,
     * though, because the next payment cannot be worked out without it — see below.
     *
     * <p>The anniversary that pays next, not the next one on the calendar. An anniversary counts as
     * arrived the moment it falls and the sweep that pays it runs at half past three the following
     * morning, so a deposit whose anniversary fell this lunchtime is owed a bonus that has not been
     * paid — and a calendar reading would have skipped straight to next year, showing the customer
     * nothing earned beside a date twelve months out and then appearing to pay a year late. So the
     * ordinal reported is the earliest one this deposit is owed and has not been paid, and the
     * calendar's next only when it is owed nothing. The date can therefore be a day just gone, which
     * is the honest answer while a payment is outstanding.
     *
     * <p>Crossed with what the deposit is worth, because an anniversary that pays nothing writes no
     * row to have been paid. A deposit holding nine euros is worth nothing on any anniversary at a
     * tenth — and is worth one on a product paying twelve percent, which is the same rule reading
     * the account's own rate rather than a constant — so
     * every anniversary it has ever passed is for ever unrecorded, and an earliest-unpaid rule on its
     * own would pin such a deposit's date in the first year of its life. Worth nothing, and the
     * calendar's next anniversary is the promise — which is also the only one it can keep.
     *
     * <p>This one reads the clock, unlike the sweep above, which is told what time it is. The sweep
     * has a caller that already knows the moment and one transaction in which every anniversary must
     * be judged against the same one; this has a customer looking at a page, and there is nobody in
     * that path with a better claim to what time it is than the application's own clock — a read
     * that took the moment from the web layer would let the answer depend on which endpoint asked.
     */
    @Transactional(readOnly = true)
    public Map<Long, NextAnniversaryOfADeposit> whenTheDepositsInAnAccountNextPay(long savingsAccountId) {
        Instant now = clock.instant();
        List<DepositStillHoldingMoney> holding = deposits.depositsStillHoldingMoneyIn(savingsAccountId);
        Set<AnAnniversary> alreadyPaid = whatHasAlreadyBeenPaidFor(holding);
        // One account, so one rate, asked once. What the page promises and what the sweep pays are
        // the same figure because both of them ask the same question of the same agreement.
        BigDecimal rate = theRate.perWholeEuroIn(savingsAccountId);
        Map<Long, NextAnniversaryOfADeposit> next = new LinkedHashMap<>();
        long worthAltogether = 0;
        long worthNothing = 0;
        long owedAnAnniversaryAlready = 0;
        for (DepositStillHoldingMoney deposit : holding) {
            long wholeEuros = LoyaltyRate.wholeEurosIn(deposit.remainingAmount());
            long worth = LoyaltyRate.pointsOn(wholeEuros, rate);
            int ordinal = theAnniversaryThatPaysNextFor(deposit, now, worth, alreadyPaid);
            Instant anniversary = LoyaltyAnniversary.anniversaryOf(deposit.depositedAt(), ordinal);
            next.put(deposit.id(), new NextAnniversaryOfADeposit(
                    deposit.id(), LoyaltyAnniversary.dayOf(anniversary), worth));
            worthAltogether += worth;
            if (worth == 0) {
                worthNothing++;
            }
            if (!anniversary.isAfter(now)) {
                owedAnAnniversaryAlready++;
            }
        }
        // One line for the read, with the arithmetic behind it: the moment the anniversaries were
        // counted from, how many deposits still hold money and are therefore being promised
        // something, how many of them are promised nothing because a tenth of what they hold rounds
        // away, how many are being promised an anniversary that has already fallen and is still
        // waiting on the sweep, and what the account's next anniversaries come to altogether. A
        // deposit is counted once there however many anniversaries it is owed, because the promise
        // reported against it is one date. A customer asking why a figure fell after they withdrew
        // is answered by this line either side of the withdrawal, and a date in the past on the page
        // is explained by the count beside it rather than looking like an off-by-a-year.
        //
        // Counts and a total rather than a line per deposit, as the history read beside it does:
        // this runs on every read of an account's history, and a line per deposit would bury the
        // business events in a page load.
        log.debug("when each deposit in an account next pays savingsAccountId={} asAt={} "
                        + "ratePerWholeEuro={} depositsStillHoldingMoney={} "
                        + "worthNothingOnTheirNextAnniversary={} "
                        + "depositsOwedAnAnniversaryTheSweepHasNotPaid={} "
                        + "nextAnniversariesWorthAltogether={}",
                savingsAccountId, now, rate, holding.size(), worthNothing, owedAnAnniversaryAlready,
                worthAltogether);
        return next;
    }

    /**
     * What moving this much money from one of a customer's savings accounts to another of their own
     * would cost them in loyalty, worked out before anything has moved.
     *
     * <p><strong>The only price a move has, and the reason this module is the one asked.</strong>
     * Moving money is deliberately free of everything else — no points earned and none lost, no
     * week secured and none broken, and the most the customer has ever saved standing exactly where
     * it was. What it cannot be free of is the clock: the euros arrive as a new deposit dated the
     * day they arrive, so whatever they were part-way towards is given up and a fresh twelve months
     * begins. Only this module can put a number on that, and putting it on before the press rather
     * than after is the difference between a cost and a surprise.
     *
     * <p><strong>Which euros would move is asked of Deposits rather than guessed at here.</strong>
     * A savings account gives its rows up in an order that is Deposits' rule — the bank's own
     * interest first, then the customer's deposits oldest first — and a reading that walked the
     * deposits in its own order would quote the forfeit of euros the move would not have touched.
     * So the question crosses once, as a list of euros with what would be left behind in each row,
     * and this method prices exactly those. It is the same walk the move itself uses, which is what
     * makes the figure quoted and the figure charged one number rather than two.
     *
     * <p><strong>It refuses whatever the move would refuse</strong>, because Deposits refuses it on
     * the way past: a cost quoted for a move that would have been turned away is worse than no
     * quote, since somebody would weigh a loyalty clock against a notice period they were never
     * going to get past.
     *
     * <p><strong>The forfeit is a subtraction and not a total.</strong> What a deposit's next
     * anniversary pays is a fraction of what is still in it, so emptying half of one gives up half
     * of its anniversary — the same generous reading a withdrawal already gets, and for the same
     * reason: the strict one would let a single euro moving destroy a hundred points. Both sides of
     * the subtraction are taken at the account's own rate, which is asked once.
     *
     * <p><strong>Both rates are asked, because they are not the same rate.</strong> What the
     * arriving money will be worth on its new anniversary is the <em>destination</em> product's
     * figure, and a customer moving to a better product is owed that side of it as well as the
     * loss. A reading that quoted only the forfeit would be arguing one way about a decision the
     * whole feature exists to let somebody make with their eyes open.
     *
     * <p>It reads the clock, unlike the sweep and like the reading above it, and for that reading's
     * reason: there is a customer looking at a page and nobody in that path with a better claim to
     * what time it is than the application's own clock.
     */
    @Transactional(readOnly = true)
    public WhatMovingWouldCostInLoyalty whatMovingWouldCost(long fromSavingsAccountId,
                                                            long toSavingsAccountId,
                                                            BigDecimal amount) {
        Instant now = clock.instant();
        List<MoneyAMoveWouldTake> wouldTake =
                deposits.whatAMoveWouldDrawDown(fromSavingsAccountId, toSavingsAccountId, amount);
        // The account the euros are leaving, so that what each of its deposits is promised now and
        // what it would be promised afterwards are worked out at one rate — its own.
        BigDecimal rateItIsLeaving = theRate.perWholeEuroIn(fromSavingsAccountId);
        Map<Long, NextAnniversaryOfADeposit> promisedNow =
                whenTheDepositsInAnAccountNextPay(fromSavingsAccountId);
        long givenUp = 0;
        LocalDate soonest = null;
        for (MoneyAMoveWouldTake taken : wouldTake) {
            NextAnniversaryOfADeposit promised = promisedNow.get(taken.depositId());
            if (promised == null) {
                // A row the bank wrote. Interest has never had an anniversary of its own — it is
                // the reward for leaving money alone rather than a second thing to be rewarded for
                // — so moving it gives nothing up, and it is left out of the soonest day as well.
                continue;
            }
            long afterwards = LoyaltyRate.pointsOn(
                    LoyaltyRate.wholeEurosIn(taken.leftInItAfterwards()), rateItIsLeaving);
            givenUp += promised.points() - afterwards;
            if (soonest == null || promised.on().isBefore(soonest)) {
                soonest = promised.on();
            }
        }
        // What the euros would be worth a year from the day of the move, at the rate the account
        // they are arriving in pays. Twelve months from now rather than from any deposit's date,
        // because the arriving money is a new deposit and its clock starts when it lands.
        BigDecimal rateItIsArrivingAt = theRate.perWholeEuroIn(toSavingsAccountId);
        long worthOnTheNewOne = LoyaltyRate.pointsOn(LoyaltyRate.wholeEurosIn(amount),
                rateItIsArrivingAt);
        LocalDate newAnniversary =
                LoyaltyAnniversary.dayOf(LoyaltyAnniversary.anniversaryOf(now, 1));
        WhatMovingWouldCostInLoyalty cost = new WhatMovingWouldCostInLoyalty(
                fromSavingsAccountId, toSavingsAccountId, AmountOfMoney.quotedToTheCent(amount),
                newAnniversary, worthOnTheNewOne, soonest, givenUp, wouldTake.size());
        // The whole quote on one line, with both rates beside it, because the two figures only mean
        // anything together: a customer is weighing a clock they are giving up against a rate they
        // are moving to, and a reviewer checking that the page told them the truth needs the same
        // pair. DEBUG rather than INFO because nothing happened — this is a reading, and the move it
        // may or may not lead to writes its own line.
        log.debug("what moving money would cost in loyalty fromSavingsAccountId={} "
                        + "toSavingsAccountId={} amount={} depositsItWouldDrawDown={} "
                        + "rateItIsLeaving={} rateItIsArrivingAt={} pointsGivenUp={} "
                        + "soonestAnniversaryGivenUp={} theNewAnniversary={} "
                        + "pointsOnTheNewAnniversary={}",
                fromSavingsAccountId, toSavingsAccountId, AmountOfMoney.asMoney(amount),
                wouldTake.size(), rateItIsLeaving, rateItIsArrivingAt, givenUp, soonest,
                newAnniversary, worthOnTheNewOne);
        return cost;
    }

    /**
     * Which anniversary of this deposit the sweep would pay next if the money stayed where it is:
     * the earliest one it is owed and has not been paid, and otherwise the next one the calendar has
     * coming.
     *
     * <p>The same two facts the sweep itself decides on, read the other way round. The sweep walks a
     * deposit's arrived anniversaries and pays each one that has no row and is worth something; this
     * asks which one it would reach first. So what a customer is promised on the page and what the
     * sweep pays them overnight are one rule rather than two that have to be kept in step.
     *
     * <p>Only when the deposit is worth something, because an anniversary worth nothing is never
     * written down. The sweep passes such an anniversary over without a row, so there is nothing to
     * distinguish "not paid yet" from "paid nothing, for ever" — and reading the absence as the
     * former would pin a nine-euro deposit's promise on an anniversary in its first year and leave it
     * there. A deposit worth nothing is promised the calendar's next anniversary, which is the only
     * promise it can keep at what it holds.
     *
     * <p>Walks from the first anniversary rather than back from the last, so the earliest debt is the
     * one reported: a deposit made before this scheme existed and never swept is owed its first
     * anniversary, not its ninth. One step per year the deposit has been open, on a handful of rows.
     */
    private int theAnniversaryThatPaysNextFor(DepositStillHoldingMoney deposit, Instant now,
                                              long worth, Set<AnAnniversary> alreadyPaid) {
        int theCalendarsNext =
                LoyaltyAnniversary.theAnniversaryAfterTheOnesThatHaveArrived(deposit.depositedAt(), now);
        if (worth > 0) {
            for (int ordinal = 1; ordinal < theCalendarsNext; ordinal++) {
                if (!alreadyPaid.contains(new AnAnniversary(deposit.id(), ordinal))) {
                    return ordinal;
                }
            }
        }
        return theCalendarsNext;
    }

    /**
     * Pays one anniversary of one deposit if it has not been paid and is worth anything, and answers
     * what it paid.
     *
     * <p>The euros are read as the deposit stands now rather than as it stood on the anniversary,
     * which is the rule and not an approximation of it: what an anniversary is worth is decided from
     * what is still in the deposit at the moment the anniversary is judged. A deposit whose money
     * left last week pays nothing for the anniversary it passed a fortnight ago, and that is exactly
     * the forfeit the scheme intends — the money did not stay.
     *
     * <p>The rate is read the same way and for the same reason: it is the rate of the product the
     * money is sitting in when the anniversary is judged, not the one the account was on when the
     * money landed. Which version priced a deposit is written on the deposit and matters to what it
     * earned <em>then</em>; an anniversary is a reward for the money being here <em>now</em>, and
     * the agreement it is here under is the one it is paid by. The caller hands the rate in, having
     * read it once for the account, so that every anniversary in one sweep of one account is judged
     * against the same figure.
     */
    private long payIfItIsDue(DepositStillHoldingMoney deposit, int ordinal, BigDecimal rate,
                              Set<AnAnniversary> alreadyPaid) {
        Instant anniversary = LoyaltyAnniversary.anniversaryOf(deposit.depositedAt(), ordinal);
        if (alreadyPaid.contains(new AnAnniversary(deposit.id(), ordinal))) {
            // Which is every anniversary on the second run of a nightly job, so this is the line
            // that says a sweep paying nothing is a sweep that has already paid.
            log.debug("deposit passed over for a loyalty bonus depositId={} customerId={} "
                            + "reason=this anniversary has already been paid anniversary={} "
                            + "ordinal={}",
                    deposit.id(), deposit.customerId(), anniversary, ordinal);
            return 0;
        }
        long wholeEuros = LoyaltyRate.wholeEurosIn(deposit.remainingAmount());
        long bonus = LoyaltyRate.pointsOn(wholeEuros, rate);
        if (bonus == 0) {
            // The rate and the threshold it implies, because with the rate varying by product
            // "rounds down to nothing" no longer means one fixed amount — and a threshold of null
            // says the honest thing about a product that pays nothing for money staying put: there
            // is no amount this deposit could hold that would earn a point.
            log.debug("deposit passed over for a loyalty bonus depositId={} customerId={} "
                            + "savingsAccountId={} reason=that rate on what it still holds rounds "
                            + "down to no points anniversary={} ordinal={} remainingAmount={} "
                            + "wholeEuros={} ratePerWholeEuro={} theLeastABonusIsPaidOn={}",
                    deposit.id(), deposit.customerId(), deposit.savingsAccountId(), anniversary,
                    ordinal, deposit.remainingAmount(), wholeEuros, rate,
                    LoyaltyRate.theLeastABonusIsPaidOn(rate).orElse(null));
            return 0;
        }
        // Written down before the points are credited, so that what the ledger was paid and what
        // this module says it paid are one decision rather than two. The pair of deposit and ordinal
        // is unique, so a second sweep inside the same night cannot get past this row.
        LoyaltyBonusPaid record = paid.save(LoyaltyBonusPaid.of(
                deposit.id(), deposit.customerId(), ordinal, anniversary, wholeEuros, bonus));
        points.creditLoyaltyBonus(deposit.customerId(), deposit.id(), bonus, anniversary);
        // One line per anniversary paid, with the whole of the arithmetic on it: what the deposit
        // still held, the whole euros that came to, and the tenth of those the customer was paid.
        // A reviewer redoes it by hand from this line alone.
        log.debug("loyalty bonus paid depositId={} customerId={} savingsAccountId={} "
                        + "anniversary={} ordinal={} remainingAmount={} wholeEuros={} "
                        + "ratePerWholeEuro={} points={} recordId={}",
                deposit.id(), deposit.customerId(), deposit.savingsAccountId(), anniversary,
                ordinal, deposit.remainingAmount(), wholeEuros, rate, bonus, record.getId());
        return bonus;
    }

    /**
     * What an anniversary pays per whole euro in that account, asked once per account per sweep.
     *
     * <p>A map the sweep carries rather than a field, because it is a fact about tonight. A sweep
     * walks every deposit in the application and several of them sit in one account, so asking per
     * deposit would put the same question to the same agreement four times and get the same answer;
     * asking once and remembering it for the length of the sweep costs one entry per account. A
     * cache that outlived the sweep would be a rate that stopped moving when an account took newer
     * terms, which is the one thing a stored rate must never do here.
     *
     * <p>Said out loud the first time each account is asked about, because "why did this account's
     * anniversaries pay what they paid" is answered by this line and by nothing else in the sweep.
     */
    private BigDecimal rateFor(long savingsAccountId, Map<Long, BigDecimal> ratesByAccount) {
        return ratesByAccount.computeIfAbsent(savingsAccountId, account -> {
            BigDecimal rate = theRate.perWholeEuroIn(account);
            log.debug("what an anniversary pays in this account savingsAccountId={} "
                            + "ratePerWholeEuro={} theLeastABonusIsPaidOn={}",
                    account, rate, LoyaltyRate.theLeastABonusIsPaidOn(rate).orElse(null));
            return rate;
        });
    }

    /**
     * Which of these deposits' anniversaries have already been paid, as a set that can be asked
     * about one anniversary at a time.
     *
     * <p>What both of this module's answers turn on, and the reason they agree: the sweep asks it
     * which anniversaries it may skip, and the read above asks it which one is owed next. Two
     * readings of one set of rows cannot disagree the way two separate rules would.
     *
     * <p>One query for every deposit rather than one per anniversary considered: a deposit ten years
     * old is ten questions, and the rows are the same rows either way.
     *
     * <p>Nothing is asked at all when there is nothing to ask about — an account whose deposits have
     * all been emptied, or a clock nobody has wound forward.
     */
    private Set<AnAnniversary> whatHasAlreadyBeenPaidFor(List<DepositStillHoldingMoney> holding) {
        if (holding.isEmpty()) {
            return Set.of();
        }
        List<AnniversaryAlreadyPaid> rows = paid.anniversariesAlreadyPaidFor(
                holding.stream().map(DepositStillHoldingMoney::id).toList());
        Set<AnAnniversary> alreadyPaid = new HashSet<>();
        for (AnniversaryAlreadyPaid row : rows) {
            alreadyPaid.add(new AnAnniversary(row.getDepositId(), row.getAnniversaryOrdinal()));
        }
        log.debug("anniversaries already paid for these deposits deposits={} "
                + "anniversariesAlreadyPaid={}", holding.size(), alreadyPaid.size());
        return alreadyPaid;
    }

    /**
     * One anniversary of one deposit: the pair the record is unique over, and the only thing the
     * sweep needs in order to ask whether it has been paid.
     *
     * <p>The same pair the database keeps as a unique constraint. That constraint is what makes the
     * sweep safe; this is only the sweep's way of asking about one anniversary without a query per
     * anniversary.
     */
    private record AnAnniversary(long depositId, int ordinal) {
    }
}
