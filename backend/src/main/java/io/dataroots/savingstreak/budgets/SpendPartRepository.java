package io.dataroots.savingstreak.budgets;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the rest of the application goes through {@link SpendsService}. */
interface SpendPartRepository extends JpaRepository<SpendPart, Long> {

    /**
     * The parts of several spends at once, in the order they were written.
     *
     * <p>All of them in one query rather than one query per spend, because the list this feeds is a
     * page of spends and asking fifty times for two rows each is fifty round trips to answer one
     * question. The caller groups them by the spend they belong to, which is arithmetic rather than
     * a second read.
     *
     * <p>In the order they were written, so that a split typed as thirty to Groceries and twenty
     * unfiled is read back in that order — the order the customer entered is the order they check
     * their own arithmetic in, and it is the same reason a category list is in declaration order.
     */
    List<SpendPart> findBySpendIdInOrderByIdAsc(Collection<Long> spendIds);

    /**
     * The parts of one spend, in the order they were written.
     *
     * <p>For the correction, which is the one caller that has a single spend in hand rather than a
     * page of them. It reads the old split before replacing it, because a correction that could not
     * say what the parts <em>were</em> would leave the log with only half the story — and what a
     * reviewer is checking is the move from one category to another rather than the destination.
     */
    List<SpendPart> findBySpendIdOrderByIdAsc(long spendId);
}
