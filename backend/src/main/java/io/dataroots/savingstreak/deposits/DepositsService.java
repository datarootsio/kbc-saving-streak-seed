package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.points.PointsByReason;
import io.dataroots.savingstreak.points.PointsReason;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.streaks.StreakMultiplier;
import io.dataroots.savingstreak.streaks.WeekAndStreak;
import io.dataroots.savingstreak.streaks.WeekAndStreakDerivation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;
import static io.dataroots.savingstreak.deposits.AmountOfMoney.quotedToTheCent;
import static io.dataroots.savingstreak.deposits.DepositRefused.Kind.AGAINST_THE_RULES;
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
    private final Clock clock;

    DepositsService(DepositRepository deposits, AccountsService accounts, PointsService points, Clock clock) {
        this.deposits = deposits;
        this.accounts = accounts;
        this.points = points;
        this.clock = clock;
    }

    /**
     * Moves an amount from a current account into a savings account and credits the points it earns.
     *
     * <p>Both happen in one transaction, which is the load-bearing guarantee of this slice: a deposit
     * that earned no points, or points earned by no deposit, would be a balance nobody could explain.
     *
     * <p>How many points the amount is worth is not decided here. The Points module owns that rule
     * and reports what it credited. Nor is what is in the current account: Accounts is asked to take
     * the money and answers whether there was any to take. Nor is the rate the deposit is paid at:
     * that is the run of weeks the deposit has just been counted into, which the Streaks module
     * derives.
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
        // is no such movement to make, the amount is beside the point.
        refuseUnlessOneCustomersOwnAccounts(savingsAccountId, fromCurrentAccountId);
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
        Instant clockReads = clock.instant();
        Instant now = clockReads.truncatedTo(ChronoUnit.MILLIS);
        // Both readings, so that whoever reads this log can see that the moment came off the
        // application's clock and what the truncation did to it, rather than taking the recorded
        // moment on trust.
        log.debug("deposit takes its moment from the application clock savingsAccountId={} "
                + "clockReads={} recordedMoment={}", savingsAccountId, clockReads, now);

        Deposit deposit = deposits.save(new Deposit(savingsAccountId, fromCurrentAccountId, amount, now));
        // What the deposit starts out with still in it, which is all of it. The account's money
        // balance is summed from this figure rather than from the amount, so a reader adding the
        // balance up by hand needs to see it recorded rather than assume it.
        log.debug("deposit records what remains of it depositId={} amount={} remainingAmount={}",
                deposit.getId(), asMoney(deposit.getAmount()), asMoney(deposit.getRemainingAmount()));
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
        WeekAndStreak saving = WeekAndStreakDerivation.asAt(this, savingsAccountId, now);
        BigDecimal multiplier = saving.streak().multiplier();
        // Written on the deposit before the points are credited, so that what the ledger was paid and
        // what the deposit says it was paid are one decision rather than two.
        deposit.paidAt(multiplier);
        PointsByReason credited =
                points.creditPointsFor(savingsAccountId, deposit.getId(), amount, multiplier, now);
        // Everything that decided the outcome, on one line: the week the deposit landed in, what has
        // now landed in it, whether this deposit is the one that carried the week over the line, how
        // long the run is with the week counted, the rate that run pays, and the two figures the
        // points are made of. A reviewer can redo the whole pricing from this line — floor the
        // amount, multiply by the rate, floor again — and the DEBUG lines underneath it say which
        // weeks the walk went through to arrive at the run.
        log.info("deposit accepted depositId={} savingsAccountId={} fromCurrentAccountId={} "
                        + "amount={} week={} newSavingsThisWeek={} securedByThisDeposit={} "
                        + "streakWeeks={} multiplier={} basePoints={} streakBonusPoints={} "
                        + "pointsEarned={} pointsByReason={} depositedAt={}",
                deposit.getId(), savingsAccountId, fromCurrentAccountId, asMoney(amount),
                saving.week().week(), asMoney(saving.week().newSavings()),
                saving.week().wasCarriedOverBy(amount), saving.streak().currentWeeks(), multiplier,
                credited.earnedAs(PointsReason.BASE_ACCRUAL),
                credited.earnedAs(PointsReason.STREAK_BONUS),
                credited.total(), credited.points(), deposit.getDepositedAt());
        return new RecordedDeposit(
                deposit.getId(), deposit.getAmount(), credited.total(),
                credited.earnedAs(PointsReason.BASE_ACCRUAL),
                credited.earnedAs(PointsReason.STREAK_BONUS), multiplier, deposit.getDepositedAt());
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
     * Refuses a deposit whose two ends are not one customer's own accounts.
     *
     * <p>Asked of Accounts rather than enforced by the database: a deposit names both accounts by
     * identifier and has no foreign key to either, so nothing underneath would object. Which module
     * gets asked is the point — who holds what is Accounts' answer, and Deposits only records that a
     * transfer between two of them happened.
     *
     * <p>Whose the other account was is never named in a refusal. Whoever is asking already knew the
     * identifier they sent; saying who it belongs to would be telling them something new about a
     * customer who is not them.
     */
    private void refuseUnlessOneCustomersOwnAccounts(long savingsAccountId, long fromCurrentAccountId) {
        switch (accounts.pairingFor(savingsAccountId, fromCurrentAccountId)) {
            // The one pairing money can move across, so the only one that goes no further.
            case HELD_BY_ONE_CUSTOMER -> { }
            case NO_SUCH_SAVINGS_ACCOUNT -> throw new DepositRefused(NO_SUCH_ACCOUNT,
                    AccountsService.noSuchSavingsAccount(savingsAccountId));
            case NO_SUCH_CURRENT_ACCOUNT -> throw new DepositRefused(NO_SUCH_ACCOUNT,
                    AccountsService.noSuchCurrentAccount(fromCurrentAccountId));
            case HELD_BY_DIFFERENT_CUSTOMERS -> throw new DepositRefused(AGAINST_THE_RULES,
                    "A savings account can only be paid into from a current account held by the "
                            + "same customer.");
        }
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
        List<Deposit> made = deposits.findBySavingsAccountIdOrderByDepositedAtDescIdDesc(savingsAccountId);
        Map<Long, PointsByReason> pointsEarned =
                points.pointsEarnedBy(made.stream().map(Deposit::getId).toList());
        return made.stream()
                .map(deposit -> asRecorded(
                        deposit,
                        pointsEarned.getOrDefault(deposit.getId(), PointsByReason.nothing())))
                .toList();
    }

    /** One deposit and what the ledger says it earned, put together for whoever is listing them. */
    private static RecordedDeposit asRecorded(Deposit deposit, PointsByReason earned) {
        return new RecordedDeposit(
                deposit.getId(),
                deposit.getAmount(),
                earned.total(),
                earned.earnedAs(PointsReason.BASE_ACCRUAL),
                earned.earnedAs(PointsReason.STREAK_BONUS),
                rateItWasPaidAt(deposit),
                deposit.getDepositedAt());
    }

    /**
     * The rate the deposit was paid at, and the ordinary rate for one recorded before any rate was
     * written down.
     *
     * <p>Not a guess: a deposit made before this scheme existed earned one point per whole euro and
     * nothing else, and the ordinary rate is that figure written as a rate. Quoted to two places
     * because the figure has been through SQLite, which has no decimal type and hands 1.50 back as
     * 1.5 — the rate a deposit reports here has to read the way it read in the answer to the deposit
     * itself.
     */
    private static BigDecimal rateItWasPaidAt(Deposit deposit) {
        return deposit.getMultiplierApplied() == null
                ? StreakMultiplier.THE_ORDINARY_RATE
                : StreakMultiplier.asARate(deposit.getMultiplierApplied());
    }

    /**
     * The deposits that landed in this savings account inside a stretch of time, oldest first.
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
    public List<DepositLanded> depositsLandedBetween(long savingsAccountId, Instant from, Instant until) {
        // A stretch that ends before it begins is a caller that worked its boundaries out wrongly,
        // and the query would answer it with an empty list — which reads as "nothing landed in that
        // week" and would have a week silently reporting nothing rather than reporting a fault. Said
        // out loud instead: nobody types these two moments, so the only way to get here is a bug.
        if (from.isAfter(until)) {
            String reason = "a stretch of time runs forwards, and " + from + " is after " + until;
            log.warn("deposits not counted savingsAccountId={} reason={}", savingsAccountId, reason);
            throw new IllegalArgumentException(reason);
        }
        List<DepositLanded> landed = deposits.landedBetween(savingsAccountId, from, until).stream()
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
        log.debug("deposits that landed in a stretch of time savingsAccountId={} from={} until={} "
                + "deposits={}", savingsAccountId, from, until, landed.size());
        return landed;
    }

    /**
     * Everything that landed in this savings account before a moment, oldest first — the whole of its
     * history up to that point.
     *
     * <p>Exclusive of the moment, so that this and {@link #depositsLandedBetween} split time at it
     * the same way and a caller asking for both sides of a boundary counts nothing twice.
     *
     * <p>Up to a moment rather than all of it, because the application's clock moves: a trainer who
     * winds it forward, pays money in and winds it back has left a deposit dated in the future, and a
     * caller counting an account's history is entitled to ask for the part of it that has actually
     * happened. Whoever asks names the moment; this module does not read the clock.
     *
     * <p>What landed rather than what is left, for the reason {@link DepositLanded} gives: a
     * withdrawal since then draws a deposit down without un-happening it.
     */
    @Transactional(readOnly = true)
    public List<DepositLanded> depositsLandedBefore(long savingsAccountId, Instant until) {
        List<DepositLanded> landed = deposits.landedBefore(savingsAccountId, until).stream()
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
        log.debug("deposits that landed before a moment savingsAccountId={} until={} deposits={}",
                savingsAccountId, until, landed.size());
        return landed;
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
}
