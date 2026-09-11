package io.dataroots.savingstreak.deposits;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link DepositsService}. */
interface DepositRepository extends JpaRepository<Deposit, Long> {

    List<Deposit> findBySavingsAccountId(long savingsAccountId);

    /**
     * Newest first, and the identifier settles it when two deposits share a moment: a moment is only
     * kept to the millisecond, and two deposits can land inside one.
     */
    List<Deposit> findBySavingsAccountIdOrderByDepositedAtDescIdDesc(long savingsAccountId);

    /** Oldest first, with the identifier settling ties at the millisecond the application records. */
    List<Deposit> findBySavingsAccountIdOrderByDepositedAtAscIdAsc(long savingsAccountId);

    /**
     * Every deposit into any of a set of savings accounts, newest first.
     *
     * <p>Several accounts at once because the ledger it feeds is a customer's rather than an
     * account's: somebody saving towards two goals moved their money once, and reading it back as
     * two histories to interleave by eye is not an answer.
     *
     * <p>Ordered here rather than in Java, and by the identifier as well as the moment, the way this
     * module's other listings are: a moment is only kept to the millisecond and two deposits can
     * land inside one.
     */
    @Query("select deposit from Deposit deposit "
            + "where deposit.savingsAccountId in :savingsAccountIds "
            + "order by deposit.depositedAt desc, deposit.id desc")
    List<Deposit> intoAnyOfNewestFirst(@Param("savingsAccountIds") Collection<Long> savingsAccountIds);

    /**
     * The deposits that landed in a stretch of time, oldest first, with the identifier settling ties
     * at the millisecond the application records.
     *
     * <p>Half-open — from the first moment inclusive, to the last exclusive — so that two adjacent
     * stretches agree about which of them owns the moment between them. A deposit on the stroke of
     * Monday belongs to the week beginning and not to both, and a closed interval on either side
     * would count it twice or not at all depending on which end was asked first.
     *
     * <p>By customer, because the stretch of time this answers about is a week of somebody's saving:
     * a week counts what they paid in, whichever of their savings accounts it went into. Which
     * account holds the money is a different question and {@link #findBySavingsAccountId} answers it.
     *
     * <p>Written out rather than derived from the method name: the name that says this reads
     * {@code findByCustomerIdAndDepositedAtGreaterThanEqualAndDepositedAtLessThanOrderBy...}, which
     * is a sentence nobody can check against the query it stands for.
     */
    @Query("select deposit from Deposit deposit "
            + "where deposit.customerId = :customerId "
            + "and deposit.depositedAt >= :from and deposit.depositedAt < :until "
            + "order by deposit.depositedAt asc, deposit.id asc")
    List<Deposit> landedBetween(@Param("customerId") long customerId,
                                @Param("from") Instant from,
                                @Param("until") Instant until);

    /**
     * Everything that landed before a moment, oldest first, with the identifier settling ties at the
     * millisecond the application records.
     *
     * <p>Exclusive of the moment itself, for the reason {@link #landedBetween} gives: a caller
     * splitting time at that moment gets each deposit on exactly one side of it. The two queries
     * agree about the boundary, so a caller can ask this for everything before a week and that for
     * the week itself and count nothing twice.
     *
     * <p>No lower bound, because there is nothing below the first deposit the customer ever made. A
     * caller walking the whole of somebody's saving back through the weeks wants exactly this.
     */
    @Query("select deposit from Deposit deposit "
            + "where deposit.customerId = :customerId "
            + "and deposit.depositedAt < :until "
            + "order by deposit.depositedAt asc, deposit.id asc")
    List<Deposit> landedBefore(@Param("customerId") long customerId,
                               @Param("until") Instant until);

    /**
     * Gives what remains to every deposit that has no answer to the question, and reports how many
     * that was.
     *
     * <p>A deposit recorded before the column existed says nothing about how much of it is left, and
     * all of it is: nothing could have taken any, because nothing could take money out of a savings
     * account when that deposit was made. Run at start-up by {@link DepositsOnStartUp}, which is what
     * lets the money balance be summed from what remains without a balance changing underneath
     * anybody the first time a database written before this is opened.
     *
     * <p>Only the deposits with nothing recorded, so that running it again on a database it has
     * already been through changes nothing — and so that a deposit a withdrawal has drawn down is
     * never handed its money back.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query("update Deposit deposit set deposit.remainingAmount = deposit.amount "
            + "where deposit.remainingAmount is null")
    int giveEveryDepositWhatRemainsOfIt();

    /**
     * The savings accounts behind every deposit that does not yet say whose saving it was, each named
     * once.
     *
     * <p>Which customer holds them is not this module's to know: {@link DepositsOnStartUp} asks
     * Accounts and comes back with the answer. Named accounts rather than deposits, so that a
     * customer with a hundred deposits into two accounts is two questions and two statements.
     */
    @Query("select distinct deposit.savingsAccountId from Deposit deposit "
            + "where deposit.customerId is null")
    List<Long> savingsAccountsBehindDepositsWithoutACustomer();

    /**
     * Says whose saving every deposit into one savings account was, and reports how many that was.
     *
     * <p>A deposit recorded before this says nothing about whose saving it was, and a week and a run
     * of weeks are now counted by exactly that: without this, a customer opening the application on
     * the morning of the upgrade has saved nothing this week and is on no run, whatever they paid in
     * yesterday. Only the deposits with nothing recorded, so a start after the first changes nothing.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query("update Deposit deposit set deposit.customerId = :customerId "
            + "where deposit.customerId is null and deposit.savingsAccountId = :savingsAccountId")
    int sayWhoseSavingWentInto(@Param("savingsAccountId") long savingsAccountId,
                               @Param("customerId") long customerId);
}
