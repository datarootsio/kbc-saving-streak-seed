package io.dataroots.savingstreak.accounts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private for the same reason as {@link CustomerRepository}. */
interface CurrentAccountRepository extends JpaRepository<CurrentAccount, Long> {

    List<CurrentAccount> findByCustomerId(Long customerId);

    /**
     * Who holds the account, as an identifier rather than as the Customer: the only thing asked of
     * it is whether it is the same person as somewhere else, and loading a customer to compare one
     * field would hand the whole entity out to answer a question about identity.
     */
    @Query("select account.customer.id from CurrentAccount account where account.id = :currentAccountId")
    Optional<Long> findHolderIdById(@Param("currentAccountId") long currentAccountId);
}
