package io.dataroots.savingstreak.sharedpots;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Package-private, for the same reason as {@link SharedPotRepository}. */
interface WithdrawalProposalAnswerRepository extends JpaRepository<WithdrawalProposalAnswer, Long> {

    /**
     * Every answer one proposal has had, in the order they were given.
     *
     * <p>Oldest first, which is the order the answering happened in and the one a page can read down
     * without being told how to sort it. By the identifier after the moment, the idiom every ordered
     * read in this application uses: a moment is only kept to the millisecond and two members can
     * answer inside one.
     */
    List<WithdrawalProposalAnswer> findByWithdrawalProposalIdOrderByAnsweredAtAscIdAsc(
            long withdrawalProposalId);

    /**
     * The same question asked of every proposal a pot has at once, so that listing them is one query
     * however long the list is — the arithmetic the pot's own list of members and names already
     * does.
     */
    List<WithdrawalProposalAnswer> findByWithdrawalProposalIdInOrderByAnsweredAtAscIdAsc(
            Collection<Long> withdrawalProposalIds);

    /**
     * Whether one answer per member per proposal is already a rule the database keeps, rather than
     * one this module merely intends.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, for the reason
     * {@link PotMembershipRepository#membershipIsAlreadyUniquePerCustomer} gives: {@code create
     * unique index if not exists} is idempotent on its own, and asking first is what lets the
     * start-up step say whether it did anything.
     */
    @Query(value = "select count(*) from pragma_index_list('withdrawal_proposal_answer') "
            + "where name = 'one_answer_per_member_per_proposal'", nativeQuery = true)
    long answeringIsAlreadyOncePerMember();

    /**
     * Makes it so, over the proposal and the customer.
     *
     * <p>Created here rather than declared on the entity, for the reason the membership index gives:
     * this schema is generated from the entity model against SQLite, and that dialect writes a
     * composite unique clause nowhere.
     *
     * <p>What it protects is the count that moves money. The last approval to arrive is the one the
     * withdrawal happens in, and "the last" is worked out by subtracting the members who have
     * answered from the members who must. A member whose approval was written twice would close that
     * subtraction on their own, and the pot would pay out on an assent somebody else never gave.
     * {@link SharedPotsService} refuses a second answer in words a person can read; this is what
     * makes the refusal a fact rather than a race the application usually wins.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_answer_per_member_per_proposal "
            + "on withdrawal_proposal_answer (withdrawal_proposal_id, customer_id)",
            nativeQuery = true)
    void makeAnsweringOncePerMember();
}
