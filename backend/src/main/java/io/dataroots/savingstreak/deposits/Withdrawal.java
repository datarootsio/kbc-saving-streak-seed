package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * One movement out of a savings account: back to one of its holder's current accounts, or into the
 * bank's pocket as the price of breaking an agreement early.
 *
 * <p><strong>Two kinds of row rather than two tables</strong>, for the reason {@link DepositOrigin}
 * gives about the same decision on the way in: an early-exit charge is money that left the savings
 * account, so it has to be a row of the thing that says what a savings account holds. Written here
 * it draws the deposits down like any other withdrawal, writes an allocation per row it touches and
 * appears in the record of money that moved, all of it for nothing. {@link WithdrawalPurpose} is
 * the column that tells the two apart and carries the argument in full.
 */
@Entity
class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long savingsAccountId;

    /**
     * The current account the money went back to, and nothing at all on a charge.
     *
     * <p>A {@code Long} rather than a {@code long} since a charge has no other end: the euros do
     * not go back to the customer, so an identifier put here to keep the column full would be a row
     * claiming a current account it never credited, and a page drawing "from → to" would print a
     * lie. The same null, for the same reason, that a month's interest carries on the way in.
     */
    private Long destinationCurrentAccountId;

    /**
     * The savings account the money went to, on a row written by a move, and nothing at all on
     * every other row.
     *
     * <p><strong>The other end of the one movement out of savings whose other end is savings.</strong>
     * A withdrawal names a current account, a charge names none because the euros stayed with the
     * bank, and a move names this — a second savings account, of the same customer's, settled before
     * a cent was allowed to leave. Written down rather than inferred from the deposit on the other
     * side, because inferring it would mean matching two rows by amount and moment, which is exactly
     * the pairing-up that reporting a move as one row exists to spare a reader.
     *
     * <p>Null on every row that is not a move, and nothing backfills it: an absence means "not a
     * move", which is what the purpose beside it says from the other side. The two are written in
     * one breath by {@link #movedToAnotherSavingsAccount}.
     */
    private Long destinationSavingsAccountId;

    private BigDecimal amount;
    private Instant withdrawnAt;

    /**
     * Which of the two kinds of row this is, written as a word rather than inferred from the
     * absent account: the purpose is the fact, and the missing identifier is a consequence of it.
     *
     * <p>Stored as its name rather than as an ordinal, like every other enum this application keeps,
     * so that a row means the same thing after somebody adds a value in the middle of the enum.
     */
    @Enumerated(EnumType.STRING)
    private WithdrawalPurpose purpose;

    protected Withdrawal() {
        // for JPA
    }

    private Withdrawal(long savingsAccountId, Long destinationCurrentAccountId, BigDecimal amount,
                       Instant withdrawnAt, WithdrawalPurpose purpose) {
        this.savingsAccountId = savingsAccountId;
        this.destinationCurrentAccountId = destinationCurrentAccountId;
        this.amount = amount;
        this.withdrawnAt = withdrawnAt;
        this.purpose = purpose;
    }

    /** Money the customer took back out, into a current account of their own. */
    static Withdrawal madeByTheCustomer(long savingsAccountId, long destinationCurrentAccountId,
                                        BigDecimal amount, Instant withdrawnAt) {
        return new Withdrawal(savingsAccountId, destinationCurrentAccountId, amount, withdrawnAt,
                WithdrawalPurpose.CUSTOMER);
    }

    /**
     * The stated price of breaking an agreement early, taken out of the account that agreed to it.
     *
     * <p>No destination, because there is none: the charge is what the bank kept, and the customer's
     * current account is not touched by it. A factory rather than a flag on the constructor above,
     * so that the call site reads as the event it is and so that nothing can write a charge that
     * quietly credits somebody.
     */
    static Withdrawal anEarlyExitCharge(long savingsAccountId, BigDecimal amount,
                                        Instant chargedAt) {
        return new Withdrawal(savingsAccountId, null, amount, chargedAt,
                WithdrawalPurpose.AN_EARLY_EXIT_CHARGE);
    }

    /**
     * Money leaving one savings account for another of the same customer's, as one half of a move.
     *
     * <p>No current account, because there is none: the euros never reach the customer's everyday
     * money, which is the whole of what makes a move a move rather than a withdrawal somebody
     * happened to follow with a deposit. A factory rather than a flag, so that the call site reads
     * as the event it is and so that nothing can write a move that quietly credits a current
     * account as well.
     *
     * @param destinationSavingsAccountId the account that received it, settled as the same
     *                                    customer's before this is called
     */
    static Withdrawal movedToAnotherSavingsAccount(long savingsAccountId,
                                                   long destinationSavingsAccountId,
                                                   BigDecimal amount, Instant movedAt) {
        Withdrawal moved = new Withdrawal(savingsAccountId, null, amount, movedAt,
                WithdrawalPurpose.A_MOVE_TO_ANOTHER_SAVINGS_ACCOUNT);
        moved.destinationSavingsAccountId = destinationSavingsAccountId;
        return moved;
    }

    Long getId() { return id; }

    /** Which savings account the money left, which is what a ledger of one is assembled by. */
    long getSavingsAccountId() { return savingsAccountId; }
    BigDecimal getAmount() { return amount; }

    /** Where it went, and nothing at all on a charge — which is what the purpose beside it says. */
    Long getDestinationCurrentAccountId() { return destinationCurrentAccountId; }

    /** The savings account it went to, and nothing at all unless this row is half of a move. */
    Long getDestinationSavingsAccountId() { return destinationSavingsAccountId; }
    Instant getWithdrawnAt() { return withdrawnAt; }

    /**
     * Why the money left, and {@link WithdrawalPurpose#CUSTOMER} for a row written before there was
     * a word for it — which {@link WithdrawalsOnStartUp} fills in before the application serves
     * anything, so the reading here is a backstop rather than the answer anybody relies on.
     */
    WithdrawalPurpose getPurpose() {
        return purpose == null ? WithdrawalPurpose.CUSTOMER : purpose;
    }
}
