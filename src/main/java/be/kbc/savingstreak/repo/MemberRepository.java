package be.kbc.savingstreak.repo;

import be.kbc.savingstreak.domain.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findFirstByPrimaryCustomerTrue();

    /** Everyone the signed-in customer can send points to. */
    List<Member> findByPrimaryCustomerFalseOrderByFirstNameAsc();
}
