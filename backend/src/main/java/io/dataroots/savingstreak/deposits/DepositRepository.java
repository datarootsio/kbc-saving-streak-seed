package io.dataroots.savingstreak.deposits;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the rest of the application goes through {@link DepositsService}. */
interface DepositRepository extends JpaRepository<Deposit, Long> {

    List<Deposit> findBySavingsAccountId(long savingsAccountId);

    /**
     * Newest first, and the identifier settles it when two deposits share a moment: a moment is only
     * kept to the millisecond, and two deposits can land inside one.
     */
    List<Deposit> findBySavingsAccountIdOrderByDepositedAtDescIdDesc(long savingsAccountId);
}
