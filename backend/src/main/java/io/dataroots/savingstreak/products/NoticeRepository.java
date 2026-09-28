package io.dataroots.savingstreak.products;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * Every notice ever given on that account, oldest first — cancelled and spent ones included.
     *
     * <p>One ordering of an account's notices, used by the reading, by the refusal and by the
     * draw-down alike, for the reason the deposits' own ordering gives: which notice is used first
     * is a rule rather than an accident of storage, and two queries would be two answers to it.
     * The day first and the identifier second, so that two notices given on one morning are still
     * used in the order they were given.
     *
     * <p>Everything rather than only what is standing, because what a notice still covers is a
     * question about the row — cancelled, part-used or whole — and filtering it in SQL would put
     * half of {@link Notice#stillStanding()} into a query where nobody would look for it.
     */
    List<Notice> findBySavingsAccountIdOrderByGivenOnAscIdAsc(long savingsAccountId);

    Optional<Notice> findByIdAndSavingsAccountId(long id, long savingsAccountId);
}
