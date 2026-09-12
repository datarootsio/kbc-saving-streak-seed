package be.kbc.savingstreak.repo;

import be.kbc.savingstreak.domain.PointsLot;
import be.kbc.savingstreak.domain.PointsSource;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointsLotRepository extends JpaRepository<PointsLot, Long> {

    /** Batches one customer can still spend, the ones expiring soonest first. */
    List<PointsLot> findByMemberIdAndPointsRemainingGreaterThanAndExpiresAtAfterOrderByExpiresAtAscIdAsc(
            Long memberId, int minimumRemaining, Instant moment);

    Optional<PointsLot> findFirstByMemberIdAndPointsRemainingGreaterThanAndExpiresAtAfterOrderByExpiresAtAscIdAsc(
            Long memberId, int minimumRemaining, Instant moment);

    @Query("""
            select coalesce(sum(lot.pointsRemaining), 0)
            from PointsLot lot
            where lot.memberId = :memberId and lot.expiresAt > :moment
            """)
    int sumSpendableAt(@Param("memberId") Long memberId, @Param("moment") Instant moment);

    /** Points that were never spent before they lapsed. */
    @Query("""
            select coalesce(sum(lot.pointsRemaining), 0)
            from PointsLot lot
            where lot.memberId = :memberId and lot.expiresAt <= :moment
            """)
    int sumLapsedAt(@Param("memberId") Long memberId, @Param("moment") Instant moment);

    @Query("select coalesce(sum(lot.pointsEarned), 0) from PointsLot lot where lot.memberId = :memberId")
    int sumEverCredited(@Param("memberId") Long memberId);

    @Query("""
            select coalesce(sum(lot.pointsEarned), 0)
            from PointsLot lot
            where lot.memberId = :memberId and lot.source = :source
            """)
    int sumEverCreditedFrom(@Param("memberId") Long memberId, @Param("source") PointsSource source);

    List<PointsLot> findByTransferIdIn(Collection<Long> transferIds);
}
