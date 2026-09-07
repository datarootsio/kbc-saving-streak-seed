package io.dataroots.savingstreak.points;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private: the rest of the application goes through {@link PointsService}. */
interface PointsCreditRepository extends JpaRepository<PointsCredit, Long> {

    /** Zero for an account that has earned nothing, which is an answer rather than an absence. */
    @Query("select coalesce(sum(credit.remainingPoints), 0) from PointsCredit credit "
            + "where credit.savingsAccountId = :savingsAccountId")
    long remainingPointsOf(@Param("savingsAccountId") long savingsAccountId);

    /**
     * The batches a spend can draw from, oldest first, skipping those with nothing left in them.
     *
     * <p>Oldest first is the rule the whole batch structure exists for: points earned earliest are
     * spent earliest, so the ones nearest expiring leave first. The identifier settles it when two
     * batches share a moment, the same way the deposit list does — a moment is only kept to the
     * millisecond, and two deposits can land inside one.
     */
    @Query("select credit from PointsCredit credit "
            + "where credit.savingsAccountId = :savingsAccountId and credit.remainingPoints > 0 "
            + "order by credit.earnedAt asc, credit.id asc")
    List<PointsCredit> unspentOldestFirst(@Param("savingsAccountId") long savingsAccountId);

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
     * One batch as this query reports it: what earned it, why, and how many points that was worth.
     */
    interface EarnedPoints {

        long getSourceReferenceId();

        PointsReason getReason();

        long getPoints();
    }
}
