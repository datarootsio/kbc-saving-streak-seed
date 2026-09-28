package io.dataroots.savingstreak.products;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private: the rest of the application goes through {@link MaturitiesService}.
 *
 * <p><strong>Nothing here rewrites or deletes a settlement.</strong> A maturity that has been
 * settled is a morning that happened: the account was moved onto another agreement, or deliberately
 * left on the one it had, and every deposit and interest posting since has been decided under
 * whatever came out of it. A row that could be edited would be a row an account could be rolled
 * over twice out of.
 */
interface MaturitySettledRepository extends JpaRepository<MaturitySettled, Long> {

    /**
     * Which maturities of which of these accounts have already been settled — the account, the day
     * and the ordinal, and nothing else, because those three are the whole of what the sweep has to
     * know in order not to settle twice.
     *
     * <p>Every account at once rather than a question per account, so that a sweep over a hundred of
     * them is one query and not a hundred. The rows a sweep would otherwise read one at a time are
     * exactly the rows it is about to decide against — the same arrangement the loyalty and interest
     * sweeps already use, and for the same reason.
     *
     * <p>The day <em>and</em> the ordinal, because the sweep asks two different questions of them.
     * "Has the maturity falling on this date been settled" is the skip, and it is keyed on the date
     * because that is what a derived maturity can be compared against; "which number is this one" is
     * the ordinal, allocated one higher than the highest already recorded. The products and versions
     * are deliberately not asked for: they are the audit trail, and the sweep has no use for them.
     */
    @Query("select settled.savingsAccountId as savingsAccountId, "
            + "settled.maturityOrdinal as maturityOrdinal, settled.maturedOn as maturedOn "
            + "from MaturitySettled settled where settled.savingsAccountId in :savingsAccountIds")
    List<AMaturityAlreadySettled> maturitiesAlreadySettledFor(
            @Param("savingsAccountIds") Collection<Long> savingsAccountIds);

    /**
     * Whether the index that makes one settlement per account per maturity a rule the database keeps
     * — rather than one this module merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, for the reason every other guarantee
     * in this module gives: the ordinary start reads one row and does nothing, and the start-up step
     * can then say truthfully whether it did anything, which is the difference between a log a
     * reviewer can trust and one that always claims the same thing.
     */
    @Query(value = "select count(*) from pragma_index_list('maturity_settled') "
            + "where name = 'one_settlement_per_account_per_maturity'", nativeQuery = true)
    long aMaturityIsAlreadyUniquePerAccount();

    /**
     * Makes it so, over the account and the maturity ordinal.
     *
     * <p>Created here rather than declared on the entity because the entity cannot say it: this
     * schema is generated from the entity model against SQLite, and that dialect writes a composite
     * unique clause nowhere — {@code LoyaltyBonusPaid} discovered it first and
     * {@link MaturitySettled} restates what the generated DDL does instead. A unique index is a
     * statement SQLite does accept, so this is where the guarantee comes from, and it is a step a
     * reviewer can watch happen in a start-up log rather than an annotation to take on trust.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure. It carries its own transaction because its one
     * caller runs before the application has a transaction, a request, or a web server.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_settlement_per_account_per_maturity "
            + "on maturity_settled (savings_account_id, maturity_ordinal)", nativeQuery = true)
    void makeAMaturityUniquePerAccount();

    /** One maturity of one account that has already been settled, as the query above reports it. */
    interface AMaturityAlreadySettled {

        long getSavingsAccountId();

        int getMaturityOrdinal();

        LocalDate getMaturedOn();
    }
}
