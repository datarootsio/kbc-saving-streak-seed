package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.MoneyMovement;
import io.dataroots.savingstreak.deposits.MoneyMovementDirection;
import io.dataroots.savingstreak.deposits.MoneyMovementsService;
import io.dataroots.savingstreak.products.InterestPostingRepository.APeriodAlreadyJudged;
import io.dataroots.savingstreak.products.TheDailyBalancesOfAnAccount.AMovementOfMoney;
import io.dataroots.savingstreak.products.TheDailyBalancesOfAnAccount.TheBalanceAcrossAPeriod;
import io.dataroots.savingstreak.products.TheRateAPeriodIsPaidAt.WhatAPeriodEarned;
import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The euros start growing: every month an account has been open, it is paid interest at the rate
 * its own terms name, on the average balance it held across that month.
 *
 * <p><strong>The average daily balance, and that is the decision this class exists to make.</strong>
 * The closing balance would pay a whole month on money that arrived yesterday, which is wrong in
 * the customer's favour; the opening balance would pay nothing on it, which is wrong in the bank's.
 * The average pays for the money that was actually there for as long as it was actually there, and
 * {@link TheDailyBalancesOfAnAccount} is the walk that works it out from the movements the account
 * already records. Nothing about a past balance is stored: it is replayed from the ledger, because
 * a stored daily balance would be a second record of what the ledger already says and the day the
 * two disagreed the interest would be right in neither.
 *
 * <p><strong>What a period pays is the average times the rate divided by twelve, floored to the
 * cent.</strong> The rate is the account's own — the version it was opened under, not what its
 * product is selling today — which is the whole reason this module keeps the two apart. The floor
 * is downwards and it is applied once, to a figure worked out in whole cents, so that a customer
 * redoing the arithmetic from what the posting says arrives at the same number.
 *
 * <p><strong>And the rate is the headline rate plus the bonus, on a month that kept what the
 * product asks for.</strong> {@link TheRateAPeriodIsPaidAt} makes that judgement — the lowest daily
 * balance the walk above found, against the floor the account's own version names — and this class
 * does nothing differently for a month that earned it than for one that did not. That is
 * deliberate: the only thing the bonus changes is the number handed to the arithmetic, so a month
 * that dipped is priced by exactly the same sentence as a month that did not, and the posting says
 * which it was. A dip costs that month's bonus and nothing else; the month afterwards is walked
 * afresh and paid in full, because no period knows what the one before it did. <strong>No
 * withdrawal is refused for it</strong>, ever: {@code TheConditionsOnTheWayOut} has no floor in it
 * on purpose, and a rule that both refused the withdrawal and withheld the bonus could only ever do
 * one of them.
 *
 * <p><strong>Paid into the account that earned it, as money.</strong> The Deposits module writes it
 * as a row of the savings ledger with an origin of its own, which is what makes it withdrawable,
 * puts it in the record of money that moved, and puts it in next month's average — so interest
 * compounds monthly without anything here compounding it. It earns no points, secures no week and
 * pays no anniversary, and not one of those is a rule this class applies: each is a query in the
 * Deposits module that asks for the customer's own rows, so there is nothing here to remember not
 * to do.
 *
 * <p><strong>Every period an account has passed and not been paid for, rather than the most recent
 * one.</strong> Winding the clock a year forward and running this once pays twelve months, which is
 * what makes a monthly scheme demonstrable in an afternoon — and it is the same arrangement that
 * covers an application that was switched off for a fortnight. Each period is worked out from the
 * ledger as it stands once the period before it has been paid, so the second month's average
 * includes the first month's interest.
 *
 * <p><strong>No interest is backdated.</strong> An account that was already open when this bank
 * started paying is paid for the periods that begin from the day it started, never for the year of
 * history behind it — {@code AccountAgreement} carries the two dates that say so and the reason
 * there are two.
 *
 * <p><strong>Nothing is paid into an account nobody holds.</strong> A shared pot's savings account
 * is on a product like any other, and interest paid into it would be euros belonging to nobody in
 * particular: what each member is owed out of a pot is worked out from the contributions they made,
 * so a euro that arrived from the bank has no member to be settled to and would sit in the pot for
 * ever. Whether a pot earns interest, and whose it is when it does, is a governance question this
 * feature does not open — the spec puts products aimed at pots out of scope, and this is the
 * smallest reading of that which does not quietly invent an answer.
 *
 * <p>Idempotent by construction. Every period that is judged writes a row naming the account and
 * the ordinal, the pair is unique, and a second pass over the same months finds every one of them
 * already judged and pays nothing. The row is written even when the month paid nothing, because a
 * period is a window that has closed and the row is the statement that it was looked at.
 */
@Service
public class InterestService {

    private static final Logger log = LoggerFactory.getLogger(InterestService.class);

    private final AccountAgreementRepository agreements;
    private final ProductTermsRepository terms;
    private final InterestPostingRepository postings;
    /** Who holds each account, because nothing is paid into one nobody holds. */
    private final AccountsService accounts;
    /**
     * Where the euros are written, and the only thing this class asks of Deposits. It hands over an
     * amount already worked out: that module has no opinion about rates, and this one has none
     * about how a savings ledger is kept.
     */
    private final DepositsService deposits;
    /**
     * The movements the walk is replayed over — every euro in or out of the account, including the
     * interest already paid into it, which is what makes the next month's average include the last
     * month's payment. Asked of the ledger that owns them rather than reassembled here out of
     * deposits and withdrawals, so that "what moved" has one answer in this application.
     */
    private final MoneyMovementsService movements;
    private final Clock clock;

    InterestService(AccountAgreementRepository agreements, ProductTermsRepository terms,
                    InterestPostingRepository postings, AccountsService accounts,
                    DepositsService deposits, MoneyMovementsService movements, Clock clock) {
        this.agreements = agreements;
        this.terms = terms;
        this.postings = postings;
        this.accounts = accounts;
        this.deposits = deposits;
        this.movements = movements;
        this.clock = clock;
    }

    /**
     * Pays every period every account has passed and not been paid for, each worked out on the
     * balance it actually held.
     *
     * <p>Answers nothing, for the reason the loyalty sweep gives: its one caller runs on a schedule
     * with nobody waiting on it, and a figure returned to a scheduled method is a figure nothing can
     * read. What the sweep did is in the INFO line at the end of it.
     *
     * <p>Public, unlike the record and the repositories, and not because anything outside wants it:
     * {@code @Transactional} is applied by a proxy, and the power this leaks is the power to run
     * the nightly job early — which is idempotent and is exactly what the development jobs endpoint
     * offers anyway.
     *
     * <p>The caller says what time it is. This module reads no clock inside a sweep: a run against
     * a wound-forward clock has to judge periods against the moment the application thinks it is,
     * and a service that read the machine's clock would quietly refuse to be demonstrated.
     *
     * <p>One transaction for the whole night, so that a posting and the euros it paid are one event
     * — and so that a sweep interrupted half-way leaves no account paid for a month the record does
     * not mention.
     */
    @Transactional
    public void postMonthlyInterest(Instant now) {
        LocalDate today = asADay(now);
        List<AccountAgreement> onProducts = agreements.everyAgreement();
        Set<APeriod> alreadyJudged = whatHasAlreadyBeenJudged(onProducts);
        WhatASweepPaid paid = WhatASweepPaid.NOTHING;
        for (AccountAgreement agreement : onProducts) {
            paid = paid.and(payWhatThisAccountIsOwed(agreement, today, now, alreadyJudged));
        }
        // One line per sweep with everything that decided it: the moment it judged the periods
        // against, the day that moment was read as, how many accounts it looked at, how many
        // periods it posted and what they came to. A balance that grew overnight is explainable
        // from this line alone, and a sweep that read forty accounts and paid none of them can be
        // told from a sweep that was handed nothing to look at.
        log.info("interest posted asAt={} today={} accountsConsidered={} accountsPaid={} "
                        + "periodsPosted={} interest={}",
                now, today, onProducts.size(), paid.accounts(), paid.periods(),
                asEuros(paid.cents()));
    }

    /**
     * Every month this account has been judged for, oldest first, with the arithmetic behind each.
     *
     * <p>The months that paid nothing are in it, for the reason the record gives: a customer whose
     * account was empty in March is owed the row that says so rather than a gap they have to count
     * to find.
     */
    @Transactional(readOnly = true)
    public List<AnInterestPosting> interestPaidInto(long savingsAccountId) {
        List<AnInterestPosting> paid =
                postings.findBySavingsAccountIdOrderByPeriodOrdinalAsc(savingsAccountId).stream()
                        .map(InterestPosting::asPaid)
                        .toList();
        log.debug("the interest an account has been paid was read savingsAccountId={} periods={}",
                savingsAccountId, paid.size());
        return paid;
    }

    /**
     * Every period this one account has passed, has not been judged for, and this bank is willing
     * to pay for.
     *
     * <p>Three reasons to pass an account over entirely, and each of them says so at DEBUG rather
     * than being silent: nobody holds it, nothing says when its interest starts counting, or it is
     * still inside its first month. A reader counting accounts against the sweep's own line would
     * otherwise be short and have nowhere to look.
     */
    private WhatASweepPaid payWhatThisAccountIsOwed(AccountAgreement agreement, LocalDate today,
                                                    Instant now, Set<APeriod> alreadyJudged) {
        long savingsAccountId = agreement.savingsAccountId();
        Optional<AccountHolder> holder = accounts.holderOfSavingsAccount(savingsAccountId);
        if (holder.isEmpty()) {
            log.debug("savings account passed over for interest savingsAccountId={} "
                    + "reason=nobody holds it", savingsAccountId);
            return WhatASweepPaid.NOTHING;
        }
        LocalDate countsFrom = agreement.interestCountsFrom();
        if (countsFrom == null) {
            log.debug("savings account passed over for interest savingsAccountId={} "
                    + "reason=nothing says when its interest starts counting", savingsAccountId);
            return WhatASweepPaid.NOTHING;
        }
        int gone = TheMonthlyPeriodsOfAnAccount.periodsGoneBy(agreement.openedOn(), today);
        if (gone == 0) {
            log.debug("savings account passed over for interest savingsAccountId={} "
                            + "reason=still inside its first period openedOn={} firstPeriodEnds={}",
                    savingsAccountId, agreement.openedOn(),
                    TheMonthlyPeriodsOfAnAccount.endOf(agreement.openedOn(), 1));
            return WhatASweepPaid.NOTHING;
        }
        ProductTerms itsTerms = theTermsItIsOn(agreement);
        int periods = 0;
        long cents = 0;
        for (int ordinal = 1; ordinal <= gone; ordinal++) {
            OptionalLong paid = payThePeriodIfItIsOwed(agreement, itsTerms, ordinal, holder.get(),
                    now, countsFrom, alreadyJudged);
            if (paid.isPresent()) {
                periods++;
                cents += paid.getAsLong();
            }
        }
        return periods == 0 ? WhatASweepPaid.NOTHING : new WhatASweepPaid(1, periods, cents);
    }

    /**
     * One period of one account, judged if it is owed, and what it paid — or nothing at all when it
     * was passed over, which is not the same as a period that paid nought.
     *
     * <p>That distinction is the whole reason this answers an {@code OptionalLong}. A month that
     * was judged and paid nothing is a month this sweep did something about and wrote a row for; a
     * month already judged, or one that began before this bank was paying, is a month it left
     * alone. A nought for both would make the sweep's own count of what it did untrue.
     */
    private OptionalLong payThePeriodIfItIsOwed(AccountAgreement agreement, ProductTerms itsTerms,
                                                int ordinal, AccountHolder holder, Instant now,
                                                LocalDate countsFrom, Set<APeriod> alreadyJudged) {
        long savingsAccountId = agreement.savingsAccountId();
        if (alreadyJudged.contains(new APeriod(savingsAccountId, ordinal))) {
            // Which is every period on the second run of a nightly job, so this is the line that
            // says a sweep paying nothing is a sweep that has already paid.
            log.debug("period passed over for interest savingsAccountId={} period={} "
                    + "reason=it has already been judged", savingsAccountId, ordinal);
            return OptionalLong.empty();
        }
        LocalDate from = TheMonthlyPeriodsOfAnAccount.beginningOf(agreement.openedOn(), ordinal);
        LocalDate until = TheMonthlyPeriodsOfAnAccount.endOf(agreement.openedOn(), ordinal);
        if (from.isBefore(countsFrom)) {
            log.debug("period passed over for interest savingsAccountId={} period={} from={} "
                            + "until={} reason=it began before this bank started paying interest on "
                            + "this account interestCountsFrom={}",
                    savingsAccountId, ordinal, from, until, countsFrom);
            return OptionalLong.empty();
        }
        TheBalanceAcrossAPeriod balances = TheDailyBalancesOfAnAccount.across(
                theMovementsOf(savingsAccountId), from, until);
        ProductTerms pricedUnder = whatThisPeriodIsPricedUnder(agreement, itsTerms, from);
        // The headline rate plus the bonus when this month kept what the product asks for, and the
        // headline rate alone when it did not. The judgement is TheRateAPeriodIsPaidAt's and the
        // arithmetic below is unchanged by it: what varies is the rate handed in, never the way a
        // rate is turned into a month's worth of cents.
        WhatAPeriodEarned earned = TheRateAPeriodIsPaidAt.forAPeriodWhoseLowestBalanceWas(
                pricedUnder.annualRateBasisPoints(), pricedUnder.bonusRateBasisPoints(),
                pricedUnder.minimumBalanceCents(), balances.lowestCents());
        int rate = earned.annualRateBasisPoints();
        long cents = whatAPeriodPays(balances.averageCents(), rate);
        Long paidAs = cents == 0 ? null : deposits.payInterestInto(savingsAccountId,
                holder.customerId(), asEuros(cents), theEndOf(until));
        InterestPosting posting = postings.save(InterestPosting.of(savingsAccountId, ordinal, from,
                until, balances.averageCents(), balances.lowestCents(), rate,
                earned.bonusEarned(), pricedUnder.version(), cents, paidAs, now));
        // A month that was paid less than the product advertises says so on a line of its own, with
        // the two figures that decided it side by side. A sweep is read afterwards rather than
        // watched, and "why did March pay half of what February paid" has to be answerable from the
        // log without re-walking a ledger that has moved since.
        if (pricedUnder.bonusRateBasisPoints() > 0 && !earned.bonusEarned()) {
            log.debug("a period was paid without its bonus savingsAccountId={} period={} from={} "
                            + "until={} lowestDailyBalance={} floor={} headlineRateBasisPoints={} "
                            + "bonusGivenUpBasisPoints={}",
                    savingsAccountId, ordinal, from, until, asEuros(balances.lowestCents()),
                    asEuros(pricedUnder.minimumBalanceCents()),
                    pricedUnder.annualRateBasisPoints(), pricedUnder.bonusRateBasisPoints());
        }
        log.debug("interest worked out savingsAccountId={} customerId={} period={} from={} until={} "
                        + "days={} averageDailyBalance={} lowestDailyBalance={} rateBasisPoints={} "
                        + "pricedUnder={} termsVersion={} bonusEarned={} interest={} "
                        + "paidAsDepositId={} postingId={}",
                savingsAccountId, holder.customerId(), ordinal, from, until, balances.days(),
                asEuros(balances.averageCents()), asEuros(balances.lowestCents()), rate,
                pricedUnder.productCode(), pricedUnder.version(), earned.bonusEarned(),
                asEuros(cents), paidAs, posting.getId());
        return OptionalLong.of(cents);
    }

    /**
     * The version this one period is priced under, which is the account's own except for the one
     * case where it honestly is not.
     *
     * <p><strong>A term left waiting earns what free savings earns, from the morning it
     * matured.</strong> {@link AWaitingTermEarnsTheFreeSavingsRate} argues the rule at length and
     * decides which day it applies from; this method does the only part that needs the catalogue,
     * which is reading the version of free savings that was on offer on that day. Every other
     * account in this application — every product with no term, every rolling term, every account
     * moved to instant access, and a waiting term for all the months before it matured — takes the
     * first line out and is priced by the version of record exactly as before.
     *
     * <p><strong>The version on offer on the maturity day, and never today's.</strong> The account
     * fell onto the ordinary rate that morning, so those are the terms it fell onto; reading
     * today's would quietly apply to it every rate change the bank published afterwards, which is
     * the one thing this module exists to make impossible.
     *
     * <p>Whole periods, never split ones. A month that straddles the maturity is priced under the
     * term's own version, because the period began while the term was still running and the account
     * was still locked for most of it. The alternative — pro-rating the month at two rates — would
     * make an interest posting say two things about itself, and the rule underneath it has always
     * been that a period is priced by one agreement.
     */
    private ProductTerms whatThisPeriodIsPricedUnder(AccountAgreement agreement,
                                                     ProductTerms itsTerms, LocalDate from) {
        LocalDate fellOnTheOrdinaryRateOn =
                AWaitingTermEarnsTheFreeSavingsRate.theDayItFellOntoTheOrdinaryRate(
                        itsTerms, agreement.theDayTheTermRunsFrom(), from);
        if (fellOnTheOrdinaryRateOn == null) {
            return itsTerms;
        }
        ProductTerms freeSavings = TheTermsOnOfferToday.outOf(
                terms.findByProductCodeOrderByVersionAsc(WhatEachSavingsAccountIsOn.FREE_SAVINGS),
                fellOnTheOrdinaryRateOn);
        log.debug("a period of a matured term left waiting is priced at the free savings rate "
                        + "savingsAccountId={} from={} wasOn={} wasOnVersion={} "
                        + "wasOnRateBasisPoints={} maturedOn={} pricedUnder={} version={} "
                        + "rateBasisPoints={}",
                agreement.savingsAccountId(), from, itsTerms.productCode(), itsTerms.version(),
                itsTerms.annualRateBasisPoints(), fellOnTheOrdinaryRateOn,
                freeSavings.productCode(), freeSavings.version(),
                freeSavings.annualRateBasisPoints());
        return freeSavings;
    }

    /**
     * What a period pays: the average daily balance at a twelfth of the annual rate, floored to the
     * cent.
     *
     * <p>Worked out in cents and basis points, which is what makes the floor the only rounding in
     * it. A percentage would have to be divided by a hundred first, and where that division rounded
     * would be a second decision nobody wrote down.
     *
     * <p>Floored rather than rounded, downwards, because the spec says so and because a bank that
     * rounded a half-cent up on every account every month would be paying money nobody had earned.
     * The figure is small by construction — a month at 0.60% on a thousand euros is fifty cents —
     * so the flooring is visible rather than academic, and a customer can see exactly where it
     * happened.
     *
     * <p><strong>The arithmetic itself lives in {@link WhatAnAnnualRateIsWorth} and is quoted rather
     * than kept here.</strong> The price of breaking a fixed term early is the same multiplication
     * over a different slice of the year, and a second copy of it would be the first place two rates
     * disagreed — a customer charged at a rate they were never paid at. This method stays because
     * the sweep's reader wants to see the sentence "the average at a twelfth of the annual rate"
     * written where the sweep is, and because the name says which of the two slices this one is.
     */
    private static long whatAPeriodPays(long averageCents, int annualRateBasisPoints) {
        return WhatAnAnnualRateIsWorth.overAMonth(averageCents, annualRateBasisPoints);
    }

    /**
     * The movements this account records, as the walk needs them: a day and a signed number of
     * cents.
     *
     * <p>Read afresh for every period rather than once per account, which is deliberate and is the
     * whole of how a year paid in one run compounds. The first month's interest is written into
     * this ledger before the second month is worked out, so the second month's average includes it
     * — and it does because the ledger is asked again, not because anything here remembers to add
     * it. A real bank would keep a daily balance and index the next period rather than walk to find
     * it; for a handful of accounts and a few hundred days of arithmetic this is the reading that
     * cannot drift from what the account actually holds.
     *
     * <p>The direction is turned into a sign once, here, where the ledger's own word is the thing
     * being read. Interest that has already been paid is money in, exactly like a deposit: it is a
     * separate word on the ledger because it earned nothing, not because it is worth less in a
     * balance.
     */
    private List<AMovementOfMoney> theMovementsOf(long savingsAccountId) {
        List<MoneyMovement> ledger = movements.movementsAcross(List.of(savingsAccountId));
        List<AMovementOfMoney> asCents = new ArrayList<>(ledger.size());
        long netted = 0;
        for (MoneyMovement moved : ledger) {
            long cents = AmountOfMoney.quotedToTheCent(moved.amount())
                    .movePointRight(2)
                    .longValueExact();
            long signed = signedFor(savingsAccountId, moved, cents);
            netted += signed;
            asCents.add(new AMovementOfMoney(asADay(moved.movedAt()), signed));
        }
        // What the whole ledger nets to once every direction has been signed, which is the one
        // figure that says the signing was right: it is what the account holds today, and a reader
        // who can see it disagreeing with the balance on the account's own page has found the walk
        // reading a direction the wrong way round before any interest is paid on it. Netted rather
        // than a line per movement, because this runs once per period and a year of a busy account
        // would otherwise be hundreds of lines around one business event.
        log.debug("the ledger was signed for the daily-balance walk savingsAccountId={} "
                        + "movements={} netted={}",
                savingsAccountId, asCents.size(), asEuros(netted));
        return asCents;
    }

    /**
     * Which way a movement went as far as <em>this</em> account's balance is concerned.
     *
     * <p><strong>Every direction is named here, and none of them may be left to a default.</strong>
     * This is a switch over the whole enum with no {@code default} branch, so it is exhaustive and
     * the compiler says so: the next word added to {@link MoneyMovementDirection} will not compile
     * until somebody has decided, here, which way it moves a balance. That is not a style
     * preference, it is the fix for the defect this method actually had. A fall-through to "money
     * in" quietly signed {@link MoneyMovementDirection#AN_EARLY_EXIT_CHARGE} as a rise from the day
     * that word was added, and every account that broke a fixed term was paid interest on the
     * penalty the bank had taken off it, every month, for as long as the account existed. A
     * direction this walk has not thought about is a wrong balance, not a safe one, and the enum
     * has grown twice already.
     *
     * <p><strong>Money out is money out, whoever it went to.</strong> A withdrawal reaches the
     * customer's current account and a charge reaches nobody, which is exactly why they are two
     * words on the ledger — but a balance does not care where the euros went, only that they are
     * gone. The two directions share a branch for that reason and not by accident.
     *
     * <p><strong>A move between two savings accounts is the one entry the word alone cannot
     * settle, because it is the one movement with a savings account at both ends.</strong> The
     * ledger reports it once, naming the account the euros left and the account they reached, so
     * the same entry is a fall on one account's balance and a rise on the other's — and the walk
     * has to say which of the two it is looking at. Read off the word alone it would be a rise on
     * both, and the account somebody moved five thousand euros <em>out</em> of would go on earning
     * interest on money it no longer held.
     */
    private static long signedFor(long savingsAccountId, MoneyMovement moved, long cents) {
        return switch (moved.direction()) {
            case INTO_SAVINGS, INTEREST_INTO_SAVINGS -> cents;
            case OUT_OF_SAVINGS, AN_EARLY_EXIT_CHARGE -> -cents;
            case BETWEEN_SAVINGS_ACCOUNTS ->
                    moved.savingsAccountId() == savingsAccountId ? -cents : cents;
        };
    }

    /**
     * Which periods of which of these accounts have already been judged, as a set that can be asked
     * about one period at a time.
     *
     * <p>One query for every account rather than one per period considered: an account twelve
     * months old is twelve questions, and the rows are the same rows either way. The same
     * arrangement the loyalty sweep uses, for the same reason.
     */
    private Set<APeriod> whatHasAlreadyBeenJudged(List<AccountAgreement> onProducts) {
        if (onProducts.isEmpty()) {
            return Set.of();
        }
        List<APeriodAlreadyJudged> rows = postings.periodsAlreadyJudgedFor(
                onProducts.stream().map(AccountAgreement::savingsAccountId).toList());
        Set<APeriod> judged = new HashSet<>();
        for (APeriodAlreadyJudged row : rows) {
            judged.add(new APeriod(row.getSavingsAccountId(), row.getPeriodOrdinal()));
        }
        log.debug("periods already judged for these accounts accounts={} periodsAlreadyJudged={}",
                onProducts.size(), judged.size());
        return judged;
    }

    /**
     * The exact version this account is living under, by the pair that addresses it.
     *
     * <p>A broken record rather than a customer's mistake when there is no such version, so it
     * throws rather than refusing in words — the same reading {@code WhatEachSavingsAccountIsOn}
     * makes of the same impossible state. Nobody did anything wrong, and there is no sentence to
     * tell them.
     */
    private ProductTerms theTermsItIsOn(AccountAgreement agreement) {
        return terms.findByProductCodeOrderByVersionAsc(agreement.productCode()).stream()
                .filter(version -> version.version() == agreement.version())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "savings account " + agreement.savingsAccountId() + " is on "
                                + agreement.productCode() + " version " + agreement.version()
                                + ", which has never been published"));
    }

    /**
     * The moment a period's interest is dated at: the start of the day the period ended, which is
     * the first day of the next one.
     *
     * <p>Dated by the period rather than by the run, which is the rule a paid anniversary already
     * states: the month had passed whether or not anything was running at a quarter to four that
     * morning, and dating it by the sweep would make the same period report a different moment
     * depending on when the job was actually run. It also puts the money in the account on the
     * first day of the following period, which is exactly where the compounding wants it.
     */
    private static Instant theEndOf(LocalDate until) {
        return until.atStartOfDay(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toInstant();
    }

    private static BigDecimal asEuros(long cents) {
        return AmountOfMoney.quotedToTheCent(BigDecimal.valueOf(cents, 2));
    }

    /**
     * A moment read as the day it fell on, in the zone this application counts its days in —
     * borrowed from {@link SavingsWeek} for the reason {@link ProductsService} gives about the same
     * line.
     */
    private static LocalDate asADay(Instant moment) {
        return LocalDate.ofInstant(moment, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }

    /**
     * One period of one account: the pair the record is unique over, and the only thing the sweep
     * needs in order to ask whether it has been judged.
     *
     * <p>The same pair the database keeps as a unique index. That index is what makes the sweep
     * safe; this is only the sweep's way of asking about one period without a query per period.
     */
    private record APeriod(long savingsAccountId, int ordinal) {
    }

    /**
     * What a sweep has paid so far: how many accounts it has paid anything for, how many periods it
     * judged, and what they came to in cents.
     *
     * <p>A value added up rather than three counters walked along beside each other, so that the
     * line at the end of the sweep cannot report a number of periods from one place and a total
     * from another. The three are one fact about one night.
     */
    private record WhatASweepPaid(int accounts, int periods, long cents) {

        static final WhatASweepPaid NOTHING = new WhatASweepPaid(0, 0, 0);

        WhatASweepPaid and(WhatASweepPaid more) {
            return new WhatASweepPaid(accounts + more.accounts, periods + more.periods,
                    cents + more.cents);
        }
    }
}
