package be.kbc.savingstreak.repo;

import be.kbc.savingstreak.domain.Transfer;
import be.kbc.savingstreak.domain.TransferDirection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    List<Transfer> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Query("""
            select coalesce(sum(t.amountCents), 0)
            from Transfer t
            where t.direction = :direction and t.createdAt >= :since
            """)
    long sumAmountSince(@Param("direction") TransferDirection direction, @Param("since") Instant since);

    /** New savings, meaning the part above the savings peak, booked since a moment in time. */
    @Query("""
            select coalesce(sum(t.newSavingsCents), 0)
            from Transfer t
            where t.createdAt >= :since
            """)
    long sumNewSavingsSince(@Param("since") Instant since);
}
