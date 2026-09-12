package be.kbc.savingstreak.repo;

import be.kbc.savingstreak.domain.Redemption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RedemptionRepository extends JpaRepository<Redemption, Long> {

    List<Redemption> findAllByOrderByCreatedAtDescIdDesc();
}
