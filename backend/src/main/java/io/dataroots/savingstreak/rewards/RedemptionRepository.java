package io.dataroots.savingstreak.rewards;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the rest of the application goes through {@link RewardsService}. */
interface RedemptionRepository extends JpaRepository<Redemption, Long> {

    /**
     * Newest first, because someone checking what they have spent starts from what they claimed
     * last. The identifier settles it when two claims share a moment, as everywhere else.
     */
    List<Redemption> findBySavingsAccountIdOrderByClaimedAtDescIdDesc(long savingsAccountId);
}
