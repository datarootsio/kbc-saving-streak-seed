package io.dataroots.savingstreak.loyalty;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link LoyaltyService}. */
interface LoyaltyBonusPaidRepository extends JpaRepository<LoyaltyBonusPaid, Long> {

    /**
     * Which anniversaries of which of these deposits have already been paid — the pairs and nothing
     * else, because that is the whole of what the sweep has to know in order not to pay twice.
     *
     * <p>Every deposit at once rather than a question per deposit, so that a sweep over a hundred
     * deposits is one query and not a hundred. The rows a sweep would otherwise read one at a time
     * are exactly the rows it is about to decide against.
     *
     * <p>The euros and the points are deliberately not asked for. They are the audit trail and the
     * sweep has no use for them; what a deposit has been paid altogether is read out of the points
     * ledger, which is where a customer's points live.
     */
    @Query("select bonus.depositId as depositId, bonus.anniversaryOrdinal as anniversaryOrdinal "
            + "from LoyaltyBonusPaid bonus where bonus.depositId in :depositIds")
    List<AnniversaryAlreadyPaid> anniversariesAlreadyPaidFor(
            @Param("depositIds") Collection<Long> depositIds);

    /**
     * Whether the index that makes one bonus per deposit per anniversary a rule the database keeps
     * — rather than one this application merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every
     * start after the first — reads one row and does nothing. {@code create unique index if not
     * exists} would be idempotent on its own; asking first is what lets the start-up step say
     * whether it did anything, which is the difference between a log a reviewer can trust and one
     * that always claims the same thing.
     */
    @Query(value = "select count(*) from pragma_index_list('loyalty_bonus_paid') "
            + "where name = 'one_bonus_per_deposit_per_anniversary'", nativeQuery = true)
    long theRecordIsAlreadyUniquePerAnniversary();

    /**
     * Makes it so, over the deposit and the ordinal.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite, and that dialect writes a composite unique
     * clause nowhere — {@link LoyaltyBonusPaid} says what the generated DDL does instead. A unique
     * index is a statement SQLite accepts, so this is where the guarantee comes from.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_bonus_per_deposit_per_anniversary "
            + "on loyalty_bonus_paid (deposit_id, anniversary_ordinal)", nativeQuery = true)
    void makeTheRecordUniquePerAnniversary();

    /** One anniversary this deposit has already been paid for, as the query above reports it. */
    interface AnniversaryAlreadyPaid {

        long getDepositId();

        int getAnniversaryOrdinal();
    }
}
