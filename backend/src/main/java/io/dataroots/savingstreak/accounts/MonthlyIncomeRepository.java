package io.dataroots.savingstreak.accounts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private for the same reason as {@link CustomerRepository}. */
interface MonthlyIncomeRepository extends JpaRepository<MonthlyIncome, Long> {

    Optional<MonthlyIncome> findByCurrentAccountId(long currentAccountId);

    /**
     * Every declaration there is, in the order they were made, for the nightly job — which has
     * nobody's account in front of it and has to walk them all.
     *
     * <p>All of them on every run, for the reason {@code AccountsService.everySavingsAccount} gives
     * about the nightly sweep: with the seeded customers this is a handful of rows, and being handed
     * them a page at a time is a change to make when there is a population rather than before. An
     * account with no declaration is not a row here at all, so the job's work is already bounded by
     * how many customers have said anything.
     */
    List<MonthlyIncome> findAllByOrderByIdAsc();

    /**
     * Takes the declaration away, and answers how many rows that was — which is what tells a
     * withdrawal of something from a withdrawal of nothing, so the log can say which happened
     * without a second query.
     */
    long deleteByCurrentAccountId(long currentAccountId);
}
