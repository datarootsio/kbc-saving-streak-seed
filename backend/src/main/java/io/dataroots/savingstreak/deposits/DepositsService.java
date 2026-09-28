package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountPairing;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.WhatACurrentAccountHolds;
import io.dataroots.savingstreak.points.PointsByReason;
import io.dataroots.savingstreak.points.PointsReason;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.scheme.SchemeService;
import io.dataroots.savingstreak.scheme.TheSchemeEachWeekWasJudgedUnder;
import io.dataroots.savingstreak.streaks.WeekAndStreak;
import io.dataroots.savingstreak.streaks.WeekAndStreakDerivation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;
import static io.dataroots.savingstreak.deposits.AmountOfMoney.quotedToTheCent;
import static io.dataroots.savingstreak.deposits.DepositRefused.Kind.AGAINST_THE_RULES;
import static io.dataroots.savingstreak.deposits.DepositRefused.Kind.NOT_ALLOWED;
import static io.dataroots.savingstreak.deposits.DepositRefused.Kind.NOT_ENOUGH_MONEY;
import static io.dataroots.savingstreak.deposits.DepositRefused.Kind.NO_SUCH_ACCOUNT;

/**
 * The Deposits module's face to the rest of the application: it records money moving into a savings
 * account, and answers what has moved in — altogether, or one payment at a time.
 */
@Service
public class DepositsService {

    private static final Logger log = LoggerFactory.getLogger(DepositsService.class);

    private final DepositRepository deposits;
    private final AccountsService accounts;
    private final PointsService points;
    /**
     * The other half of a week of saving, for the derivation below: a week counts what was put away
     * less what was taken back out of savings during it. This module's other face rather than
     * another module — the two ledgers a week is walked over are both this one's.
     */
    private final WithdrawalsService withdrawals;
    /**
     * Which version of its account's terms a deposit is landing under, asked of whoever keeps the
     * agreements. {@link TheTermsADepositLandsUnder} says at length why the dependency runs this
     * way round: this module records a version number and learns nothing else about products.
     */
    private final TheTermsADepositLandsUnder theTerms;
    /**
     * What a euro saved into the account is worth in points, asked of whoever keeps the agreements.
     * {@link WhatAEuroSavedIntoAnAccountIsWorth} says at length why the dependency runs this way
     * round and why it is a second question rather than a second method on the one above: this
     * module multiplies by a number and learns nothing else about products.
     *
     * <p>Injected as the interface and never as the class behind it, which is what keeps a
     * start-up cycle from forming — the module that implements this reads this module's ledger.
     */
    private final WhatAEuroSavedIntoAnAccountIsWorth whatAEuroIsWorth;
    /**
     * Where the figures a run of weeks is judged and priced by come from, read here rather than by
     * the derivation this module calls.
     *
     * <p><strong>The whole published history and never the version in force.</strong> A deposit is
     * priced at the run it has just been counted into, and that run spans Mondays: each of its weeks
     * is judged against what its own Monday published, so handing over one set of figures would
     * un-secure every EUR 60 week behind this one the morning the minimum rose. The ladder the
     * deposit is then paid on is this week's, off the same reading, which is what makes "a deposit
     * after a change is priced at the new ladder against a run counted week by week under each
     * week's own scheme" one reading of one history rather than two questions that could disagree.
     *
     * <p>Read here because {@link WeekAndStreakDerivation} has no clock, no state and no bean, and
     * that is what it is for: two callers need the same walk at two different points in a request,
     * so the things it needs are arguments. Which makes the history an argument like the moment.
     *
     * <p>The dependency runs this way and not the other: {@code scheme} depends on nothing in this
     * application, holds numbers and owns no rule about what a deposit earns.
     */
    private final SchemeService scheme;
    private final Clock clock;

    DepositsService(DepositRepository deposits, AccountsService accounts, PointsService points,
                    WithdrawalsService withdrawals, TheTermsADepositLandsUnder theTerms,
                    WhatAEuroSavedIntoAnAccountIsWorth whatAEuroIsWorth,
                    SchemeService scheme,
                    Clock clock) {
        this.deposits = deposits;
        this.accounts = accounts;
        this.points = points;
        this.withdrawals = withdrawals;
        this.theTerms = theTerms;
        this.whatAEuroIsWorth = whatAEuroIsWorth;
        this.scheme = scheme;
        this.clock = clock;
    }

    /**
     * Moves an amount from a current account into a savings account and credits the points it earns.
     *
     * <p>Both happen in one transaction, which is the load-bearing guarantee of this slice: a deposit
     * that earned no points, or points earned by no deposit, would be a balance nobody could explain.
     *
     * <p>Which euros of the amount earn at all <em>is</em> decided here, because this module is the
     * one that knows what the customer has saved and what has since left it. A point is paid for a
     * euro saved, and a euro saved twice is one euro: a deposit filling a gap an earlier withdrawal
     * left has already been paid for, and {@link TheMostEverSaved} is the whole of that rule. It is
     * also what lets a withdrawal leave the points ledger alone — nothing is ever taken back, so no
     * balance can go negative and using your savings costs you nothing.
     *
     * <p>How many points those euros are worth is not decided here. The Points module owns that rule
     * and reports what it credited. Nor is what is in the current account: Accounts is asked to take
     * the money and answers whether there was any to take. Nor is either half of the rate the
     * deposit is paid at: one half is the run of weeks the deposit has just been counted into, which
     * the Streaks module derives, and the other is what a euro saved into this particular account is
     * worth, which is answered by whoever keeps the agreements. This module multiplies the two
     * together — {@link TheRateADepositIsPaidAt} is that one line and the argument for it — and
     * hands the ledger a single rate, so the euros are floored once and the rate is floored once,
     * exactly as they were before there was a second factor.
     *
     * <p>The order of the last three steps is the whole feature. The deposit is recorded first, the
     * run of weeks is walked <em>after</em> it, and the points are credited at what that run pays.
     * One rule decides every case that way: a deposit is paid at the rate of the run as it stands
     * once that deposit has been counted. A deposit that carries its week past the weekly minimum has
     * lengthened the run by the time it is priced, so it is already paid at the new, higher rate; one
     * that does not secure the week is paid whatever the live run was already paying; and with no
     * live run the length is nothing and the rate is the ordinary one. There are no special cases
     * here to get wrong because there are no special cases.
     *
     * @throws DepositRefused if there is no such transfer to make: either account unknown, an amount
     *                         that is not an amount of money, or not enough money to move
     */
    @Transactional
    public RecordedDeposit deposit(long savingsAccountId, long fromCurrentAccountId, BigDecimal amount) {
        // The accounts before the amount. A deposit is a movement between two of them, and if there
        // is no such movement to make, the amount is beside the point. How the two stand to each
        // other decides both whether this can happen at all and whose deposit it is, so it is asked
        // once, here, and carried down to the line that reports what happened.
        AccountPairing pairing = accounts.pairingFor(savingsAccountId, fromCurrentAccountId);
        long customerId =
                theCustomerThisDepositIsForOrRefuse(pairing, savingsAccountId, fromCurrentAccountId);
        // What the savings account is living under, read once and before a cent moves. It decides
        // two separate things — whether this deposit may happen at all, because an agreement that
        // has ended takes no more money, and which version the row below is stamped with — and it
        // is one reading rather than two so that a deposit cannot be refused on the agreement as it
        // stood at one moment and stamped with the agreement as it stood at another.
        //
        // Asked here, in the company of the pairing, rather than further down beside the stamping:
        // a refusal has to arrive before the money leaves the current account, and everything below
        // this line either moves money or writes a record of money having moved.
        Optional<WhatAnAccountIsLivingUnder> livingUnder =
                theTerms.whatAnAccountIsLivingUnder(savingsAccountId);
        refuseIfTheAgreementHasEnded(savingsAccountId, livingUnder);
        refuseUnlessAnAmountOfMoney(amount);
        takeTheMoneyOrRefuse(fromCurrentAccountId, amount);

        // One moment for both records. The money moving and the points being earned are the same
        // event, and later slices date them against each other: expiry runs off the age of the
        // points, the loyalty bonus off the age of the deposit.
        //
        // Read from the application's clock rather than the machine's, so that a moment wound
        // forward moves the deposits and the points it dates together with everything else.
        //
        // To the millisecond, so that the moment reported back to whoever made the deposit is the
        // same moment the deposit is later listed under. A finer reading would only be a moment the
        // application could not hold on to, and one deposit would appear to have happened twice.
        // What the customer holds and what they have ever held, read before this deposit is in the
        // ledger so that both figures are about the past rather than about this moment. The two are
        // the whole of what decides which of these euros are new saving.
        // Both of them counting the customer's own money and not a cent the bank added: interest
        // is money in the account, but it is not money they put away, so it neither raises the
        // mark nor fills in the gap an earlier withdrawal left. TheMostEverSaved subtracts one of
        // these from the other, so the two have to be about the same money or the difference stops
        // meaning what it says.
        BigDecimal stillSaved = deposits.stillSavedOutOfTheirOwnMoneyBy(customerId);
        BigDecimal everEarnedOn = deposits.everEarnedOnBy(customerId);
        BigDecimal newSaving = TheMostEverSaved.newSavingIn(amount, stillSaved, everEarnedOn);
        // Said out loud because it is the one figure a customer cannot see for themselves: a deposit
        // that earned nothing did so because these three numbers say it put nothing away that had
        // not been put away before, and a reader can redo that subtraction from this line.
        log.debug("what a deposit is new saving on customerId={} amount={} stillSaved={} "
                        + "everEarnedOn={} newSaving={}",
                customerId, asMoney(amount), asMoney(stillSaved), asMoney(everEarnedOn),
                asMoney(newSaving));

        Instant clockReads = clock.instant();
        Instant now = clockReads.truncatedTo(ChronoUnit.MILLIS);
        // Both readings, so that whoever reads this log can see that the moment came off the
        // application's clock and what the truncation did to it, rather than taking the recorded
        // moment on trust.
        log.debug("deposit takes its moment from the application clock savingsAccountId={} "
                + "clockReads={} recordedMoment={}", savingsAccountId, clockReads, now);

        Deposit deposit = deposits.save(
                new Deposit(savingsAccountId, customerId, fromCurrentAccountId, amount, now));
        // What the deposit starts out with still in it, which is all of it. The account's money
        // balance is summed from this figure rather than from the amount, so a reader adding the
        // balance up by hand needs to see it recorded rather than assume it.
        log.debug("deposit records what remains of it depositId={} amount={} remainingAmount={}",
                deposit.getId(), asMoney(deposit.getAmount()), asMoney(deposit.getRemainingAmount()));
        // Which agreement the money landed under, written on the deposit while the deposit is being
        // written: the account can take its product's newer terms tomorrow, and what this deposit
        // was decided under must not move when it does. Nothing at all is recorded for an account
        // no agreement has been written for yet, which is the window before the start-up migration
        // has run and is stamped on the next start rather than guessed at now.
        livingUnder.map(WhatAnAccountIsLivingUnder::version).ifPresent(deposit::landedUnder);
        // The run of weeks as it stands with this deposit in it. Derived rather than asked of the
        // Streaks module's service, because that service reads its ledger from this one and a
        // service that read back into a service that called it would be a cycle: the derivation is a
        // function both of us call, off the moment the money moved rather than off a second reading
        // of the clock.
        //
        // The deposit is in the ledger by the time the walk queries it: the row above was saved
        // inside this transaction, and a query against the deposits flushes it first. That is what
        // "once that deposit has been counted" means, and the test that a deposit crossing the
        // weekly minimum is itself paid at the new rate is the one that would fail if it were not so.
        //
        // The scheme's whole history, read once for this deposit and handed to the walk. Not the
        // version in force: the walk goes back through weeks, and each of them is judged against
        // what its own Monday published, so a threshold raised last Monday leaves every week behind
        // it exactly as it stood and this deposit is priced against the run the customer actually
        // has. The ladder the rate comes off is this week's, out of the same reading, so the run and
        // what it pays cannot come from two different versions.
        TheSchemeEachWeekWasJudgedUnder theSchemeThroughItsVersions =
                scheme.theSchemeThroughItsVersions();
        WeekAndStreak saving = WeekAndStreakDerivation.asAt(this, withdrawals, customerId, now,
                theSchemeThroughItsVersions);
        BigDecimal streak = saving.streak().multiplier();
        // What the account's own agreement pays on a euro saved into it, asked of whoever keeps the
        // agreements rather than worked out here. It multiplies with the run of weeks rather than
        // replacing it, and TheRateADepositIsPaidAt is the whole of that rule — including that the
        // flooring happens once, in the ledger, on the combined rate.
        BigDecimal product = whatAEuroIsWorth.theMultipleAEuroEarnsAt(savingsAccountId);
        BigDecimal multiplier = TheRateADepositIsPaidAt.combining(streak, product);
        // The two factors and what they came to, before a single point is credited, because this is
        // the one line a reviewer needs in order to tell a deposit paid short by a lapsed run from
        // one paid short by a product that changes nothing. The combined rate is the figure the
        // ledger multiplies by; the two beside it are what it is made of.
        log.debug("what a deposit is priced at savingsAccountId={} customerId={} streakWeeks={} "
                        + "schemeVersion={} ladder={} streak={} product={} combined={}",
                savingsAccountId, customerId, saving.streak().currentWeeks(),
                saving.theVersionOfTheSchemeThisWeekWasJudgedUnder(),
                saving.streak().theLadderThisWeekPays(), streak, product, multiplier);
        // Written on the deposit before the points are credited, so that what the ledger was paid and
        // what the deposit says it was paid are one decision rather than two.
        deposit.paidAt(multiplier);
        // And what the product contributed to it, written in the same breath, so that a history row
        // years from now can show the run of weeks and the agreement apart. Neither figure can be
        // recovered from the other once the ladder has moved.
        deposit.theProductPaid(TheRateADepositIsPaidAt.quoted(product));
        // Written on the deposit before the points are credited, for the reason the rate is: what
        // the ledger was paid on and what the deposit says it was paid on are one decision rather
        // than two. Summed across the customer's deposits it is also the mark the next deposit is
        // judged against, so the figure recorded here is the figure that decides the next one.
        deposit.earnedOn(newSaving);
        // Credited to the customer, not to the account the euros went into: their points are one
        // pot, and this deposit adds to it whichever of their accounts it landed in.
        // The euros that are new saving, not the euros that moved. The Points module prices whatever
        // it is handed and has no opinion about which euros those are — that question is answered
        // above, where the ledger this module owns can answer it.
        PointsByReason credited =
                points.creditPointsFor(customerId, deposit.getId(), newSaving, multiplier, now);
        // Everything that decided the outcome, on one line: the week the deposit landed in, what has
        // now landed in it, whether this deposit is the one that carried the week over the line, how
        // long the run is with the week counted, the rate that run pays, and the two figures the
        // points are made of. A reviewer can redo the whole pricing from this line — floor the
        // amount, multiply by the rate, floor again — and the DEBUG lines underneath it say which
        // weeks the walk went through to arrive at the run.
        log.info("deposit accepted depositId={} savingsAccountId={} customerId={} "
                        + "fromCurrentAccountId={} pairing={} "
                        + "amount={} newSaving={} week={} newSavingsThisWeek={} securedByThisDeposit={} "
                        + "streakWeeks={} streakMultiplier={} productMultiplier={} multiplier={} "
                        + "weeklyMinimum={} schemeVersion={} "
                        + "termsVersion={} basePoints={} "
                        + "streakBonusPoints={} pointsEarned={} pointsByReason={} depositedAt={}",
                deposit.getId(), savingsAccountId, customerId, fromCurrentAccountId, pairing,
                asMoney(amount), asMoney(newSaving),
                saving.week().week(), asMoney(saving.week().newSavings()),
                saving.week().wasCarriedOverBy(amount), saving.streak().currentWeeks(), streak,
                deposit.getProductMultiplierApplied(), multiplier,
                saving.week().weeklyMinimum(),
                saving.theVersionOfTheSchemeThisWeekWasJudgedUnder(),
                deposit.getTermsVersion(),
                credited.earnedAs(PointsReason.BASE_ACCRUAL),
                credited.earnedAs(PointsReason.STREAK_BONUS),
                credited.total(), credited.points(), deposit.getDepositedAt());
        return new RecordedDeposit(
                deposit.getId(), deposit.getAmount(), quotedToTheCent(newSaving), credited.total(),
                credited.earnedAs(PointsReason.BASE_ACCRUAL),
                credited.earnedAs(PointsReason.STREAK_BONUS),
                // Nothing, and read out of the breakdown rather than written as a nought: a deposit
                // earns a loyalty bonus on its anniversaries and it has not had one yet. Asked the
                // same way here as in the history, so a deposit just made and the same deposit read
                // back cannot answer differently.
                credited.earnedAs(PointsReason.LOYALTY_BONUS),
                multiplier, deposit.getProductMultiplierApplied(), deposit.getTermsVersion(),
                deposit.getDepositedAt());
    }

    /**
     * Writes a month's interest into the savings ledger as money in the account, and answers which
     * row it became.
     *
     * <p><strong>A row of this ledger rather than a table of its own, and that is the decision the
     * whole of the interest feature rests on.</strong> The account's balance is the sum of what
     * these rows still hold, a withdrawal draws these rows down and writes an allocation per row it
     * touches, and next month's average is worked out over this ledger — so interest written here
     * is withdrawable, is in the record of money that moved, and compounds into the next period
     * without any of those three being re-implemented. {@link DepositOrigin} is the column that
     * makes it possible and carries the argument in full.
     *
     * <p><strong>It earns nothing, and that is the whole of what this method does differently.</strong>
     * No points are credited, no run of weeks is walked, no rate is recorded and the most the
     * customer has ever saved does not move. A point is paid for a euro <em>saved</em>, and nobody
     * saved this one — the bank added it. The three figures that would otherwise have quietly
     * counted it are each blind to it in {@link DepositRepository}, by asking for the customer's
     * own rows, so this method has nothing to remember not to do.
     *
     * <p><strong>Nothing leaves a current account.</strong> Every other way money arrives here is a
     * transfer with two ends and Accounts is asked to take it from the first; interest has one end,
     * and asking Accounts for it would be this application debiting a customer to pay them.
     *
     * <p>The version the row landed under is stamped the way a deposit's is, through
     * {@link TheTermsADepositLandsUnder} and in the same words, so that one code path answers "which
     * agreement was this row written under" for every row in the ledger. Whoever pays the interest
     * has its own record of the period, the rate and the version it priced with; this is the
     * ledger's copy of the last of those, and it is stamped here because it is stamped here for
     * everything else.
     *
     * <p><strong>It is not refused for a closed account, and {@link #deposit} is.</strong> Interest
     * is the bank paying for money that was in the account while the agreement was running, and the
     * period it pays for ended before the day the account closed or there would have been a balance
     * in the way of closing it at all. Refusing it here would be this application keeping money it
     * has already worked out that it owes, on the grounds that the customer has since tidied up.
     *
     * <p>It takes the moment rather than reading the clock, unlike {@link #deposit}. What interest
     * pays for is a month that ended, and the sweep dates the payment at the end of that month so
     * that the same period reports the same moment whenever the job is actually run — the argument
     * a paid anniversary already makes, quoted rather than restated.
     *
     * <p>Public because it is this module's door for the Products module, which owns what interest
     * is worth and knows nothing about how a savings ledger is written. It moves money and is not a
     * read, so it opens a writing transaction; it takes part in the sweep's transaction when there
     * is one, which is what makes the posting and the money one event.
     *
     * @param amount what the period paid, already worked out and floored to the cent by whoever
     *               priced it — this module has no opinion about rates
     * @return the identifier of the ledger row the money became, so that the record of the posting
     *         can name the euros it produced
     */
    @Transactional
    public long payInterestInto(long savingsAccountId, long customerId, BigDecimal amount,
                                Instant paidAt) {
        Deposit interest = deposits.save(
                Deposit.ofInterest(savingsAccountId, customerId, amount, paidAt));
        theTerms.whatAnAccountIsLivingUnder(savingsAccountId)
                .map(WhatAnAccountIsLivingUnder::version)
                .ifPresent(interest::landedUnder);
        // One line per payment, with everything that makes it a row of this ledger and not a
        // deposit: what it added, what it says it earned on, and the fact that no current account
        // was touched to pay it. A reviewer grepping this module for where a balance grew without
        // a deposit finds exactly this.
        log.info("interest paid into savings depositId={} savingsAccountId={} customerId={} "
                        + "amount={} origin={} earnedOn={} pointsEarned=0 termsVersion={} paidAt={}",
                interest.getId(), savingsAccountId, customerId, asMoney(amount),
                interest.getOrigin(), asMoney(interest.getEarnedOnAmount()),
                interest.getTermsVersion(), paidAt);
        return interest.getId();
    }

    /**
     * Writes into the savings ledger the money arriving from another of the same customer's savings
     * accounts, and answers which row it became.
     *
     * <p><strong>A row of this ledger for the reason interest is one</strong>, and by now the
     * argument is the module's oldest: a savings account's balance is the sum of what these rows
     * still hold, so money that has arrived has to be one of them or the balance is a sum of two
     * tables. Written here it is withdrawable, it is in the record of money that moved, and it
     * compounds into the next period's interest without any of those three being re-implemented.
     * {@link DepositOrigin#MOVED_FROM_ANOTHER_SAVINGS_ACCOUNT} is the column that makes it possible
     * and carries the argument in full.
     *
     * <p><strong>It earns nothing, and that is the whole of what this method does differently from
     * {@link #deposit}.</strong> No points are credited, no run of weeks is walked and no rate is
     * recorded. A point is paid for a euro saved and nobody saved this one <em>today</em> — it was
     * saved when it first went into the account next door, and it was paid for then. Left as an
     * ordinary deposit it would earn nothing anyway, because the mark would already be above what
     * the customer holds; the difference is that this way the customer is not also charged a week
     * and a streak for the privilege, which is the punishment the whole operation exists to lift.
     *
     * <p><strong>The mark does not move, and the figure that keeps it still is handed in.</strong>
     * {@code earnedOnCarriedAcross} is what the rows on the other side of the move gave up — what
     * they had already been paid for, plus every euro of interest among them, which the bank added
     * and which has never earned a point here. Written onto this row it makes the sum those rows
     * were counted in come out exactly as it did before. Worked out here instead, this module would
     * be deciding twice what a move costs, and the two answers would be a cent apart the first time
     * an odd amount moved.
     *
     * <p><strong>Nothing leaves a current account</strong>, because there is none at either end:
     * the euros came out of a savings account, which is what makes a move a move. And nothing is
     * refused here either — whether these two accounts are one customer's, whether the destination
     * is still open and whether the source will let the money go are all settled by
     * {@link MovesService} before this is called, every one of them before a cent moved.
     *
     * <p><strong>The version is stamped exactly the way a deposit's is</strong>, through
     * {@link TheTermsADepositLandsUnder} and in the same words, so that one code path answers
     * "which agreement was this row written under" for every row in this ledger. Money moving to a
     * better product is priced under that product's terms from the moment it lands, which is the
     * entire point of moving it.
     *
     * <p>It takes the moment rather than reading the clock, for the reason {@link #payInterestInto}
     * does: a move is one event with a row at each end, and a second reading of the clock would
     * date the two halves of it a millisecond apart. That moment is also the new anniversary — the
     * one cost of moving, charged by the plainest means there is, a new row with a new date on it.
     *
     * <p>Package-private: half of a move is not a thing anything outside this package may write.
     *
     * @return the identifier of the ledger row the money became, so that the record of the move can
     *         name the euros it produced
     */
    @Transactional
    long landAMoveFrom(long intoSavingsAccountId, long customerId, long fromSavingsAccountId,
                       BigDecimal amount, BigDecimal earnedOnCarriedAcross, Instant movedAt) {
        Deposit arrived = deposits.save(Deposit.movedFrom(intoSavingsAccountId, customerId,
                fromSavingsAccountId, amount, earnedOnCarriedAcross, movedAt));
        theTerms.whatAnAccountIsLivingUnder(intoSavingsAccountId)
                .map(WhatAnAccountIsLivingUnder::version)
                .ifPresent(arrived::landedUnder);
        // One line per arrival, with everything that makes it a row of this ledger and not a
        // deposit: where it came from, what it says it earned on, that it earned no points, and
        // the day its loyalty clock now starts from. A reviewer grepping this module for where a
        // balance grew without anybody paying anything in finds exactly this and the interest line
        // above it.
        log.info("money moved into savings depositId={} savingsAccountId={} customerId={} "
                        + "fromSavingsAccountId={} amount={} origin={} earnedOn={} pointsEarned=0 "
                        + "termsVersion={} movedAt={}",
                arrived.getId(), intoSavingsAccountId, customerId, fromSavingsAccountId,
                asMoney(amount), arrived.getOrigin(), asMoney(arrived.getEarnedOnAmount()),
                arrived.getTermsVersion(), movedAt);
        return arrived.getId();
    }

    /**
     * Moves the money out of the current account, or refuses the deposit because it is not there.
     *
     * <p>Inside the same transaction as everything below it, which is what makes a deposit one
     * event rather than three. Money that left a current account without landing in a savings
     * account is the worst balance in this application to be asked to explain, and it cannot happen
     * here: if the points cannot be credited, the withdrawal is rolled back with them.
     *
     * <p>Accounts is asked to take the amount rather than asked what the balance is and then told to
     * take it. Between the question and the instruction the balance can change, and two deposits
     * that both asked first would both be told yes.
     *
     * <p>The balance is read only once the answer is no. Nothing was taken, so it is still what the
     * account has, and it is the figure the person needs in order to see how much of the deposit
     * they can actually make.
     */
    private void takeTheMoneyOrRefuse(long fromCurrentAccountId, BigDecimal amount) {
        if (accounts.withdrawFrom(fromCurrentAccountId, amount)) {
            return;
        }
        BigDecimal left = accounts.balanceOfCurrentAccount(fromCurrentAccountId)
                // The account was there a moment ago, when the pairing was checked. Being asked to
                // report a balance for an account that has since gone is not something this module
                // can word helpfully, so it says the part it is sure of.
                .orElse(BigDecimal.ZERO);
        throw new DepositRefused(NOT_ENOUGH_MONEY, "There is not enough in that current account to "
                + "move EUR " + asMoney(amount) + ". It holds EUR " + asMoney(left) + ".");
    }

    /**
     * Refuses a deposit the two accounts cannot make, and answers whose deposit it is.
     *
     * <p>Answered rather than merely checked, because the deposit needs it: the points it earns
     * belong to a customer, and which customer that is falls out of the same check.
     *
     * <p>Asked of Accounts rather than enforced by the database: a deposit names both accounts by
     * identifier and has no foreign key to either, so nothing underneath would object. Which module
     * gets asked is the point — who holds what is Accounts' answer, and Deposits only records that a
     * transfer between two of them happened.
     *
     * <p><strong>There are two pairings money moves across and they name different people.</strong>
     * A personal deposit is one customer's own two accounts, and that customer is the holder of
     * either end. A contribution to a shared pot is a member's current account and an account no
     * customer holds, and the only person on it is the one whose current account the euros came out
     * of — so that is who it is credited to, at their own multiplier, against their own mark, into
     * their own week. Nothing below this line knows which of the two it was, which is exactly the
     * point: a contribution is priced by the rules a deposit is priced by, because it is one.
     *
     * <p>Whose the other account was is never named in a refusal. Whoever is asking already knew the
     * identifier they sent; saying who it belongs to would be telling them something new about a
     * customer who is not them. A pot is not named either, for the same reason — somebody refused
     * from a pot they are not in is told what they ran into, not what it is called.
     */
    private long theCustomerThisDepositIsForOrRefuse(AccountPairing pairing, long savingsAccountId,
                                                     long fromCurrentAccountId) {
        switch (pairing) {
            // The two pairings money can move across, so the only ones that go no further.
            case HELD_BY_ONE_CUSTOMER, HELD_BY_A_POT_THE_PAYER_MAY_PAY_INTO -> { }
            case NO_SUCH_SAVINGS_ACCOUNT -> throw new DepositRefused(NO_SUCH_ACCOUNT,
                    AccountsService.noSuchSavingsAccount(savingsAccountId));
            case NO_SUCH_CURRENT_ACCOUNT -> throw new DepositRefused(NO_SUCH_ACCOUNT,
                    AccountsService.noSuchCurrentAccount(fromCurrentAccountId));
            case HELD_BY_DIFFERENT_CUSTOMERS -> throw new DepositRefused(AGAINST_THE_RULES,
                    "A savings account can only be paid into from a current account held by the "
                            + "same customer.");
            // The two role refusals, which are the only refusals in this module that are about who
            // is asking rather than about what they asked for. Said out loud here, because the
            // pairing that decided them is a DEBUG line in two other modules and a reviewer reading
            // at INFO would otherwise find a contribution that simply never happened.
            case HELD_BY_A_POT_THE_PAYER_DOES_NOT_BELONG_TO -> throw refusingAContribution(
                    savingsAccountId, fromCurrentAccountId, pairing,
                    "That savings account belongs to a shared pot you are not a member of. Ask "
                            + "somebody who owns the pot to invite you.");
            case HELD_BY_A_POT_THE_PAYER_ONLY_WATCHES -> throw refusingAContribution(
                    savingsAccountId, fromCurrentAccountId, pairing,
                    "You are a viewer of that shared pot, so you can watch what it holds but not "
                            + "pay into it. Ask somebody who owns the pot to make you a contributor.");
            // And the pot that is over. Nothing about the person asking comes into it — an owner, a
            // contributor and a stranger are all told the same thing, because what is wrong is the
            // pot rather than them, and there is nothing any of them could be granted that would
            // let a euro into it.
            case HELD_BY_A_POT_THAT_IS_CLOSED -> throw refusingAContribution(
                    savingsAccountId, fromCurrentAccountId, pairing,
                    DepositRefused.Kind.THE_POT_IS_CLOSED,
                    "That savings account belongs to a shared pot that has been closed, and a "
                            + "closed pot cannot be paid into. Its history is still there to read.");
        }
        return pairing == AccountPairing.HELD_BY_A_POT_THE_PAYER_MAY_PAY_INTO
                ? theHolderOfTheCurrentAccountOrRefuse(fromCurrentAccountId)
                : theHolderOfTheSavingsAccountOrRefuse(savingsAccountId);
    }

    /** Whose personal deposit this is: the customer who holds both ends of it. */
    private long theHolderOfTheSavingsAccountOrRefuse(long savingsAccountId) {
        return accounts.holderOfSavingsAccount(savingsAccountId)
                .map(AccountHolder::customerId)
                // One customer held both a moment ago, when the pairing was checked. An account that
                // has gone in between is reported as the absence it is rather than allowed to credit
                // points to nobody.
                .orElseThrow(() -> new DepositRefused(NO_SUCH_ACCOUNT,
                        AccountsService.noSuchSavingsAccount(savingsAccountId)));
    }

    /**
     * Whose contribution to a shared pot this is: the customer whose current account it comes out
     * of, which is the only person a contribution has on it.
     *
     * <p>It is also what makes paying in from somebody else's current account impossible rather than
     * merely refused. The account the euros leave decides who is being asked about, so a member
     * naming a stranger's current account is asking whether that stranger may pay into the pot — and
     * a stranger who is not in it is refused above, in those words.
     */
    private long theHolderOfTheCurrentAccountOrRefuse(long fromCurrentAccountId) {
        return accounts.currentAccountWith(fromCurrentAccountId)
                .map(WhatACurrentAccountHolds::customerId)
                // It was there a moment ago, when the pairing was checked; an account that has gone
                // in between is reported as the absence it is rather than allowed to credit points
                // to nobody.
                .orElseThrow(() -> new DepositRefused(NO_SUCH_ACCOUNT,
                        AccountsService.noSuchCurrentAccount(fromCurrentAccountId)));
    }

    /**
     * A contribution to a pot the payer may not make, refused in words they can act on and warned
     * about with everything that decided it.
     *
     * <p>The sentence is logged rather than a summary of it, so that what a reviewer reads and what
     * the person at the keyboard was told are the same words.
     */
    private DepositRefused refusingAContribution(long savingsAccountId, long fromCurrentAccountId,
                                                 AccountPairing pairing, String reason) {
        return refusingAContribution(savingsAccountId, fromCurrentAccountId, pairing, NOT_ALLOWED,
                reason);
    }

    /**
     * The same, for the one contribution refusal that is not about who is asking: a pot that has
     * been closed, which refuses an owner and a stranger in the same sentence.
     *
     * <p>The kind travels rather than being read off the pairing here, so that the switch above
     * stays the one place in this module that decides what each pairing means. One line either way,
     * under the same {@code contribution rejected} opening, so that a single grep still finds every
     * contribution this module has ever turned down.
     */
    private DepositRefused refusingAContribution(long savingsAccountId, long fromCurrentAccountId,
                                                 AccountPairing pairing, DepositRefused.Kind kind,
                                                 String reason) {
        log.warn("contribution rejected savingsAccountId={} fromCurrentAccountId={} pairing={} "
                + "kind={} reason={}", savingsAccountId, fromCurrentAccountId, pairing, kind, reason);
        return new DepositRefused(kind, reason);
    }

    /**
     * Refuses anything that is not an amount of money moving in.
     *
     * <p>What counts as one is {@link AmountOfMoney}'s answer, given in the same words a withdrawal
     * of the same figure would come back with. Checked before a single record is written, so a
     * refusal never has to be undone.
     */
    private void refuseUnlessAnAmountOfMoney(BigDecimal amount) {
        AmountOfMoney.whyItIsNotOne("deposit", amount).ifPresent(reason -> {
            throw new DepositRefused(AGAINST_THE_RULES, reason);
        });
    }

    /**
     * Refuses a deposit into a savings account whose agreement has ended, naming the day it ended.
     *
     * <p><strong>The day is the whole sentence.</strong> A customer holding three savings accounts
     * who is told only that "this account is closed" has to go and work out which of the three they
     * are looking at and why; told that it was closed on a day, they recognise the one they emptied
     * in March. It is the same date the second press of the closing button already answers with, in
     * the same shape, because a customer meeting this twice should not meet two vocabularies.
     *
     * <p><strong>Before the money leaves the current account and after the accounts are known.</strong>
     * A refused deposit must not have moved a cent, so this sits above {@code takeTheMoneyOrRefuse};
     * and it sits below the pairing, so that a number nobody holds is still answered as the absent
     * account it is rather than as an agreement nobody can find.
     *
     * <p><strong>Above the check that the amount is an amount</strong>, which is the same order a
     * closed pot is refused in. Nothing the customer typed can make a closed account take money, so
     * sending them off to correct a figure first would be asking them to fix something that was not
     * going to help.
     *
     * <p>An account with no agreement on record is not refused. That is a row the start-up migration
     * has not reached yet, which is a window measured in milliseconds, and an account nobody has
     * written an agreement for has not been closed by anybody either.
     */
    private void refuseIfTheAgreementHasEnded(long savingsAccountId,
                                              Optional<WhatAnAccountIsLivingUnder> livingUnder) {
        livingUnder.filter(WhatAnAccountIsLivingUnder::isClosed).ifPresent(closed -> {
            String reason = becauseTheAccountWasClosed(savingsAccountId, closed.closedOn());
            // The account and the day it closed, which are the two values that decided this, and
            // the sentence itself rather than a summary of it — so that what a reviewer reads and
            // what the person at the keyboard was told are the same words.
            log.warn("deposit rejected savingsAccountId={} closedOn={} kind={} reason={}",
                    savingsAccountId, closed.closedOn(),
                    DepositRefused.Kind.THE_ACCOUNT_IS_CLOSED, reason);
            throw new DepositRefused(DepositRefused.Kind.THE_ACCOUNT_IS_CLOSED, reason);
        });
    }

    /**
     * Whether anything about the savings account itself stops money being paid into it, and the
     * sentence to say about it when something does.
     *
     * <p><strong>Asked rather than attempted, for the one caller that cannot be refused by an
     * exception.</strong> The nightly saving rules run is one transaction over every standing rule
     * in the application, and an exception crossing that boundary marks the whole night
     * rollback-only — so a rule pointing at a closed account would take every other customer's
     * transfers down with it. That run already reads a balance and judges it for exactly this
     * reason, and this is the same bargain for the same reason: the question is asked first, the
     * occurrence is recorded as the refusal it is, and the night goes on.
     *
     * <p><strong>It answers about the account and about nothing else.</strong> Not whether the
     * payer may pay in, not whether there is money to move, not whether the amount is an amount:
     * those are about a pairing, a balance and a figure, none of which exist here. What it answers
     * is the one refusal that is a fact about the destination on its own, and {@link #deposit}
     * refuses it in these same words — one sentence, written once, so that a customer who meets it
     * through their own press and a customer who meets it in a rule's history are told the same
     * thing.
     *
     * <p>Public because Automation is the caller, and empty is the ordinary answer.
     */
    @Transactional(readOnly = true)
    public Optional<String> whatStopsMoneyBeingPaidInto(long savingsAccountId) {
        Optional<String> stopping = theTerms.whatAnAccountIsLivingUnder(savingsAccountId)
                .filter(WhatAnAccountIsLivingUnder::isClosed)
                .map(closed -> becauseTheAccountWasClosed(savingsAccountId, closed.closedOn()));
        log.debug("what stops money being paid into a savings account savingsAccountId={} "
                + "stopping={}", savingsAccountId, stopping.orElse("nothing"));
        return stopping;
    }

    /**
     * The sentence a closed savings account turns money away in, written in one place because it is
     * said in two: to whoever pressed the button, and into the record of a saving rule that fell due
     * on an account that had been closed since it was left standing.
     *
     * <p>It ends by saying the history is still readable, which is the same closing clause a shut
     * pot's refusal carries, and it is there for the same reason: "closed" in this application means
     * ended and never deleted, and somebody told that money cannot go in is entitled to know that
     * the money already in there is still accounted for.
     */
    private static String becauseTheAccountWasClosed(long savingsAccountId, LocalDate closedOn) {
        return "Savings account " + savingsAccountId + " was closed on " + closedOn + ", so no more "
                + "money can be paid into it. Its history is still there to read.";
    }

    /**
     * Which deposits a move out of this account would draw down and how much of each, refusing
     * first everything that would refuse the move itself.
     *
     * <p><strong>This module's one face to the module that prices a loyalty forfeit.</strong> What
     * moving money costs is a loyalty question — the euros arrive with a new anniversary and give
     * up the one they were part-way through — and only Loyalty can put a number on it. Which euros
     * are about to be moved is this module's answer, because only this module knows the order a
     * savings account gives its rows up in. So the question crosses here, once, and Loyalty is
     * handed a list of euros rather than a reason to learn what a deposit is.
     *
     * <p>A read that refuses, which is unusual and deliberate: a cost quoted for a move that would
     * have been turned away is worse than no quote, because somebody would weigh a loyalty clock
     * against a notice period they were never going to get past. It goes through the same gate the
     * move itself goes through, in the same order, with the same sentences, and writes nothing.
     *
     * <p>It is the other face of this module's own withdrawals rather than a query of its own, for
     * the reason the week's arithmetic already gives: both ledgers a move touches are this module's,
     * and a second walk over them would be a second answer to "which deposit did that come out of".
     *
     * @throws WithdrawalRefused for every reason the move would be refused on the source's side
     */
    @Transactional(readOnly = true)
    public List<MoneyAMoveWouldTake> whatAMoveWouldDrawDown(long fromSavingsAccountId,
                                                            long toSavingsAccountId,
                                                            BigDecimal amount) {
        return withdrawals.whatAMoveWouldDrawDown(fromSavingsAccountId, toSavingsAccountId, amount);
    }

    /**
     * The most this customer has ever had in savings, across every account they hold.
     *
     * <p>The mark a deposit is judged against: euros above it are new saving and earn, euros below
     * it have been saved before and have already been paid for. Said out loud rather than kept
     * inside this module, because a customer whose deposit earned nothing is owed the reason, and
     * the reason is this figure sitting above what they currently hold.
     *
     * <p>It never falls. Taking money out lowers what they hold and leaves this exactly where it
     * was, which is what makes a withdrawal free: it costs no points then, and the same euros
     * simply do not earn again on the way back in.
     *
     * <p>Derived on every read from what the customer's deposits earned on, so no stored total can
     * drift away from the deposits underneath it.
     */
    @Transactional(readOnly = true)
    public BigDecimal mostEverSavedBy(long customerId) {
        BigDecimal mark = quotedToTheCent(deposits.everEarnedOnBy(customerId));
        log.debug("the most a customer has ever saved customerId={} mostEverSaved={}", customerId, mark);
        return mark;
    }

    /**
     * What this customer still holds across every savings account they have.
     *
     * <p>The other half of the pair {@link TheMostEverSaved} judges a deposit against, and it is
     * said out loud for the caller that needs both. What a deposit earns on is what it adds above
     * the mark, which is {@code stillSaved + amount - everEarnedOn} — so a caller holding only the
     * mark can price a deposit for a customer at their peak and nobody else. The gap between the two
     * figures is exactly what has been taken out and not yet put back, and it is the whole of why a
     * withdrawal costs a customer their next deposit's points.
     *
     * <p>Narrower than the balance read beside it on purpose. {@link #moneyBalanceOf} answers about
     * one savings account, which is the pot on a screen; this answers about the person, which is who
     * the mark belongs to. A customer with two savings accounts has one mark and one run of weeks
     * across both, so anything reasoning about what their next deposit earns has to ask about them
     * and not about a pot.
     *
     * <p>Derived on every read from what the deposits still hold, so no stored total can drift away
     * from the deposits underneath it — the same bargain {@link #mostEverSavedBy} makes.
     */
    @Transactional(readOnly = true)
    public BigDecimal stillSavedBy(long customerId) {
        BigDecimal held = quotedToTheCent(deposits.stillSavedBy(customerId));
        log.debug("what a customer still holds in savings customerId={} stillSaved={}",
                customerId, held);
        return held;
    }

    /**
     * The same figure counting only the money they put there themselves, with the interest the bank
     * has paid left out.
     *
     * <p><strong>The term the mark is judged with, and the reason there are two figures where
     * there used to be one.</strong> {@link #stillSavedBy} is a balance: every euro actually in
     * their savings accounts, which is what a challenge asking somebody to hold a buffer is about
     * and what the account's own balance agrees with. This is one side of a subtraction —
     * {@code stillSaved + amount - everEarnedOn} — whose other side is the most they have ever put
     * away, and the two have to count the same money or the gap between them stops meaning "what an
     * earlier withdrawal took and has not been put back".
     *
     * <p>Without it, a year of interest would quietly refill that gap and a customer who had
     * withdrawn five hundred euros would earn points a second time on euros they never moved. The
     * repository's query carries the whole argument; this is the door it leaves by.
     *
     * <p>Public for the one caller outside this module that prices a deposit rather than reading a
     * balance: the simulator's snapshot, whose fold restates {@link TheMostEverSaved} and would
     * otherwise promise a projection the ledger will not honour.
     */
    @Transactional(readOnly = true)
    public BigDecimal stillSavedOutOfTheirOwnMoneyBy(long customerId) {
        BigDecimal held = quotedToTheCent(deposits.stillSavedOutOfTheirOwnMoneyBy(customerId));
        log.debug("what a customer still holds out of their own money customerId={} "
                + "stillSavedOutOfTheirOwn={}", customerId, held);
        return held;
    }

    /**
     * The deposits that produced the savings account's balance, each with what it earned.
     *
     * <p>What each one earned is asked of the Points module rather than kept here, for the same
     * reason the deposit itself does not record it: the two are one event, and one of them owning
     * the answer is what stops them from ever disagreeing.
     *
     * <p>The ledger answers with a breakdown by reason, and both the total and what it is made of are
     * reported: the total is every point the deposit earned however it earned it, and the two parts
     * say which of them was the euros and which the run of weeks. A deposit the ledger has never
     * heard of earned nothing, which is the figure a deposit whose euros floored away earned as well.
     *
     * <p>The rate comes off the deposit itself rather than out of a fresh derivation, which is the
     * whole reason it was written down: a past deposit explains itself the same way after the ladder
     * changes, after the run it was paid on lapses, and after a trainer has wound the clock in either
     * direction.
     */
    @Transactional(readOnly = true)
    public List<RecordedDeposit> depositsInto(long savingsAccountId) {
        List<Deposit> made = deposits.madeIntoNewestFirst(savingsAccountId);
        Map<Long, PointsByReason> pointsEarned =
                points.pointsEarnedBy(made.stream().map(Deposit::getId).toList());
        List<RecordedDeposit> history = made.stream()
                .map(deposit -> asRecorded(
                        deposit,
                        pointsEarned.getOrDefault(deposit.getId(), PointsByReason.nothing())))
                .toList();
        // The one decision this listing takes, counted rather than assumed: a rate read off the
        // deposit, or the ordinary rate reported for a deposit recorded before there was a rate to
        // record. A history that suddenly quotes 1.00 against every entry is either a lapsed run or
        // a column that stopped being written, and those two are told apart here and nowhere else.
        // Counts rather than a line per deposit: this runs on every read of an account's history.
        long withoutARateOfTheirOwn = made.stream()
                .filter(deposit -> deposit.getMultiplierApplied() == null)
                .count();
        // And what the account's anniversaries have paid altogether, counted the same way: how many
        // of these deposits have ever been paid a loyalty bonus and what those bonuses came to. A
        // total that grew overnight is explainable from the sweep's own line; this is the other end
        // of it, and it says which deposits the customer is being shown the growth against.
        long paidALoyaltyBonus = history.stream()
                .filter(deposit -> deposit.loyaltyBonusPoints() > 0)
                .count();
        long loyaltyBonusPoints = history.stream()
                .mapToLong(RecordedDeposit::loyaltyBonusPoints)
                .sum();
        log.debug("deposit history reported with what each deposit earned savingsAccountId={} "
                        + "deposits={} atTheRateTheyWerePaidAt={} atTheOrdinaryRateForLackOfOne={} "
                        + "paidALoyaltyBonus={} loyaltyBonusPoints={}",
                savingsAccountId, history.size(), history.size() - withoutARateOfTheirOwn,
                withoutARateOfTheirOwn, paidALoyaltyBonus, loyaltyBonusPoints);
        return history;
    }

    /** One deposit and what the ledger says it earned, put together for whoever is listing them. */
    private static RecordedDeposit asRecorded(Deposit deposit, PointsByReason earned) {
        return new RecordedDeposit(
                deposit.getId(),
                deposit.getAmount(),
                // Quoted to the cent, because SQLite hands EUR 12.50 back as 12.5 and a figure the
                // customer reads beside the amount has to read as the same kind of money.
                quotedToTheCent(deposit.getEarnedOnAmount()),
                earned.total(),
                earned.earnedAs(PointsReason.BASE_ACCRUAL),
                earned.earnedAs(PointsReason.STREAK_BONUS),
                earned.earnedAs(PointsReason.LOYALTY_BONUS),
                rateItWasPaidAt(deposit),
                whatItsProductPaid(deposit),
                // Straight through, null and all. A deposit nothing stamped has no version, and
                // there is no ordinary version to report for it the way there is an ordinary rate:
                // the account could have been on any of them.
                deposit.getTermsVersion(),
                deposit.getDepositedAt());
    }

    /**
     * The rate the deposit was paid at, and the multiple that changes nothing for one recorded
     * before any rate was written down.
     *
     * <p>Not a guess: a deposit made before this scheme existed earned one point per whole euro and
     * nothing else, and {@link TheRateADepositIsPaidAt#THE_MULTIPLE_THAT_CHANGES_NOTHING} is that
     * figure written as a rate. Emphatically not the ladder's first rung as the scheme in force
     * publishes it today — a history row is what the ledger did, and a scheme published this morning
     * may not reach back and restate it. Quoted to two places
     * because the figure has been through SQLite, which has no decimal type and hands 1.50 back as
     * 1.5 — the rate a deposit reports here has to read the way it read in the answer to the deposit
     * itself.
     */
    private static BigDecimal rateItWasPaidAt(Deposit deposit) {
        return TheRateADepositIsPaidAt.quoted(deposit.getMultiplierApplied() == null
                ? TheRateADepositIsPaidAt.THE_MULTIPLE_THAT_CHANGES_NOTHING
                : deposit.getMultiplierApplied());
    }

    /**
     * What the account's product contributed to that rate, and the multiple that changes nothing
     * for a deposit made before there were products to contribute anything.
     *
     * <p>Not a guess either: a deposit recorded before the catalogue existed was not priced under a
     * product at a rate nobody wrote down, it was not priced under a product at all, and
     * {@code 1.00} is what that means when it is multiplied by. It is the same reading, the same
     * constant, and the same argument as the rate above — and it is emphatically not the reading
     * {@link Deposit#getTermsVersion} gets, where there is no honest default because an account
     * could have been on any version.
     *
     * <p>Quoted the same way the rate beside it is, for the same reason: the figure has been
     * through SQLite, which has no decimal type and hands 1.25 back as whatever a float kept.
     */
    private static BigDecimal whatItsProductPaid(Deposit deposit) {
        return TheRateADepositIsPaidAt.quoted(deposit.getProductMultiplierApplied() == null
                ? TheRateADepositIsPaidAt.THE_MULTIPLE_THAT_CHANGES_NOTHING
                : deposit.getProductMultiplierApplied());
    }

    /**
     * The deposits this customer made inside a stretch of time, oldest first — everything they paid
     * in, whichever of their savings accounts it went into.
     *
     * <p>The customer's rather than one account's, because that is what a week of saving is: a week
     * counts what somebody put away, and which goal they were putting it towards decides where the
     * euros sit and nothing about the week. Whoever wants one account's payments in wants
     * {@link #depositsInto}.
     *
     * <p>The stretch is half-open — the first moment counts, the last does not — so that whoever
     * splits time into adjacent stretches gets each deposit in exactly one of them. A caller
     * counting calendar weeks is the reason this exists, and a deposit made on the stroke of Monday
     * has to fall in one week rather than in both or in neither.
     *
     * <p>What landed rather than what is left: {@link DepositLanded} carries the amount that was
     * paid in, and a withdrawal since then has not changed it. This module keeps both figures and a
     * caller asking what came in during a stretch of time is asking for the first — the second is
     * {@link #moneyBalanceOf}, which answers about the account rather than about a stretch of time.
     *
     * <p>The deposits rather than a total of them, because what a total means is the caller's rule
     * and not this module's: whoever is asking is the one who knows whether a week is judged on
     * everything that landed in it, and a total handed over would have decided that here.
     *
     * @throws IllegalArgumentException if the stretch ends before it begins
     */
    @Transactional(readOnly = true)
    public List<DepositLanded> depositsLandedBetween(long customerId, Instant from, Instant until) {
        // A stretch that ends before it begins is a caller that worked its boundaries out wrongly,
        // and the query would answer it with an empty list — which reads as "nothing landed in that
        // week" and would have a week silently reporting nothing rather than reporting a fault. Said
        // out loud instead: nobody types these two moments, so the only way to get here is a bug.
        if (from.isAfter(until)) {
            String reason = "a stretch of time runs forwards, and " + from + " is after " + until;
            log.warn("deposits not counted customerId={} reason={}", customerId, reason);
            throw new IllegalArgumentException(reason);
        }
        List<DepositLanded> landed = deposits.landedBetween(customerId, from, until).stream()
                // Quoted to the cent here, once, because this is where the amount leaves the module:
                // SQLite has no decimal type and hands EUR 12.50 back as 12.5, and a caller adding
                // those up or writing them into a log line would either restate the rounding or
                // print a figure that does not read as money.
                .map(deposit -> new DepositLanded(
                        deposit.getId(), quotedToTheCent(deposit.getAmount()), deposit.getDepositedAt()))
                .toList();
        // The stretch that was asked about and how many deposits were in it, so that a caller's own
        // figure can be checked against the deposits this module handed it. The deposits themselves
        // are left to whoever asked to log: it knows what it was counting them for, and this runs on
        // every read of an account.
        log.debug("deposits that landed in a stretch of time customerId={} from={} until={} "
                + "deposits={}", customerId, from, until, landed.size());
        return landed;
    }

    /**
     * Everything this customer paid in before a moment, oldest first — the whole of their saving up
     * to that point, across every account they hold.
     *
     * <p>Exclusive of the moment, so that this and {@link #depositsLandedBetween} split time at it
     * the same way and a caller asking for both sides of a boundary counts nothing twice.
     *
     * <p>Up to a moment rather than all of it, because the application's clock moves: a trainer who
     * winds it forward, pays money in and winds it back has left a deposit dated in the future, and a
     * caller walking somebody's saving back through the weeks is entitled to ask for the part of it
     * that has actually happened. Whoever asks names the moment; this module does not read the clock.
     *
     * <p>What landed rather than what is left, for the reason {@link DepositLanded} gives: a
     * withdrawal since then draws a deposit down without un-happening it.
     *
     * <p>The customer's, not one account's: a week of somebody's saving counts what they paid in
     * wherever they paid it, so the run of weeks behind it is walked over the same ledger.
     */
    @Transactional(readOnly = true)
    public List<DepositLanded> depositsLandedBefore(long customerId, Instant until) {
        List<DepositLanded> landed = deposits.landedBefore(customerId, until).stream()
                // Quoted to the cent here, once, for the reason the stretch-of-time query gives:
                // SQLite hands EUR 12.50 back as 12.5, and a caller adding those up or writing one
                // into a log line would either restate the rounding or print a figure that does not
                // read as money.
                .map(deposit -> new DepositLanded(
                        deposit.getId(), quotedToTheCent(deposit.getAmount()), deposit.getDepositedAt()))
                .toList();
        // The boundary that was asked about and how many deposits fell before it, so that a caller's
        // own figure can be checked against what this module handed it. The deposits themselves are
        // left to whoever asked to log: it knows what it was counting them for, and this runs on
        // every read of an account.
        log.debug("deposits that landed before a moment customerId={} until={} deposits={}",
                customerId, until, landed.size());
        return landed;
    }

    /**
     * The deposits that still hold money and landed before a moment, oldest first — which deposit,
     * whose it is, how much of it is left, and when it landed.
     *
     * <p>A fact, and this module keeps its opinion about points to itself, which is none. What money
     * that has stayed put is worth is somebody else's rule; the question Deposits can answer is
     * which money has stayed and how much of it there is.
     *
     * <p>Everybody's deposits at once, because the caller is a nightly sweep over all of them rather
     * than a customer looking at their own. Whoever wants one account's history asks
     * {@link #depositsInto}.
     *
     * <p>What is left rather than what landed, which is the difference that makes the answer worth
     * asking for: a deposit half drawn down still holds half, and a deposit emptied holds nothing
     * and is not in the answer at all. Deposits holding nothing are left out by the query rather
     * than by the caller — money never comes back into one, so a deposit at zero has nothing left
     * to decide about.
     *
     * <p>Before a moment, exclusive, so that this and the two listings above split time at it the
     * same way. Whoever asks names the moment; this module does not read the clock.
     */
    @Transactional(readOnly = true)
    public List<DepositStillHoldingMoney> depositsStillHoldingMoneyThatLandedBefore(Instant until) {
        List<DepositStillHoldingMoney> holding =
                deposits.stillHoldingMoneyThatLandedBefore(until).stream()
                        // Quoted to the cent here, once, because this is where the amount leaves the
                        // module: SQLite has no decimal type and hands EUR 12.50 back as 12.5, and a
                        // caller working a figure out from it or writing it into a log line would
                        // either restate the rounding or print something that does not read as money.
                        .map(deposit -> new DepositStillHoldingMoney(
                                deposit.getId(),
                                deposit.getCustomerId(),
                                deposit.getSavingsAccountId(),
                                quotedToTheCent(deposit.getRemainingAmount()),
                                deposit.getDepositedAt()))
                        .toList();
        // The boundary that was asked about and how many deposits still holding money fell before
        // it, so that a caller finding nothing to do can tell a query that came back empty from a
        // rule that declined everything it was handed. The deposits themselves are left to whoever
        // asked to log: it knows what it was counting them for.
        log.debug("deposits still holding money that landed before a moment until={} deposits={}",
                until, holding.size());
        return holding;
    }

    /**
     * The deposits into one savings account that still hold money, oldest first — which deposit,
     * whose it is, how much of it is left, and when it landed.
     *
     * <p>The same fact {@link #depositsStillHoldingMoneyThatLandedBefore} answers, asked about one
     * account instead of about everybody and without a boundary in time. A nightly sweep wants the
     * deposits old enough for its rule; a customer's history wants the deposits in front of that
     * customer, whatever age they are, because a rule about money that stays put has something to
     * say about every one of them.
     *
     * <p>Two reads rather than one that does both, because the two callers are asking different
     * questions and a single read taking an account and a moment would have each of them passing
     * something it does not mean. This module's opinion about points is, as ever, none: it says
     * which money has stayed and how much of it there is.
     */
    @Transactional(readOnly = true)
    public List<DepositStillHoldingMoney> depositsStillHoldingMoneyIn(long savingsAccountId) {
        List<DepositStillHoldingMoney> holding = deposits.stillHoldingMoneyIn(savingsAccountId).stream()
                // Quoted to the cent here, once, for the reason the sweep's listing gives: SQLite
                // has no decimal type and hands EUR 12.50 back as 12.5, and a caller working a
                // figure out from it would either restate the rounding or print something that does
                // not read as money.
                .map(deposit -> new DepositStillHoldingMoney(
                        deposit.getId(),
                        deposit.getCustomerId(),
                        deposit.getSavingsAccountId(),
                        quotedToTheCent(deposit.getRemainingAmount()),
                        deposit.getDepositedAt()))
                .toList();
        // How many of the account's deposits still hold money, so that a caller reporting nothing
        // about the older ones can be told from a query that came back empty. The deposits
        // themselves are left to whoever asked to log: this runs on every read of an account's
        // history, and it knows what it was counting them for.
        log.debug("deposits in an account that still hold money savingsAccountId={} deposits={}",
                savingsAccountId, holding.size());
        return holding;
    }

    /**
     * Which deposits were made into a savings account, oldest first — the identifiers and nothing
     * else.
     *
     * <p>The fact, so that a rule about what those deposits earned can be applied by whoever owns
     * it. This module's opinion about points is none: it says which payments went into this pot, and
     * a caller holding them can ask the ledger what they are worth and when that is going to change.
     *
     * <p>Every deposit ever made into the account, not only those that still hold money. The two
     * questions are different and this application asks both: money that has stayed is what pays an
     * anniversary, and {@link #depositsStillHoldingMoneyIn} answers that; points that were earned
     * outlive the euros that earned them and run their own twelve months whether or not the money is
     * still there.
     *
     * <p>Answered as a list of identifiers rather than of deposits, which is the point of it. Every
     * other read here assembles a record because its caller wants what is in one; this caller wants
     * to name a handful of deposits to another module, and handing it the rows would be reading and
     * decorating each of them in order to keep the number off the front.
     */
    @Transactional(readOnly = true)
    public List<Long> whichDepositsWereMadeInto(long savingsAccountId) {
        List<Long> made = deposits.idsOfDepositsInto(savingsAccountId);
        // How many, so that a caller answering with nothing can be told from an account that has
        // never been paid into. The identifiers themselves are left to whoever asked to log, as the
        // listing above leaves its deposits: it is the one that knows what it wanted them for.
        log.debug("deposits made into an account savingsAccountId={} deposits={}",
                savingsAccountId, made.size());
        return made;
    }

    /**
     * What the savings account holds, summed from what remains of the deposits made into it. Derived
     * on every read, so there is no stored figure that could drift away from them.
     *
     * <p>What remains rather than what was put in, which is the same figure today and will not be
     * once money can go back out: a withdrawal draws its deposits down, and a balance summed from
     * what each one still holds is then still the money that is actually there. Summing what was put
     * in would report money that has already left.
     */
    @Transactional(readOnly = true)
    public BigDecimal moneyBalanceOf(long savingsAccountId) {
        // Summed here rather than by the database, which is what the points ledger does with its
        // whole numbers. SQLite has no decimal type and keeps an amount as a float, so a sum it
        // worked out itself would accumulate in floating point; adding the amounts back as decimals
        // keeps the cents the customer typed.
        List<Deposit> made = deposits.findBySavingsAccountId(savingsAccountId);
        BigDecimal balance = made.stream()
                .map(Deposit::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // How many terms went into the figure, so that a balance can be checked against the deposits
        // logged into the same account rather than taken on trust. The terms themselves are not
        // listed: this runs on every read of an account, and one line per deposit would bury the
        // business events in a page load.
        log.debug("money balance summed from what remains savingsAccountId={} deposits={} balance={}",
                savingsAccountId, made.size(), asMoney(balance));
        return balance;
    }

    /**
     * Every payment into one savings account, oldest first — which deposit, whose money it was, how
     * much went in, what it earned, and when it landed.
     *
     * <p><strong>Whose, which is the whole of why this is not {@link #depositsInto}.</strong> That
     * listing answers a customer reading back over one account of their own, so whose the money was
     * is never in question and is not on the row. An account several people pay into turns that into
     * the first question anybody asks of it, and the answer was already written on every deposit —
     * so this is a read over rows that exist rather than a record of something new.
     *
     * <p><strong>And what went in rather than what is left</strong>, which is the other half.
     * {@link #depositsStillHoldingMoneyIn} answers what is still there, deposit by deposit; this
     * answers what was put in. The two are the same figure until money leaves and different
     * afterwards, because a withdrawal draws the oldest deposits down whoever paid them in — and a
     * caller that could only ask one of them could not say that somebody's contribution has been
     * spent. Both are needed and neither is derivable from the other, so there are two reads.
     *
     * <p>Every deposit, including those drawn down to nothing: a contribution that has since been
     * spent was still made, and the points it earned are still in somebody's pot.
     *
     * <p>Which current account each one came out of is on the row too, and it was already written
     * there: a shared pot that closes returns each member's own euros to the account their most
     * recent contribution came from, and that is the fact it reads to find out where. Nothing here
     * says whose the account is, and nothing needs to — it is the account this very deposit came out
     * of.
     *
     * <p>What each one earned is asked of the Points module, for the reason the deposit history and
     * the money-movement ledger both give: the money moving and the points being earned are one
     * event, and one module owning what it earned is what stops the two from ever disagreeing. Asked
     * for all of them at once, because a question per deposit would be a query per deposit.
     */
    @Transactional(readOnly = true)
    public List<DepositPaidIn> depositsPaidInto(long savingsAccountId) {
        List<Deposit> made = deposits.madeIntoOldestFirst(savingsAccountId);
        Map<Long, PointsByReason> earned =
                points.pointsEarnedBy(made.stream().map(Deposit::getId).toList());
        List<DepositPaidIn> paidIn = made.stream()
                // Quoted to the cent here, once, for the reason every other listing in this module
                // gives: SQLite has no decimal type and hands EUR 12.50 back as 12.5, and a caller
                // adding these up would either restate the rounding or print something that does
                // not read as money.
                .map(deposit -> new DepositPaidIn(
                        deposit.getId(),
                        deposit.getCustomerId(),
                        deposit.getSourceCurrentAccountId(),
                        quotedToTheCent(deposit.getAmount()),
                        earned.getOrDefault(deposit.getId(), PointsByReason.nothing()).total(),
                        deposit.getDepositedAt()))
                .toList();
        // How many deposits and how many people made them, so that a caller reporting one person's
        // figures out of an account several people pay into can be checked against what it was
        // handed. Counts rather than a line per deposit: this runs on every read of the screen it
        // feeds, and one line per payment would bury the business events in a page load.
        log.debug("deposits paid into an account with whose each was savingsAccountId={} "
                        + "deposits={} whoPaidThemIn={}",
                savingsAccountId, paidIn.size(),
                paidIn.stream().map(DepositPaidIn::customerId).distinct().count());
        return paidIn;
    }

    /**
     * When this savings account's money first arrived, or nothing at all when none ever has.
     *
     * <p>For whoever has to date a savings account and has no opening date to date it from. An
     * account written before this application recorded what an account is on has exactly one fact
     * saying when it started being a savings account, and it is this one: the moment its first
     * deposit landed.
     *
     * <p>A moment rather than a day, because which day a moment falls on depends on the zone it is
     * read in, and the zone this application counts its days in is not this module's to declare.
     * The caller reads it into a day in the one zone everything else here already uses.
     */
    @Transactional(readOnly = true)
    public Optional<Instant> whenTheFirstDepositIntoLanded(long savingsAccountId) {
        Optional<Instant> firstArrived = deposits
                .findFirstBySavingsAccountIdOrderByDepositedAtAscIdAsc(savingsAccountId)
                .map(Deposit::getDepositedAt);
        log.debug("when a savings account's money first arrived savingsAccountId={} firstLandedAt={}",
                savingsAccountId, firstArrived.orElse(null));
        return firstArrived;
    }

    /**
     * How many deposits in the whole ledger do not say which version of their account's terms they
     * landed under.
     *
     * <p>For a start-up migration deciding whether it has anything to do. Nought is the answer on
     * every start after the first, and the question is asked rather than assumed so that the step
     * can say truthfully whether it stamped anything — which is the difference between a log a
     * reviewer can trust and one that claims the same thing every morning.
     */
    @Transactional(readOnly = true)
    public long howManyDepositsDoNotSayWhichVersionTheyLandedUnder() {
        return deposits.howManyDoNotSayWhichVersionTheyLandedUnder();
    }

    /**
     * Says which version every unstamped deposit into one savings account landed under, and reports
     * how many that was.
     *
     * <p>Told rather than asked, because the version is the account's agreement and that is not
     * this module's answer: whoever keeps the agreements reads the account's own record and hands
     * the number down. This module owns the column and writes it, which is the whole of the
     * division — the same one {@code DepositsOnStartUp} makes when it is told whose saving a
     * deposit was.
     *
     * <p>Only the deposits that say nothing. A deposit that already names a version names the
     * agreement it was actually priced under, and overwriting it would be rewriting history to
     * match the present.
     */
    @Transactional
    public int sayWhichVersionTheDepositsIntoAnAccountLandedUnder(long savingsAccountId, int version) {
        int stamped = deposits.sayWhichVersionTheyLandedUnder(savingsAccountId, version);
        if (stamped == 0) {
            return 0;
        }
        // A business event, and one a reviewer looking at an older database will want to see: these
        // deposits could not say what they were priced under until this line ran.
        log.info("deposits recorded before this release told which version they landed under "
                        + "savingsAccountId={} version={} deposits={}",
                savingsAccountId, version, stamped);
        return stamped;
    }
}
