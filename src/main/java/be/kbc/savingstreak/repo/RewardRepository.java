package be.kbc.savingstreak.repo;

import be.kbc.savingstreak.domain.Reward;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRepository extends JpaRepository<Reward, Long> {

    List<Reward> findAllByOrderBySortOrderAsc();
}
