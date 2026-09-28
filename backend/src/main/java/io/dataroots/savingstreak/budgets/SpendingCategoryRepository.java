package io.dataroots.savingstreak.budgets;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link BudgetsService}. */
interface SpendingCategoryRepository extends JpaRepository<SpendingCategory, Long> {

    /**
     * The categories in one state on one account, in the order they were declared.
     *
     * <p>By identifier rather than alphabetically, for the reason a bill's list is in declaration
     * order: the order a customer wrote their words in is the order they recognise their own list
     * in, and a list that re-sorted itself when somebody fixed a spelling would be a list that moved
     * under them. Sorting by name is a reading, and belongs to whoever is drawing it.
     *
     * <p>A state rather than two methods, so that "what am I describing my money with" and "what did
     * I stop using" are one question asked twice rather than two queries that could drift.
     */
    List<SpendingCategory> findByCurrentAccountIdAndStateInOrderByIdAsc(long currentAccountId,
                                                                        List<CategoryState> states);

    /**
     * Every category this account has ever had, standing and ended alike, in declaration order.
     *
     * <p>For whoever is printing what a split was filed under rather than for anybody choosing a
     * category to file under. Ending a category leaves every euro ever filed under it exactly where
     * it is, so a spend recorded in March against a category ended in April still has to say
     * "Groceries" when it is read back in June — and a list of the standing ones would answer that
     * lookup with nothing and draw a real record as an unknown one.
     *
     * <p>Without a state at all rather than with every state passed in, so that a value added to
     * {@link CategoryState} later cannot quietly fall out of this answer. That is the opposite of
     * the reasoning above it, and deliberately: there the states are the question, and here they are
     * beside the point.
     */
    List<SpendingCategory> findByCurrentAccountIdOrderByIdAsc(long currentAccountId);

    /**
     * Every category on any of these accounts, standing and ended alike, in declaration order.
     *
     * <p>The same question as the one above it asked of a holding rather than of one account, and it
     * is what prints a split: the money-movement ledger merges the spends of every everyday account
     * somebody holds into one list, and every part of every split on it has to say what it was filed
     * under. One read for the whole page rather than one per account, for the reason the read above
     * gives about one per part — and one query rather than two, so that the ledger and the
     * recent-spends page cannot come to disagree about what an ended category is called.
     *
     * <p>Keyed on the category's own identifier by whoever reads it, which is unique across the
     * table and therefore across accounts; there is no need for the account to come back with it.
     */
    List<SpendingCategory> findByCurrentAccountIdInOrderByIdAsc(Collection<Long> currentAccountIds);

    /**
     * One category, and only if it is on the account that was named.
     *
     * <p>Scoped by the account the same way a bill is: a category identifier somebody guessed
     * answers as a category that is not there rather than with somebody else's groceries. It is the
     * whole of what this application can say about whose a category is, because there is no
     * authentication to ask.
     */
    Optional<SpendingCategory> findByIdAndCurrentAccountId(long categoryId, long currentAccountId);

    /**
     * Whether that word is already one of the things this account's money goes on.
     *
     * <p>In one of the states given rather than in any state at all, which is the whole point: a
     * name whose category was ended is free again, because an ended category is a record of months
     * already gone rather than a word still in use. The states travel in from
     * {@link CategoryState#theOnesStillStanding} so that what "standing" means stays in one place.
     *
     * <p>This is the check that produces the sentence a customer reads. The <em>guarantee</em> is
     * the partial unique index below, and the two are different things — see
     * {@link BudgetsOnStartUp}.
     */
    Optional<SpendingCategory> findByCurrentAccountIdAndNameAndStateIn(long currentAccountId,
                                                                       String name,
                                                                       List<CategoryState> states);

    /**
     * How many categories are standing on this account, for the cap.
     *
     * <p>Counted rather than fetched, because the cap is a number and loading twenty rows to call
     * {@code size()} on them would be reading a list to ask its length.
     */
    long countByCurrentAccountIdAndStateIn(long currentAccountId, List<CategoryState> states);

    /**
     * Whether the index that makes one standing category of each name per account a rule the
     * database keeps — rather than one this application merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every
     * start after the first — reads one row and does nothing. {@code create unique index if not
     * exists} would be idempotent on its own; asking first is what lets the start-up step say
     * whether it did anything, which is the difference between a log a reviewer can trust and one
     * that always claims the same thing.
     */
    @Query(value = "select count(*) from pragma_index_list('spending_category') "
            + "where name = 'one_standing_category_of_each_name_per_account'", nativeQuery = true)
    long theStandingCategoriesAreAlreadyUniquePerName();

    /**
     * Makes it so, over the account and the name, for the standing ones only.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite, and that dialect writes a composite unique
     * clause nowhere — declared as a unique constraint or as an index marked unique, the table is
     * created without it and the only statement that reaches the database is a drop that does
     * nothing. A {@code create unique index} is a statement SQLite does accept.
     *
     * <p><strong>Partial, which is the whole shape of the rule.</strong> A category that has been
     * ended is a record of months already gone, so the name it was using is free again and the
     * customer may declare it afresh; an index over the pair alone would refuse that and make
     * ending a category quietly destroy the word. {@code where state = 'STANDING'} is how SQLite
     * says "only the rows that are competing", and it is the counterpart of the nulls-are-distinct
     * trick {@code GoalsOnStartUp} leans on for abandoned goals. The literal is the value
     * {@code @Enumerated(EnumType.STRING)} writes, which is why {@link CategoryState#STANDING} must
     * keep its name for the index to keep its meaning.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists "
            + "one_standing_category_of_each_name_per_account "
            + "on spending_category (current_account_id, name) where state = 'STANDING'",
            nativeQuery = true)
    void makeTheStandingCategoriesUniquePerName();
}
