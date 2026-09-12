package be.kbc.savingstreak.repo;

import be.kbc.savingstreak.domain.Account;
import be.kbc.savingstreak.domain.AccountType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByOrderBySortOrderAsc();

    @Query("select coalesce(sum(a.balanceCents), 0) from Account a where a.type = :type")
    long sumBalanceCentsByType(@Param("type") AccountType type);
}
