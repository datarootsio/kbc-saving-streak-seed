package be.kbc.savingstreak.repo;

import be.kbc.savingstreak.domain.SavingsPosition;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsPositionRepository extends JpaRepository<SavingsPosition, Long> {

    /** Open positions in one account, the ones that have been there longest first. */
    List<SavingsPosition> findByAccountIdAndPrincipalLeftCentsGreaterThanOrderByOpenedAtAscIdAsc(
            Long accountId, long minimumLeft);

    List<SavingsPosition> findByPrincipalLeftCentsGreaterThan(long minimumLeft);

    List<SavingsPosition> findByTransferIdIn(Collection<Long> transferIds);
}
