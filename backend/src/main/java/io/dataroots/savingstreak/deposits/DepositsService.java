package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.points.PointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** Euros are quoted to the cent, so an amount carrying more places than this is not one. */
    private static final int DECIMAL_PLACES_IN_AN_AMOUNT_OF_MONEY = 2;

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
     * the money and answers whether there was any to take.
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
        long pointsEarned = points.creditBasePointsFor(savingsAccountId, deposit.getId(), amount, now);
        log.info("deposit accepted depositId={} savingsAccountId={} fromCurrentAccountId={} "
                        + "amount={} pointsEarned={} depositedAt={}",
                deposit.getId(), savingsAccountId, fromCurrentAccountId, asMoney(amount),
                pointsEarned, deposit.getDepositedAt());
        return new RecordedDeposit(deposit.getId(), deposit.getAmount(), pointsEarned, deposit.getDepositedAt());
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
     * An amount of money written the way money is written, to the cent.
     *
     * <p>A figure that has been through the database comes back carrying whatever scale SQLite kept
     * — it has no decimal type and holds an amount as a float — so a balance of 2359.50 arrives as
     * 2359.5, and printed straight into a sentence it reads as a number rather than as money. This
     * is the only place it matters: everywhere else an amount is compared, which BigDecimal does by
     * value rather than by how many places it is carrying, or formatted by whoever displays it.
     */
    private static String asMoney(BigDecimal amount) {
        return amount.setScale(DECIMAL_PLACES_IN_AN_AMOUNT_OF_MONEY, RoundingMode.HALF_UP)
                .toPlainString();
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
                    "There is no current account " + fromCurrentAccountId + ".");
            case HELD_BY_DIFFERENT_CUSTOMERS -> throw new DepositRefused(AGAINST_THE_RULES,
                    "A savings account can only be paid into from a current account held by the "
                            + "same customer.");
        }
    }

    /**
     * Refuses anything that is not an amount of money moving in.
     *
     * <p>Checked before a single record is written, so a refusal never has to be undone.
     */
    private void refuseUnlessAnAmountOfMoney(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new DepositRefused(AGAINST_THE_RULES, "A deposit has to be an amount of more than "
                    + "zero, and " + amount.toPlainString() + " is not.");
        }
        // Refused rather than rounded. Rounding would move an amount nobody typed, and a bank that
        // quietly decides what a figure was meant to say is worse than one that asks.
        if (amount.scale() > DECIMAL_PLACES_IN_AN_AMOUNT_OF_MONEY) {
            throw new DepositRefused(AGAINST_THE_RULES, "An amount of money has at most two decimal "
                    + "places, and " + amount.toPlainString() + " has " + amount.scale() + ".");
        }
    }

    /**
     * The deposits that produced the savings account's balance, each with what it earned.
     *
     * <p>What each one earned is asked of the Points module rather than kept here, for the same
     * reason the deposit itself does not record it: the two are one event, and one of them owning
     * the answer is what stops them from ever disagreeing.
     */
    @Transactional(readOnly = true)
    public List<RecordedDeposit> depositsInto(long savingsAccountId) {
        List<Deposit> made = deposits.findBySavingsAccountIdOrderByDepositedAtDescIdDesc(savingsAccountId);
        Map<Long, Long> pointsEarned =
                points.basePointsEarnedBy(made.stream().map(Deposit::getId).toList());
        return made.stream()
                .map(deposit -> new RecordedDeposit(
                        deposit.getId(),
                        deposit.getAmount(),
                        pointsEarned.getOrDefault(deposit.getId(), 0L),
                        deposit.getDepositedAt()))
                .toList();
    }

    /**
     * What the savings account holds, summed from the deposits made into it. Derived on every read,
     * so there is no stored figure that could drift away from them.
     */
    @Transactional(readOnly = true)
    public BigDecimal moneyBalanceOf(long savingsAccountId) {
        // Summed here rather than by the database, which is what the points ledger does with its
        // whole numbers. SQLite has no decimal type and keeps an amount as a float, so a sum it
        // worked out itself would accumulate in floating point; adding the amounts back as decimals
        // keeps the cents the customer typed.
        return deposits.findBySavingsAccountId(savingsAccountId).stream()
                .map(Deposit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
