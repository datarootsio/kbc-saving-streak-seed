package io.dataroots.savingstreak.products;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private: the rest of the application goes through {@link ProductsService}.
 *
 * <p><strong>Nothing here deletes an agreement.</strong> An account is living under the row, every
 * deposit into it is stamped with the version the row names, and an interest posting will point at
 * the same pair. A row that went away would leave all of them unanswerable. Closing an account is a
 * later ticket and it does not remove the record of what the account was on.
 */
interface AccountAgreementRepository extends JpaRepository<AccountAgreement, Long> {

    /** What one account is living under, or nothing at all when nothing has recorded it yet. */
    Optional<AccountAgreement> findBySavingsAccountId(long savingsAccountId);

    /**
     * Every savings account that is already on a product, by identifier.
     *
     * <p>Identifiers rather than rows, and all of them in one query, because the start-up migration
     * asks exactly one question of this table: which of the accounts Accounts has heard of are not
     * in it. One query against one column answers that for the whole database, where a lookup per
     * account would be one round trip per account on every start for ever.
     */
    @Query("select agreement.savingsAccountId from AccountAgreement agreement")
    List<Long> everySavingsAccountAlreadyOnAProduct();

    /**
     * Every agreement there is, in the order the accounts were put on their products.
     *
     * <p>The rows rather than the identifiers, because the one caller is the nightly interest sweep
     * and it needs all of what each row says: the product and version to price the month with, the
     * day the account was opened to count the periods from, and the day interest starts to know
     * which of those periods this bank will pay for.
     *
     * <p>All of them in one query, like the migration above and for the same reason: a sweep that
     * asked per account would be one round trip per account every night, and the rows it reads are
     * exactly the rows it is about to decide against. A training application has a handful of
     * accounts and a real one would index the next period rather than walk to find it — which the
     * spec notes and does not build.
     */
    @Query("select agreement from AccountAgreement agreement order by agreement.id asc")
    List<AccountAgreement> everyAgreement();

    /**
     * Says that interest counts from a given day for every agreement that does not yet say when it
     * counts from, and reports how many that was.
     *
     * <p><strong>The migration that keeps the promise about backdating for an existing file.</strong>
     * An agreement written by the release before this one is dated at the day its account's money
     * first arrived and says nothing about interest, because there was none. Read that date as the
     * day interest starts and a lab that has been running since the spring would wake up owed half
     * a year of it on the morning of the upgrade; leave it null and the sweep can never pay the
     * account at all. Told the day of this start, both problems go away: the account is paid for
     * the periods that begin from here, which is exactly what "no interest is backdated" means.
     *
     * <p>Only the rows with nothing recorded, so that a start after the first changes nothing — and
     * so that an account which has already been paid for a month cannot have its starting day
     * moved out from under the postings that were worked out with it.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query("update AccountAgreement agreement set agreement.interestCountsFrom = :from "
            + "where agreement.interestCountsFrom is null")
    int sayInterestCountsFrom(@Param("from") LocalDate from);

    /**
     * Whether the index that makes one agreement per savings account a rule the database keeps —
     * rather than one this module merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, for the reason
     * {@link ProductTermsRepository#aVersionIsAlreadyUniquePerProduct} gives: the ordinary start
     * reads one row and does nothing, and the step can then say truthfully whether it did anything.
     */
    @Query(value = "select count(*) from pragma_index_list('account_agreement') "
            + "where name = 'one_agreement_per_savings_account'", nativeQuery = true)
    long anAgreementIsAlreadyUniquePerSavingsAccount();

    /**
     * Makes it so, over the savings account.
     *
     * <p>Created here rather than declared on the entity because a {@code unique = true} column is
     * one more thing the SQLite dialect writes into the generated table only sometimes, and this
     * module has already decided once that a guarantee it can watch happen in a start-up log beats
     * an annotation it would have to take on trust. Two agreements for one account would be two
     * answers to what that account is living under, and both the reading and the stamping of a
     * deposit would then depend on which row came back first.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting
     * against one file cannot race each other into a failure. It carries its own transaction
     * because its one caller runs before the application has a transaction, a request, or a web
     * server.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_agreement_per_savings_account "
            + "on account_agreement (savings_account_id)", nativeQuery = true)
    void makeAnAgreementUniquePerSavingsAccount();
}
