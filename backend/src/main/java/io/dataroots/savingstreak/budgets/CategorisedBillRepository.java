package io.dataroots.savingstreak.budgets;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link BudgetsService}. */
interface CategorisedBillRepository extends JpaRepository<CategorisedBill, Long> {

    /**
     * Where one bill is filed, and only if the bill is on the account that was named.
     *
     * <p>Scoped by the account the same way a category and a bill are: an identifier somebody
     * guessed answers as a bill that is not filed anywhere rather than with somebody else's rent. It
     * is the whole of what this application can say about whose a bill is, because there is no
     * authentication to ask.
     */
    Optional<CategorisedBill> findByCurrentAccountIdAndBillId(long currentAccountId, long billId);

    /**
     * Every bill on one account that is in a category, in the order the bills were declared.
     *
     * <p>One query for the whole account rather than one per bill, so that drawing twenty bills with
     * their labels is two reads and not twenty-one. A bill in no category is simply missing from the
     * answer, which is the honest shape: there is no row, and an absence is exactly what the page
     * draws as "not in a category".
     *
     * <p>By bill identifier, which is the order the customer declared their bills in and therefore
     * the order the page already draws them in — a second ordering would make the labels and the
     * bills they belong to two lists that have to be reconciled.
     */
    List<CategorisedBill> findByCurrentAccountIdOrderByBillIdAsc(long currentAccountId);

    /**
     * Whether the index that makes one category per bill a rule the database keeps — rather than one
     * this application merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every
     * start after the first — reads one row and does nothing, and so that the start-up step can say
     * whether it did anything.
     */
    @Query(value = "select count(*) from pragma_index_list('categorised_bill') "
            + "where name = 'one_category_per_bill'", nativeQuery = true)
    long theBillsAreAlreadyInAtMostOneCategoryEach();

    /**
     * Makes it so, over the account and the bill.
     *
     * <p>Created here rather than declared on the entity for the reason
     * {@link SpendingCategoryRepository#makeTheStandingCategoriesUniquePerName} gives at length: the
     * SQLite dialect writes a composite unique clause nowhere, so a constraint on the class would be
     * an intention the database never hears about. A {@code create unique index} is a statement it
     * does accept.
     *
     * <p><strong>Not partial, unlike the categories' index.</strong> There is no state on this row
     * and nothing to exclude: a bill is in one category or in none, and a bill taken out of every
     * category leaves no row behind to compete with the next one. Ending the <em>category</em>
     * changes nothing here either — the row stands, pointing at a category that has ended, which is
     * the whole of what this slice promises about that.
     *
     * <p>Over the pair rather than over the bill alone, although a bill belongs to exactly one
     * account and the bill alone would be enough. The pair is what every read here is scoped by, and
     * an index that did not carry the account would make the one query that draws an account's
     * labels walk rows belonging to other accounts to find them.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_category_per_bill "
            + "on categorised_bill (current_account_id, bill_id)", nativeQuery = true)
    void makeEachBillFiledInAtMostOneCategory();
}
