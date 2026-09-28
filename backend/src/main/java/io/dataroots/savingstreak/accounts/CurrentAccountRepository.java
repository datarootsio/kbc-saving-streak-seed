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

    /**
     * The account as the screen that belongs to it reads it: what it is called, what is in it and
     * who holds it, in one question.
     *
     * <p>A projection rather than the entity, for the reason {@link SavingsAccountRepository}'s own
     * holder query gives: what leaves this module is a record, and loading the customer behind the
     * account to read two of its fields would hand the whole graph out to answer a question about
     * one account.
     */
    @Query("select new io.dataroots.savingstreak.accounts.WhatACurrentAccountHolds("
            + "account.id, account.iban, account.balance, account.customer.id, account.customer.name) "
            + "from CurrentAccount account where account.id = :currentAccountId")
    Optional<WhatACurrentAccountHolds> findWhatItHoldsById(@Param("currentAccountId") long currentAccountId);

    @Query("select account.id from CurrentAccount account order by account.id")
    List<Long> findEveryId();
}
