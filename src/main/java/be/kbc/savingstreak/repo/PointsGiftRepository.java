package be.kbc.savingstreak.repo;

import be.kbc.savingstreak.domain.PointsGift;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointsGiftRepository extends JpaRepository<PointsGift, Long> {

    @Query("""
            select gift from PointsGift gift
            where gift.fromMemberId = :memberId or gift.toMemberId = :memberId
            order by gift.createdAt desc, gift.id desc
            """)
    List<PointsGift> findAllForMember(@Param("memberId") Long memberId);

    @Query("""
            select coalesce(sum(gift.points), 0) from PointsGift gift
            where gift.fromMemberId = :memberId
            """)
    int sumSentBy(@Param("memberId") Long memberId);

    @Query("""
            select coalesce(sum(gift.points), 0) from PointsGift gift
            where gift.toMemberId = :memberId
            """)
    int sumReceivedBy(@Param("memberId") Long memberId);
}
