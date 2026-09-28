package io.dataroots.savingstreak.sharedpots;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Package-private, for the same reason as {@link SharedPotRepository}. */
interface PotMembershipRepository extends JpaRepository<PotMembership, Long> {

    /**
     * Who belongs to one pot, in the order they joined it.
     *
     * <p>Which puts the owner who opened it first, and everybody who was invited after them behind
     * it — the order the story of the pot happened in, and the one a page can read down without
     * being told how to sort it. By the identifier after the moment, the idiom every ordered read in
     * this application uses: two members who joined in the same millisecond are otherwise in
     * whatever order the database felt like, and the later identifier is the later member.
     */
    List<PotMembership> findBySharedPotIdOrderByJoinedAtAscIdAsc(long sharedPotId);

    /** The same question asked of several pots at once, so that a customer's list is one query. */
    List<PotMembership> findBySharedPotIdInOrderByJoinedAtAscIdAsc(Collection<Long> sharedPotIds);

    /**
     * What one customer is to one pot, or nothing at all if they are not in it.
     *
     * <p>The question every rule about who may do what starts from, and the one the deposit pairing
     * asks: not "is this person a member" but "what are they to it", because being kept out and
     * being allowed only to watch are two different refusals and the caller has to be able to word
     * them differently.
     *
     * <p>One row or none, which the unique index below makes a fact rather than a hope.
     */
    Optional<PotMembership> findBySharedPotIdAndCustomerId(long sharedPotId, long customerId);

    /** Every pot this customer belongs to, whatever they are to it, oldest membership first. */
    List<PotMembership> findByCustomerIdOrderByJoinedAtAscIdAsc(long customerId);

    /**
     * Whether one membership per customer per pot is already a rule the database keeps, rather than
     * one this module merely intends.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, for the reason
     * {@code IncomePaidRepository} gives: {@code create unique index if not exists} is idempotent on
     * its own, and asking first is what lets the start-up step say whether it did anything.
     */
    @Query(value = "select count(*) from pragma_index_list('pot_membership') "
            + "where name = 'one_membership_per_customer_per_pot'", nativeQuery = true)
    long membershipIsAlreadyUniquePerCustomer();

    /**
     * Makes it so, over the pot and the customer.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite, and that dialect writes a composite unique
     * clause nowhere — declared as a unique constraint or as an index marked unique, the table is
     * created without it. A {@code create unique index} is a statement SQLite does accept, so this
     * is where the guarantee comes from.
     *
     * <p>What it protects is a person's role. A customer who appeared twice in one pot would hold
     * two roles at once, and every question this feature answers about what somebody may do would
     * have two answers with nothing to choose between them. Until a pot can be invited into there is
     * one row per pot and nothing can collide; the index is made now because it is the shape of the
     * table rather than the shape of one slice, and because a rule added after the rows it governs
     * is a rule that can fail to be added.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_membership_per_customer_per_pot "
            + "on pot_membership (shared_pot_id, customer_id)", nativeQuery = true)
    void makeMembershipUniquePerCustomer();
}
