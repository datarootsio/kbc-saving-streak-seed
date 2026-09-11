package io.dataroots.savingstreak.deposits;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.points.PointsByReason;
import io.dataroots.savingstreak.points.PointsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.quotedToTheCent;

/**
 * This module's third face: everything that has moved across the boundary between an everyday
 * account and savings, as one chronological ledger.
 *
 * <p>{@link DepositsService} answers what came in and {@link WithdrawalsService} what went back out.
 * Both are the right answer to their own question, and neither answers the question a person asks
 * first — "what have I actually done with my money?" That answer is one list, and assembling it is
 * this module's job because both halves of it are this module's records.
 *
 * <p>Asked for by savings account rather than by customer. A deposit records whose it was and a
 * withdrawal does not, so a customer's ledger would be two different lookups joined by a rule about
 * ownership — and ownership is Accounts' answer, not this module's. Whoever asks has already been
 * told which accounts are the customer's, which is the same shape the customer overview uses to put
 * a balance beside each account.
 *
 * <p>Nothing is added up. There is no running balance and no total, because a running balance across
 * several accounts is not a figure that means anything: the same euro moving out of one pot and into
 * another would be counted twice by a reader following the column down. What each account is worth is
 * {@link DepositsService#moneyBalanceOf}'s answer and is reported beside the account itself.
 */
@Service
public class MoneyMovementsService {

    private static final Logger log = LoggerFactory.getLogger(MoneyMovementsService.class);

    /**
     * Newest first, and the moment settles it: two movements inside one millisecond are ordered by
     * kind and then by identifier, so that a ledger read twice reads the same way both times.
     *
     * <p>Deposits and withdrawals are numbered from separate sequences, so the identifier only means
     * anything alongside the direction — which is why the direction is the tie-break before it
     * rather than after.
     */
    private static final Comparator<MoneyMovement> NEWEST_FIRST =
            Comparator.comparing(MoneyMovement::movedAt).reversed()
                    .thenComparing(MoneyMovement::direction)
                    .thenComparing(Comparator.comparingLong(MoneyMovement::id).reversed());

    private final DepositRepository deposits;
    private final WithdrawalRepository withdrawals;
    private final PointsService points;

    MoneyMovementsService(DepositRepository deposits, WithdrawalRepository withdrawals,
                          PointsService points) {
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.points = points;
    }

    /**
     * Every movement in or out of any of these savings accounts, newest first.
     *
     * <p>What a deposit earned is asked of the Points module, for the reason the deposit history
     * gives: the money moving and the points being earned are one event, and one module owning what
     * it earned is what stops the two from ever disagreeing. Asked for all of them at once, because
     * a question per deposit would be a query per deposit.
     *
     * <p>A withdrawal earns nothing and reports nothing earned. That is the rule rather than a gap:
     * money coming back out has never earned a point in this application.
     *
     * <p>No accounts at all is an empty ledger rather than a query. A customer who holds no savings
     * account has moved nothing into one, and {@code in ()} is not a thing to ask a database.
     */
    @Transactional(readOnly = true)
    public List<MoneyMovement> movementsAcross(Collection<Long> savingsAccountIds) {
        if (savingsAccountIds.isEmpty()) {
            log.debug("money movements asked for across no savings accounts, so there are none");
            return List.of();
        }
        List<Deposit> paidIn = deposits.intoAnyOfNewestFirst(savingsAccountIds);
        List<Withdrawal> takenOut = withdrawals.outOfAnyOfNewestFirst(savingsAccountIds);
        Map<Long, PointsByReason> earned =
                points.pointsEarnedBy(paidIn.stream().map(Deposit::getId).toList());

        List<MoneyMovement> ledger = new ArrayList<>(paidIn.size() + takenOut.size());
        for (Deposit deposit : paidIn) {
            ledger.add(new MoneyMovement(
                    MoneyMovementDirection.INTO_SAVINGS,
                    deposit.getId(),
                    deposit.getSavingsAccountId(),
                    deposit.getSourceCurrentAccountId(),
                    // Quoted to the cent here, once, because this is where the amount leaves the
                    // module: SQLite has no decimal type and hands EUR 12.50 back as 12.5, and a
                    // ledger that quoted one movement to the cent and the next not would read as
                    // two different kinds of money.
                    quotedToTheCent(deposit.getAmount()),
                    earned.getOrDefault(deposit.getId(), PointsByReason.nothing()).total(),
                    deposit.getDepositedAt()));
        }
        for (Withdrawal withdrawal : takenOut) {
            ledger.add(new MoneyMovement(
                    MoneyMovementDirection.OUT_OF_SAVINGS,
                    withdrawal.getId(),
                    withdrawal.getSavingsAccountId(),
                    withdrawal.getDestinationCurrentAccountId(),
                    quotedToTheCent(withdrawal.getAmount()),
                    0,
                    withdrawal.getWithdrawnAt()));
        }
        ledger.sort(NEWEST_FIRST);
        // The counts of each direction and the accounts they were read from, so that a ledger that
        // looks short can be checked against the two halves it was merged from. Counts rather than
        // the movements themselves: this runs on every read of the history page, and one line per
        // movement would bury the business events in a page load.
        log.debug("money movements listed savingsAccounts={} intoSavings={} outOfSavings={} movements={}",
                savingsAccountIds.size(), paidIn.size(), takenOut.size(), ledger.size());
        return ledger;
    }
}
