package io.dataroots.savingstreak.deposits;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: withdrawal records are read through {@link WithdrawalsService}. */
interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    /**
     * The purpose a query means when it says the money the customer took back out, spelled out in
     * full because that is what the query language wants and written once because three queries
     * want it.
     *
     * <p>The same arrangement, for the same reason,
     * {@link DepositRepository#THE_CUSTOMERS_OWN_MONEY} makes on the way in: a compile-time
     * constant reads as part of the rule in the query it is in rather than as an argument a caller
     * could hand in wrong. The rows it leaves out are the ones the bank charged, and every query
     * carrying it says why that matters.
     */
    String WHAT_THE_CUSTOMER_TOOK_BACK_OUT =
            "io.dataroots.savingstreak.deposits.WithdrawalPurpose.CUSTOMER";

    /**
     * The withdrawals one account's holder actually made, newest first — and never the charges the
     * bank took out of it.
     *
     * <p>This feeds the account's own list of what its holder has taken back, which is the reading
     * {@link DepositsService#depositsInto} gives on the way in: that listing leaves out the interest
     * the bank paid, and this one leaves out the charges the bank took, because both lists answer
     * "what have I done with this money" rather than "what has happened to this account". What has
     * happened to the account is the money-movement ledger, and both kinds of row are in it.
     */
    List<Withdrawal> findBySavingsAccountIdAndPurposeOrderByWithdrawnAtDescIdDesc(
            long savingsAccountId, WithdrawalPurpose purpose);

    /**
     * Every withdrawal out of any of a set of savings accounts, newest first, for the reason
     * {@link DepositRepository#intoAnyOfNewestFirst} gives: the ledger these feed is a customer's
     * rather than an account's.
     *
     * <p>Found by the accounts rather than by the customer, because a withdrawal does not record
     * whose it was — it names the savings account the money left and the current account it returned
     * to, and both were checked to be one customer's before it was allowed at all. Whoever asks has
     * already been told by Accounts which accounts are theirs.
     */
    @Query("select withdrawal from Withdrawal withdrawal "
            + "where withdrawal.savingsAccountId in :savingsAccountIds "
            + "order by withdrawal.withdrawnAt desc, withdrawal.id desc")
    List<Withdrawal> outOfAnyOfNewestFirst(
            @Param("savingsAccountIds") Collection<Long> savingsAccountIds);

    /**
     * Everything this customer took back out of savings inside a stretch of time, oldest first —
     * whichever of their savings accounts it left.
     *
     * <p>Found through their deposits, because a withdrawal records the savings account the money
     * left and not whose it was. The subquery is not a guess at ownership: money can only be
     * withdrawn from an account it was first paid into, so every account a withdrawal names is an
     * account this customer has a deposit in, and the deposit is where the application wrote down
     * whose saving it was. An account of somebody else's cannot appear, because their deposits are
     * not in the set.
     *
     * <p>Half-open, exactly as {@link DepositRepository#landedBetween} is and for the same reason:
     * whoever splits time into adjacent stretches has to get each movement in exactly one of them,
     * and the two halves of a week's net saving have to be counted across the same boundaries.
     *
     * <p><strong>The customer's own rows and never a charge the bank took.</strong> A week counts
     * what somebody moved, and an early-exit charge is not money they took back out — it is the
     * stated price of an agreement they ended. The spec holds the same line on the way in, where
     * interest is not new saving for the week because the bank added it; leaving a charge in here
     * would break a run of weeks as a side effect of a penalty that was already paid in euros.
     */
    @Query("select withdrawal from Withdrawal withdrawal "
            + "where withdrawal.savingsAccountId in "
            + "(select distinct deposit.savingsAccountId from Deposit deposit "
            + "where deposit.customerId = :customerId) "
            + "and withdrawal.purpose = " + WHAT_THE_CUSTOMER_TOOK_BACK_OUT + " "
            + "and withdrawal.withdrawnAt >= :from and withdrawal.withdrawnAt < :until "
            + "order by withdrawal.withdrawnAt asc, withdrawal.id asc")
    List<Withdrawal> takenOutBetween(@Param("customerId") long customerId,
                                     @Param("from") Instant from,
                                     @Param("until") Instant until);

    /**
     * Everything this customer took back out of savings before a moment, oldest first, found the
     * same way and bounded the same way.
     *
     * <p>Exclusive of the moment, so that this and {@link #takenOutBetween} split time at it
     * identically and a caller asking for both sides of a boundary counts nothing twice — and
     * blind to a charge the bank took, for the reason that query gives at length.
     */
    @Query("select withdrawal from Withdrawal withdrawal "
            + "where withdrawal.savingsAccountId in "
            + "(select distinct deposit.savingsAccountId from Deposit deposit "
            + "where deposit.customerId = :customerId) "
            + "and withdrawal.purpose = " + WHAT_THE_CUSTOMER_TOOK_BACK_OUT + " "
            + "and withdrawal.withdrawnAt < :until "
            + "order by withdrawal.withdrawnAt asc, withdrawal.id asc")
    List<Withdrawal> takenOutBefore(@Param("customerId") long customerId,
                                    @Param("until") Instant until);

    /**
     * Tells every withdrawal recorded before there was a word for it that it was money the customer
     * took back out, which is what every one of them was.
     *
     * <p>The mirror of {@link DepositRepository#sayThatEveryRecordedDepositWasTheCustomersOwnMoney}
     * and it exists for exactly that query's reason. The two week queries above ask the database
     * for rows whose purpose says so, and a row that says nothing is not one of them: comparing a
     * null to a value leaves it out. A file written by the release before this one holds nothing but
     * customers' withdrawals, and every one of them would drop out of both answers at once — so
     * somebody who took EUR 60 back out yesterday would open the application on the morning of the
     * upgrade to find the week counted only what they put in.
     *
     * <p>Only the rows with nothing recorded, so a start after the first changes nothing, and so
     * that no charge this bank wrote can ever be relabelled as something a customer took.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query("update Withdrawal withdrawal "
            + "set withdrawal.purpose = " + WHAT_THE_CUSTOMER_TOOK_BACK_OUT + " "
            + "where withdrawal.purpose is null")
    int sayThatEveryRecordedWithdrawalWasTakenByTheCustomer();
}
