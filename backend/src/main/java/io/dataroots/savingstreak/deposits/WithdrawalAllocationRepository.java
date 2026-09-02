package io.dataroots.savingstreak.deposits;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: allocation records are an implementation fact of moving money out. */
interface WithdrawalAllocationRepository extends JpaRepository<WithdrawalAllocation, Long> {

    List<WithdrawalAllocation> findByWithdrawalIdOrderByIdAsc(long withdrawalId);
}
