package io.dataroots.savingstreak.sharedpots;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the rest of the application goes through {@link SharedPotsService}. */
interface SharedPotRepository extends JpaRepository<SharedPot, Long> {

    /**
     * The pots with these identifiers, oldest first.
     *
     * <p>One query for the whole of a customer's list rather than a fetch per membership, so that
     * somebody in five pots is read in one round trip and not five. Ordered by the identifier, which
     * on this table is the order they were opened in: a customer's list reads oldest first, the way
     * a list of things somebody started does.
     */
    List<SharedPot> findByIdInOrderByIdAsc(Collection<Long> ids);

    /**
     * The pot that holds this savings account, if any pot does.
     *
     * <p>The question asked from the other end, and the only one asked that way: everywhere else a
     * pot is named first and its account read off it. It is here because a deposit arrives naming
     * the account and nothing else — the endpoint is the savings account's, which is the whole point
     * of paying into a pot with the deposit call that already exists — so something has to be able
     * to get from the account back to the pot holding it.
     *
     * <p>At most one, and the pot is the only thing that says so: one account per pot is a rule
     * {@link SharedPot} argues for and no index enforces, because nothing in this application ever
     * writes a second pot against an account that already has one. An answer rather than a list, so
     * that a second one would fail loudly here rather than be quietly picked between.
     */
    Optional<SharedPot> findBySavingsAccountId(long savingsAccountId);
}
