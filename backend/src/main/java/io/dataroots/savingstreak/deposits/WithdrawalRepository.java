package io.dataroots.savingstreak.deposits;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private: withdrawal records are read through {@link WithdrawalsService}. */
interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    List<Withdrawal> findBySavingsAccountIdOrderByWithdrawnAtDescIdDesc(long savingsAccountId);

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
}
