package io.dataroots.savingstreak.accounts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private for the same reason as {@link CustomerRepository}. */
interface SavingsAccountRepository extends JpaRepository<SavingsAccount, Long> {

    List<SavingsAccount> findByCustomerId(Long customerId);

    /**
     * Who holds the account, and what to call them, in one question. The name is what a page shows
     * and the identifier is what the modules that keep something per customer are keyed by — points
     * among them — and a caller that needed both used to have to ask twice.
     */
    @Query("select new io.dataroots.savingstreak.accounts.AccountHolder("
            + "account.customer.id, account.customer.name) "
            + "from SavingsAccount account where account.id = :savingsAccountId")
    Optional<AccountHolder> findHolderById(@Param("savingsAccountId") long savingsAccountId);

    /** Who holds the account, for the same reason as {@link CurrentAccountRepository#findHolderIdById}. */
    @Query("select account.customer.id from SavingsAccount account where account.id = :savingsAccountId")
    Optional<Long> findHolderIdById(@Param("savingsAccountId") long savingsAccountId);
}
