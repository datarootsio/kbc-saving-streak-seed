package io.dataroots.savingstreak.products;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private: the rest of the application goes through {@link InterestService}.
 *
 * <p><strong>Nothing here rewrites or deletes a posting.</strong> A month that has been judged is a
 * month that happened: the euros are in the savings ledger, the customer has read the arithmetic,
 * and the next month's average was worked out over a balance this one raised. A row that could be
 * edited would be a row somebody could be paid twice out of.
 */
interface InterestPostingRepository extends JpaRepository<InterestPosting, Long> {

    /**
     * Every month this account has been judged for, oldest first.
     *
     * <p>Oldest first because this reads as a story — what the account was worth in January, and
     * what each month since has paid — and because a screen drawing it wants the periods in the
     * order they happened rather than in whatever order the rows were written.
     *
     * <p>Including the months that paid nothing, which is not an oversight. A customer whose
     * account was empty in March is owed the row that says so, and a list that silently skipped it
     * would leave them counting months to work out which one was missing.
     */
    List<InterestPosting> findBySavingsAccountIdOrderByPeriodOrdinalAsc(long savingsAccountId);

    /**
     * Which periods of which of these accounts have already been judged — the pairs and nothing
     * else, because that is the whole of what the sweep has to know in order not to pay twice.
     *
     * <p>Every account at once rather than a question per account, so that a sweep over a hundred
     * of them is one query and not a hundred. The rows a sweep would otherwise read one at a time
     * are exactly the rows it is about to decide against. The same arrangement the loyalty sweep
     * already uses, and for the same reason.
     *
     * <p>The balances, the rate and the euros are deliberately not asked for. They are the audit
     * trail and the sweep has no use for them; what a month paid is read back through the account's
     * own list of postings.
     */
    @Query("select posting.savingsAccountId as savingsAccountId, "
            + "posting.periodOrdinal as periodOrdinal "
            + "from InterestPosting posting where posting.savingsAccountId in :savingsAccountIds")
    List<APeriodAlreadyJudged> periodsAlreadyJudgedFor(
            @Param("savingsAccountIds") Collection<Long> savingsAccountIds);

    /**
     * Whether the index that makes one posting per account per period a rule the database keeps —
     * rather than one this module merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, for the reason every other guarantee
     * in this module gives: the ordinary start reads one row and does nothing, and the start-up
     * step can then say truthfully whether it did anything.
     */
    @Query(value = "select count(*) from pragma_index_list('interest_posting') "
            + "where name = 'one_posting_per_account_per_period'", nativeQuery = true)
    long aPostingIsAlreadyUniquePerPeriod();

    /**
     * Makes it so, over the account and the period ordinal.
     *
     * <p>Created here rather than declared on the entity because the entity cannot say it: this
     * schema is generated from the entity model against SQLite, and that dialect writes a composite
     * unique clause nowhere — {@code LoyaltyBonusPaid} discovered it first and {@link InterestPosting}
     * restates what the generated DDL does instead. A unique index is a statement SQLite does
     * accept, so this is where the guarantee comes from, and it is a step a reviewer can watch
     * happen in a start-up log rather than an annotation they would have to take on trust.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting
     * against one file cannot race each other into a failure. It carries its own transaction
     * because its one caller runs before the application has a transaction, a request, or a web
     * server.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_posting_per_account_per_period "
            + "on interest_posting (savings_account_id, period_ordinal)", nativeQuery = true)
    void makeAPostingUniquePerPeriod();

    /** One period of one account that has already been judged, as the query above reports it. */
    interface APeriodAlreadyJudged {

        long getSavingsAccountId();

        int getPeriodOrdinal();
    }
}
