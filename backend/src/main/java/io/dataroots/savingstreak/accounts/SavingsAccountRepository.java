package io.dataroots.savingstreak.accounts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private for the same reason as {@link CustomerRepository}. */
interface SavingsAccountRepository extends JpaRepository<SavingsAccount, Long> {

    List<SavingsAccount> findByCustomerId(Long customerId);

    @Query("select account.customer.name from SavingsAccount account where account.id = :savingsAccountId")
    Optional<String> findOwnerNameById(@Param("savingsAccountId") long savingsAccountId);

    /** Who holds the account, for the same reason as {@link CurrentAccountRepository#findHolderIdById}. */
    @Query("select account.customer.id from SavingsAccount account where account.id = :savingsAccountId")
    Optional<Long> findHolderIdById(@Param("savingsAccountId") long savingsAccountId);
}
