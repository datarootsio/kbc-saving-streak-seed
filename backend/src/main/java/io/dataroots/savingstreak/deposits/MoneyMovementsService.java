package io.dataroots.savingstreak.deposits;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <p><strong>A move between two of the customer's own savings accounts is one entry here, not
 * two.</strong> It is written into this module's ledgers as a row leaving one account and a row
 * arriving in the other, because that is what each account's balance is summed from — but the
 * customer did one thing, and a list that showed them two would be asking them to pair the halves
 * up by amount and moment. So the row that left reports the whole move whenever the account it
 * left was asked about, the row that arrived reports it when only the far end was, and the entry
 * names both accounts in the order the euros travelled. Both ends of a move belong to one person,
 * so a customer's own ledger always takes the first reading.
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
     * <p><strong>The interest the bank paid is in this list, and it is the one read in this module
     * that takes every row of the ledger rather than the customer's own.</strong> Interest postings
     * are euros, and this is a record of euros — which is exactly why a loyalty bonus still is not
     * in it. They arrive under a direction of their own, with no current account at either end, so
     * a reader can tell the money somebody moved from the money that arrived by itself without
     * adding anything up.
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

        // Which accounts were actually asked about, so that the half of a move whose other half is
        // already in this answer can be left out of it. A move is one thing the customer did and
        // is reported once; which of its two rows reports it depends on which of the two accounts
        // the question was about, and this set is how that is decided.
        Set<Long> asked = Set.copyOf(savingsAccountIds);
        List<MoneyMovement> ledger = new ArrayList<>(paidIn.size() + takenOut.size());
        for (Deposit deposit : paidIn) {
            MoneyMovementDirection direction = theDirectionOf(deposit.getOrigin());
            if (direction == MoneyMovementDirection.BETWEEN_SAVINGS_ACCOUNTS) {
                // The arriving half of a move, which the switch above has just said this row is.
                // It becomes a row of this ledger only when the account the euros came out of was
                // not asked about — otherwise the withdrawal on the other side is in this very
                // answer and reports the same move, and a customer who holds both accounts would
                // be shown one thing they did as two rows to pair up by eye. The branch is on the
                // direction rather than on the origin, so that what a row *is* has one place it is
                // decided and this is only what is done about it.
                if (!asked.contains(deposit.getMovedFromSavingsAccountId())) {
                    ledger.add(MoneyMovement.between(deposit.getId(),
                            deposit.getMovedFromSavingsAccountId(), deposit.getSavingsAccountId(),
                            quotedToTheCent(deposit.getAmount()), deposit.getDepositedAt()));
                }
                continue;
            }
            ledger.add(MoneyMovement.of(
                    direction,
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
            MoneyMovementDirection direction = theDirectionOf(withdrawal.getPurpose());
            if (direction == MoneyMovementDirection.BETWEEN_SAVINGS_ACCOUNTS) {
                // The leaving half of the same move, which the switch above has just said this row
                // is, and the half that reports it whenever the account it left is one of the
                // accounts asked about — which is every reading a customer ever sees, because both
                // ends of a move belong to the same person. One row, naming both accounts in the
                // order the euros travelled.
                ledger.add(MoneyMovement.between(withdrawal.getId(),
                        withdrawal.getSavingsAccountId(),
                        withdrawal.getDestinationSavingsAccountId(),
                        quotedToTheCent(withdrawal.getAmount()), withdrawal.getWithdrawnAt()));
                continue;
            }
            ledger.add(MoneyMovement.of(
                    direction,
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
        log.debug("money movements listed savingsAccounts={} rowsPaidIn={} interestPaidIn={} "
                        + "outOfSavings={} earlyExitCharges={} movements={}",
                savingsAccountIds.size(), paidIn.size(),
                paidIn.stream().filter(row -> row.getOrigin() == DepositOrigin.INTEREST).count(),
                takenOut.size(),
                takenOut.stream()
                        .filter(row -> row.getPurpose() == WithdrawalPurpose.AN_EARLY_EXIT_CHARGE)
                        .count(),
                ledger.size());
        // Said separately because it is the one count that does not add up the way the others do:
        // a move writes two rows into this module's ledgers and is one entry here, so a reader
        // checking the total against the two halves it was merged from needs to know how many
        // pairs were folded into one.
        log.debug("money movements folded the two halves of each move into one savingsAccounts={} "
                        + "movesReportedFromTheAccountTheyLeft={} "
                        + "movesReportedFromTheAccountTheyReached={}",
                savingsAccountIds.size(),
                takenOut.stream().filter(row ->
                        row.getPurpose() == WithdrawalPurpose.A_MOVE_TO_ANOTHER_SAVINGS_ACCOUNT)
                        .count(),
                paidIn.stream().filter(row ->
                        row.getOrigin() == DepositOrigin.MOVED_FROM_ANOTHER_SAVINGS_ACCOUNT
                                && !asked.contains(row.getMovedFromSavingsAccountId()))
                        .count());
        return ledger;
    }

    /**
     * Which way a deposit went, as the ledger reports it: the word a page reads the row by, decided
     * from the one column that says what kind of row it is.
     *
     * <p><strong>Every origin is named here, and none of them may be left to a default.</strong>
     * This is a switch over the whole of {@link DepositOrigin} with no {@code default} branch, so
     * it is exhaustive and the compiler says so: the next kind of deposit somebody invents will not
     * compile until it has been told, here, which way the money it describes moved. That is the fix
     * rather than a preference. Written as {@code INTEREST ? … : INTO_SAVINGS} this decision was
     * right only by arithmetic — two origins existed, one was tested for, and the other fell
     * through — and it stayed right through two further origins only because a branch further up
     * happened to lift the move out before the ternary ran. A reader could not see that, the
     * compiler could not check it, and the day it stopped being true no test in this application
     * would have failed: the third origin would simply have been drawn as money arriving from a
     * current account that was never debited. That is exactly the defect
     * {@code InterestService.signedFor} was hardened against one level below this, where a
     * direction read the wrong way round paid a customer interest on a penalty for the life of
     * their account. The direction is <em>written</em> here, so a wrong word written here is wrong
     * in every reader downstream at once.
     *
     * <p><strong>A move is decided here too, and that is the point of naming it.</strong> The
     * arriving half of a move between two of the customer's own savings accounts is a deposit like
     * any other in the ledger underneath, and it is the one whose direction the caller then acts
     * on — one row rather than two. Deciding it in this switch rather than in a branch above the
     * switch is what makes the switch honest: an exhaustive switch whose completeness depends on a
     * {@code continue} somewhere above it is not exhaustive, it only looks it, and the value that
     * would prove it is the one nobody remembers to add.
     */
    private static MoneyMovementDirection theDirectionOf(DepositOrigin origin) {
        return switch (origin) {
            case CUSTOMER -> MoneyMovementDirection.INTO_SAVINGS;
            case INTEREST -> MoneyMovementDirection.INTEREST_INTO_SAVINGS;
            case MOVED_FROM_ANOTHER_SAVINGS_ACCOUNT ->
                    MoneyMovementDirection.BETWEEN_SAVINGS_ACCOUNTS;
        };
    }

    /**
     * Which way a withdrawal went, as the ledger reports it, and the mirror of the reading above in
     * every respect.
     *
     * <p>Exhaustive over the whole of {@link WithdrawalPurpose} with no {@code default}, for the
     * argument the deposits make: this is where the word every downstream reader trusts is chosen,
     * and a purpose nobody has thought about here is not a safe row but a wrong one — money the
     * bank kept drawn as money the customer took back, or a move drawn as an everyday transfer to
     * an account that was never credited. Both of these enums have grown twice while this one
     * feature was being built, which is how long "every value is covered today" lasts.
     *
     * <p>Each purpose keeps its own word rather than sharing one. {@link #theDirectionOf} for a
     * deposit says why interest and a move are not deposits with a flag on them; a charge and a
     * move are not withdrawals with a flag on them for the same reason, and it is the direction
     * that carries the difference out of this module — nothing outside it names a purpose.
     */
    private static MoneyMovementDirection theDirectionOf(WithdrawalPurpose purpose) {
        return switch (purpose) {
            case CUSTOMER -> MoneyMovementDirection.OUT_OF_SAVINGS;
            case AN_EARLY_EXIT_CHARGE -> MoneyMovementDirection.AN_EARLY_EXIT_CHARGE;
            case A_MOVE_TO_ANOTHER_SAVINGS_ACCOUNT ->
                    MoneyMovementDirection.BETWEEN_SAVINGS_ACCOUNTS;
        };
    }
}
