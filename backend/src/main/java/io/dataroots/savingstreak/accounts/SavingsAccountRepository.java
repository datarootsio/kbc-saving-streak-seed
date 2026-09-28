package io.dataroots.savingstreak.accounts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Package-private for the same reason as {@link CustomerRepository}. */
interface SavingsAccountRepository extends JpaRepository<SavingsAccount, Long> {

    /**
     * The accounts this customer holds.
     *
     * <p>An account nobody holds matches nobody, which is the whole of what keeps a shared pot's
     * account out of every customer's list and out of their totals. Nothing here had to be written
     * to make that true: a link that is empty is equal to no identifier.
     */
    List<SavingsAccount> findByCustomerId(Long customerId);

    /**
     * Who holds the account, and what to call them, in one question. The name is what a page shows
     * and the identifier is what the modules that keep something per customer are keyed by — points
     * among them — and a caller that needed both used to have to ask twice.
     *
     * <p>Nothing at all for an account nobody holds, which is an answer and not a failure: the join
     * onto the customer finds nothing to join to, and "nobody" is what comes back. Every caller
     * already had to handle an account that is not there, and a holderless account arrives the same
     * way — {@link AccountsService#holderOfSavingsAccount} says what that costs and why it is right.
     */
    @Query("select new io.dataroots.savingstreak.accounts.AccountHolder("
            + "account.customer.id, account.customer.name) "
            + "from SavingsAccount account where account.id = :savingsAccountId")
    Optional<AccountHolder> findHolderById(@Param("savingsAccountId") long savingsAccountId);

    /**
     * Who holds the account, for the same reason as {@link CurrentAccountRepository#findHolderIdById}
     * — and nothing at all when nobody does, for the same reason as {@link #findHolderById}.
     */
    @Query("select account.customer.id from SavingsAccount account where account.id = :savingsAccountId")
    Optional<Long> findHolderIdById(@Param("savingsAccountId") long savingsAccountId);

    /**
     * Every savings account's identifier, oldest first, for a sweep that has to walk them all.
     *
     * <p>Identifiers rather than rows: what asks for these wants to consider one account at a time
     * and to ask about each one on its own terms, and a query that loaded the customers behind them
     * would fetch a graph nobody reads.
     *
     * <p>Every account, including the ones nobody holds. A sweep that asks who holds each one is
     * told "nobody" and moves on, which is the honest answer; leaving them out here would be this
     * query deciding on the sweeps' behalf what they are interested in.
     */
    @Query("select account.id from SavingsAccount account order by account.id")
    List<Long> findEveryId();

    /**
     * Whether a savings account in this database is still required to have a holding customer.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, because both shapes exist in the wild:
     * a file this release created has a column that may be empty, and a file an earlier release
     * wrote has one that may not. Schema generation adds columns and never relaxes one, so a
     * database written before pots existed would refuse the first pot's account at the database
     * rather than open it — which is what {@link AccountsOnStartUp} uses this to notice.
     */
    @Query(value = "select count(*) from pragma_table_info('savings_account') "
            + "where name = 'customer_id' and \"notnull\" = 1", nativeQuery = true)
    long aSavingsAccountStillHasToHaveAHolder();

    /**
     * Clears away a rebuilt table left behind by a start that was killed part-way through the four
     * statements below.
     *
     * <p>Only ever run while {@code savings_account} is still the table with the rows in it — which
     * is what {@link #aSavingsAccountStillHasToHaveAHolder} has just said — so what this drops is a
     * copy and never the record.
     *
     * <p>Each of these statements carries its own transaction because their one caller runs before
     * the application has a transaction, a request, or a web server: a statement that writes has to
     * say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "drop table if exists savings_account_rebuilt", nativeQuery = true)
    void clearAwayAnyHalfFinishedRebuild();

    /**
     * The same table with the holder made optional.
     *
     * <p>Rebuilt rather than altered, because SQLite has no statement for relaxing a column: {@code
     * alter table} adds, renames and drops, and the constraint is written into the table's own
     * definition. The shape below is the one schema generation writes for this entity now — the
     * identity primary key and the holder, nullable — so a database that goes through this is in the
     * shape a database created today is already in.
     */
    @Transactional
    @Modifying
    @Query(value = "create table savings_account_rebuilt "
            + "(id integer, customer_id bigint, primary key (id))", nativeQuery = true)
    void openATableWhoseHolderIsOptional();

    /** Every account there is, holder and all, moved across unchanged. */
    @Transactional
    @Modifying
    @Query(value = "insert into savings_account_rebuilt (id, customer_id) "
            + "select id, customer_id from savings_account", nativeQuery = true)
    int copyEverySavingsAccountAcross();

    /**
     * The old table goes, and the rebuilt one takes its name.
     *
     * <p>Two statements rather than one, because SQLite has no way to swap two tables. A start
     * killed between them leaves every account in {@code savings_account_rebuilt} — nothing is lost,
     * and the rename is the one statement it takes to finish by hand — which is the honest thing to
     * say about a rebuild this application has no migration tool to run for it.
     */
    @Transactional
    @Modifying
    @Query(value = "drop table savings_account", nativeQuery = true)
    void takeAwayTheTableThatRequiredAHolder();

    @Transactional
    @Modifying
    @Query(value = "alter table savings_account_rebuilt rename to savings_account", nativeQuery = true)
    void letTheRebuiltTableTakeItsName();
}
