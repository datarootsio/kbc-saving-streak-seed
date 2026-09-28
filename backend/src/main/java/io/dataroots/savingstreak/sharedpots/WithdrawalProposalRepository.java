package io.dataroots.savingstreak.sharedpots;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private, for the same reason as {@link SharedPotRepository}. */
interface WithdrawalProposalRepository extends JpaRepository<WithdrawalProposal, Long> {

    /**
     * Every proposal one pot has ever had, in any state, in the order they were made.
     *
     * <p>All of them and not only the waiting ones, because this is a record rather than an inbox: a
     * proposal that vanished when it was rejected would leave the pot with no trace that anybody
     * ever said no, and the member who proposed it looking at a list that says they never asked. The
     * panel that empties is a page's own filter over these rows.
     *
     * <p>Oldest first, which is the order the asking happened in and the one a page can read down
     * without being told how to sort it. By the identifier after the moment, the idiom every ordered
     * read in this application uses: a moment is only kept to the millisecond and two proposals can
     * be made inside one.
     */
    List<WithdrawalProposal> findBySharedPotIdOrderByProposedAtAscIdAsc(long sharedPotId);
}
