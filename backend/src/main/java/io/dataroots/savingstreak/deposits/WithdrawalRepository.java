package io.dataroots.savingstreak.deposits;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: withdrawal records are read through {@link WithdrawalsService}. */
interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    List<Withdrawal> findBySavingsAccountIdOrderByWithdrawnAtDescIdDesc(long savingsAccountId);
}
