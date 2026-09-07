package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.points.PointsByReason;
import io.dataroots.savingstreak.points.PointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;
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
        // What the deposit starts out with still in it, which is all of it. The account's money
        // balance is summed from this figure rather than from the amount, so a reader adding the
        // balance up by hand needs to see it recorded rather than assume it.
        log.debug("deposit records what remains of it depositId={} amount={} remainingAmount={}",
                deposit.getId(), asMoney(deposit.getAmount()), asMoney(deposit.getRemainingAmount()));
        PointsByReason credited = points.creditPointsFor(savingsAccountId, deposit.getId(), amount, now);
        // The breakdown as well as the total, so that a reader can see what the total is made of
        // rather than having to trust that base accrual is still all there is to it.
        log.info("deposit accepted depositId={} savingsAccountId={} fromCurrentAccountId={} "
                        + "amount={} pointsEarned={} pointsByReason={} depositedAt={}",
                deposit.getId(), savingsAccountId, fromCurrentAccountId, asMoney(amount),
                credited.total(), credited.points(), deposit.getDepositedAt());
        return new RecordedDeposit(
                deposit.getId(), deposit.getAmount(), credited.total(), deposit.getDepositedAt());
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
     * <p>The ledger answers with a breakdown by reason and what is reported is its total, which is
     * every point the deposit earned however it earned it. A deposit the ledger has never heard of
     * earned nothing, which is the figure a deposit whose euros floored away earned as well.
     */
    @Transactional(readOnly = true)
    public List<RecordedDeposit> depositsInto(long savingsAccountId) {
        List<Deposit> made = deposits.findBySavingsAccountIdOrderByDepositedAtDescIdDesc(savingsAccountId);
        Map<Long, PointsByReason> pointsEarned =
                points.pointsEarnedBy(made.stream().map(Deposit::getId).toList());
        return made.stream()
                .map(deposit -> new RecordedDeposit(
                        deposit.getId(),
                        deposit.getAmount(),
                        pointsEarned.getOrDefault(deposit.getId(), PointsByReason.nothing()).total(),
                        deposit.getDepositedAt()))
                .toList();
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
