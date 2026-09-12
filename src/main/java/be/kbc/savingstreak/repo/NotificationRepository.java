package be.kbc.savingstreak.repo;

import be.kbc.savingstreak.domain.Notification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);

    List<Notification> findByMemberIdAndReadAtIsNull(Long memberId);

    boolean existsByDedupeKey(String dedupeKey);

    int countByMemberIdAndReadAtIsNull(Long memberId);
}
