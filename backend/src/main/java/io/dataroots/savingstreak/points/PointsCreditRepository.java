package io.dataroots.savingstreak.points;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link PointsService}. */
interface PointsCreditRepository extends JpaRepository<PointsCredit, Long> {

    /** Zero for a customer who has earned nothing, which is an answer rather than an absence. */
    @Query("select coalesce(sum(credit.remainingPoints), 0) from PointsCredit credit "
            + "where credit.customerId = :customerId")
    long remainingPointsOf(@Param("customerId") long customerId);

    /**
     * The batches a spend can draw from, oldest first, skipping those with nothing left in them.
     *
     * <p>Oldest first is the rule the whole batch structure exists for: points earned earliest are
     * spent earliest, so the ones nearest expiring leave first. The identifier settles it when two
     * batches share a moment, the same way the deposit list does — a moment is only kept to the
     * millisecond, and two deposits can land inside one.
     */
    @Query("select credit from PointsCredit credit "
            + "where credit.customerId = :customerId and credit.remainingPoints > 0 "
            + "order by credit.earnedAt asc, credit.id asc")
    List<PointsCredit> unspentOldestFirst(@Param("customerId") long customerId);

    /**
     * What each of the given deposits earned when it was made, one row per reason it earned under —
     * the batch as it was credited, not what is left of it, because what a deposit earned cannot
     * change afterwards.
     *
     * <p>The reasons are asked for rather than assumed, because a reference only means anything
     * alongside the reason that wrote it: the day a batch is earned for something other than a
     * deposit, its reference will be to that other thing and must not be read as a deposit's. The
     * caller names the reasons a deposit can earn under and gets every one of them back, so a
     * second way of earning shows up in the answer instead of being filtered out of it.
     */
    @Query("select credit.sourceReferenceId as sourceReferenceId, credit.reason as reason, "
            + "credit.points as points "
            + "from PointsCredit credit "
            + "where credit.reason in :reasons and credit.sourceReferenceId in :sourceReferenceIds")
    List<EarnedPoints> earnedBy(@Param("reasons") Collection<PointsReason> reasons,
                                @Param("sourceReferenceIds") Collection<Long> sourceReferenceIds);

    /**
     * Whether batches are still stored against the savings account they were earned in, which is how
     * every batch written before points belonged to a customer was stored.
     *
     * <p>Asked of the database's own catalogue rather than assumed, because the two shapes both
     * exist in the wild: a file this release created has never had the column, and a file the
     * previous release wrote still has it. Every statement below only means anything while it is
     * there, which is why they are never run without asking this first.
     */
    @Query(value = "select count(*) from pragma_table_info('points_credit') "
            + "where name = 'savings_account_id'", nativeQuery = true)
    long batchesStillNameTheSavingsAccountTheyWereEarnedIn();

    /**
     * The savings accounts behind every batch that has no customer on it yet, each named once.
     *
     * <p>Native, and the only thing that reads the old column: it is not on the entity any more,
     * because a batch belongs to a customer now and a mapped column would be a second answer to who
     * owns these points. Who holds these accounts is not this module's to know — {@link
     * PointsOnStartUp} asks Accounts and comes back with the answer.
     */
    @Query(value = "select distinct savings_account_id from points_credit where customer_id is null",
            nativeQuery = true)
    List<Long> savingsAccountsBehindBatchesWithoutACustomer();

    /**
     * Gives every ownerless batch earned in one savings account to the customer who holds it.
     *
     * <p>Only the batches that have no customer, so a start after the first changes nothing, and a
     * batch already credited to somebody is never reassigned.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "update points_credit set customer_id = :customerId "
            + "where customer_id is null and savings_account_id = :savingsAccountId",
            nativeQuery = true)
    int giveBatchesEarnedIn(@Param("savingsAccountId") long savingsAccountId,
                            @Param("customerId") long customerId);

    /**
     * Takes the old column away, once nothing in it is needed any more.
     *
     * <p>Dropped rather than left behind, and this is the one migration in the application that has
     * to do more than fill a column in. The column was written {@code not null}, so a batch credited
     * from now on — which names a customer and no account — could not be inserted beside it at all.
     * A column that cannot be written and is read by nothing is not history, it is a table that
     * refuses to be added to.
     */
    @Transactional
    @Modifying
    @Query(value = "alter table points_credit drop column savings_account_id", nativeQuery = true)
    void stopNamingTheSavingsAccountBatchesWereEarnedIn();

    /**
     * One batch as this query reports it: what earned it, why, and how many points that was worth.
     */
    interface EarnedPoints {

        long getSourceReferenceId();

        PointsReason getReason();

        long getPoints();
    }
}
