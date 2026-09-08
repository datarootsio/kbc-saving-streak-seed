package io.dataroots.savingstreak.rewards;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link RewardsService}. */
interface RedemptionRepository extends JpaRepository<Redemption, Long> {

    /**
     * Newest first, because someone checking what they have spent starts from what they claimed
     * last. The identifier settles it when two claims share a moment, as everywhere else.
     */
    List<Redemption> findByCustomerIdOrderByClaimedAtDescIdDesc(long customerId);

    /**
     * Whether claims are still stored against the savings account they were made from, which is how
     * every claim recorded before points belonged to a customer was stored.
     *
     * <p>Asked of the database's own catalogue rather than assumed, because both shapes exist in the
     * wild: a file this release created has never had the column, and a file the previous release
     * wrote still has it. Every statement below only means anything while it is there.
     */
    @Query(value = "select count(*) from pragma_table_info('redemption') "
            + "where name = 'savings_account_id'", nativeQuery = true)
    long claimsStillNameASavingsAccount();

    /**
     * The savings accounts behind every claim that has no customer on it yet, each named once.
     *
     * <p>Native, and the only thing that reads the old column: it is not on the entity any more,
     * because a claim is a customer's now. Who holds those accounts is not this module's to know —
     * {@link RewardsOnStartUp} asks Accounts and comes back with the answer.
     */
    @Query(value = "select distinct savings_account_id from redemption where customer_id is null",
            nativeQuery = true)
    List<Long> savingsAccountsBehindClaimsWithoutACustomer();

    /**
     * Gives every ownerless claim made from one savings account to the customer who holds it. Only
     * the claims that have no customer, so a start after the first changes nothing.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "update redemption set customer_id = :customerId "
            + "where customer_id is null and savings_account_id = :savingsAccountId",
            nativeQuery = true)
    int giveClaimsMadeFrom(@Param("savingsAccountId") long savingsAccountId,
                           @Param("customerId") long customerId);

    /**
     * Takes the old column away, once nothing in it is needed any more.
     *
     * <p>Dropped rather than left behind: it was written {@code not null}, so a claim made from now
     * on — which names a customer and no account — could not be inserted beside it at all.
     */
    @Transactional
    @Modifying
    @Query(value = "alter table redemption drop column savings_account_id", nativeQuery = true)
    void stopNamingTheSavingsAccountClaimsWereMadeFrom();
}
