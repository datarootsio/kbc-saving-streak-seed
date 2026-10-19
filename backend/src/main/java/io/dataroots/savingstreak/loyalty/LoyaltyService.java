package io.dataroots.savingstreak.loyalty;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.dataroots.savingstreak.deposits.DepositStillHoldingMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
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
 * <p>Every twelve months a deposit's money is still there, that deposit pays a tenth of the whole
 * euros still sitting in it. A EUR 500 deposit left alone pays 50 points a year after it landed, 50
 * more the year after that, and 50 more the year after — because the clock belongs to the deposit,
 * recurs, and is counted from the day the money landed rather than from the last payment.
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
    private final Clock clock;

    LoyaltyService(DepositsService deposits, PointsService points, LoyaltyBonusPaidRepository paid,
                   Clock clock) {
        this.deposits = deposits;
        this.points = points;
        this.paid = paid;
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
            for (int ordinal = 1; ordinal <= passed; ordinal++) {
                long bonus = payIfItIsDue(deposit, ordinal, alreadyPaid);
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
                        + "anniversariesPaid={} points={}",
                now, landedBefore, holding.size(), anniversariesPaid, pointsPaid);
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
     * withdrawal happened. It is a tenth of the euros still in the deposit, so drawing a deposit
     * halfway down halves what its next anniversary is worth — the cost of a withdrawal, legible on
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
     * row to have been paid. A deposit holding nine euros is worth nothing on any anniversary, so
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
        Map<Long, NextAnniversaryOfADeposit> next = new LinkedHashMap<>();
        long worthAltogether = 0;
        long worthNothing = 0;
        long owedAnAnniversaryAlready = 0;
        for (DepositStillHoldingMoney deposit : holding) {
            long wholeEuros = LoyaltyRate.wholeEurosIn(deposit.remainingAmount());
            long worth = LoyaltyRate.pointsOn(wholeEuros);
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
                        + "depositsStillHoldingMoney={} worthNothingOnTheirNextAnniversary={} "
                        + "depositsOwedAnAnniversaryTheSweepHasNotPaid={} "
                        + "nextAnniversariesWorthAltogether={}",
                savingsAccountId, now, holding.size(), worthNothing, owedAnAnniversaryAlready,
                worthAltogether);
        return next;
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
     */
    private long payIfItIsDue(DepositStillHoldingMoney deposit, int ordinal,
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
        long bonus = LoyaltyRate.pointsOn(wholeEuros);
        if (bonus == 0) {
            log.debug("deposit passed over for a loyalty bonus depositId={} customerId={} "
                            + "reason=a tenth of what it still holds rounds down to no points "
                            + "anniversary={} ordinal={} remainingAmount={} wholeEuros={} "
                            + "theLeastABonusIsPaidOn={}",
                    deposit.id(), deposit.customerId(), anniversary, ordinal,
                    deposit.remainingAmount(), wholeEuros, LoyaltyRate.THE_LEAST_A_BONUS_IS_PAID_ON);
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
        log.debug("loyalty bonus paid depositId={} customerId={} anniversary={} ordinal={} "
                        + "remainingAmount={} wholeEuros={} rate={} points={} recordId={}",
                deposit.id(), deposit.customerId(), anniversary, ordinal, deposit.remainingAmount(),
                wholeEuros, LoyaltyRate.WHAT_AN_ANNIVERSARY_PAYS_ON_THE_EUROS, bonus,
                record.getId());
        return bonus;
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
